# 多版本支持

基于 [Stonecutter](https://stonecutter.kikugie.dev/),单一源码树编译出多个 Minecraft 版本。版本节点在 `settings.gradle.kts` 声明,每版参数(loader / parchment / java)在 `build.gradle.kts` 的 `VersionSpec` 里。

## 版本矩阵

| 版本 | LWJGL | 渲染世代 | 关键 API 边界 |
|---|---|---|---|
| 1.19.2 | 3.3.1 | 预 GuiGraphics | `GuiGraphics` 不存在 → `PoseStack` 适配层;`MaterialColor`/`com.mojang.math`/`Registry` 旧名 |
| 1.20.1 | 3.3.2 | 立即模式 | 基线旧世代 |
| 1.21.1 | 3.3.3 | 立即模式 + 1.20.5 API | `BuiltInRegistries`/`PotionContents`/`SectionRenderDispatcher`/4 参 `mouseScrolled` |
| 1.21.8 | 3.3.4* | 1.21.5 渲染重写 | `RenderPipeline`/`GuiRenderState`,但**无** submit-node/`Identifier` 改名 |
| 1.21.11 | 3.3.4* | submit-node | `MouseButtonEvent`/`KeyEvent`、`ResourceLocation→Identifier`、`SubmitNodeCollector`、`renderer.rendertype`/`renderer.feature` 包重组 |

\* `lwjgl-glfw` 在 `>=1.21.5` 版本上被强制升到 3.3.4(GLFW preedit API,见 [IME 文档](ime-support.md)),其余模块保持 Mojang 原版。

## Stonecutter 判定边界(`//? if ...`)
MC 在 1.21.5→1.21.11 之间分多次做了破坏性改动,代码按"每个改动各自的真实版本边界"分流,而不是一个总开关:

- `>=1.21.5`:渲染重写(RenderPipeline / GuiRenderState / 着色器 builder)
- `>=1.21.11`:输入事件对象、`Identifier` 改名、`window.handle()`、submit-node、包重组
- `>=1.20.5`:components/`BuiltInRegistries`/Scoreboard/Holder 化附魔音效/4 参签名
- `>=1.20`:`GuiGraphics` 存在与否(1.19.2 用适配层)
- 支持 `&&` 区间,如 `>=1.21.5 && <1.21.11`(1.21.8 专属)

## 渲染世代与构建产物
- 共用一个 MC 解耦的 CEF(`mcef-nova`),所有版本共享。
- `isLegacyRender`(1.19.2/1.20.1/1.21.1)走立即模式 CEF 渲染 + 1.20.1 access widener;`>=1.21.5` 走 `GuiRenderState`/`RenderPipeline` 桥。
- `GuiGraphics` 适配层:`top.fpsmaster.compat.GuiGraphics`(仅 1.19.2 编译)把 1.20.1 GuiGraphics API 子集映射到 1.19.2 的 `PoseStack` 调用。

## 按版本门控的复杂渲染特性(暂未适配)
策略:**HUD/UI 全量可用;需要逐版本专属变体的复杂渲染 mixin 暂门控**(各版本有独立 `fpsmaster-<版本>.mixins.json` + 源码排除)。这些特性的核心功能在其它世代可用,只是该版本上暂缺:

- **1.21.1**(10 项):LevelRenderer 区块限流 + 方块覆盖、钓鱼线、披风、名牌血量、运动模糊后处理、LivingEntity 渲染微调、屏幕模糊、屏幕特效、标题屏背景、鞘翅层。
- **1.21.8**(13 项):submit-node 世代的 Cape/Wings/NameTag/LivingEntity/ItemEntity/Debug/Hitbox/LevelRenderer/ItemInHand/GameRenderer/RenderType/Minecraft + 钓鱼线。
- **1.19.2**(15 项):上述同类渲染 + 标题屏 FPSMaster 按钮(`Button.builder` 为 1.19.4+);此外聊天发送(AutoGG/快捷指令)因 1.19.2 聊天签名 API 不同而为 no-op。

> 这些是"按需逐版本补"的清单,不是缺陷——核心客户端(模块、HUD、Web UI)在所有版本可用。

## 新增一个版本
1. `settings.gradle.kts` 的 `versions(...)` 加上版本号。
2. `build.gradle.kts` 的 `VersionSpec` when 加一条(loader / parchment / java);如属立即模式世代,加入 `isLegacyRender` / `usesLegacyHelpers` 集合。
3. `./gradlew :<版本>:compileKotlin :<版本>:compileJava`,按报错逐个用 `//?` 判定把差异 API 分流。
4. 复杂渲染 mixin 编不过时:生成 `fpsmaster-<版本>.mixins.json`(从最接近的现有配置去掉门控项)+ 在 `build.gradle.kts` 排除其源码,并在 `mixinConfig` 的 when 里指向它。
5. `./gradlew :<版本>:runClient` 实测(mixin `@At` 命中、渲染、Web UI)。
