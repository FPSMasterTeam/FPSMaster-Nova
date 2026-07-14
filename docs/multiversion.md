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
| 26.2 | 3.3.4* | 延迟渲染(extract-render-state) | **未混淆游戏**(见下);`GuiGraphics→GuiGraphicsExtractor`(经 `GuiGraphics26` shim)、`drawString→text`、Screen `render→extractRenderState`/`renderBackground→extractBackground`、`Minecraft.setScreen→gui.setScreen`、`Minecraft.screen→gui.screen()`、`Options.hideGui` 移除、`TextureFormat→GpuFormat`、`Tesselator` 移除、`RenderPipeline.Builder` fluent API 改名、需 **JDK 25** 编译 |

\* `lwjgl-glfw` 在 `>=1.21.5` 版本上被强制升到 3.3.4(GLFW preedit API,见 [IME 文档](ime-support.md)),其余模块保持 Mojang 原版。

## 26.x 未混淆游戏(与 1.x 完全不同的接入)

MC 26.x 是 Mojang 年份制且 **游戏未混淆**——不再发布 client_mappings,jar 自带真名。接入方式(已跑通,详见记忆 `nova-mc26-unobfuscated-build`):

- **工具链**:`fabric-loom 1.17.14` + Gradle `9.5.1`;26.2 是 Java 25 字节码,`build.gradle.kts` 的 toolchain 用 `maxOf(spec.java, 21)`(26.2 的 VersionSpec java=25),跑 gradle 的 daemon 也须 JDK 25。
- **无 mappings**:不加 `mappings` 依赖,改为 Gradle 属性 `fabric.loom.disableObfuscation=true`(`settings.gradle.kts` 的 `beforeProject` 按 `版本major>=26` 逐节点设,避免污染 1.x)。开启后 Loom 不注册 `mappings`/`modImplementation` 等 remap 配置 → 共享 build 脚本里改用字符串 `add("mappings"/"implementation", …)`;access widener 头用 `official` 命名空间(`fpsmaster-26.2.accesswidener`)。
- **GuiGraphics shim**:`top.fpsmaster.compat.GuiGraphics26` 包裹 `GuiGraphicsExtractor`,把旧 `GuiGraphics` API(drawString/fill/blit/pose…)映射过去;30 个 HUD/UI 文件经 import swap `import …GuiGraphics26 as GuiGraphics`(`//? if >=26`)。文本类 HUD(FPS/CPS/坐标/延迟…)完全可用;物品渲染(renderItem*)stub 占位。
- **HUD 绘制钩子**:`MixinGui` 注入 `Gui.extractRenderState`,`@Shadow` 私有 `guiRenderState` → new `GuiGraphicsExtractor` → shim → `HudManager.render`(HUD 实际渲染)。

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
- **26.2**(15 门控 mixin):MixinChatComponent/EditBox/EntityRenderer/GameRenderer/GuiGraphics/ItemEntityRenderer/LevelRenderer/LightTexture/LivingEntityRenderer/NameTagFeatureRenderer/PlayerTabOverlay/Screen/ScreenEffectRenderer/ScreenHud/WingsLayer(延迟渲染 extract-render-state 重写)。**26.2 已可用**:文本类 HUD(MixinGui 注入 `Gui.extractRenderState` 实渲)、**Web UI 网页渲染**(CEF 帧经 `BrowserOwnedTexture` CPU 上传成 RGBA8 纹理 + `GuiGraphicsExtractor.blit(view, sampler, …)` 记入延迟渲染态;`accelerate` 恒 false 走软件渲染)、模块/配置/网络/鉴权/命令/按键、各 2D Screen。**仍暂缓(原生/3D)**:① CEF **零拷贝加速**纹理(`GlTexture` 反射 ctor 变、`GpuFormat` 改名 → `AcceleratedBrowserTexture` stub,自定义 bgra shader 走 `ShaderManager` stub,改用 CPU 上传 + CPU 端 B↔R swizzle);② HUD 内物品图标(renderItem/药水/小地图);③ 主菜单自定义背景、F1 隐藏 HUD。

> 这些是"按需逐版本补"的清单,不是缺陷——核心客户端(模块、HUD、Web UI 网页)在所有版本可用。26.2 的 CEF 渲染走 CPU 上传路径(未做零拷贝加速),网页 blit 的 UV/透明混合待目视校正。

### 26.2 runClient 状态(已跑通,网页已出图)
`:26.2:runClient` 在 macOS 已完整跑通:开窗、资源、CEF 软件渲染,**OOBE 网页 UI 正常渲染出图(颜色/朝向/透明都对)**。中途修的 5 个运行时坑(详见记忆 `nova-mc26-unobfuscated-build`):① netty 4.2 的 `netty-transport-native-epoll` 传递依赖解析出 Mojang 镜像没有的 `linux-riscv64` 变体 → `configurations.all{ exclude }`(仅 26.2);② MC26.2 用 LWJGL 3.4.1,项目为 IME 强降 `lwjgl-glfw`→3.3.4 导致 `AbstractMethodError`,那个 force 排除 26.2;③ `ItemStack` 在静态 `<clinit>` 里建会抛 `Components not bound yet`(DataComponent 晚绑),改 `by lazy`;④ `defaultRequire: 0`(bring-up 容错,命中0目标的 mixin 非致命跳过);⑤ **CEF 泵在 MixinGameRenderer**(`MCEF.update()` @render HEAD)——误门控整个 mixin 导致 CEF 不出帧→网页黑;修=保留它、只 swap `.screen`、把描述符变了的 `bobHurt` inject(cancelHurtCam)`//? if <26` 门控掉(描述符不匹配是硬崩,defaultRequire:0 管不住)。**已确认 QoL 延后**:NoHurtCam、SmoothZoom(getFov 在 26.2 没了)、motion blur。

## 新增一个版本
1. `settings.gradle.kts` 的 `versions(...)` 加上版本号。
2. `build.gradle.kts` 的 `VersionSpec` when 加一条(loader / parchment / java);如属立即模式世代,加入 `isLegacyRender` / `usesLegacyHelpers` 集合。
3. `./gradlew :<版本>:compileKotlin :<版本>:compileJava`,按报错逐个用 `//?` 判定把差异 API 分流。
4. 复杂渲染 mixin 编不过时:生成 `fpsmaster-<版本>.mixins.json`(从最接近的现有配置去掉门控项)+ 在 `build.gradle.kts` 排除其源码,并在 `mixinConfig` 的 when 里指向它。
5. `./gradlew :<版本>:runClient` 实测(mixin `@At` 命中、渲染、Web UI)。

> **26.x 未混淆版本**额外步骤:不加 `mappings`,改在 `settings.gradle.kts` 的 `beforeProject` 里设 `fabric.loom.disableObfuscation=true`;`VersionSpec` 的 java 用 25;新建 `official` 命名空间的 access widener;新增的 `GuiGraphics` 差异用 `GuiGraphics26` shim + import swap。参见上文「26.x 未混淆游戏」。
