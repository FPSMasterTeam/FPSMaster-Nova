package top.fpsmaster.render;

// 1.21.11+ only. On 1.21.5..1.21.10 RenderType.create and every CompositeStateBuilder setter are
// package-private/protected, so the equivalent render types are built from
// net.minecraft.client.renderer.FpsmasterBlockOverlay, which sits inside that package.
//? if >=1.21.11 {

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import top.fpsmaster.mixin.impl.MixinRenderPipelines;
import top.fpsmaster.mixin.impl.MixinRenderType;

public final class FpsmasterBlockOverlayRenderTypes {
    private static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    private static final RenderPipeline.Snippet FOG_SNIPPET = RenderPipeline.builder()
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    private static final RenderPipeline.Snippet GLOBALS_SNIPPET = RenderPipeline.builder()
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .buildSnippet();

    private static final RenderPipeline.Snippet MATRICES_FOG_SNIPPET = RenderPipeline.builder(
            MATRICES_PROJECTION_SNIPPET,
            FOG_SNIPPET
    ).buildSnippet();

    private static final RenderPipeline.Snippet LINES_SNIPPET = RenderPipeline.builder(
                    MATRICES_FOG_SNIPPET,
                    GLOBALS_SNIPPET
            )
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
            .buildSnippet();

    private static final RenderPipeline.Snippet DEBUG_FILLED_SNIPPET = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .buildSnippet();

    private static final RenderPipeline BLOCK_OVERLAY_LINES_NO_DEPTH = MixinRenderPipelines.fpsmaster$register(
            RenderPipeline.builder(LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("fpsmaster", "pipeline/block_overlay_lines_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderPipeline BLOCK_OVERLAY_FILL_NO_DEPTH = MixinRenderPipelines.fpsmaster$register(
            RenderPipeline.builder(DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("fpsmaster", "pipeline/block_overlay_fill_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderType LINES_NO_DEPTH = MixinRenderType.fpsmaster$create(
            "fpsmaster_block_overlay_lines_no_depth",
            RenderSetup.builder(BLOCK_OVERLAY_LINES_NO_DEPTH)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup()
    );

    private static final RenderType FILL_NO_DEPTH = MixinRenderType.fpsmaster$create(
            "fpsmaster_block_overlay_fill_no_depth",
            RenderSetup.builder(BLOCK_OVERLAY_FILL_NO_DEPTH)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    private FpsmasterBlockOverlayRenderTypes() {
    }

    public static RenderType linesNoDepth() {
        return LINES_NO_DEPTH;
    }

    public static RenderType fillNoDepth() {
        return FILL_NO_DEPTH;
    }
}

//?}
