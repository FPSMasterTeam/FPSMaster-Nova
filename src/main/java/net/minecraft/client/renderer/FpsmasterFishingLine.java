package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

/**
 * 1.20.1-only helper. Lives in {@code net.minecraft.client.renderer} on purpose: the line-strip
 * render type's composite ({@code RenderType.CompositeState}, {@code RenderStateShard.LineStateShard}
 * and the shard constants) is {@code protected}, so it can only be assembled from inside this
 * package. 1.20.1 has no {@code Window.getAppropriateLineWidth} hook, so BetterFishingRod rebuilds
 * the vanilla {@code LINE_STRIP} render type with a configurable line width here.
 *
 * <p>Excluded from the source set on 1.21.x (the render-pipeline API differs there).
 */
public final class FpsmasterFishingLine {
    private static RenderType cached;
    private static double cachedWidth = -1.0;

    private FpsmasterFishingLine() {
    }

    public static RenderType lineStrip(double width) {
        if (cached == null || cachedWidth != width) {
            cachedWidth = width;
            cached = RenderType.create(
                    "fpsmaster_fishing_line",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    VertexFormat.Mode.LINE_STRIP,
                    256,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                            .setCullState(RenderStateShard.NO_CULL)
                            .createCompositeState(false)
            );
        }
        return cached;
    }
}
