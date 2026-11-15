# Nova runClient 验收

需要 JDK 25、Python 3、macOS GUI 操作权限。验收不依赖 Python 第三方包。

## 先准备 Minecraft 资产

首次联网执行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew --max-workers=1 :<version>:downloadAssets`。
六个版本分别为 `1.19.2`、`1.20.1`、`1.21.1`、`1.21.8`、`1.21.11`、`26.2`。

`--offline` 只约束 Gradle 依赖解析，不代表 Loom 资产缓存完整。资产准备后，再加 `--offline` 执行同一任务，确认没有遗漏。

## 执行

- `tools/verify-runclient.sh 26.2 --self-test`：测试证据检查器，不启动游戏。
- `tools/verify-runclient.sh 26.2 --dry-run`：检查 smoke 参数转发、隔离工作目录，不启动游戏，也不检查资产。
- `tools/verify-runclient.sh 26.2`：拿桌面锁后启动离线客户端，人工操作以下流程。
- `tools/verify-runclient.sh 26.2 --check`：复查上一次运行的日志和图片。

脚本使用 `/tmp/nova-verify/<version>`，不读取现有玩家存档。已存在且没有所有权标记的目录会被拒绝。
桌面锁被占用时不启动游戏。每次运行最多 19 分钟，包含世界内默认 180 秒停留。
缩短 `NOVA_VERIFY_SOAK_SECONDS` 只能用于调试，不算默认验收。

按顺序操作，每屏至少停留 3 秒：

| 阶段 | 操作 | 图片后缀 |
|---|---|---|
| 主菜单 | 等待载入结束 | `01-menu.png` |
| 创建世界 | 单人游戏，进入创建世界界面 | `02-create-world.png` |
| 世界 | 创建世界，关闭所有菜单，停留至少 180 秒 | `03-world.png` |
| ClickGUI | 打开原生 ClickGUI | `04-clickgui.png` |
| 饰品 | 打开原生饰品界面 | `05-cosmetics.png` |
| 设置 | 打开原版设置界面 | `06-settings.png` |
| 返回主菜单 | 保存退出世界，返回自定义主菜单 | `07-returned-menu.png` |

全部完成后正常退出客户端。图片来自 Minecraft framebuffer，不是桌面截图。
输出位于仓库同级 `.work/verification/nova-shots/`，文件前缀为版本号。
每个阶段必须有本次 run ID 的有序日志和对应固定文件名。缺少任一项、异常日志或非正常退出均失败。

## 黑帧判定与人工复核

检查器完整解码 RGB/RGBA PNG（含 checksum 和 PNG 行过滤），横纵每 16 像素采样。
每张图片打印采样数、唯一 RGB 色数、亮度方差、可见像素比例。
唯一色少于 16、亮度方差低于 25、RGB 和大于 60 的像素少于 5%，任一条件即失败。
纯黑、纯色和近纯色不能通过。自测覆盖这些情况和七个缺图阶段。
世界图额外要求下方 1/3 至少 4 个血条红色系像素（R ≥ 120 且 R > 1.5G、R > 1.5B），
以及画面中心 25×25 区域亮度极差至少 50（准星局部对比）。这是生存模式、显示 HUD 的验收配置，不能使用创造模式或隐藏 HUD。
这些结构判据用于排除加载画面，不替代人工检查。

可用 `NOVA_VERIFY_REFERENCE=/absolute/path/to/successful-world.png tools/verify-runclient.sh 26.2 --self-test`
检查已人工确认的成功帧。对照帧不是 Nova 的阶段证据。

自动通过仍不等于验收完成。逐图确认标题与控件可读、没有黑屏/花屏/遮挡，世界图确有天空或地形、血量和快捷栏。
同时实际操作 ClickGUI、饰品和设置，记录交互结果；帧率或单张非黑图不能证明功能正常。
