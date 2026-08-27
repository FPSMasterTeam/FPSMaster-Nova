package top.fpsmaster.cosmetic

//? if >=26 {
/*import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.resources.Identifier

class WingPreviewRenderState(
    val texture: Identifier,
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int,
    private val zoom: Float,
    private val scissor: ScreenRectangle
) : PictureInPictureRenderState {
    private val renderBounds = PictureInPictureRenderState.getBounds(left, top, right, bottom, scissor)
        ?: ScreenRectangle.empty()

    override fun x0(): Int = left
    override fun y0(): Int = top
    override fun x1(): Int = right
    override fun y1(): Int = bottom
    override fun scale(): Float = zoom
    override fun scissorArea(): ScreenRectangle = scissor
    override fun bounds(): ScreenRectangle = renderBounds
}

class WingPreviewRenderer : PictureInPictureRenderer<WingPreviewRenderState>() {
    override fun getRenderStateClass(): Class<WingPreviewRenderState> = WingPreviewRenderState::class.java

    override fun renderToTexture(state: WingPreviewRenderState, poseStack: PoseStack, nodeCollector: SubmitNodeCollector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        nodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entitySolid(state.texture)
        ) { pose, vertexConsumer -> DragonWingsRenderer.render(pose, vertexConsumer, 15728880) }
    }

    override fun getTranslateY(height: Int, guiScale: Int): Float = height * 0.58f

    override fun getTextureLabel(): String = "FPSMaster wing preview"
}
*///?}

//? if >=1.21.11 && <26 {
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier

class WingPreviewRenderState(
    val texture: Identifier,
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int,
    private val zoom: Float,
    private val scissor: ScreenRectangle
) : PictureInPictureRenderState {
    private val renderBounds = PictureInPictureRenderState.getBounds(left, top, right, bottom, scissor)
        ?: ScreenRectangle.empty()

    override fun x0(): Int = left
    override fun y0(): Int = top
    override fun x1(): Int = right
    override fun y1(): Int = bottom
    override fun scale(): Float = zoom
    override fun scissorArea(): ScreenRectangle = scissor
    override fun bounds(): ScreenRectangle = renderBounds
}

class WingPreviewRenderer(bufferSource: MultiBufferSource.BufferSource) :
    PictureInPictureRenderer<WingPreviewRenderState>(bufferSource) {
    override fun getRenderStateClass(): Class<WingPreviewRenderState> = WingPreviewRenderState::class.java

    override fun renderToTexture(state: WingPreviewRenderState, poseStack: PoseStack) {
        Minecraft.getInstance().gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        val consumer = bufferSource.getBuffer(RenderTypes.entityCutoutNoCull(state.texture))
        DragonWingsRenderer.render(poseStack.last(), consumer, 15728880)
    }

    override fun getTranslateY(height: Int, guiScale: Int): Float = height * 0.58f

    override fun getTextureLabel(): String = "FPSMaster wing preview"
}
//?}
