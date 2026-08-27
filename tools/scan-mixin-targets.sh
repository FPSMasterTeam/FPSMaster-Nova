#!/bin/bash
# 对一个版本：遍历该版本 mixins.json 注册的全部 mixin，
# 解析活跃 Stonecutter 分支里的 @Mixin 目标类与每个 method= 目标，
# 去 named jar 核对：方法是否存在、无 descriptor 时是否有多重载（多重载=注入失败风险）。
NOVA=$(cd "$(dirname "$0")/.." && pwd)
JAVAP=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/javap
V=$1
if [ "$V" = "1.21.11" ]; then SRC=$NOVA/src/main/java; JSON=$NOVA/src/main/resources/fpsmaster.mixins.json
else SRC=$NOVA/versions/$V/build/generated/stonecutter/main/java; JSON=$NOVA/src/main/resources/fpsmaster-$V.mixins.json; fi
JAR=$(find $NOVA/.gradle/loom-cache/minecraftMaven -path "*$V*" -name "*.jar" 2>/dev/null | grep -viE sources | head -1)
[ -z "$JAR" ] && { echo "[$V] 无 jar"; exit 0; }
EX=/tmp/scanjar_$V
[ -d "$EX" ] || { mkdir -p $EX; (cd $EX && unzip -o -q "$JAR" 'net/minecraft/*' 2>/dev/null); }

python3 - "$SRC" "$JSON" "$EX" "$JAVAP" "$V" <<'PY'
import re,sys,json,subprocess,os
src,jsonf,ex,javap,ver=sys.argv[1:6]
names=json.load(open(jsonf))['client']
for n in names:
    f=os.path.join(src,'top/fpsmaster/mixin/impl',n+'.java')
    if not os.path.exists(f): continue
    s=open(f).read()
    s=re.sub(r'/\*.*?\*/','',s,flags=re.S)          # 去掉未激活的 stonecutter 分支
    s=re.sub(r'^\s*//.*$','',s,flags=re.M)           # 去掉行注释
    m=re.search(r'@Mixin\(\s*([A-Za-z0-9_.]+)\.class',s)
    if not m: continue
    imports=dict((b,a+'.'+b) for a,b in re.findall(r'^import\s+([\w.]+)\.(\w+);',s,re.M))
    cls=m.group(1); cls=imports.get(cls,cls)
    if not cls.startswith('net.minecraft'): continue
    p=os.path.join(ex,cls.replace('.','/')+'.class')
    if not os.path.exists(p):
        print(f"[{ver}] {n} -> {cls}  X class missing"); continue
    try: out=subprocess.run([javap,'-p',p],capture_output=True,text=True,timeout=30).stdout
    except Exception: continue
    # 目标方法名 -> 重载数
    for target in re.findall(r'method\s*=\s*(\{[^}]*\}|"[^"]*")',s):
        for t in re.findall(r'"([^"]+)"',target):
            base=t.split('(')[0]; hasdesc='(' in t
            cnt=len(re.findall(r'[ .]'+re.escape(base)+r'\(',out))
            if cnt==0:
                print(f"[{ver}] {n}  method={base}  X NOT FOUND")
            elif cnt>1 and not hasdesc:
                print(f"[{ver}] {n}  method={base}  ! {cnt} overloads, no descriptor")
PY

# 判读说明（重要）：
# "! N overloads, no descriptor" 只是线索，不是失败判据。Mixin 能容忍泛型桥方法这类
# 同名重载——1.21.8 实测启动通过时，本脚本仍报若干条该告警。真正会 fail 的是：
#   1. 匹配到的某个重载里根本没有注入点（报 "Scanned 0 target(s)"）
#   2. handler 形参与目标签名不符（报 "Invalid descriptor ... Expected ... but found ..."）
# 所以本脚本用于「改完 mixin 后先看一眼有没有明显不对」，不能替代真实 runClient。
# "X NOT FOUND" 更值得看，但双名并列（如 renderArmWithItem/submitArmWithItem）会互报
# NOT FOUND，属预期。<init> 一律误报。
