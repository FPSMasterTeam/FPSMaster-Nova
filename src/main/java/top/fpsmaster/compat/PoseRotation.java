package top.fpsmaster.compat;

import com.mojang.blaze3d.vertex.PoseStack;
//? if >=1.19.4 {
import com.mojang.math.Axis;
//?} else {
/*import com.mojang.math.Vector3f;
*///?}

/** Version bridge for the 1.19.2 Quaternion to 1.19.4+ Quaternionf axis transition. */
public final class PoseRotation {
    private PoseRotation() {
    }

    public static void x(PoseStack pose, float degrees) {
        //? if >=1.19.4 {
        pose.mulPose(Axis.XP.rotationDegrees(degrees));
        //?} else {
        /*pose.mulPose(Vector3f.XP.rotationDegrees(degrees));*/
        //?}
    }

    public static void z(PoseStack pose, float degrees) {
        //? if >=1.19.4 {
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        //?} else {
        /*pose.mulPose(Vector3f.ZP.rotationDegrees(degrees));*/
        //?}
    }
}
