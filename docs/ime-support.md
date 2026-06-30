# IME（输入法）支持

> 状态：**部分已实现 + 可选增强特性（默认关闭）**
>
> - ✅ **已实现（所有受支持版本）**：中文/非 ASCII 文本**上屏**到 Web UI 输入框。
> - 🧩 **可选特性（需 LWJGL 3.4，按版本启用）**：候选词窗口**定位**——包含①原版内置文本框 IME 定位 ②Webview（CEF）输入框 IME 定位。

---

## 1. 背景与现状

Nova 的 Web UI 通过 **CEF 离屏渲染（OSR）** 把网页画进 Minecraft 纹理。OSR 模式下，输入法（IME）的合成、候选、上屏都需要宿主程序自己接管——不像原生窗口由操作系统自动处理。

当前 CEF 链路存在两个相互独立的问题：

| 能力 | 现状 | 原因 |
| --- | --- | --- |
| 非 ASCII 文本**上屏**（如中文打字选词后进入输入框） | ✅ 已修复 | 见下方"已实现"部分 |
| 候选词窗口**定位**（候选框出现在光标处而非屏幕角落） | ❌ 未实现 | 需要 GLFW 的 preedit 定位 API，**仅存在于 LWJGL 3.4**，而 MC 当前版本仍是 3.3.x |

---

## 2. 已实现：非 ASCII 文本上屏（JS 注入）

**问题根因**：本项目 vendored 的 java-cef **没有暴露 CEF 的 OSR IME 绑定**（`imeSetComposition` / `imeCommitText` / `onImeCompositionRangeChanged` 全部缺失，上游 `chromiumembedded/java-cef`、`JetBrains/jcef`、`CCBlueX/java-cef` 的 `CefBrowser` / `CefBrowser_N` 均无此方法）。而 fork 里 `sendKeyTyped` 走的是 CEF `KEY_TYPE` 字符事件，该路径在 **macOS OSR 下塞不进非 ASCII 文本**。

**解决方案（曲线救国）**：GLFW 的 `glfwSetCharCallback` 本来就会把 IME **已上屏**的文本（和英文同一条路）送进 `charTyped`。所以不依赖 CEF 的 IME 接口，而是把已上屏文本直接注入聚焦的网页输入框：

- `ClientBrowser.insertText(text)`：通过 `executeJavaScript` 在 `document.activeElement` 上执行 `execCommand('insertText', …)`（带 `value` 拼接 + `input` 事件兜底），会触发标准 `beforeinput`/`input` 事件，所以 React 受控输入框能正确更新。非 ASCII 字符做 `\uXXXX` 转义，避免编码损坏。
- `BasicBrowser.charTyped`（1.21.5+ 与 1.20.1 两条 swap 分支均改）：**ASCII 仍走原 `sendKeyTyped`**（英文本就正常，不动）；**非 ASCII（IME 上屏的 CJK、emoji 等）改走 `insertText` JS 注入**。

> 提交：`fix(cef): input IME-committed non-ASCII text via JS injection`

**此方案不依赖 LWJGL 版本，在所有受支持版本上均生效。**

---

## 3. 可选特性：候选词窗口定位（需 LWJGL 3.4）

### 3.1 为什么默认做不到

原版 Minecraft 的"游戏内 IME"（候选框出现在光标处）依赖 GLFW 的 preedit 定位 API：

```
glfwSetPreeditCursorRectangle(window, x, y, w, h)   // 把候选框定位到指定矩形
glfwSetPreeditCallback(window, cb)                  // 合成（preedit）字符串回调
glfwSetPreeditCandidateCallback(window, cb)         // 候选词列表回调（可自绘候选）
glfwSetIMEStatusCallback(window, cb)
GLFW_IME                                            // IME 输入模式开关
```

这些 API **仅存在于 LWJGL 3.4-snapshot**。实测确认（`./gradlew :<ver>:dependencies`）：

