package top.fpsmaster.render.shaders

import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import net.minecraft.resources.Identifier

class ShaderShaderSource: ShaderSource {
    val shaders = HashMap<Identifier, String>()

    init {
        loadShader(Identifier.fromNamespaceAndPath("fpsmaster", "shaders/bgra.frag"))
    }

    fun loadShader(identifier: Identifier){
        val resourcePath = "assets/${identifier.namespace}/${identifier.path}"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Shader resource not found: $resourcePath")
        shaders[identifier] = stream.bufferedReader().use { it.readText() }
    }

    override fun get(
        identifier: Identifier,
        shaderType: ShaderType
    ): String? {
        return shaders[identifier]
    }
}
