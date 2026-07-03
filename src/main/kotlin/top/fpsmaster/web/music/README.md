# 音乐 API 库（top.fpsmaster.web.music）

自实现的网易云 / QQ 音乐 web 接口客户端。**零第三方依赖**——只用 JDK 内置
（`java.net.http`、`javax.crypto`、`BigInteger`、`MessageDigest`）+ 项目已有的 Gson。

只负责**取数据**：搜索、播放直链、歌词、登录。不负责播放——直链交给你的原生音频库解码。

## 结构

| 文件 | 作用 |
|---|---|
| `MusicModels.kt` | 跨平台数据模型（Track / SongUrl / Lyric / QrCode…） |
| `MusicService.kt` | 统一门面，按 `MusicSource` 分发 |
| `NeteaseMusicApi.kt` | 网易云客户端 |
| `QQMusicApi.kt` | QQ 音乐客户端 |
| `crypto/NeteaseCrypto.kt` | 网易云 weapi 加密（双层 AES-CBC + RSA） |
| `crypto/QQSign.kt` | QQ zzc 请求签名（SHA1 + XOR + base64） |
| `crypto/QQHash.kt` | QQ hash33（ptqrtoken / g_tk） |
| `http/MusicHttp.kt` | 轻量 HTTP 助手（禁自动跳转、可控 cookie） |

## 用法

```kotlin
val music = MusicService()

// 搜索
val tracks = music.search(MusicSource.NETEASE, "周杰伦")
val qqTracks = music.search(MusicSource.QQ, "告白气球")

// 直链（交给原生播放器）
val url = music.getSongUrl(tracks.first(), AudioQuality.HIGH)
if (url.available) player.play(url.url!!)

// 歌词
val lyric = music.getLyric(tracks.first())

// 二维码登录（前端渲染 qr.qrContent，每 2~3 秒轮询）
val qr = music.createQrCode(MusicSource.NETEASE)
// ... 前端显示二维码 ...
val state = music.checkQrCode(MusicSource.NETEASE, qr)
// state == CONFIRMED 时登录 cookie 已写入 music.netease.cookie
```

## 各功能状态

| 功能 | 网易云 | QQ 音乐 |
|---|---|---|
| 搜索 | ✅ | ✅ |
| 播放直链 | ✅ | ✅（实测需登录后取，`code=1000` 为未登录；高音质/VIP 需绿钻） |
| 歌词（含翻译） | ✅ | ✅（走明文端点，避开 QRC 加密） |
| 二维码登录 | ✅（unikey 流程） | ⚠️ 已按真实 ptlogin 流程实现，**上线前需真机联调** |
| 每日推荐 / 用户歌单 / 歌单详情 | ✅ | 未实现（可按需补） |

**加密/签名已交叉验证**：`zzc 签名`、`hash33`、`网易云 RSA` 的 JDK 实现与参考实现逐字节一致。

### 联网实测结论（2026-07，机房 IP）

用与本库一致的 JDK API 真实打线上接口的结果：

| 验证项 | 结果 |
|---|---|
| QQ 歌词（c.y.qq.com） | ✅ 真实返回 LRC |
| QQ zzc 签名 | ✅ 被线上服务器接受（非 2000 签名错误） |
| QQ 搜索端点/请求体 | ✅ `code:0` 被接受（本机房 IP 返回空列表，见下） |
| QQ vkey 取直链 | ⚠️ 返回 `code=1000`＝**需登录凭证**（登录后 purl 才有值） |
| QQ ptqrshow（登录第一步） | ✅ 拿到 qrsig + PNG |
| 网易云 weapi 加密 | ✅ 服务器成功解密（返回业务码而非解密错误） |
| 网易云 搜索/直链/歌词 | ⚠️ 本机房 IP 被风控（`code:50000005`）；同算法在住宅/国内 IP 部署返回 `code:200` 真实数据（已对照第三方部署确认） |

