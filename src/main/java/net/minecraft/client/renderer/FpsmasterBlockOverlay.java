package net.minecraft.client.renderer;

//? if >=1.21.11 {

// 1.21.11 moved RenderType to net.minecraft.client.renderer.rendertype and replaced the composite
// state with RenderSetup, which is public, so top.fpsmaster.render.FpsmasterBlockOverlayRenderTypes
// builds the block-overlay types there. Nothing needs package-private access on this era, so this
// compilation unit is intentionally empty.

//?} else if >=1.21.5 {

/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import top.fpsmaster.mixin.impl.MixinRenderPipelines;

import java.util.OptionalDouble;

// 1.21.5..1.21.10 block-overlay render types. Lives in net.minecraft.client.renderer on purpose:
// RenderType.create(String, int, RenderPipeline, CompositeState) is package-private and every
// CompositeStateBuilder setter is protected on this era, so the composite can only be assembled from
// inside this package (top.fpsmaster.render cannot reach either).
//
// The custom line width still lives in RenderStateShard.LineStateShard (vanilla's own
// RenderType.debugLineStrip(double) is built the same way), so the outline width is expressed as a
// width-carrying composite over the vanilla LINES pipeline; only "render through blocks" needs a
// separate pipeline, because the depth test moved into RenderPipeline.
public final class FpsmasterBlockOverlay {
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

    private static final RenderPipeline.Snippet LINES_SNIPPET = RenderPipeline.builder(
                    MATRICES_PROJECTION_SNIPPET,
                    FOG_SNIPPET,
                    GLOBALS_SNIPPET
            )
            .withVertexShader("core/rendertype_lines")
            .withFragmentShader("core/rendertype_lines")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            .buildSnippet();

    private static final RenderPipeline.Snippet FILL_SNIPPET = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .buildSnippet();

    private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = MixinRenderPipelines.fpsmaster$register(
            RenderPipeline.builder(LINES_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("fpsmaster", "pipeline/block_overlay_lines_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderPipeline FILL_PIPELINE = MixinRenderPipelines.fpsmaster$register(
            RenderPipeline.builder(FILL_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("fpsmaster", "pipeline/block_overlay_fill"))
                    .withDepthWrite(false)
                    .build()
    );

    private static final RenderPipeline FILL_NO_DEPTH_PIPELINE = MixinRenderPipelines.fpsmaster$register(
            RenderPipeline.builder(FILL_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("fpsmaster", "pipeline/block_overlay_fill_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private static RenderType cachedLines;
    private static double cachedLinesWidth = -1.0;
    private static RenderType cachedLinesNoDepth;
    private static double cachedLinesNoDepthWidth = -1.0;
    private static RenderType cachedFill;
    private static RenderType cachedFillNoDepth;

    private FpsmasterBlockOverlay() {
    }

    public static RenderType lines(double width) {
        if (cachedLines == null || cachedLinesWidth != width) {
            cachedLinesWidth = width;
            cachedLines = buildLines("fpsmaster_block_overlay_lines", width, RenderPipelines.LINES);
        }
        return cachedLines;
    }

    public static RenderType linesNoDepth(double width) {
        if (cachedLinesNoDepth == null || cachedLinesNoDepthWidth != width) {
            cachedLinesNoDepthWidth = width;
            cachedLinesNoDepth = buildLines("fpsmaster_block_overlay_lines_no_depth", width, LINES_NO_DEPTH_PIPELINE);
        }
        return cachedLinesNoDepth;
    }

    public static RenderType fill() {
        if (cachedFill == null) {
            cachedFill = buildFill("fpsmaster_block_overlay_fill", FILL_PIPELINE);
        }
        return cachedFill;
    }

    public static RenderType fillNoDepth() {
        if (cachedFillNoDepth == null) {
            cachedFillNoDepth = buildFill("fpsmaster_block_overlay_fill_no_depth", FILL_NO_DEPTH_PIPELINE);
        }
        return cachedFillNoDepth;
    }

    private static RenderType buildLines(String name, double width, RenderPipeline pipeline) {
        return RenderType.create(
                name,
                1536,
                pipeline,
                RenderType.CompositeState.builder()
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .createCompositeState(false)
        );
    }

    private static RenderType buildFill(String name, RenderPipeline pipeline) {
        return RenderType.create(
                name,
                1536,
                pipeline,
                RenderType.CompositeState.builder()
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false)
        );
    }
}

*///?} else {

/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

// Pre-1.21.5 block-overlay render types. Lives in net.minecraft.client.renderer on purpose: the
// line/fill composites are assembled from protected RenderStateShard constants and the
// package-private RenderType.create factory, which are only reachable from inside this package (no
// access-widener needed).
//
// Provides the no-depth / custom-width line and translucent fill render types used by BlockOverlay
// on 1.19.2/1.20.1/1.21.1, where LevelRenderer.renderHitOutline draws via the legacy
// RenderType.lines() / renderShape path instead of the 1.21.5+ render pipeline.
public final class FpsmasterBlockOverlay {
    private static RenderType cachedLines;
    private static double cachedLinesWidth = -1.0;
    private static RenderType cachedLinesNoDepth;
    private static double cachedLinesNoDepthWidth = -1.0;
    private static RenderType cachedFill;
    private static RenderType cachedFillNoDepth;

    private FpsmasterBlockOverlay() {
    }

    public static RenderType lines(double width) {
        if (cachedLines == null || cachedLinesWidth != width) {
            cachedLinesWidth = width;
            cachedLines = buildLines("fpsmaster_block_overlay_lines", width, false);
        }
        return cachedLines;
    }

    public static RenderType linesNoDepth(double width) {
        if (cachedLinesNoDepth == null || cachedLinesNoDepthWidth != width) {
            cachedLinesNoDepthWidth = width;
            cachedLinesNoDepth = buildLines("fpsmaster_block_overlay_lines_no_depth", width, true);
        }
        return cachedLinesNoDepth;
    }

    public static RenderType fill() {
        if (cachedFill == null) {
            cachedFill = buildFill("fpsmaster_block_overlay_fill", false);
        }
        return cachedFill;
    }

    public static RenderType fillNoDepth() {
        if (cachedFillNoDepth == null) {
            cachedFillNoDepth = buildFill("fpsmaster_block_overlay_fill_no_depth", true);
        }
        return cachedFillNoDepth;
    }

    private static RenderType buildLines(String name, double width, boolean noDepth) {
        RenderType.CompositeState.CompositeStateBuilder builder = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setCullState(RenderStateShard.NO_CULL);
        if (noDepth) {
            builder.setDepthTestState(RenderStateShard.NO_DEPTH_TEST);
        }
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                256,
                builder.createCompositeState(false)
        );
    }

    private static RenderType buildFill(String name, boolean noDepth) {
        RenderType.CompositeState.CompositeStateBuilder builder = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL);
        if (noDepth) {
            builder.setDepthTestState(RenderStateShard.NO_DEPTH_TEST);
        }
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                131072,
                builder.createCompositeState(false)
        );
    }
}

*///?}
