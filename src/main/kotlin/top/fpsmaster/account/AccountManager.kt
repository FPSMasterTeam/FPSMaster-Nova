package top.fpsmaster.account

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.minecraft.UserApiService
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.mixin.interfaces.IMinecraftSession
import java.net.Proxy
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Executors

class AccountManager private constructor() {
    class Account {
        var name: String = ""
        var uuid: String = ""
        var type: String = TYPE_OFFLINE
        var accessToken: String? = null
        var refreshToken: String? = null
        var expiresAt: Long? = null
        var xuid: String? = null

        fun microsoft(): Boolean = TYPE_MICROSOFT.equals(type, ignoreCase = true)
    }

    private val accounts = ArrayList<Account>()
    private val launcherUser: User? = mc.user
    private val launcherOnline: Boolean = isOnlineToken(mc.user.accessToken)
    private var selectedName: String? = null

    init {
        load()
        restoreSelected()
    }

    fun launcherAccount(): Account? {
        val user = launcherUser ?: return null
        val account = Account()
        account.name = user.name
        account.uuid = user.profileId.toString()
        account.type = if (launcherOnline) TYPE_MICROSOFT else TYPE_OFFLINE
        account.accessToken = user.accessToken
        return account
    }

    fun storedAccounts(): List<Account> = accounts

    fun currentName(): String = mc.user.name

    fun currentOnline(): Boolean = isOnlineToken(mc.user.accessToken)

    fun isLauncherCurrent(): Boolean {
        val launcher = launcherUser ?: return false
        return namesEqual(launcher.name, currentName()) && findByName(currentName()) == null
    }

    fun addAndUse(name: String): Boolean {
        if (!isValidUsername(name)) {
            return false
        }
        var existing = findOffline(name)
        if (existing == null) {
            existing = Account()
            existing.name = name
            existing.uuid = offlineUuid(name)
            existing.type = TYPE_OFFLINE
            accounts.add(existing)
        }
        use(existing)
        return true
    }

    fun addAndUseMicrosoft(profile: MicrosoftAuth.MinecraftProfile) {
        if (profile.name == null || profile.uuid == null) {
            return
        }
        var existing = findMicrosoft(profile.uuid)
        if (existing == null) {
            existing = Account()
            accounts.add(existing)
        }
        existing.name = profile.name
        existing.uuid = MicrosoftAuth.dashedUuid(profile.uuid)
        existing.type = TYPE_MICROSOFT
        existing.accessToken = profile.accessToken
        existing.refreshToken = profile.refreshToken
        existing.expiresAt = profile.expiresAt
        existing.xuid = profile.xuid
        applySession(existing)
        selectedName = existing.name
        save()
        logger.info("Signed in Microsoft account ${existing.name}")
    }

    fun use(account: Account) {
        if (account.microsoft()) {
            val stored = findMicrosoft(account.uuid) ?: account
            if (tokenUsable(stored)) {
                applySession(stored)
                selectedName = stored.name
                save()
                return
            }
            if (!stored.refreshToken.isNullOrEmpty()) {
                refreshMicrosoft(stored)
            }
            return
        }
        val launcher = launcherUser
        if (launcher != null && namesEqual(account.name, launcher.name) && findOffline(account.name) == null) {
            useLauncher()
            return
        }
        applySession(account)
        selectedName = account.name
        save()
    }

    fun useLauncher() {
        val launcher = launcherUser ?: return
        applyUser(launcher, if (launcherOnline) null else UserApiService.OFFLINE)
        selectedName = null
        save()
    }

    fun remove(account: Account) {
        val stored = findStored(account) ?: return
        val wasCurrent = namesEqual(stored.name, currentName())
        accounts.remove(stored)
        if (wasCurrent) {
            selectedName = null
            if (launcherUser != null) {
                useLauncher()
            } else if (accounts.isNotEmpty()) {
                use(accounts[0])
                return
            }
        }
        save()
    }

    fun selectById(id: String) {
        if (id == LAUNCHER_ID) {
            useLauncher()
            return
        }
        val account = accounts.firstOrNull { it.name.equals(id, ignoreCase = true) || it.uuid.equals(id, ignoreCase = true) }
        if (account != null) {
            use(account)
        }
    }

    fun removeById(id: String) {
        val account = accounts.firstOrNull { it.name.equals(id, ignoreCase = true) || it.uuid.equals(id, ignoreCase = true) }
        if (account != null) {
            remove(account)
        }
    }

    private fun restoreSelected() {
        val name = selectedName ?: return
        val selected = findByName(name) ?: return
        if (selected.microsoft()) {
            if (tokenUsable(selected)) {
                applySession(selected)
            } else if (!selected.refreshToken.isNullOrEmpty()) {
                refreshMicrosoft(selected)
            }
            return
        }
        applySession(selected)
    }

    private fun refreshMicrosoft(account: Account) {
        IO.execute {
            try {
                val profile = MicrosoftAuth.refresh(account.refreshToken)
                mc.execute { addAndUseMicrosoft(profile) }
            } catch (exception: Exception) {
                logger.warn("Failed to refresh Microsoft account ${account.name}: ${exception.message}")
            }
        }
    }

