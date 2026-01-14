package top.fpsmaster.render.shaders

import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

class ShaderShaderSource: ShaderSource {
    val shaders = HashMap<Identifier, String>()

    init {
        loadShader(Identifier.fromNamespaceAndPath("fpsmaster", "shaders/bgra.frag"))
    }

    fun loadShader(identifier: Identifier){
        shaders[identifier] = Minecraft.getInstance().resourceManager.getResource(identifier).get().openAsReader().readLines().joinToString("\n")
    }

    override fun get(
        identifier: Identifier,
        shaderType: ShaderType
    ): String? {
        return shaders[identifier]
    }
}