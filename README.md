# FPSMaster Nova
## 如何部署开发环境

### UI
1. 安装Node.js
2. 克隆仓库
3. 进入 ui 目录
4. 运行`npm install`
5. 运行`npm run dev`

### Mod
1. 使用 IntelliJ IDEA 打开项目
2. Link `build.gradle.kts`
3. 运行某个版本的客户端：`gradlew :<版本>:runClient`（如 `gradlew :1.21.11:runClient`）
4. 构建某个版本的 jar：`gradlew :<版本>:remapJar`

## 支持的 Minecraft 版本
基于 [Stonecutter](https://stonecutter.kikugie.dev/) 多版本，单一源码树同时支持：

| 版本 | 渲染世代 | HUD/UI |
|---|---|---|
| 1.19.2 | 预 GuiGraphics（PoseStack 适配层） | ✅ |
| 1.20.1 | 立即模式 | ✅ |
| 1.21.1 | 立即模式 + 1.20.5 API | ✅ |
| 1.21.8 | 1.21.5 渲染（GuiRenderState） | ✅ |
| 1.21.11 | submit-node | ✅ |

所有版本共用一个 MC 解耦的 CEF（[FPSMasterTeam/mcef-nova](https://github.com/FPSMasterTeam)）。HUD/UI 与 Web UI 在全部版本可用；部分需要逐版本专属变体的复杂渲染特性按版本门控，详见下方文档。

### 特殊说明：
支持 Windows、Linux（未测试）、MacOS（已测试）

不支持 Android

GPU渲染加速仅支持 Nvidia、AMD GPUs，不支持 Intel GPUs

## 文档
- [多版本支持](docs/multiversion.md)：版本矩阵、各版本门控的渲染特性、如何新增一个版本。
- [IME（输入法）支持](docs/ime-support.md)：非 ASCII 文本上屏（已实现）；候选词窗口定位（可选特性，需 LWJGL 3.4，含原版内置 IME 与 Webview IME 定位）。
- [ViaFabric](docs/viafabric.md)：与 ViaFabric / ViaFabricPlus 共存（二选一；不强制 Fabric API / registry-sync）。

## 许可
All rights reserved