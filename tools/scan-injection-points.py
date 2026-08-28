#!/usr/bin/env python3
"""对一个版本核对每个 @At(value="INVOKE"/"INVOKE_ASSIGN") 的调用点是否真的存在于目标方法体内。

scan-mixin-targets.sh 只核对「目标方法存在」，证明不了「注入点存在」——而
`Scanned 0 target(s)` 类的启动崩溃恰恰都出在后者：方法找得到，@At 指的那个
invoke 在这一代里换了名字（1.19.2 的 ChatComponent 用 Font.drawShadow 而不是
Font.draw）、换了签名，或者压根不在这个重载里。

做法：javap -c 目标类，逐方法收集全部 invoke 指令的 `name:descriptor`，再拿
mixin 里写的 @At target 去比对。

用法：tools/scan-injection-points.py <version>

判读边界（和 scan-mixin-targets.sh 一样，告警不等于失败）：
  - MISSING  = 目标方法里找不到这个调用。基本都是真 bug，会 Scanned 0 target(s)。
  - OWNER    = name+desc 对得上但 owner 类名不同。多半没问题：javac 会把继承来的
               静态方法按调用处的类当 owner 写进常量池（ChatComponent.fill 就是
               这样），Mixin 也按常量池里的 owner 匹配。需要人工看一眼。
  - SKIP     = 注入到 lambda / 合成方法，或 method= 用了通配符，本脚本不展开。
本脚本同样不能替代真实 runClient。
"""
import json
import os
import re
import subprocess
import sys

NOVA = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVAP = "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/javap"

# @At(value = "INVOKE", target = "Lowner/Class;name(args)ret")
AT_RE = re.compile(
    r'@At\s*\(\s*value\s*=\s*"(INVOKE|INVOKE_ASSIGN)"\s*,\s*target\s*=\s*"'
    r'L([^;]+);([^(]+)(\([^)]*\)[^"]*)"'
)
# 注入注解块：从 @Inject/@Redirect/... 到紧随其后的方法签名
ANNOT_RE = re.compile(
    r'@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|WrapOperation|WrapWithCondition)\s*\('
)
METHOD_RE = re.compile(r'method\s*=\s*"([^"]+)"')
MIXIN_RE = re.compile(r'@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z0-9_.]+)\.class')
IMPORT_RE = re.compile(r'^import\s+([\w.]+)\.(\w+);', re.M)


def strip_inactive(src):
    """去掉未激活的 stonecutter 分支（/* *​/ 包起来的）与行注释。"""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'^\s*//.*$', '', src, flags=re.M)
    return src


def balanced_block(text, start):
    """从 text[start] 处的 '(' 起，返回配平括号内的内容。"""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                return text[start + 1:i]
    return ""


