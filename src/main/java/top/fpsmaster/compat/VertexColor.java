package top.fpsmaster.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Vertex colour method rename bridge for the pre/post-1.21 immediate renderer. */
public final class VertexColor {
    private VertexColor() {
    }

    public static VertexConsumer set(VertexConsumer vertex, float red, float green, float blue, float alpha) {
        //? if >=1.21 {
        return vertex.setColor(red, green, blue, alpha);
        //?} else {
        /*return vertex.color(red, green, blue, alpha);*/
        //?}
    }
}
