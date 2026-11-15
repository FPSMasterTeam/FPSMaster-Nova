# Fork 与自行发布

## 构建

构建全部版本使用 JDK 25（26.2 必须用 JDK 25）。

```sh
./gradlew build test --no-daemon           # 六个版本全部构建并测试
./gradlew :1.21.11:runClient               # 启动指定版本
./gradlew :1.21.11:remapJar                # 产物在 versions/1.21.11/build/libs/
```

共享库从 `vendor/maven/` 解析。Gradle 发行版、Minecraft、Fabric 等公开依赖和游戏资源首次构建时需要联网下载。真机验收方法见 [VERIFY_RUNCLIENT.md](VERIFY_RUNCLIENT.md)。

## 需要替换的标识

| 项目 | 位置 |
|---|---|
| 模组 id `fpsmaster` | `src/main/resources/fabric.mod.json` |
| 包名 `top.fpsmaster` | 源码目录与各 `fpsmaster-*.mixins.json` |
| 版本号 | `gradle.properties` 的 `mod_version` |
| Microsoft 应用 client id | `src/main/java/top/fpsmaster/account/MicrosoftAuth.java` |
| 发布地址 | README 与 `fabric.mod.json` 中的链接 |

## Microsoft 正版登录

原发行版使用的 Azure 应用注册必须由原维护者长期保留，删除后已发布的客户端将无法正版登录。Fork 应注册自己的 Microsoft Entra 应用：支持个人 Microsoft 账号，并按微软要求申请 Minecraft API 权限。这是公开客户端，不要在客户端里放 client secret。client id 按以下顺序读取：

1. 环境变量 `FPSMASTER_MINECRAFT_CLIENT_ID`
2. 环境变量 `MICROSOFT_CLIENT_ID`
3. `MicrosoftAuth.DEFAULT_CLIENT_ID`

## 发布

仓库不含 CI，发布为手动操作：`./gradlew build` 后，把 `versions/<MC 版本>/build/libs/nova-<版本>.jar` 依次改名为 `fpsmaster-nova-<MC 版本>-<版本>.jar`，用 `gh release create v<版本>` 一起上传。

如果要让 FPSMaster 启动器安装你的版本，还需要用新的 jar 地址和 SHA-256 重新生成启动器的 catalog，见启动器仓库的 `docs/FORKING.md`。

## 许可证

本项目采用 MIT。随附的 mcef-nova 为 LGPL-2.1，分发时须保留其许可证和源码获取方式。