def javap_methods(class_path):
    """返回 {方法名: [(descriptor, [调用点...])]}；调用点为 (owner, name, desc)。"""
    try:
        out = subprocess.run(
            [JAVAP, "-p", "-c", "-s", class_path],  # -s 才会打印 descriptor 行
            capture_output=True, text=True, check=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return {}

    methods = {}
    cur_name = cur_desc = None
    # javap 的 "  descriptor: (…)V" 行给出精确签名，比解析人类可读的签名可靠
    for line in out.splitlines():
        # javap 把 descriptor 行缩进 4 空格，方法头缩进 2 空格
        m = re.match(r'\s+descriptor: (\S+)', line)
        if m:
            cur_desc = m.group(1)
            continue
        # 方法头：形如 "  public void render(...);" —— 取括号前最后一个标识符
        m = re.match(r'\s{2}[\w\s<>\[\].,$]*?([\w$<>]+)\(.*\);?$', line)
        if m and 'descriptor:' not in line:
            cur_name = m.group(1)
            cur_desc = None
            continue
        # invokeinterface / invokedynamic 的常量池索引后面还跟 ", N"
        m = re.match(r'\s+\d+: invoke\w+\s+#\d+(?:,\s*\d+)?\s+//\s+(?:InterfaceMethod|Method)\s+(\S+)', line)
        if m and cur_name and cur_desc:
            ref = m.group(1)
            # 形如 owner/Class.name:(desc)ret，或省略 owner 的 name:(desc)ret（即本类）
            if ':' not in ref:
                continue
            lhs, desc = ref.split(':', 1)
            if '.' in lhs:
                owner, name = lhs.rsplit('.', 1)
            else:
                owner, name = None, lhs
            methods.setdefault((cur_name, cur_desc), []).append((owner, name, desc))
    return methods


def main():
    if len(sys.argv) < 2:
        sys.exit("用法: scan-injection-points.py <version>")
    ver = sys.argv[1]

    if ver == "1.21.11":
        src_root = os.path.join(NOVA, "src/main/java")
        mixin_json = os.path.join(NOVA, "src/main/resources/fpsmaster.mixins.json")
    else:
        src_root = os.path.join(NOVA, f"versions/{ver}/build/generated/stonecutter/main/java")
        mixin_json = os.path.join(NOVA, f"src/main/resources/fpsmaster-{ver}.mixins.json")

    if not os.path.isdir(src_root):
        sys.exit(f"[{ver}] 没有生成源码，先跑 ./gradlew :{ver}:compileJava")

    extracted = f"/tmp/scanjar_{ver}"
    if not os.path.isdir(extracted):
        jar = subprocess.run(
            f'find {NOVA}/.gradle/loom-cache/minecraftMaven -path "*{ver}*" -name "*.jar" '
            f'| grep -viE sources | head -1',
            shell=True, capture_output=True, text=True,
        ).stdout.strip()
        if not jar:
            sys.exit(f"[{ver}] 找不到 named jar")
        os.makedirs(extracted, exist_ok=True)
        subprocess.run(["unzip", "-o", "-q", jar, "net/minecraft/*"], cwd=extracted)

    names = json.load(open(mixin_json))["client"]
    problems = 0
    checked = 0

    for entry in names:
        path = os.path.join(src_root, "top/fpsmaster/mixin/impl", entry.replace('.', '/') + ".java")
        if not os.path.exists(path):
            continue
        src = strip_inactive(open(path).read())

        m = MIXIN_RE.search(src)
        if not m:
            continue
        imports = {b: f"{a}.{b}" for a, b in IMPORT_RE.findall(src)}
        target_cls = imports.get(m.group(1), m.group(1))
        if not target_cls.startswith("net.minecraft"):
            continue
        cls_file = os.path.join(extracted, target_cls.replace('.', '/') + ".class")
        if not os.path.exists(cls_file):
            print(f"[{ver}] {entry}  ! 目标类不在 jar 里: {target_cls}")
            problems += 1
            continue

        table = javap_methods(cls_file)

        for am in ANNOT_RE.finditer(src):
            block = balanced_block(src, am.end() - 1)
            mm = METHOD_RE.search(block)
            at = AT_RE.search(block)
            if not mm or not at:
                continue
            sel = mm.group(1)
            _kind, at_owner, at_name, at_desc = at.groups()
            at_owner = at_owner.replace('/', '.')

            if '*' in sel or sel.startswith("lambda$"):
                print(f"[{ver}] {entry}.{sel}  SKIP 通配/lambda 选择器，本脚本不展开")
                continue

            # method= 可能带 descriptor
            if '(' in sel:
                sel_name, sel_desc = sel.split('(', 1)
                sel_desc = '(' + sel_desc
                cands = [k for k in table if k[0] == sel_name and k[1] == sel_desc]
            else:
                sel_name = sel
                cands = [k for k in table if k[0] == sel_name]

            if not cands:
                # 可能是 intermediary 名（method_xxxxx），named jar 里查不到
                if re.fullmatch(r'method_\d+', sel_name):
                    print(f"[{ver}] {entry}.{sel}  SKIP intermediary 名，named jar 无法核对")
                else:
                    print(f"[{ver}] {entry}  ! 目标方法不存在: {sel}")
                    problems += 1
                continue

            checked += 1
            hit_exact = False
            hit_by_sig = []
            for key in cands:
                for owner, name, desc in table[key]:
                    if name != at_name or desc != at_desc:
                        continue
                    owner_dotted = (owner or target_cls.replace('.', '/')).replace('/', '.')
                    if owner_dotted == at_owner:
                        hit_exact = True
                    else:
                        hit_by_sig.append((key, owner_dotted))
            if hit_exact:
                continue
            if hit_by_sig:
                key, actual = hit_by_sig[0]
                print(f"[{ver}] {entry}.{sel}  OWNER @At 写 {at_owner}，字节码里是 {actual}"
                      f"（{at_name}{at_desc}）")
                continue

            # 给出同名但签名不同的候选，方便直接看出该改成什么
            near = sorted({
                f"{name}{desc}"
                for key in cands for _o, name, desc in table[key]
                if name == at_name or desc == at_desc
            })
            hint = f"  同方法体内相近调用: {', '.join(near[:4])}" if near else ""
            print(f"[{ver}] {entry}.{sel}  MISSING 找不到 {at_owner}.{at_name}{at_desc}{hint}")
            problems += 1

    print(f"[{ver}] 核对 {checked} 个注入点，可疑 {problems} 个")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
