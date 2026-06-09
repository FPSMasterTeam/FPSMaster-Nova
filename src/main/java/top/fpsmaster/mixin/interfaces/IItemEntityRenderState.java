package top.fpsmaster.mixin.interfaces;

public interface IItemEntityRenderState {
    void fpsmaster$setItemPhysicsState(boolean onGround, float rotationPitch);

    boolean fpsmaster$isOnGround();

    float fpsmaster$getRotationPitch();
}
