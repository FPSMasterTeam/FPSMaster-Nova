// Copyright (c) 2026 FPSMaster Team
// SPDX-License-Identifier: MIT
// This file is part of FPSMaster Nova. See LICENSE for details.

package top.fpsmaster.web;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * Textured quad element for the 1.21.5+ GUI render queue.
 *
 * <p>Carries a browser frame (or any textured rectangle) as four vertices with
 * UVs, color and pipeline state. This is the FPSMaster-owned replacement for the
 * previous LiquidBounce-derived bridge — same wire format required by
 * {@link net.minecraft.client.gui.render.state.GuiElementRenderState}, independent implementation.</p>
 *
 * <p>Coordinates are in screen space (float to match CEF's pixel geometry); UVs are
 * normalized. The quad is emitted counter-clockwise.</p>
 */
public record TexQuadGuiElementRenderState(
        float x0,
        float y0,
        float x1,
        float y1,
        float u1,
        float v1,
        float u2,
        float v2,
        int argb,
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    // Minecraft 1.21.11 changed VertexConsumer: buildVertices(VertexConsumer) without depth.
    // 1.21.5..1.21.10 requires buildVertices(VertexConsumer, float depth) and
    // addVertexWith2DPose(pose, x, y, depth). We gate only the method (not the whole file)
    // because Stonecutter whole-file swaps break on block-comment headers.
    //? if >=1.21.11 {
    @Override
    public void buildVertices(VertexConsumer sink) {
        sink.addVertexWith2DPose(pose, x0, y0).setUv(u1, v1).setColor(argb);
        sink.addVertexWith2DPose(pose, x0, y1).setUv(u1, v2).setColor(argb);
        sink.addVertexWith2DPose(pose, x1, y1).setUv(u2, v2).setColor(argb);
        sink.addVertexWith2DPose(pose, x1, y0).setUv(u2, v1).setColor(argb);
    }
    //?} else {
    /*@Override
    public void buildVertices(VertexConsumer sink, float depth) {
        sink.addVertexWith2DPose(pose, x0, y0, depth).setUv(u1, v1).setColor(argb);
        sink.addVertexWith2DPose(pose, x0, y1, depth).setUv(u1, v2).setColor(argb);
        sink.addVertexWith2DPose(pose, x1, y1, depth).setUv(u2, v2).setColor(argb);
        sink.addVertexWith2DPose(pose, x1, y0, depth).setUv(u2, v1).setColor(argb);
    }*///?}
}
