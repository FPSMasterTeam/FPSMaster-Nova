# 变更记录

## 1.0.0（最终版，停止维护日期：2026-12-31）

停止维护前的最后一个版本，分支 `final/release`。支持 Minecraft 1.19.2、1.20.1、1.21.1、1.21.8、1.21.11、26.2。客户端不再依赖任何 FPSMaster 服务器。

### 删除

- FPSMaster 账号登录与登出、Minecraft 账号绑定。
- 心跳、在线状态、离线等遥测上报。
- 推荐服务器列表。服务器浏览只保留本地置顶。
- 商城、已购物品、余额和远程饰品；IRC 与旧多人界面相关的语言条目。
- CI 中的七牛上传和后端发布通知。
- 私有 GitHub Packages 依赖。

### 变更

- 饰品改为纯本地且全部免费：内置龙翼，以及 `config/fpsmaster/cosmetics/` 下的自定义贴图。说明见 [docs/cosmetics.md](docs/cosmetics.md)。
- 升级 Prism 0.3.0、mcef-nova 1.0.3、music-api 0.1.3，全部从 `vendor/maven` 解析。
- 第三方音乐服务不可用时界面显示提示。
- 新增 runClient 自动验收工具，见 [docs/VERIFY_RUNCLIENT.md](docs/VERIFY_RUNCLIENT.md)。

### 修复

- 1.21.1：启动即崩溃（BetterScreen 背景和动态模糊的注入签名与 1.21 不符）。
- 1.21.11：聊天头像 mixin 在正式包中无法注入。
- 26.2：启动崩溃、退出世界时崩溃（`Invalid atlas id: minecraft:gui`）、雾色和粒子光照注入点失效。
- 从 Edge 导入配置时的键码与设置映射。
