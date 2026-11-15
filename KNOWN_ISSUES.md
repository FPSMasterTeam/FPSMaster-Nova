# 已知问题与限制（最终版）

## 平台与硬件

- 已在 macOS（Apple Silicon）上完成六个版本的真机验收。Windows 与 Linux 未在本轮验收。
- CEF 共享纹理加速只支持 Windows 与 Linux，macOS 走 CPU 路径。Linux EGL 加速未在真机上验证。
- Intel 核显未验证，可能无法使用 Web UI。

## 首次运行

- CEF 原生包在首次启动时从 mcef-nova 的上游（CCBlueX / LiquidBounce）下载，完全离线的首次启动需要事先放好对应平台的原生包。

## 功能边界

- 饰品只显示在自己身上，其他玩家看不到。
- 音乐功能直接调用网易云、QQ 音乐的第三方接口，接口变化后可能失效。
- Microsoft 正版登录依赖原发行版的 Azure 应用注册和 Microsoft、Mojang 服务。Fork 的替换方法见 [docs/FORKING.md](docs/FORKING.md)。
- 读取 Edge 配置文件时会自动转换为 Nova 格式。Nova 没有对应项的模块或设置会被跳过，记入 `latest.log`（`Edge import skipped`），并弹出通知。

## 验收范围

2026-11-15 的自动验收覆盖主菜单、创建世界、进入世界、ClickGUI、饰品界面、设置和退出世界，世界内停留 30 秒。以下项目没有覆盖：

- 30 分钟长时间运行（内存、显存、CEF 进程是否泄漏）；
- 连接多人服务器、回放的完整流程；
- ViaFabric 共存。
