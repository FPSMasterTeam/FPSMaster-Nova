package top.fpsmaster.mixin.impl;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface MixinRenderPipelines {
    @Invoker("register")
    static RenderPipeline fpsmaster$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
