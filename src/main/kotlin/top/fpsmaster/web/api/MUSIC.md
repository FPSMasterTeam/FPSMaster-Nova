# 音乐功能接入说明

协议实现（网易云 / QQ 的搜索、直链、歌词、歌单、扫码登录）**不在本仓库**，来自独立库
**Cadence**：<https://github.com/FPSMasterTeam/Cadence>，包名 `top.fpsmaster.music.*`，
由 JitPack 托管，坐标见 `build.gradle.kts`：

```kotlin
implementation("com.github.FPSMasterTeam:Cadence:v0.1.1")
bundledRuntime("com.github.FPSMasterTeam:Cadence:v0.1.1") { isTransitive = false }
```

Cadence 零 Minecraft 依赖、编译到 Java 8 字节码，因此不需要 Loom remap；它的两个传递依赖
（kotlin-stdlib、gson）在 Nova 运行时已经就位，故打包时不带传递依赖。同一个库也被
FPSMaster-Edge（1.8.9 / Forge）引用。

本目录只剩**接入层**：

| 位置 | 作用 |
|---|---|
| `MusicRoutes.kt` | 本地 HTTP 路由（由 `LocalServer` 在 :7781 注册）+ 登录态持久化 + 把 Cadence 的日志接到 mod logger |

## 路由

- `/api/netease/`：**兼容代理**，镜像 NeteaseCloudMusicApi 的端点与响应结构，底层用 Cadence 的
  weapi 加密转发网易云官方接口。登录态由前端持有（每次带 `?cookie=`），`login/qr/check`
  成功时回传 Set-Cookie。已配 CORS（支持 dev :3000 跨域）。
- `/api/qq/`：返回 Cadence 的规范化 JSON（Track/SongUrl/Lyric/QrCode），供前端接入 QQ 源。

## 前端

- `ui/services/netease.ts` 的 `BASE_URL` 为相对路径 `/api/netease`（跟随页面实际托管端口；
  端口 7781 被占时 `LocalServer` 会自动顺延，dev 下由 Vite `/api` 代理转发）；
  二维码图片由客户端用 `qrcode` 库本地渲染（`MusicPlayer.tsx` 数据层零改动）。
- 依赖 `qrcode` / `@types/qrcode`。
- 试听标记：`MusicPlayer` 读取 `song/url` 响应的 `freeTrialInfo`，在正在播放处显示"试听"角标。
- **QQ 源尚未在 UI 呈现**：后端路由已就绪，但来源切换器 + QQ 扫码登录 UI 属于后续独立功能。

## 登录持久化

`MusicCredentialStore.default("FPSMaster")`（Cadence 提供）把网易云 cookie 与 QQ 的
`musicid`+`musickey` 写到 `%APPDATA%/FPSMaster/music_auth.json`（或 `~/.fpsmaster/`，与
`AuthService` 同目录）。`MusicRoutes` 启动时加载并注入两个客户端，登录成功时写盘，登出时清除。

- 网易云：空 `?cookie=` 时后端用持久化 cookie 兜底；`/api/netease/logout` 清除。
- QQ：`/api/qq/status` 查询登录态（前端挂载时恢复），`/api/qq/logout` 清除。
- 凭证不放进会同步到前端的 ConfigManager，避免泄露。

## 已知限制

1. **会员墙**：高音质 / VIP 歌曲两个平台都要求登录会员账号（黑胶 / 绿钻）。未登录或非会员时
   直链会回退低音质或为空（`SongUrl.available == false`）。
2. **QQ 登录脆弱**：ptlogin 流程的 `aid/daid/pt_3rd_aid` 等常量会随腾讯改版漂移；失效时优先
   核对 Cadence 里 `QQMusicApi.createQrCode/checkQrCode` 的常量。
3. **风控**：网易云对机房 / 境外 IP 有风控（`code:50000005`）；住宅 IP 正常。
4. 直链默认可直连；个别情况需带与 API 一致的 `Referer`（QQ: `https://y.qq.com/`）。

更详细的协议状态、实测结论与合规说明见 Cadence 仓库的 README。
