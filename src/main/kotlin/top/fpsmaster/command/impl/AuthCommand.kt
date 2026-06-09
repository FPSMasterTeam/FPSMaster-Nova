package top.fpsmaster.command.impl

import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.command.Command
import top.fpsmaster.command.CommandContext
import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.command.CompletionContext
import top.fpsmaster.mc

class AuthCommand : Command(
    name = "auth",
    aliases = listOf("account"),
    description = "管理 FPSMaster 账号登录状态",
    usage = "auth <status/login/logout/user> [username] [password]"
) {
    override fun execute(context: CommandContext) {
        context.requireMinimumArgumentCount(1)
        when (val action = context.requireArgument(0, "action").lowercase()) {
            "status" -> {
                val state = if (AuthService.isLoggedIn()) "已登录" else "未登录"
                context.replyInfo("$state，auth 文件: ${AuthService.authFilePath()}")
            }

            "login" -> {
                context.requireArgumentCount(3)
                val username = context.requireArgument(1, "username")
                val password = context.requireArgument(2, "password")
                context.replyInfo("正在登录 FPSMaster 账号...")
                FPSMasterApiClient.login(username, password)
                    .whenComplete { result, exception ->
                        mc.execute {
                            if (exception != null) {
                                context.replyError("登录请求失败: ${exception.message ?: exception::class.simpleName}")
                            } else if (result.success) {
                                val user = result.data?.user?.username ?: username
                                context.replySuccess("登录成功: $user")
                            } else {
                                context.replyError("登录失败: ${result.message}")
                            }
                        }
                    }
            }

            "logout" -> {
                context.replyInfo("正在退出 FPSMaster 账号...")
                FPSMasterApiClient.logout()
                    .whenComplete { result, exception ->
                        mc.execute {
                            if (exception != null) {
                                AuthService.clearTokens()
                                context.replyError("登出请求失败，已清除本地 token: ${exception.message ?: exception::class.simpleName}")
                            } else if (result.success) {
                                context.replySuccess("已退出登录")
                            } else {
                                context.replyInfo("已清除本地 token，服务端响应: ${result.message}")
                            }
                        }
                    }
            }

            "user" -> {
                if (!AuthService.isLoggedIn()) {
                    throw CommandExecutionException("当前未登录")
                }
                context.replyInfo("正在获取账号信息...")
                FPSMasterApiClient.getUserInfo()
                    .whenComplete { result, exception ->
                        mc.execute {
                            if (exception != null) {
                                context.replyError("获取账号信息失败: ${exception.message ?: exception::class.simpleName}")
                            } else if (result.success && result.data != null) {
                                val user = result.data
                                context.replyInfo("${user.username ?: user.displayName ?: "Unknown"} / Lv.${user.level ?: 0}")
                            } else {
                                context.replyError("获取账号信息失败: ${result.message}")
                            }
                        }
                    }
            }

            else -> throw CommandExecutionException("不支持的账号操作: $action")
        }
    }

    override fun complete(context: CompletionContext): List<String> {
        return if (context.argumentIndex == 0) {
            listOf("status", "login", "logout", "user")
                .filter { it.startsWith(context.currentArgument, ignoreCase = true) }
        } else {
            emptyList()
        }
    }
}
