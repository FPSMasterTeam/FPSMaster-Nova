package top.fpsmaster.mixin.impl;

// Only 1.21.11+ needs this invoker: there RenderType.create(String, RenderSetup) is package-private
// but both RenderSetup and its builder are public, so the render types can be assembled from
// top.fpsmaster.render. Before 1.21.11 the composite-state builder's setters are protected too, so
// the block-overlay types are built from net.minecraft.client.renderer.FpsmasterBlockOverlay
// instead, which reaches RenderType.create directly.
//? if >=1.21.11 {

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface MixinRenderType {
    @Invoker("create")
    static RenderType fpsmaster$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}

//?}
