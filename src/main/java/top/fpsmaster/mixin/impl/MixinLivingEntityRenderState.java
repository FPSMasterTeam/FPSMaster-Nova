package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import top.fpsmaster.mixin.interfaces.ILivingEntityRenderState;

@Mixin(LivingEntityRenderState.class)
public class MixinLivingEntityRenderState implements ILivingEntityRenderState {
    private int fpsmaster$entityId = -1;

    @Override
    public void fpsmaster$setEntityId(int entityId) {
        this.fpsmaster$entityId = entityId;
    }

    @Override
    public int fpsmaster$getEntityId() {
        return fpsmaster$entityId;
    }
}

//?}
