package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

/**
 * 1.20.1-only helper. Lives in {@code net.minecraft.client.renderer} on purpose: the line/fill
 * render-type composites are assembled from {@code protected} {@link RenderStateShard} constants and
 * the {@code protected} {@link RenderType#create} factory, which are only reachable from inside this
 * package (no access-widener needed).
 *
 * <p>Provides the no-depth / custom-width line and translucent fill render types used by
 * BlockOverlay on 1.20.1, where {@code LevelRenderer.renderHitOutline} draws via the legacy
 * {@code RenderType.lines()} / {@code renderShape} path instead of the 1.21.5 render pipeline.
 *
 * <p>Excluded from the source set on 1.21.x (the render-pipeline API differs there).
 */
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
