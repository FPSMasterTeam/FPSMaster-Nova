package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.optimization.Optimization;
import top.fpsmaster.module.impl.render.BlockOverlay;
import top.fpsmaster.render.FpsmasterBlockOverlayRenderTypes;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    private static final double FPSMASTER_BLOCK_OVERLAY_EXPAND = 0.01D;
    private final Set<SectionRenderDispatcher.RenderSection> fpsmaster$skippedChunkUpdates =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Inject(method = "compileSections", at = @At("HEAD"))
    private void fpsmaster$resetChunkUpdateLimit(CallbackInfo ci) {
        fpsmaster$skippedChunkUpdates.clear();
        Optimization.resetChunkUpdateCount();
    }

    @Redirect(
            method = "compileSections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;rebuildSectionAsync(Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"
            )
    )
    private void fpsmaster$limitAsyncChunkUpdate(SectionRenderDispatcher.RenderSection renderSection, RenderRegionCache regionCache) {
        if (Optimization.shouldScheduleChunkUpdate()) {
            renderSection.rebuildSectionAsync(regionCache);
        } else {
            fpsmaster$skippedChunkUpdates.add(renderSection);
        }
    }

    @Redirect(
            method = "compileSections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;setNotDirty()V",
                    ordinal = 1
            )
    )
    private void fpsmaster$keepLimitedChunkDirty(SectionRenderDispatcher.RenderSection renderSection) {
        if (!fpsmaster$skippedChunkUpdates.remove(renderSection)) {
            renderSection.setNotDirty();
        }
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDIF)V"
            ),
            index = 6
    )
    private int fpsmaster$blockOverlayColor(int color) {
        return BlockOverlay.outlineColor(color);
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDIF)V"
            ),
            index = 7
    )
    private float fpsmaster$blockOverlayWidth(float width) {
        return BlockOverlay.outlineWidth(width);
    }

    @ModifyArg(
            method = "renderBlockOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            ),
            index = 0
    )
    private RenderType fpsmaster$blockOverlayOutlineRenderType(RenderType renderType) {
        return BlockOverlay.shouldRenderThroughBlocks() ? FpsmasterBlockOverlayRenderTypes.linesNoDepth() : renderType;
    }

    @Inject(method = "renderBlockOutline", at = @At("TAIL"))
    private void fpsmaster$renderBlockOverlayFill(
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            boolean translucent,
            LevelRenderState levelRenderState,
            CallbackInfo ci
    ) {
        if (!BlockOverlay.shouldRenderFill()) {
            return;
        }

        BlockOutlineRenderState outlineState = levelRenderState.blockOutlineRenderState;
        if (outlineState == null || outlineState.isTranslucent() != translucent || outlineState.shape().isEmpty()) {
            return;
        }

        BlockPos pos = outlineState.pos();
        double x = pos.getX() - levelRenderState.cameraRenderState.pos.x;
        double y = pos.getY() - levelRenderState.cameraRenderState.pos.y;
        double z = pos.getZ() - levelRenderState.cameraRenderState.pos.z;
        RenderType renderType = BlockOverlay.shouldRenderThroughBlocks()
                ? FpsmasterBlockOverlayRenderTypes.fillNoDepth()
                : RenderTypes.debugFilledBox();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        int color = BlockOverlay.fillColor();
        PoseStack.Pose pose = poseStack.last();

        outlineState.shape().forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            renderFilledBox(
                    vertexConsumer,
                    pose,
                    (float) (x + minX - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (y + minY - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (z + minZ - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (x + maxX + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (y + maxY + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (z + maxZ + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    color
            );
        });
    }

    private static void renderFilledBox(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            int color
    ) {
        renderQuad(vertexConsumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, color);
        renderQuad(vertexConsumer, pose, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);
        renderQuad(vertexConsumer, pose, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, color);
        renderQuad(vertexConsumer, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        renderQuad(vertexConsumer, pose, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, color);
        renderQuad(vertexConsumer, pose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
    }

    private static void renderQuad(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            int color
    ) {
        vertexConsumer.addVertex(pose, x1, y1, z1).setColor(color);
        vertexConsumer.addVertex(pose, x2, y2, z2).setColor(color);
        vertexConsumer.addVertex(pose, x3, y3, z3).setColor(color);
        vertexConsumer.addVertex(pose, x4, y4, z4).setColor(color);
    }
}

//?}
