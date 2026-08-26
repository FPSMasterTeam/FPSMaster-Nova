package net.minecraft.client.renderer;

//? if >=1.21.11 {

// 1.21.11 moved RenderType to net.minecraft.client.renderer.rendertype and exposes
// Window.getAppropriateLineWidth(), which MixinFishingHookRenderer redirects directly.
// No custom render type is needed on this era, so this compilation unit is intentionally empty.

//?} else if >=1.21.5 {

/*import java.util.OptionalDouble;

// 1.21.5..1.21.10 helper. Lives in net.minecraft.client.renderer on purpose: RenderType.create is
// package-private and CompositeStateBuilder's setters are protected, so the composite can only be
// assembled from inside this package. This era has no Window.getAppropriateLineWidth(), and
// FishingHookRenderer.render still draws the string through RenderType.lineStrip(), whose
// LineStateShard width is empty. Rebuild that render type with a configurable width instead.
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
                    1536,
                    RenderPipelines.LINE_STRIP,
                    RenderType.CompositeState.builder()
                            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                            .createCompositeState(false)
            );
        }
        return cached;
    }
}

*///?} else {

/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

// Pre-1.21.5 helper. Lives in net.minecraft.client.renderer on purpose: the line-strip render type's
// composite (RenderType.CompositeState, RenderStateShard.LineStateShard and the shard constants) is
// protected, so it can only be assembled from inside this package. This era has no
// Window.getAppropriateLineWidth hook, so BetterFishingRod rebuilds the vanilla LINE_STRIP render
// type with a configurable line width here.
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

*///?}
