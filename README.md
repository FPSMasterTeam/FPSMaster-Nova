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
3. 运行客户端 `gradlew runClient`

### 特殊说明：
仅支持Windows、Linux（未测试）、MacOS（未测试）

不支持 Android

GPU渲染加速仅支持 Nvidia、AMD GPUs，不支持 Intel GPUs

## 文档
- [IME（输入法）支持](docs/ime-support.md)：非 ASCII 文本上屏（已实现）；候选词窗口定位（可选特性，需 LWJGL 3.4，含原版内置 IME 与 Webview IME 定位）。

## 许可
All rights reserved