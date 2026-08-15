package top.fpsmaster.mixin.impl;

//? if <1.21.5 {

/*import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// 1.20.1-only accessor: exposes PostChain's private pass list so the MotionBlur / BetterScreen post
// effects can push live uniform values (Phosphor decay, blur Radius) before each PostChain.process.
// 1.21.5+ uses the RenderPipeline post-effect system instead, so this mixin is not applied there.
@Mixin(PostChain.class)
public interface MixinPostChain {
    @Accessor("passes")
    List<PostPass> fpsmaster$getPasses();
}

*///?}
