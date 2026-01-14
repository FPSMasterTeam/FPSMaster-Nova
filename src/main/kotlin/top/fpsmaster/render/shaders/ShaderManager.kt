package top.fpsmaster.render.shaders

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import top.fpsmaster.identifier

val shaders: HashMap<String, RenderPipeline> = hashMapOf()

fun init() {
    val builder = RenderPipeline.Builder()
    val bgra_shader = builder
        .withLocation(identifier("pipeline/jcef/bgra_blurred_texture"))
        .apply {
            withVertexShader("core/position_tex_color")
            val identifier = identifier("shaders/bgra.frag")
            withFragmentShader(identifier)
            withSampler("Sampler0")
            withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            withSnippet(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        }.build();

    val texture_shader = builder
        .withLocation(identifier("pipeline/jcef/texture"))
        .apply {
            withSnippet(RenderPipelines.GUI_TEXTURED_SNIPPET)
            withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        }.build()


    val source = ShaderShaderSource()
    RenderSystem.getDevice().precompilePipeline(bgra_shader!!, source)
    RenderSystem.getDevice().precompilePipeline(texture_shader!!, source)

    shaders["pipeline/jcef/bgra_blurred_texture"] = bgra_shader
    shaders["pipeline/jcef/texture"] = texture_shader
}

fun getShader(name: String): RenderPipeline? {
    return shaders[name]
}
