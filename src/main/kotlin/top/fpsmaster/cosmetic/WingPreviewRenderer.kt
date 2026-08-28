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
    // 原版 `ScreenArea.bounds()` 标了 @Nullable，null 的语义是「这块完全不可见」——
    // `GuiRenderState.findAppropriateNode` 靠它决定状态挂不挂进裁剪树，返 null 就在
    // submit 入口直接丢掉，离屏渲染压根不会跑。所以这里不能用 `ScreenRectangle.empty()`
    // 兜底：那会把「不可见」伪装成一块 0 尺寸的可见区，判空的地方就全失效了。
    private val renderBounds = PictureInPictureRenderState.getBounds(left, top, right, bottom, scissor)

    override fun x0(): Int = left
    override fun y0(): Int = top
    override fun x1(): Int = right
    override fun y1(): Int = bottom
    override fun scale(): Float = zoom
    override fun scissorArea(): ScreenRectangle = scissor
    override fun bounds(): ScreenRectangle? = renderBounds
}

class WingPreviewRenderer : PictureInPictureRenderer<WingPreviewRenderState>() {
    override fun getRenderStateClass(): Class<WingPreviewRenderState> = WingPreviewRenderState::class.java

    override fun renderToTexture(state: WingPreviewRenderState, poseStack: PoseStack, nodeCollector: SubmitNodeCollector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        nodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityCutout(state.texture)
        ) { pose, vertexConsumer -> DragonWingsRenderer.render(pose, vertexConsumer, 15728880) }
    }

    override fun getTranslateY(height: Int, guiScale: Int): Float = height * 0.58f

    override fun getTextureLabel(): String = "FPSMaster wing preview"
}
*///?}

// 1.21.5 起 GuiGraphics 只剩 Matrix3x2f，GUI 里没有 PoseStack 可用，3D 缩略图只能走 PIP。
// 1.21.5~1.21.10 与 1.21.11 的 PIP 形状完全一致（GuiRenderer 的 bufferSource /
// pictureInPictureRenderers 两个字段、PictureInPictureRenderer 的抽象方法都对得上），
// 差别只有渲染类型的门面类名，所以这一档共用一份实现。
//? if >=1.21.5 && <26 {
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.MultiBufferSource
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes as WingRenderTypes
//?} else {
/*import net.minecraft.client.renderer.RenderType as WingRenderTypes
*///?}

class WingPreviewRenderState(
    val texture: TextureId,
    private val left: Int,
    private val top: Int,
    private val right: Int,
    private val bottom: Int,
    private val zoom: Float,
    private val scissor: ScreenRectangle
) : PictureInPictureRenderState {
    // 原版 `ScreenArea.bounds()` 标了 @Nullable，null 的语义是「这块完全不可见」——
    // `GuiRenderState.findAppropriateNode` 靠它决定状态挂不挂进裁剪树，返 null 就在
    // submit 入口直接丢掉，离屏渲染压根不会跑。所以这里不能用 `ScreenRectangle.empty()`
    // 兜底：那会把「不可见」伪装成一块 0 尺寸的可见区，判空的地方就全失效了。
    private val renderBounds = PictureInPictureRenderState.getBounds(left, top, right, bottom, scissor)

    override fun x0(): Int = left
    override fun y0(): Int = top
    override fun x1(): Int = right
    override fun y1(): Int = bottom
    override fun scale(): Float = zoom
    override fun scissorArea(): ScreenRectangle = scissor
    override fun bounds(): ScreenRectangle? = renderBounds
}

class WingPreviewRenderer(bufferSource: MultiBufferSource.BufferSource) :
    PictureInPictureRenderer<WingPreviewRenderState>(bufferSource) {
    override fun getRenderStateClass(): Class<WingPreviewRenderState> = WingPreviewRenderState::class.java

    override fun renderToTexture(state: WingPreviewRenderState, poseStack: PoseStack) {
        Minecraft.getInstance().gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        val consumer = bufferSource.getBuffer(WingRenderTypes.entityCutoutNoCull(state.texture))
        DragonWingsRenderer.render(poseStack.last(), consumer, 15728880)
    }

    override fun getTranslateY(height: Int, guiScale: Int): Float = height * 0.58f

    override fun getTextureLabel(): String = "FPSMaster wing preview"
}
//?}
