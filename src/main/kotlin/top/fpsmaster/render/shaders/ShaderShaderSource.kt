package top.fpsmaster.render.shaders

// CEF shader-source provider for the modern (>=1.21.5) render path. The precompilePipeline source
// type changed: 1.21.11 takes a com.mojang.blaze3d.shaders.ShaderSource (get(Identifier, ShaderType)),
// while 1.21.5..1.21.10 take a BiFunction<ResourceLocation, ShaderType, String>. The class is renamed
// ResourceLocation -> Identifier at 1.21.11. Legacy (<1.21.5) has no RenderPipeline shaders at all.
//? if >=1.21.11 {
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import net.minecraft.resources.Identifier

class ShaderShaderSource : ShaderSource {
    val shaders = HashMap<Identifier, String>()

    init {
        loadShader(Identifier.fromNamespaceAndPath("fpsmaster", "shaders/bgra.frag"))
    }

    fun loadShader(identifier: Identifier) {
        val resourcePath = "assets/${identifier.namespace}/${identifier.path}"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Shader resource not found: $resourcePath")
        shaders[identifier] = stream.bufferedReader().use { it.readText() }
    }

    override fun get(identifier: Identifier, shaderType: ShaderType): String? {
        return shaders[identifier]
    }
}
//?}

//? if >=1.21.5 && <1.21.11 {
/*import com.mojang.blaze3d.shaders.ShaderType
import net.minecraft.resources.ResourceLocation
import java.util.function.BiFunction

class ShaderShaderSource : BiFunction<ResourceLocation, ShaderType, String?> {
    val shaders = HashMap<ResourceLocation, String>()

    init {
        loadShader(ResourceLocation.fromNamespaceAndPath("fpsmaster", "shaders/bgra.frag"))
    }

    fun loadShader(identifier: ResourceLocation) {
        val resourcePath = "assets/${identifier.namespace}/${identifier.path}"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Shader resource not found: $resourcePath")
        shaders[identifier] = stream.bufferedReader().use { it.readText() }
    }

    override fun apply(identifier: ResourceLocation, shaderType: ShaderType): String? {
        return shaders[identifier]
    }
}*/
//?}