    private fun applySession(account: Account) {
        val microsoft = account.microsoft()
        val token = if (microsoft && !account.accessToken.isNullOrEmpty()) account.accessToken!! else "0"
        val uuid = runCatching {
            UUID.fromString(if (microsoft) MicrosoftAuth.dashedUuid(account.uuid) else account.uuid)
        }.getOrElse { UUID.nameUUIDFromBytes(("OfflinePlayer:" + account.name).toByteArray(StandardCharsets.UTF_8)) }
        val xuid = Optional.ofNullable(account.xuid?.takeIf { it.isNotBlank() })
        //? if >=1.21.11 {
        val user = User(account.name, uuid, token, xuid, Optional.empty())
        //?} else {
        /*val user = User(
            account.name,
            uuid,
            token,
            xuid,
            Optional.empty(),
            if (microsoft) User.Type.MSA else User.Type.LEGACY
        )*/
        //?}
        val service = if (microsoft) createUserApiService(token) else UserApiService.OFFLINE
        applyUser(user, service)
    }

    private fun applyUser(user: User, service: UserApiService?) {
        val session = Minecraft.getInstance() as IMinecraftSession
        session.`fpsmaster$setUser`(user)
        if (service != null) {
            session.`fpsmaster$setUserApiService`(service)
        }
    }

    private fun createUserApiService(token: String): UserApiService {
        return try {
            YggdrasilAuthenticationService(mc.proxy).createUserApiService(token)
        } catch (exception: Exception) {
            logger.warn("UserApiService create failed: ${exception.message}")
            UserApiService.OFFLINE
        }
    }

    private fun findStored(account: Account): Account? {
        return if (account.microsoft()) findMicrosoft(account.uuid) ?: findByName(account.name) else findOffline(account.name)
    }

    private fun findOffline(name: String?): Account? {
        if (name == null) {
            return null
        }
        return accounts.firstOrNull { !it.microsoft() && namesEqual(it.name, name) }
    }

    private fun findMicrosoft(uuid: String?): Account? {
        if (uuid == null) {
            return null
        }
        val dashed = MicrosoftAuth.dashedUuid(uuid)
        return accounts.firstOrNull { it.microsoft() && dashed.equals(MicrosoftAuth.dashedUuid(it.uuid), true) }
    }

    private fun findByName(name: String?): Account? {
        if (name == null) {
            return null
        }
        return accounts.firstOrNull { namesEqual(it.name, name) }
    }

    private fun file() = mc.gameDirectory.toPath().resolve("fpsmaster").resolve("accounts.json")

    private fun load() {
        val path = file()
        if (!Files.isRegularFile(path)) {
            return
        }
        try {
            val root = JsonParser.parseString(Files.readString(path))
            if (root == null || root.isJsonNull) {
                return
            }
            if (root.isJsonArray) {
                readAccounts(root.asJsonArray)
                return
            }
            if (root.isJsonObject) {
                val obj = root.asJsonObject
                if (obj.has("selected") && !obj.get("selected").isJsonNull) {
                    selectedName = obj.get("selected").asString
                }
                if (obj.has("accounts") && obj.get("accounts").isJsonArray) {
                    readAccounts(obj.getAsJsonArray("accounts"))
                }
            }
        } catch (exception: Exception) {
            logger.warn("Failed to load accounts.json: $exception")
        }
    }

    private fun readAccounts(array: JsonArray) {
        for (element in array) {
            if (element == null || !element.isJsonObject) {
                continue
            }
            val account = GSON.fromJson(element, Account::class.java) ?: continue
            if (account.name.isBlank()) {
                continue
            }
            if (account.type.isBlank()) {
                account.type = TYPE_OFFLINE
            }
            if (account.microsoft()) {
                account.uuid = MicrosoftAuth.dashedUuid(account.uuid)
                accounts.add(account)
            } else if (isValidUsername(account.name)) {
                if (account.uuid.isBlank()) {
                    account.uuid = offlineUuid(account.name)
                }
                accounts.add(account)
            }
        }
    }

    private fun save() {
        try {
            Files.createDirectories(file().parent)
            val root = JsonObject()
            if (selectedName != null) {
                root.addProperty("selected", selectedName)
            }
            root.add("accounts", GSON.toJsonTree(accounts))
            Files.writeString(file(), GSON.toJson(root))
        } catch (exception: Exception) {
            logger.warn("Failed to save accounts.json: $exception")
        }
    }

    companion object {
        const val TYPE_OFFLINE = "offline"
        const val TYPE_MICROSOFT = "microsoft"
        const val LAUNCHER_ID = "__launcher__"
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private val IO = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "FPSMaster-Account").apply { isDaemon = true }
        }
        private const val REFRESH_SKEW_MS = 5L * 60L * 1000L

        @Volatile
        private var instance: AccountManager? = null

        @JvmStatic
        fun get(): AccountManager {
            val existing = instance
            if (existing != null) {
                return existing
            }
            synchronized(AccountManager::class.java) {
                val created = instance ?: AccountManager().also { instance = it }
                return created
            }
        }

        @JvmStatic
        fun isValidUsername(name: String?): Boolean {
            return name != null && name.matches(Regex("[A-Za-z0-9_]{3,16}"))
        }

        private fun tokenUsable(account: Account): Boolean {
            if (account.accessToken.isNullOrEmpty()) {
                return false
            }
            val expires = account.expiresAt ?: return true
            return expires - REFRESH_SKEW_MS > System.currentTimeMillis()
        }

        private fun isOnlineToken(token: String?): Boolean {
            return !token.isNullOrEmpty() && token != "undefined" && token != "offline" && token != "0"
        }

        private fun namesEqual(a: String?, b: String?): Boolean {
            return a != null && b != null && a.equals(b, ignoreCase = true)
        }

        private fun offlineUuid(name: String): String {
            return UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8)).toString()
        }
    }
}
