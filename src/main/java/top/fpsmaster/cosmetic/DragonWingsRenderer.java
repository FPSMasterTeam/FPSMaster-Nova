package top.fpsmaster.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=1.20 {
import org.joml.Matrix4f;
import org.joml.Vector4f;
//?} else {
/*import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
*///?}

public final class DragonWingsRenderer {
    private static final float UNIT = 1.0F / 16.0F;

    private DragonWingsRenderer() {
    }

    public static void render(PoseStack.Pose pose, VertexConsumer buffer, int packedLight) {
        float animation = (System.currentTimeMillis() % 1000L) / 1000.0F * (float) Math.PI * 2.0F;
        int color = 0xFFFFFFFF;
        for (int side = 0; side < 2; side++) {
            float mirror = side == 0 ? 1.0F : -1.0F;
            //? if >=1.20 {
            Matrix4f wing = new Matrix4f()
                    .translate(-2.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ((float) Math.toRadians(20.0F) * mirror)
                    .rotateY((float) Math.toRadians(20.0F) * mirror + (float) Math.sin(animation) * 0.4F * mirror)
                    .rotateX((float) Math.toRadians(-80.0F) - (float) Math.cos(animation) * 0.2F);
            Matrix4f tip = new Matrix4f(wing)
                    .translate(-10.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ(-((float) Math.sin(animation + 2.0F) + 0.5F) * 0.75F * mirror);
            //?} else {
            /*Matrix4f wing = Matrix4f.createTranslateMatrix(-2.0F * UNIT * mirror, 0.0F, 0.0F);
            wing.multiply(Vector3f.ZP.rotationDegrees(20.0F * mirror));
            wing.multiply(Vector3f.YP.rotation(0.34906585F * mirror + (float) Math.sin(animation) * 0.4F * mirror));
            wing.multiply(Vector3f.XP.rotation(-1.3962634F - (float) Math.cos(animation) * 0.2F));
            Matrix4f tip = wing.copy();
            tip.multiplyWithTranslation(-10.0F * UNIT * mirror, 0.0F, 0.0F);
            tip.multiply(Vector3f.ZP.rotation(-((float) Math.sin(animation + 2.0F) + 0.5F) * 0.75F * mirror));
            *///?}
            bone(buffer, pose, wing, -10.0F * UNIT * mirror, -UNIT, -UNIT, 10.0F * UNIT * mirror, 2 * UNIT, 2 * UNIT, 0, 0, 10F / 30F, 2F / 30F, color, packedLight);
            membrane(buffer, pose, wing, -10.0F * UNIT * mirror, 0, 0.5F * UNIT, 10.0F * UNIT * mirror, 10 * UNIT, 0, 0, 8F / 30F, 10F / 30F, 18F / 30F, color, packedLight);
            bone(buffer, pose, tip, -10.0F * UNIT * mirror, -0.5F * UNIT, -0.5F * UNIT, 10.0F * UNIT * mirror, UNIT, UNIT, 0, 5F / 30F, 10F / 30F, 6F / 30F, color, packedLight);
            membrane(buffer, pose, tip, -10.0F * UNIT * mirror, 0, 0.5F * UNIT, 10.0F * UNIT * mirror, 10 * UNIT, 0, 0, 18F / 30F, 10F / 30F, 28F / 30F, color, packedLight);
        }
    }

    private static void bone(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x, float y, float z, float w, float h, float d, float u0, float v0, float u1, float v1, int color, int light) {
        float x2 = x + w, y2 = y + h, z2 = z + d;
        quad(b, p, m, x, y, z2, x2, y, z2, x2, y2, z2, x, y2, z2, u0, v0, u1, v1, 0, 0, 1, color, light);
        quad(b, p, m, x2, y, z, x, y, z, x, y2, z, x2, y2, z, u0, v0, u1, v1, 0, 0, -1, color, light);
        quad(b, p, m, x, y, z, x, y, z2, x, y2, z2, x, y2, z, u0, v0, u1, v1, -1, 0, 0, color, light);
        quad(b, p, m, x2, y, z2, x2, y, z, x2, y2, z, x2, y2, z2, u0, v0, u1, v1, 1, 0, 0, color, light);
        quad(b, p, m, x, y, z, x2, y, z, x2, y, z2, x, y, z2, u0, v0, u1, v1, 0, -1, 0, color, light);
        quad(b, p, m, x, y2, z2, x2, y2, z2, x2, y2, z, x, y2, z, u0, v0, u1, v1, 0, 1, 0, color, light);
    }

    private static void membrane(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x, float y, float z, float w, float length, float d, float u0, float v0, float u1, float v1, int color, int light) {
        float x2 = x + w, z2 = z + length + d;
        quad(b, p, m, x, y, z, x2, y, z, x2, y, z2, x, y, z2, u0, v0, u1, v1, 0, -1, 0, color, light);
        quad(b, p, m, x, y, z2, x2, y, z2, x2, y, z, x, y, z, u0, v1, u1, v0, 0, 1, 0, color, light);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u0, float v0, float u1, float v1, float nx, float ny, float nz, int color, int light) {
        vertex(b, p, m, x1, y1, z1, u0, v1, nx, ny, nz, color, light);
        vertex(b, p, m, x2, y2, z2, u1, v1, nx, ny, nz, color, light);
        vertex(b, p, m, x3, y3, z3, u1, v0, nx, ny, nz, color, light);
        vertex(b, p, m, x4, y4, z4, u0, v0, nx, ny, nz, color, light);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x, float y, float z, float u, float v, float nx, float ny, float nz, int color, int light) {
        Vector4f position = new Vector4f(x, y, z, 1.0F);
        //? if >=1.20 {
        m.transform(position);
        //?} else {
        /*position.transform(m);
        *///?}
        //? if >=1.21 {
        b.addVertex(p, position.x(), position.y(), position.z()).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        //?} else {
        /*b.vertex(p.pose(), position.x(), position.y(), position.z()).color(color).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
        *///?}
    }
}