| MC 版本 | LWJGL glfw 版本 | 是否含 preedit API |
| --- | --- | --- |
| 1.20.1 | 3.3.2 | ❌ 无 |
| 1.21.11 | 3.3.3 | ❌ 无 |
| —（LWJGL 3.4-snapshot） | 3.4.x | ✅ 有 |

`javap` 验证：3.3.x 的 `org.lwjgl.glfw.GLFW` 整个表面只有 `glfwSetCharCallback` / `glfwSetCharModsCallback`，**没有任何 `Preedit`/`Candidate`/`Composition` 方法**。

> 即：候选框定位"不是实现不了，而是要先把整套 LWJGL 升到 3.4"。社区参考实现见 `legacy-lwjgl3` 模组。

### 3.2 前置条件（"受支持版本"的定义）

本可选特性只在满足以下条件的版本上启用：

1. 运行时 LWJGL 已升级/覆盖到 **3.4-snapshot**（含 preedit API 的 Java 绑定 + 对应平台 native）。
2. 整套 `org.lwjgl:*` 模块（core / glfw / opengl / openal / stb / …）版本一致，避免 ABI 不匹配。
3. 已为 win / mac / linux × x64 / arm64 提供 3.4 native。

> 该升级影响整个客户端的窗口/渲染/音频栈，属于侵入性改动，需先做可行性 spike 验证 MC 在 3.4-snapshot 下能正常启动，再决定是否全量启用。因此默认**关闭**，作为可选特性按版本开启。

### 3.3 范围（两部分都要做）

#### A. 原版内置文本框 IME 定位
让原版的聊天框、告示牌、铁砧改名等**原生文本框**的候选窗口出现在文本光标处：

- 开启 `GLFW_IME` 输入模式。
- 在原生文本框获得焦点 / 光标移动时，按当前光标的屏幕坐标调用 `glfwSetPreeditCursorRectangle`。
- （可选）接 `glfwSetPreeditCallback` 渲染合成串下划线，`glfwSetPreeditCandidateCallback` 自绘候选列表。

#### B. Webview（CEF）输入框 IME 定位
让 Web UI 内 `<input>`/`<textarea>` 聚焦时的候选窗口出现在该输入框处：

- 通过 JS 查询聚焦元素位置：`document.activeElement.getBoundingClientRect()`，异步回传（消息通道 / query router）。
- 把浏览器坐标映射回屏幕坐标（结合 CEF quad 的渲染位置与 `contentScale`/`deviceScale`，见 `ClientBrowser.resize`）。
- 调用 `glfwSetPreeditCursorRectangle` 定位候选框。
- 上屏仍走第 2 节的 JS 注入（`insertText`）；preedit 合成串可选地实时注入预览。

### 3.4 风险与限制

- LWJGL 3.4 为 snapshot，存在稳定性风险（渲染/音频回归），需逐平台实测。
- 需自备并托管多平台 3.4 native。
- macOS OSR 的候选定位最终由 GLFW Cocoa 后端 `NSTextInputClient` 决定，像素级位置需 `runClient` 逐轮实测。
- B 部分依赖 JS 异步回传输入框位置，存在一帧延迟；快速切换焦点时候选框定位可能短暂滞后。

---

## 4. 实施清单（启用该特性时）

- [ ] 可行性 spike：将 LWJGL 覆盖到 3.4-snapshot，确认目标 MC 版本能正常启动。
- [ ] 整套 `org.lwjgl:*` 升级 + 多平台 native 打包/托管。
- [ ] 按版本 gate（仅在 LWJGL≥3.4 的构建启用 preedit 代码路径）。
- [ ] A：原版文本框接 `glfwSetPreeditCursorRectangle`。
- [ ] B：CEF 输入框位置查询（JS）+ 坐标映射 + 候选框定位。
- [ ] 逐平台（Windows / macOS / Linux）`runClient` 实测候选框位置与上屏。
