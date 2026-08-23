package top.fpsmaster.ui.kit

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
//? if >=1.21.5 {
import com.mojang.blaze3d.textures.FilterMode
//?}
import net.minecraft.client.renderer.texture.DynamicTexture
import top.fpsmaster.identifier
import top.fpsmaster.logger
import top.fpsmaster.mc
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors

internal object NovaMusicTextures {
    private const val MAX_READY = 96
    private const val MAX_LOADING = 32
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val USER_AGENT = "Mozilla/5.0 FPSMaster/1.0"

    private data class Entry(val image: NovaImage)

    private val ready = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            if (size <= MAX_READY) return false
            eldest?.value?.let { mc.textureManager.release(it.image.id) }
            return true
        }
    }
    private val loading = HashSet<String>()
    private val failed = object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            if (size >= MAX_READY) remove(first())
            return super.add(element)
        }
    }
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FPSMaster-Music-Cover").apply { isDaemon = true }
    }
    private var nextId = 0L

    @Synchronized
    fun cover(url: String?): NovaImage? {
        if (url.isNullOrBlank()) return null
        ready[url]?.let { return it.image }
        if (url in loading || url in failed || loading.size >= MAX_LOADING) return null
        loading += url
        executor.execute { load(url) }
        return null
    }

    private fun load(url: String) {
        try {
            val bytes = readBytes(url)
            val native = NativeImage.read(ByteArrayInputStream(bytes))
            val width = native.width
            val height = native.height
            mc.execute {
                try {
                    val id = identifier("ui/music/cover/${nextId++}")
                    mc.textureManager.register(id, CoverTexture(native, "fpsmaster-music-cover"))
                    synchronized(this) {
                        ready[url] = Entry(NovaImage(id, width, height))
                        loading -= url
                    }
                } catch (exception: Exception) {
                    native.close()
                    fail(url, exception)
                }
            }
        } catch (exception: Exception) {
            fail(url, exception)
        }
    }

    private fun readBytes(url: String): ByteArray {
        val uri = URI.create(url)
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(uri)).also {
                require(it.size <= MAX_BYTES) { "Cover image is too large" }
            }
        }
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "Unsupported cover URL"
        }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return try {
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "Cover image is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray().also { require(it.isNotEmpty()) { "Cover image is empty" } }
            }
        } finally {
            connection.disconnect()
        }
    }

    @Synchronized
    private fun fail(url: String, exception: Exception) {
        loading -= url
        failed += url
        logger.warn("Failed to load music cover: ${exception.message}")
    }

    private class CoverTexture(image: NativeImage, name: String) : DynamicTexture(
        //? if >=1.21.5 {
        { name }, image
        //?} else {
        /*image*/
        //?}
    ) {
        init {
            //? if >=1.21.11 {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)
            //?} else if >=1.21.5 {
            /*setFilter(true, false)
            setClamp(true)*/
            //?} else {
            /*setFilter(true, false)*/
            //?}
        }
    }
}
