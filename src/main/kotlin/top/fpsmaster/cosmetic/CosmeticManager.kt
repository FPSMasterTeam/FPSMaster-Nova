package top.fpsmaster.cosmetic

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.texture.DynamicTexture
//? if >=1.21.11 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation
*///?}
import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.auth.ItemView
import top.fpsmaster.identifier
import top.fpsmaster.logger
import top.fpsmaster.mc
import top.fpsmaster.runtime.RuntimeProbe
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors

object CosmeticManager {
    const val BUILTIN_WINGS_ID = "builtin:dragon-wings"

    data class CosmeticOption(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val assetKey: String?,
        val price: String,
        val defaultScale: Float = 1f,
        val scaleAdjustable: Boolean = true,
        val local: Boolean = false
    )

    private const val API_BASE_URL = "https://api.fpsmaster.top"
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FPSMaster-Cosmetics").apply { isDaemon = true }
    }
    private val textures = HashMap<String, TextureId>()
    private val loading = HashSet<String>()

    @Volatile
    private var previewing = false

    @Volatile
    private var catalogOptions: List<CosmeticOption> = emptyList()
    @Volatile
    private var ownedOptions: List<CosmeticOption> = emptyList()
    @Volatile
    private var customOptions: List<CosmeticOption> = emptyList()
    @Volatile
    private var previewCapeId: String? = null
    @Volatile
    private var previewBackId: String? = null
    @Volatile
    private var previewWingScale = 1f
    @Volatile
    var selectedCapeId: String? = null
        private set
    @Volatile
    var selectedWingsId: String = BUILTIN_WINGS_ID
        private set
    @Volatile
    var wingsEnabled: Boolean = false
        private set
    @Volatile
    var capeAnimationEnabled: Boolean = false
        private set
    @Volatile
    private var configuredWingScale: Float = 1f

    val wingScale: Float
        get() {
            val option = effectiveBack()
            if (!option.scaleAdjustable) return option.defaultScale
            return if (previewing && previewBackId != null) previewWingScale else configuredWingScale
        }

    val savedWingScale: Float
        get() = configuredWingScale

    val wingScaleAdjustable: Boolean
        get() = effectiveBack().scaleAdjustable

    fun initialize() {
        reloadCustom()
        refreshOwned()
    }

    /**
     * Frees every cosmetic texture we registered and stops the loader. Called from the client
     * shutdown hook; the loader is interrupted first so an in-flight download cannot register a
     * new texture behind us.
     */
    fun shutdown() {
        executor.shutdownNow()
        val stale = synchronized(textures) {
            val ids = textures.values.toList()
            textures.clear()
            loading.clear()
            ids
        }
        releaseTextures(stale)
    }

    /** Texture deletion is a GL call, so it never happens off the render thread. */
    private fun releaseTextures(ids: List<TextureId>) {
        if (ids.isEmpty()) {
            return
        }
        if (RenderSystem.isOnRenderThread()) {
            ids.forEach(::releaseTexture)
        } else {
            mc.execute { ids.forEach(::releaseTexture) }
        }
    }

    private fun releaseTexture(id: TextureId) {
        mc.textureManager.release(id)
        RuntimeProbe.textureReleased()
    }

    fun configure(
        capeId: String?,
        wingsId: String?,
        wingsEnabled: Boolean,
        capeAnimationEnabled: Boolean,
        wingScale: Float
    ) {
        selectedCapeId = capeId
        selectedWingsId = wingsId?.takeIf { it.isNotBlank() } ?: BUILTIN_WINGS_ID
        this.wingsEnabled = wingsEnabled
        this.capeAnimationEnabled = capeAnimationEnabled
        configuredWingScale = wingScale.coerceIn(0f, 1f)
    }

    fun refreshOwned() {
        FPSMasterApiClient.getCatalogItems().whenComplete { result, exception ->
            if (exception != null || result?.success != true) {
                logger.warn("Failed to load cosmetics catalog: ${exception?.message ?: result?.message}")
                return@whenComplete
            }
            catalogOptions = result.data.orEmpty()
                .filter { it.available && it.category.lowercase() in COSMETIC_CATEGORIES && it.assetKey.isNotBlank() }
                .map(::option)
                .sortedWith(compareBy<CosmeticOption> { it.category }.thenBy { it.name })
        }
        if (!AuthService.isLoggedIn()) {
            ownedOptions = emptyList()
            validateSelections()
            return
        }
        FPSMasterApiClient.getOwnedItems().whenComplete { result, exception ->
            if (exception != null || result?.success != true) {
                logger.warn("Failed to load owned cosmetics: ${exception?.message ?: result?.message}")
                return@whenComplete
            }
            ownedOptions = result.data.orEmpty()
                .map { it.item }
                .filter { it.category.lowercase() in COSMETIC_CATEGORIES && it.assetKey.isNotBlank() }
                .map(::option)
                .sortedWith(compareBy<CosmeticOption> { it.category }.thenBy { it.name })
            validateSelections()
            selectedCape()?.let(::loadTexture)
            loadTexture(selectedBack())
        }
    }

    fun reloadCustom() {
        val directory = customDirectory()
        try {
            Files.createDirectories(directory)
            installExamples(directory)
            val options = ArrayList<CosmeticOption>()
            Files.newDirectoryStream(directory, "*.json").use { files ->
                files.forEach { file ->
                    try {
                        options.add(parseCustom(file))
                    } catch (exception: Exception) {
                        logger.warn("Failed to load custom cosmetic ${file.fileName}: ${exception.message}")
                    }
                }
            }
            val staleCustom = synchronized(textures) {
                customOptions.mapNotNull { previous -> textures.remove(previous.id) }
            }
            releaseTextures(staleCustom)
            customOptions = options.sortedWith(compareBy<CosmeticOption> { it.category }.thenBy { it.name })
            validateSelections()
        } catch (exception: Exception) {
            logger.warn("Failed to load custom cosmetics: ${exception.message}")
        }
    }

    fun openCustomDirectory() {
        reloadCustom()
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw UnsupportedOperationException("Desktop folder opening is unavailable")
            }
            Desktop.getDesktop().open(customDirectory().toFile())
        } catch (exception: Exception) {
            logger.warn("Failed to open custom cosmetics folder: ${exception.message}")
        }
    }

    fun allOptions(): List<CosmeticOption> = (listOf(BUILTIN_WINGS) + customOptions + catalogOptions + ownedOptions)
        .distinctBy { it.id }

    fun isOwned(id: String): Boolean = id == BUILTIN_WINGS_ID || customOptions.any { it.id == id } ||
        ownedOptions.any { it.id == id }

    fun isEquipped(id: String): Boolean = when (allOptions().firstOrNull { it.id == id }?.category) {
        "cape" -> selectedCapeId == id
        "wings", "elytra" -> wingsEnabled && selectedWingsId == id
        else -> false
    }

    fun preview(id: String) {
        val option = allOptions().firstOrNull { it.id == id } ?: return
        if (option.category == "cape") {
            previewCapeId = id
        } else {
            previewBackId = id
            previewWingScale = if (isEquipped(id)) configuredWingScale else option.defaultScale
        }
        loadTexture(option)
    }

    fun equip(id: String) {
        if (!isOwned(id)) return
        val option = allOptions().firstOrNull { it.id == id } ?: return
        if (option.category == "cape") {
            selectedCapeId = id
            previewCapeId = id
        } else {
            selectedWingsId = id
            previewBackId = id
            wingsEnabled = true
            configuredWingScale = if (previewing) previewWingScale else option.defaultScale
        }
        loadTexture(option)
    }

    fun grantPurchasedAndEquip(id: String) {
        val option = allOptions().firstOrNull { it.id == id } ?: return
        ownedOptions = (ownedOptions + option).distinctBy { it.id }
        equip(id)
    }

    fun clearPreview() {
        previewCapeId = null
        previewBackId = null
        previewWingScale = configuredWingScale
    }

    fun capeOptions(): List<CosmeticOption> = (customOptions + ownedOptions)
        .filter { it.category == "cape" }
        .distinctBy { it.id }

    fun backOptions(): List<CosmeticOption> = listOf(BUILTIN_WINGS) +
        (customOptions + ownedOptions)
            .filter { it.category == "wings" || it.category == "elytra" }
            .distinctBy { it.id }

    fun selectedCape(): CosmeticOption? = capeOptions().firstOrNull { it.id == selectedCapeId }

    fun selectedBack(): CosmeticOption = backOptions().firstOrNull { it.id == selectedWingsId } ?: BUILTIN_WINGS

    fun nextCape() {
        val options = listOf<CosmeticOption?>(null) + capeOptions()
        val index = options.indexOfFirst { it?.id == selectedCapeId }
        val selected = options[(index + 1).mod(options.size)]
        selectedCapeId = selected?.id
        selected?.let(::loadTexture)
    }

    fun nextWings() {
        val options = backOptions()
        val index = options.indexOfFirst { it.id == selectedWingsId }.coerceAtLeast(0)
        val selected = options[(index + 1).mod(options.size)]
        selectedWingsId = selected.id
        loadTexture(selected)
    }

    fun setWingsEnabled(enabled: Boolean) {
        wingsEnabled = enabled
        if (enabled) loadTexture(selectedBack())
    }

    fun setCapeAnimationEnabled(enabled: Boolean) {
        capeAnimationEnabled = enabled
    }

    fun setWingScale(scale: Float) {
        val option = effectiveBack()
        if (!option.scaleAdjustable) return
        val value = scale.coerceIn(0f, 1f)
        if (previewing && previewBackId != null) {
            previewWingScale = value
            if (isEquipped(option.id)) configuredWingScale = value
        } else {
            configuredWingScale = value
        }
    }

    @JvmStatic
    fun setPreviewing(value: Boolean) {
        previewing = value
    }

    @JvmStatic
    fun isPreviewing(): Boolean = previewing

    @JvmStatic
    fun animatesCape(): Boolean = capeAnimationEnabled

    @JvmStatic
    fun selectsDragonWings(): Boolean = effectiveBack().category == "wings"

    @JvmStatic
    fun rendersDragonWings(): Boolean = (wingsEnabled || previewing && previewBackId != null) && selectsDragonWings()

    @JvmStatic
    fun rendersElytra(): Boolean = (wingsEnabled || previewing && previewBackId != null) && effectiveBack().category == "elytra"

    @JvmStatic
    fun wingTexture(): TextureId? = selectedTexture(effectiveBack())

    @JvmStatic
    fun capeTexture(): TextureId? = effectiveCape()?.let(::selectedTexture)

    fun textureFor(id: String): TextureId? = allOptions().firstOrNull { it.id == id }?.let(::selectedTexture)

    private fun effectiveCape(): CosmeticOption? = if (previewing) {
        previewCapeId?.let { id -> allOptions().firstOrNull { it.id == id } } ?: selectedCape()
    } else selectedCape()

    private fun effectiveBack(): CosmeticOption = if (previewing) {
        previewBackId?.let { id -> allOptions().firstOrNull { it.id == id } } ?: selectedBack()
    } else selectedBack()

    private fun selectedTexture(option: CosmeticOption): TextureId? {
        if (option.id == BUILTIN_WINGS_ID) return null
        synchronized(textures) { textures[option.id] }?.let { return it }
        loadTexture(option)
        return null
    }

    private fun validateSelections() {
        if (selectedCapeId != null && capeOptions().none { it.id == selectedCapeId }) selectedCapeId = null
        if (backOptions().none { it.id == selectedWingsId }) selectedWingsId = BUILTIN_WINGS_ID
    }

    private fun loadTexture(option: CosmeticOption) {
        val assetKey = option.assetKey ?: return
        synchronized(textures) {
            if (option.id in textures || !loading.add(option.id)) return
        }
        executor.execute {
            try {
                val bytes = if (option.local) Files.readAllBytes(Paths.get(assetKey)).also {
                    require(it.isNotEmpty() && it.size <= MAX_BYTES) { "cosmetic asset size is invalid" }
                } else download(resolveAssetUrl(assetKey))
                validatePng(bytes, option.category)
                val image = NativeImage.read(ByteArrayInputStream(bytes))
                mc.execute {
                    try {
                        val textureId = identifier("cosmetic/${option.category}/${option.id.hashCode().toUInt()}")
                        mc.textureManager.register(textureId, CosmeticTexture(image))
                        RuntimeProbe.textureAllocated()
                        synchronized(textures) {
                            textures[option.id] = textureId
                            loading.remove(option.id)
                        }
                    } catch (exception: Exception) {
                        image.close()
                        fail(option, exception)
                    }
                }
            } catch (exception: Exception) {
                fail(option, exception)
            }
        }
    }

    private fun resolveAssetUrl(assetKey: String): String = if (assetKey.startsWith('/')) {
        API_BASE_URL + assetKey
    } else {
        assetKey
    }

    private fun download(url: String): ByteArray {
        val uri = URI.create(url)
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) { "unsupported cosmetic URL" }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "FPSMaster-Nova/${top.fpsmaster.Client.VERSION}")
        return try {
            require(connection.responseCode in 200..299) { "cosmetic download returned ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "cosmetic asset is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray().also { require(it.isNotEmpty()) { "cosmetic asset is empty" } }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validatePng(bytes: ByteArray, category: String) {
        require(bytes.size >= 24 && bytes.copyOf(8).contentEquals(PNG_SIGNATURE)) { "cosmetic asset is not PNG" }
        val dimensions = ByteBuffer.wrap(bytes, 16, 8)
        val width = dimensions.int
        val height = dimensions.int
        require(width in 1..4096 && height in 1..4096) { "cosmetic dimensions are invalid" }
        val validAtlas = if (category == "wings") {
            width == height && width % 30 == 0
        } else {
            width % 64 == 0 && height % 32 == 0 && width / 64 == height / 32
        }
        require(validAtlas) { "cosmetic atlas dimensions do not match $category" }
    }

    private fun fail(option: CosmeticOption, exception: Exception) {
        synchronized(textures) { loading.remove(option.id) }
        logger.warn("Failed to load cosmetic ${option.id}: ${exception.message}")
    }

    private fun option(item: ItemView) = CosmeticOption(
        id = item.id.toString(),
        name = item.name,
        description = item.description,
        category = item.category.lowercase(),
        assetKey = item.assetKey,
        price = item.price
    )

    private fun parseCustom(file: Path): CosmeticOption {
        val root = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
        require(root.int("schemaVersion", 0) == 1) { "unsupported schemaVersion" }
        val rawId = root.string("id")
        require(rawId.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "id contains unsupported characters" }
        val name = root.string("name")
        require(name.isNotBlank() && name.length <= 64) { "name must contain 1-64 characters" }
        val category = root.string("type").lowercase()
        require(category in COSMETIC_CATEGORIES) { "type must be cape, wings, or elytra" }
        val textureValue = root.string("texture")
        val texturePath = Paths.get(textureValue).let { path ->
            (if (path.isAbsolute) path else file.parent.resolve(path)).normalize().toAbsolutePath()
        }
        require(Files.isRegularFile(texturePath)) { "texture file does not exist" }
        require(Files.size(texturePath) in 1..MAX_BYTES.toLong()) { "cosmetic asset size is invalid" }
        val wing = root.get("wing")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        return CosmeticOption(
            id = "custom:$rawId",
            name = name,
            description = root.optionalString("description"),
            category = category,
            assetKey = texturePath.toString(),
            price = "0",
            defaultScale = wing.float("scale", 1f).coerceIn(0f, 1f),
            scaleAdjustable = wing.boolean("allowResize", true),
            local = true
        )
    }

    private fun customDirectory(): Path = mc.gameDirectory.toPath()
        .resolve("config").resolve("fpsmaster").resolve("cosmetics")

    private fun installExamples(directory: Path) {
        val examples = directory.resolve("examples")
        Files.createDirectories(examples)
        copyIfMissing(
            directory.resolve("example-cape.json.disabled"),
            "/assets/fpsmaster/cosmetics/examples/example-cape.json.disabled"
        )
        copyIfMissing(
            directory.resolve("example-wings.json.disabled"),
            "/assets/fpsmaster/cosmetics/examples/example-wings.json.disabled"
        )
        copyIfMissing(
            examples.resolve("example-cape.png"),
            "/assets/fpsmaster/cosmetics/examples/example-cape.png"
        )
        copyIfMissing(
            examples.resolve("example-wings.png"),
            "/assets/fpsmaster/cosmetics/examples/example-wings.png"
        )
    }

    private fun copyIfMissing(target: Path, resource: String) {
        if (Files.exists(target)) return
        val input = CosmeticManager::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled cosmetic example $resource")
        input.use { Files.copy(it, target) }
    }

    private fun JsonObject.string(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }
        ?.asString ?: throw IllegalArgumentException("missing $name")

    private fun JsonObject.optionalString(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }
        ?.asString.orEmpty()

    private fun JsonObject.int(name: String, fallback: Int): Int = get(name)?.takeIf { it.isJsonPrimitive }
        ?.asInt ?: fallback

    private fun JsonObject.float(name: String, fallback: Float): Float = get(name)?.takeIf { it.isJsonPrimitive }
        ?.asFloat ?: fallback

    private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean = get(name)
        ?.takeIf { it.isJsonPrimitive }?.asBoolean ?: fallback

    private class CosmeticTexture(image: NativeImage) : DynamicTexture(
        //? if >=1.21.5 {
        { "fpsmaster-cosmetic" }, image
        //?} else {
        /*image*/
        //?}
    )

    private val BUILTIN_WINGS = CosmeticOption(BUILTIN_WINGS_ID, "", "", "wings", null, "0")
    private val COSMETIC_CATEGORIES = setOf("cape", "elytra", "wings")
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )
}

//? if >=1.21.11 {
typealias TextureId = Identifier
//?} else {
/*typealias TextureId = ResourceLocation
*///?}
