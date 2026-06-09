package top.fpsmaster.mixin.impl;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import top.fpsmaster.mixin.interfaces.IItemEntityRenderState;

@Mixin(ItemEntityRenderState.class)
public class MixinItemEntityRenderState implements IItemEntityRenderState {
    private boolean fpsmaster$onGround;
    private float fpsmaster$rotationPitch;

    @Override
    public void fpsmaster$setItemPhysicsState(boolean onGround, float rotationPitch) {
        this.fpsmaster$onGround = onGround;
        this.fpsmaster$rotationPitch = rotationPitch;
    }

    @Override
    public boolean fpsmaster$isOnGround() {
        return fpsmaster$onGround;
    }

    @Override
    public float fpsmaster$getRotationPitch() {
        return fpsmaster$rotationPitch;
    }
}
