<p align="center">
  <img src="src/main/resources/assets/fpsmaster/icon.png" width="96" height="96" alt="FPSMaster Nova" />
  <h1 align="center">FPSMaster Nova</h1>
  <p align="center">Stonecutter 单源码树 · CEF Web UI · 全版本 HUD</p>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square" alt="MIT" /></a>
  <a href="https://fabricmc.net"><img src="https://img.shields.io/badge/Fabric-0.19.3-blue?style=flat-square" alt="Fabric" /></a>
  <img src="https://img.shields.io/badge/Minecraft-1.19.2_~_1.21.11-green?style=flat-square" alt="MC 1.19.2-1.21.11" />
  <img src="https://img.shields.io/badge/Stonecutter-0.9.6-orange?style=flat-square" alt="Stonecutter" />
  <a href="https://github.com/FPSMasterTeam/FPSMaster-Nova/actions/workflows/ci-release.yml"><img src="https://github.com/FPSMasterTeam/FPSMaster-Nova/actions/workflows/ci-release.yml/badge.svg" alt="CI" /></a>
</p>

<p align="center">
  <b>一个源码，多世代运行。</b> 基于 Fabric + Stonecutter 的多版本客户端模组，CEF 渲染与 Minecraft 解耦，HUD / Web UI 全版本可用。
</p>

---

### ✨ 特性

| | 能力 |
|---|---|
| **多版本单仓** | `1.19.2` / `1.20.1` / `1.21.1` / `1.21.8` / `1.21.11` / `26.2` 共用一套 `Kotlin/Java`，Stonecutter `//? if` 门控渲染差异 |
| **CEF Web UI** | `mcef-nova`（MC 解耦的 `java-cef` 分支）零拷贝加速（Win/Linux）+ CPU 兜底，`vendor/maven` 离线可构建 |
| **Prism UI 套件** | 自研 `top.fpsmaster.prism` 声明式 UI（`fpsmaster-prism`），`NovaCanvas / NovaHost / Chrome` 统一设计语言 |
| **输入法** | `GLFW 3.3.4` 预编辑定位 + 非 ASCII 上屏，`docs/ime-support.md` |
| **Via 兼容** | 可选 `-PwithViaFabric`，不强制 `fabric-api/registry-sync`，与 `ViaFabric / ViaFabricPlus` 共存 |

### 📸 预览

> `ui/prototypes/shots/` 为本地设计稿，未随仓发布。启动 `ui` 的 `npm run dev` 可实时预览。

### 🚀 快速开始

**UI 预览**
```bash
cd ui
npm install
npm run dev   # http://localhost:5173
```

**Mod 开发**
```bash
# 任选一个 MC 版本
./gradlew :1.21.11:runClient   # 启动游戏
./gradlew :1.21.11:remapJar    # 构建 jar → versions/1.21.11/build/libs/
./gradlew :1.21.11:build       # 完整构建（含 remap + sources）

# 外部贡献者无需配置 GitHub Token
# mcef-nova 已在 vendor/maven 兜底，CI 自动用 GITHUB_TOKEN 解析
```

### 🧩 支持版本

| 版本 | 渲染管线 | HUD / Web UI | 说明 |
|---|---|---|---|
| `1.19.2` | `PoseStack` + 适配层 | ✅ | 需 `GuiGraphics` shim |
| `1.20.1` | 立即模式 | ✅ |  |
| `1.21.1` | 立即模式 + 1.20.5 API | ✅ |  |
| `1.21.8` | `GuiRenderState` / `RenderPipeline` | ✅ |  |
| `1.21.11` | `submit-node` | ✅ | 推荐开发版本 |
| `26.2` | 年号版本（未混淆，Java 25） | ✅ | `GuiGraphics26` shim，复杂渲染特性待适配 |

> 复杂渲染（`WingsLayer / LevelRenderer / FishingHook` 等）按版本门控，见 `fpsmaster-<version>.mixins.json` + `build.gradle.kts#sourceSets.exclude`。

**运行环境：** Windows / macOS（已测）/ Linux（未测）· `Nvidia / AMD` GPU 加速，暂不支持 `Intel iGPU` / `Android`。

### 🏗️ 架构

```
Nova (Fabric Mod, Kotlin/Java)
 ├─ Stonecutter 多版本分发
 ├─ Prism UI  (fpsmaster-prism, MIT)
 ├─ CEF 桥  (mcef-nova, LGPL-2.1)  ── vendor/maven 兜底
 ├─ Cadence 音乐 (MIT) + mp3spi
 └─ ViaFabric 可选兼容 (-PwithViaFabric)
```

### 📚 文档

* [多版本支持](docs/multiversion.md) — 版本矩阵、门控特性、如何新增版本
* [输入法支持](docs/ime-support.md)
* [ViaFabric 共存](docs/viafabric.md)

### 🤝 贡献

欢迎 `Issue / PR`！

```bash
./gradlew :1.21.11:compileJava   # 提交前请通过
```

* 遵循现有 `Kotlin` 风格，不引入与 `Stonecutter` 冲突的抽象
* 大型渲染特性请按 `>=1.21.5 / isLegacyRender / isUnobfuscated` 三档门控
* 需要新依赖请走 `vendor/maven` 兜底 + `GitHub Packages` 双源

### 🙏 致谢

* [CCBlueX/mcef](https://github.com/CCBlueX/mcef) — `mcef-nova` 上游
* [FabricMC](https://fabricmc.net/) · [Stonecutter](https://stonecutter.kikugie.dev/) · [Kotlin](https://kotlinlang.org/)

---

### 📄 许可

**MIT** © 2026 FPSMaster Team — 见 [LICENSE](LICENSE) / [LICENSE.txt](LICENSE.txt)。可商用、可闭源、可分发，需保留版权声明。