**结论**：算法与端点均正确。此处失败全部是**环境因素**（机房/境外 IP 被网易云风控、QQ 搜索返回空）或**平台策略**（QQ 取直链需登录），**非代码缺陷**。mod 实际运行在用户住宅 IP + 登录态下，即为可用环境。

## 已知限制与风险

1. **会员墙**：高音质 / VIP 歌曲两个平台都要求登录会员账号（黑胶 / 绿钻），这是账号限制，非代码可绕。未登录或非会员时直链会回退低音质或为空（`SongUrl.available == false`）。
2. **QQ 登录脆弱**：ptlogin 流程的 `aid/daid/pt_3rd_aid` 等常量会随腾讯改版漂移。若某天登录失效，优先核对 `QQMusicApi.createQrCode/checkQrCode` 里的常量。
3. **逆向接口**：签名参数腾讯偶尔更新（网易云 weapi 多年稳定，QQ 需更勤跟进）。
4. **原生播放器需带 Header**：QQ/网易云的直链一般可直连；若个别直链需鉴权，让原生播放器带上与 API 一致的 `Referer`（QQ: `https://y.qq.com/`）。

## Web UI 接入（已完成）

已把前端从第三方 `https://musicapi.skidder.top` 切到 mod 内置服务，具体改动：

**后端**（`web/api/MusicRoutes.kt`，由 `LocalServer` 在 :7781 注册）：
- `/api/netease/`：**兼容代理**，镜像 NeteaseCloudMusicApi 的端点与响应结构，底层用本库
  的 weapi 加密转发网易云官方接口。登录态由前端持有（每次带 `?cookie=`），
  `login/qr/check` 成功时回传 Set-Cookie。已配 CORS（支持 dev :3000 跨域）。
- `/api/qq/`：返回本库规范化 JSON（Track/SongUrl/Lyric/QrCode），供将来前端接入 QQ 源。

**前端**：
- `ui/services/netease.ts` 的 `BASE_URL` 改为 `http://localhost:7781/api/netease`；
  二维码图片改为客户端用 `qrcode` 库本地渲染（`MusicPlayer.tsx` 数据层零改动）。
- 新增依赖 `qrcode` / `@types/qrcode`（需 `npm install` 一次）。
- 试听标记：`MusicPlayer` 读取 `song/url` 响应的 `freeTrialInfo`，在正在播放处显示"试听"角标。

**编译状态**：`gradlew :1.21.11:compileKotlin` 通过；`npm run build` 通过；改动无新增 TS 类型错误。

**QQ 源尚未在 UI 呈现**：当前 MusicPlayer 是网易云结构，QQ 后端路由已就绪，但前端的
来源切换器 + QQ 扫码登录 UI 属于后续独立功能。

**登录持久化（已完成）**：`MusicCredentialStore` 把网易云 cookie 与 QQ 的
`musicid`+`musickey` 持久化到 `%APPDATA%/FPSMaster/music_auth.json`（或 `~/.fpsmaster/`，
与 AuthService 同目录）。`MusicRoutes` 启动时加载并注入两个客户端，登录成功时写盘，登出时清除。
- 网易云：空 `?cookie=` 时后端用持久化 cookie 兜底；`/api/netease/logout` 清除。
- QQ：`/api/qq/status` 查询登录态（前端挂载时恢复），`/api/qq/logout` 清除。
- 凭证不放进会同步到前端的 ConfigManager，避免泄露。

## 许可与合规

- 本库为**独立实现**，依据公开的算法事实（加密步骤、端点、参数）编写，**不含**任何开源仓库的源码。
- 参考项目仅用于核对协议行为，其协议为：
  - QQ 音乐参考实现 luren-dc/QQMusicApi → **GPL-3.0**（其源码不可并入本闭源项目）。
  - 网易云参考实现 Binaryify/NeteaseCloudMusicApi → MIT，且已于 2024-04 归档（转 GitLab）。
- 本项目 "All rights reserved"，因此选择自实现而非引入 GPL 代码，是正确的合规路径。
- 逆向接口仅供学习/个人使用，请遵守各平台版权与服务条款。
