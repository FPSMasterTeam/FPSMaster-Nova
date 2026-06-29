package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

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
