package top.fpsmaster.render.shaders

// 26.2 native-render deferral: the RenderPipeline.Builder fluent API (withUniform/withSampler/withBlend/
// withDepthTestFunction/withVertexFormat, DepthTestFunction, VertexFormat.Mode) changed on 26.2. The
// CEF jcef shader pipelines are part of the accelerated web-UI render bridge, which is deferred on 26.2
// (see [[nova-mc26-unobfuscated-build]]); provide an empty stub so callers compile and getShader()
// returns null (the bridge skips the shader path).
//? if >=26 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline

val shaders: HashMap<String, RenderPipeline> = hashMapOf()
fun init() {}
fun getShader(name: String): RenderPipeline? = shaders[name]*/
//?}

//? if >=1.21.5 && <26 {
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import top.fpsmaster.identifier

val shaders: HashMap<String, RenderPipeline> = hashMapOf()

private val matricesProjectionSnippet = RenderPipeline.builder()
    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
    .buildSnippet()

private val guiTexturedSnippet = RenderPipeline.builder(matricesProjectionSnippet)
    .withVertexShader("core/position_tex_color")
    .withFragmentShader("core/position_tex_color")
    .withSampler("Sampler0")
    .withBlend(BlendFunction.TRANSLUCENT)
    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
    .buildSnippet()

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
            withSnippet(matricesProjectionSnippet)
            // Straight (non-premultiplied) alpha, matching upstream CCBlueX/mcef which draws the CEF
            // texture with the default GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA blend and treats it as
            // straight alpha. The fork previously used TRANSLUCENT_PREMULTIPLIED_ALPHA, whose mismatch
            // with the texture data drove the shader's opaque-forcing hack that killed translucency.
            withBlend(BlendFunction.TRANSLUCENT)
            withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        }.build();

    val texture_shader = builder
        .withLocation(identifier("pipeline/jcef/texture"))
        .apply {
            withSnippet(guiTexturedSnippet)
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
//?}
