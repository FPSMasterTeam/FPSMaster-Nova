package top.fpsmaster.mixin.impl;

//? if >=26 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.render.BlockOverlay;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    private static final double FPSMASTER_BLOCK_OVERLAY_EXPAND = 0.01D;

    @ModifyArg(
            method = "submitHitOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitShapeOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/client/renderer/rendertype/RenderType;IFZ)V"
            ),
            index = 3
    )
    private int fpsmaster$blockOverlayColor(int color) {
        if (top.fpsmaster.diagnostics.Smoke.ENABLED && BlockOverlay.isActive()) {
            top.fpsmaster.diagnostics.Smoke.mixin("level-renderer");
            top.fpsmaster.diagnostics.Smoke.feature("block-overlay");
        }
        return BlockOverlay.outlineColor(color);
    }

    @ModifyArg(
            method = "submitHitOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitShapeOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/client/renderer/rendertype/RenderType;IFZ)V"
            ),
            index = 4
    )
    private float fpsmaster$blockOverlayWidth(float width) {
        return BlockOverlay.outlineWidth(width);
    }

    @Inject(method = "submitBlockOutline", at = @At("TAIL"))
    private void fpsmaster$renderBlockOverlayFill(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            LevelRenderState levelRenderState,
            CallbackInfo ci
    ) {
        if (!BlockOverlay.shouldRenderFill()) {
            return;
        }
        BlockOutlineRenderState outlineState = levelRenderState.blockOutlineRenderState;
        if (outlineState == null || outlineState.shape().isEmpty()) {
            return;
        }
        BlockPos pos = outlineState.pos();
        double x = pos.getX() - levelRenderState.cameraRenderState.pos.x;
        double y = pos.getY() - levelRenderState.cameraRenderState.pos.y;
        double z = pos.getZ() - levelRenderState.cameraRenderState.pos.z;
        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, vertexConsumer) -> {
                int color = BlockOverlay.fillColor();
                outlineState.shape().forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                    renderFilledBox(
                            vertexConsumer,
                            pose,
                            (float) (minX - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                            (float) (minY - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                            (float) (minZ - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                            (float) (maxX + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                            (float) (maxY + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                            (float) (maxZ + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                            color
                    );
                });
            });
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderFilledBox(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
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
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            int color
    ) {
        vertexConsumer.addVertex(pose, x1, y1, z1).setColor(color);
        vertexConsumer.addVertex(pose, x2, y2, z2).setColor(color);
        vertexConsumer.addVertex(pose, x3, y3, z3).setColor(color);
        vertexConsumer.addVertex(pose, x4, y4, z4).setColor(color);
    }
}
*///?} elif >=1.21.11 {

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

//?} else if >=1.21.5 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FpsmasterBlockOverlay;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.optimization.Optimization;
import top.fpsmaster.module.impl.render.BlockOverlay;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    private static final double FPSMASTER_BLOCK_OVERLAY_EXPAND = 0.01D;

    @Shadow
    private Minecraft minecraft;

    @Shadow
    private ClientLevel level;

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

    // BlockOverlay outline color: on 1.21.5..1.21.10 renderHitOutline forwards a packed ARGB int to
    // ShapeRenderer.renderShape (arg index 6); unlike 1.21.11 there is no line-width argument.
    @ModifyArg(
            method = "renderHitOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDI)V"
            ),
            index = 6
    )
    private int fpsmaster$blockOverlayColor(int color) {
        return BlockOverlay.outlineColor(color);
    }

    // BlockOverlay outline width / through-blocks: the width still lives in the render type's
    // LineStateShard on this era, so swap the whole type that renderBlockOutline draws the hit
    // outline with.
    @Redirect(
            method = "renderBlockOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;lines()Lnet/minecraft/client/renderer/RenderType;"
            )
    )
    private RenderType fpsmaster$blockOverlayLinesType() {
        if (!BlockOverlay.isActive() || !BlockOverlay.outline.getValue()) {
            return RenderType.lines();
        }
        double width = BlockOverlay.outlineWidth(1.0F);
        return BlockOverlay.shouldRenderThroughBlocks()
                ? FpsmasterBlockOverlay.linesNoDepth(width)
                : FpsmasterBlockOverlay.lines(width);
    }

    // BlockOverlay fill. renderBlockOutline runs once per pass (opaque and translucent), and its TAIL
    // is also reached when vanilla skipped the outline, so the vanilla guards are repeated here.
    @Inject(method = "renderBlockOutline", at = @At("TAIL"))
    private void fpsmaster$renderBlockOverlayFill(
            Camera camera,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            boolean translucent,
            CallbackInfo ci
    ) {
        if (!BlockOverlay.shouldRenderFill()) {
            return;
        }

        HitResult hitResult = this.minecraft.hitResult;
        if (!(hitResult instanceof BlockHitResult) || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();
        BlockState state = this.level.getBlockState(pos);
        if (state.isAir() || !this.level.getWorldBorder().isWithinBounds(pos)) {
            return;
        }
        if (ItemBlockRenderTypes.getChunkRenderType(state).sortOnUpload() != translucent) {
            return;
        }

        Entity entity = camera.getEntity();
        VoxelShape shape = state.getShape(this.level, pos, CollisionContext.of(entity));
        if (shape.isEmpty()) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        double x = pos.getX() - cameraPos.x;
        double y = pos.getY() - cameraPos.y;
        double z = pos.getZ() - cameraPos.z;
        RenderType renderType = BlockOverlay.shouldRenderThroughBlocks()
                ? FpsmasterBlockOverlay.fillNoDepth()
                : FpsmasterBlockOverlay.fill();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        int color = BlockOverlay.fillColor();
        PoseStack.Pose pose = poseStack.last();

        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
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

*///?} else if >=1.20.5 {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FpsmasterBlockOverlay;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.optimization.Optimization;
import top.fpsmaster.module.impl.render.BlockOverlay;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    private static final double FPSMASTER_BLOCK_OVERLAY_EXPAND = 0.01D;

    @Shadow
    private ClientLevel level;

    @Shadow
    private RenderBuffers renderBuffers;

    private final Set<SectionRenderDispatcher.RenderSection> fpsmaster$skippedChunkUpdates =
            Collections.newSetFromMap(new IdentityHashMap<>());

    // Chunk-update limiting: 1.20.5 renamed ChunkRenderDispatcher to SectionRenderDispatcher (and
    // RenderChunk/compileChunks/rebuildChunkAsync to RenderSection/compileSections/rebuildSectionAsync),
    // but rebuildSectionAsync still takes the dispatcher plus the region cache, unlike 1.21.5+.
    @Inject(method = "compileSections", at = @At("HEAD"))
    private void fpsmaster$resetChunkUpdateLimit(CallbackInfo ci) {
        fpsmaster$skippedChunkUpdates.clear();
        Optimization.resetChunkUpdateCount();
    }

    @Redirect(
            method = "compileSections",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;rebuildSectionAsync(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"
            )
    )
    private void fpsmaster$limitAsyncChunkUpdate(SectionRenderDispatcher.RenderSection renderSection, SectionRenderDispatcher dispatcher, RenderRegionCache regionCache) {
        if (Optimization.shouldScheduleChunkUpdate()) {
            renderSection.rebuildSectionAsync(dispatcher, regionCache);
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

    // BlockOverlay outline width / through-blocks: 1.21.1 still draws the outline using
    // RenderType.lines() (ordinal 0 within renderLevel is the block-hit outline buffer). Swap it for a
    // custom-width and optionally no-depth line render type.
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;lines()Lnet/minecraft/client/renderer/RenderType;",
                    ordinal = 0
            )
    )
    private RenderType fpsmaster$blockOverlayLinesType() {
        if (!BlockOverlay.isActive() || !BlockOverlay.outline.getValue()) {
            return RenderType.lines();
        }
        double width = BlockOverlay.outlineWidth(1.0F);
        return BlockOverlay.shouldRenderThroughBlocks()
                ? FpsmasterBlockOverlay.linesNoDepth(width)
                : FpsmasterBlockOverlay.lines(width);
    }

    // BlockOverlay outline color: renderHitOutline calls the private static renderShape(..) with the
    // hard-coded black 0.4-alpha edge color (red, green, blue, alpha at arg indices 6, 7, 8, 9).
    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 6
    )
    private float fpsmaster$outlineRed(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.redF());
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 7
    )
    private float fpsmaster$outlineGreen(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.greenF());
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 8
    )
    private float fpsmaster$outlineBlue(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.blueF());
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 9
    )
    private float fpsmaster$outlineAlpha(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.alphaF());
    }

    private static float fpsmaster$outlineChannel(float original, float override) {
        if (BlockOverlay.isActive() && BlockOverlay.outline.getValue()) {
            return override;
        }
        return original;
    }

    // BlockOverlay fill: 1.21.1 has no renderBlockOutline; add the translucent box fill straight after
    // the outline is drawn in renderHitOutline (shares this.renderBuffers.bufferSource(), flushed by
    // the catch-all bufferSource.endBatch() later in renderLevel).
    @Inject(method = "renderHitOutline", at = @At("TAIL"))
    private void fpsmaster$renderBlockOverlayFill(
            PoseStack poseStack,
            VertexConsumer consumer,
            Entity entity,
            double camX,
            double camY,
            double camZ,
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        if (!BlockOverlay.shouldRenderFill()) {
            return;
        }

        VoxelShape shape = state.getShape(this.level, pos, CollisionContext.of(entity));
        if (shape.isEmpty()) {
            return;
        }

        double x = pos.getX() - camX;
        double y = pos.getY() - camY;
        double z = pos.getZ() - camZ;
        RenderType renderType = BlockOverlay.shouldRenderThroughBlocks()
                ? FpsmasterBlockOverlay.fillNoDepth()
                : FpsmasterBlockOverlay.fill();
        VertexConsumer vertexConsumer = this.renderBuffers.bufferSource().getBuffer(renderType);
        int color = BlockOverlay.fillColor();
        PoseStack.Pose pose = poseStack.last();

        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
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

*///?} else {

/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FpsmasterBlockOverlay;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.optimization.Optimization;
import top.fpsmaster.module.impl.render.BlockOverlay;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    private static final double FPSMASTER_BLOCK_OVERLAY_EXPAND = 0.01D;

    @Shadow
    private ClientLevel level;

    @Shadow
    private RenderBuffers renderBuffers;

    private final Set<ChunkRenderDispatcher.RenderChunk> fpsmaster$skippedChunkUpdates =
            Collections.newSetFromMap(new IdentityHashMap<>());

    // Chunk-update limiting: 1.20.1 uses LevelRenderer.compileChunks(Camera) which schedules async
    // section rebuilds via RenderChunk.rebuildChunkAsync(..) + setNotDirty(). Reset the per-frame
    // counter at HEAD, throttle the async rebuilds, and keep throttled sections dirty so they get
    // picked up on a later frame.
    @Inject(method = "compileChunks", at = @At("HEAD"))
    private void fpsmaster$resetChunkUpdateLimit(CallbackInfo ci) {
        fpsmaster$skippedChunkUpdates.clear();
        Optimization.resetChunkUpdateCount();
    }

    @Redirect(
            method = "compileChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk;rebuildChunkAsync(Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"
            )
    )
    private void fpsmaster$limitAsyncChunkUpdate(ChunkRenderDispatcher.RenderChunk renderChunk, ChunkRenderDispatcher dispatcher, RenderRegionCache regionCache) {
        if (Optimization.shouldScheduleChunkUpdate()) {
            renderChunk.rebuildChunkAsync(dispatcher, regionCache);
        } else {
            fpsmaster$skippedChunkUpdates.add(renderChunk);
        }
    }

    @Redirect(
            method = "compileChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk;setNotDirty()V",
                    ordinal = 1
            )
    )
    private void fpsmaster$keepLimitedChunkDirty(ChunkRenderDispatcher.RenderChunk renderChunk) {
        if (!fpsmaster$skippedChunkUpdates.remove(renderChunk)) {
            renderChunk.setNotDirty();
        }
    }

    // BlockOverlay outline width / through-blocks: 1.20.1 draws the outline using RenderType.lines()
    // (ordinal 0 within renderLevel is the block-hit outline buffer). Swap it for a custom-width and
    // optionally no-depth line render type.
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;lines()Lnet/minecraft/client/renderer/RenderType;",
                    ordinal = 0
            )
    )
    private RenderType fpsmaster$blockOverlayLinesType() {
        if (!BlockOverlay.isActive() || !BlockOverlay.outline.getValue()) {
            return RenderType.lines();
        }
        double width = BlockOverlay.outlineWidth(1.0F);
        return BlockOverlay.shouldRenderThroughBlocks()
                ? FpsmasterBlockOverlay.linesNoDepth(width)
                : FpsmasterBlockOverlay.lines(width);
    }

    // BlockOverlay outline color: renderHitOutline calls the private static renderShape(..) with the
    // hard-coded black 0.4-alpha edge color (red, green, blue, alpha at arg indices 6, 7, 8, 9).
    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 6
    )
    private float fpsmaster$outlineRed(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.redF());
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 7
    )
    private float fpsmaster$outlineGreen(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.greenF());
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 8
    )
    private float fpsmaster$outlineBlue(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.blueF());
    }

    @ModifyArg(
            method = "renderHitOutline",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"),
            index = 9
    )
    private float fpsmaster$outlineAlpha(float original) {
        return fpsmaster$outlineChannel(original, BlockOverlay.outlineTint.alphaF());
    }

    private static float fpsmaster$outlineChannel(float original, float override) {
        if (BlockOverlay.isActive() && BlockOverlay.outline.getValue()) {
            return override;
        }
        return original;
    }

    // BlockOverlay fill: 1.20.1 has no renderBlockOutline; add the translucent box fill straight after
    // the outline is drawn in renderHitOutline (shares this.renderBuffers.bufferSource(), flushed by
    // the catch-all bufferSource.endBatch() later in renderLevel).
    @Inject(method = "renderHitOutline", at = @At("TAIL"))
    private void fpsmaster$renderBlockOverlayFill(
            PoseStack poseStack,
            VertexConsumer consumer,
            Entity entity,
            double camX,
            double camY,
            double camZ,
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        if (!BlockOverlay.shouldRenderFill()) {
            return;
        }

        VoxelShape shape = state.getShape(this.level, pos, CollisionContext.of(entity));
        if (shape.isEmpty()) {
            return;
        }

        double x = pos.getX() - camX;
        double y = pos.getY() - camY;
        double z = pos.getZ() - camZ;
        RenderType renderType = BlockOverlay.shouldRenderThroughBlocks()
                ? FpsmasterBlockOverlay.fillNoDepth()
                : FpsmasterBlockOverlay.fill();
        VertexConsumer vertexConsumer = this.renderBuffers.bufferSource().getBuffer(renderType);
        int color = BlockOverlay.fillColor();
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        PoseStack.Pose pose = poseStack.last();

        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            renderFilledBox(
                    vertexConsumer,
                    pose,
                    (float) (x + minX - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (y + minY - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (z + minZ - FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (x + maxX + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (y + maxY + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    (float) (z + maxZ + FPSMASTER_BLOCK_OVERLAY_EXPAND),
                    r, g, b, a
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
            int r,
            int g,
            int b,
            int a
    ) {
        renderQuad(vertexConsumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
        renderQuad(vertexConsumer, pose, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderQuad(vertexConsumer, pose, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, r, g, b, a);
        renderQuad(vertexConsumer, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderQuad(vertexConsumer, pose, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderQuad(vertexConsumer, pose, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
    }

    private static void renderQuad(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            int r, int g, int b, int a
    ) {
        vertexConsumer.vertex(pose.pose(), x1, y1, z1).color(r, g, b, a).endVertex();
        vertexConsumer.vertex(pose.pose(), x2, y2, z2).color(r, g, b, a).endVertex();
        vertexConsumer.vertex(pose.pose(), x3, y3, z3).color(r, g, b, a).endVertex();
        vertexConsumer.vertex(pose.pose(), x4, y4, z4).color(r, g, b, a).endVertex();
    }
}

*///?}
