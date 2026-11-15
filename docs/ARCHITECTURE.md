# 架构

FPSMaster Nova 是 Fabric 客户端模组，用 Stonecutter 从同一份源码编译出六个 Minecraft 版本。Kotlin 源码在 `src/main/kotlin/top/fpsmaster/`，Mixin 与部分 Java 代码在 `src/main/java/top/fpsmaster/`。

## 多版本

- 版本节点在 `settings.gradle.kts` 声明，每个版本的 loader、mappings、Java 版本在 `build.gradle.kts` 的 `VersionSpec` 中。
- 源码中的版本差异用 Stonecutter 注释（`//? if >=1.21.5 {`）标记，构建时为每个版本生成各自的源码副本。
- 每个版本有自己的 Mixin 清单 `fpsmaster-<version>.mixins.json`，个别文件通过 `sourceSets.exclude` 排除。
- 渲染相关代码按三档区分：`isLegacyRender`（1.19.2–1.21.1）、`>=1.21.5`、`isUnobfuscated`（26.2）。

版本矩阵和各版本的 API 差异见 [multiversion.md](multiversion.md)。

## 主要模块

| 目录 | 内容 |
|---|---|
| `Client.kt` | 模组入口，初始化各子系统，注册每帧回调 |
| `module/`、`hud/` | 功能模块与 HUD 组件 |
| `config/` | 配置读写、配置档、从 Edge 导入 |
| `cosmetic/` | 本地饰品：内置龙翼与自定义贴图 |
| `ui/`、`web/` | 原生界面（Prism）与 CEF Web UI |
| `account/` | Microsoft 与离线账号 |
| `musicui/` | 音乐播放界面，数据来自 music-api |
| `replay/` | 回放 |
| `diagnostics/` | 运行时探针与 runClient 自动验收 |
| `mixin/impl/` | 对原版类的修改 |

## 依赖

| 库 | 用途 | 许可 |
|---|---|---|
| fpsmaster-prism 0.3.0 | 原生 UI 组件 | MIT |
| mcef-nova 1.0.3 | 与 Minecraft 解耦的 CEF 浏览器 | LGPL-2.1 |
| music-api 0.1.3 | 网易云、QQ 音乐数据 | MIT |

三个库都放在 `vendor/maven/`，构建时不需要访问私有仓库。

## 网络

客户端不访问任何 FPSMaster 服务器。剩余的网络访问为 Microsoft 登录、Mojang 皮肤、第三方音乐接口，以及首次启动时下载 CEF 原生包。
