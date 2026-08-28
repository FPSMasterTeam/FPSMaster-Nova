package top.fpsmaster.ui

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
//? if <1.20 {
/*import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Vector3f as LegacyVector3f
*///?}
//? if <1.21.5 {
/*import com.mojang.blaze3d.platform.Lighting
import net.minecraft.client.renderer.RenderType
import top.fpsmaster.cosmetic.DragonWingsRenderer
*///?}
import net.minecraft.client.gui.screens.Screen
//? if >=1.21.11 {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
//?}
//? if >=1.21.1 {
import net.minecraft.client.gui.components.PlayerSkinWidget
import net.minecraft.client.resources.DefaultPlayerSkin
//?}
//? if <26 {
import net.minecraft.client.gui.screens.inventory.InventoryScreen
//?}
import net.minecraft.network.chat.Component
//? if >=1.20 {
import org.joml.Quaternionf
import org.joml.Vector3f
//?}
import top.fpsmaster.config.ConfigManager
import top.fpsmaster.cosmetic.CosmeticLoadoutClient
import top.fpsmaster.cosmetic.CosmeticManager
import top.fpsmaster.cosmetic.TextureId
//? if >=1.21.5 {
import top.fpsmaster.cosmetic.WingPreviewRenderState
//?}
//? if >=1.21.5 && <26 {
import top.fpsmaster.mixin.interfaces.IGuiGraphics
//?}
import top.fpsmaster.auth.AuthService
import top.fpsmaster.auth.FPSMasterApiClient
import top.fpsmaster.mc
import top.fpsmaster.prism.screen.CosmeticsBridge
import top.fpsmaster.prism.screen.SharedCosmetics
import top.fpsmaster.prism.widget.UiFrame
import top.fpsmaster.setScreenCompat
import top.fpsmaster.translation.Language
import top.fpsmaster.ui.kit.NovaCanvas
import top.fpsmaster.ui.kit.ToolkitScreen
import kotlin.math.roundToInt

class NativeCosmeticsScreen(private val parent: Screen?) : ToolkitScreen(Component.literal("Cosmetics")) {
    private val gui = SharedCosmetics()
    private val bridge = NovaCosmeticsBridge()
    private var preview = FloatArray(5)
    private val itemPreviews = ArrayList<ItemPreview>()
    private val builtinWingTexture: TextureId =
        //? if >=1.21.11 {
        net.minecraft.resources.Identifier.withDefaultNamespace("client/wings/wings.png")
        //?} else if >=1.21.1 {
        /*net.minecraft.resources.ResourceLocation.withDefaultNamespace("client/wings/wings.png")
        *///?} else {
        /*net.minecraft.resources.ResourceLocation("client/wings/wings.png")
        *///?}
    //? if >=1.21.1 {
    private var defaultSkinPreview: PlayerSkinWidget? = null
    //?}

    init {
        CosmeticManager.reloadCustom()
    }

    override fun renderUi(ui: UiFrame) {
        itemPreviews.clear()
        if (gui.draw(ui, bridge)) closeToParent()
    }

    //? if >=26 {
    /*override fun extractRenderState(g: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(g, mouseX, mouseY, partialTick)
        renderItemPreviews(GuiGraphics(g))
        if (preview[2] <= 0f) return
        val player = mc.player
        if (player == null) {
            val widget = defaultSkinPreview ?: PlayerSkinWidget(
                preview[2].toInt(), preview[3].toInt(), mc.entityModels
            ) { DefaultPlayerSkin.get(mc.gameProfile) }.also { defaultSkinPreview = it }
            widget.setRectangle(preview[0].toInt(), preview[1].toInt(), preview[2].toInt(), preview[3].toInt())
            widget.extractRenderState(g, mouseX, mouseY, partialTick)
            return
        }
        CosmeticManager.setPreviewing(true)
        val state = mc.entityRenderDispatcher.extractEntity(player, 1f)
        if (state is LivingEntityRenderState) {
            state.bodyRot = 180f + preview[4]
            state.yRot = 0f
            state.xRot = 0f
            state.boundingBoxWidth /= state.scale
            state.boundingBoxHeight /= state.scale
            state.scale = 1f
        }
        state.lightCoords = 15728880
        state.shadowPieces.clear()
        state.outlineColor = 0
        val x = preview[0].toInt()
        val y = preview[1].toInt()
        val w = preview[2].toInt()
        val h = preview[3].toInt()
        g.entity(
            state, (h * 0.42f).toInt().toFloat(), Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f),
            Quaternionf().rotateZ(Math.PI.toFloat()), Quaternionf(), x, y, x + w, y + h
        )
    }
    *///?}
    //? if >=1.20 && <26 {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderItemPreviews(guiGraphics)
        if (preview[2] <= 0f) return
        val player = mc.player
        //? if >=1.21.1 {
        if (player == null) {
            val widget = defaultSkinPreview ?: PlayerSkinWidget(
                preview[2].toInt(), preview[3].toInt(), mc.entityModels
            ) { DefaultPlayerSkin.get(mc.gameProfile) }.also { defaultSkinPreview = it }
            widget.setRectangle(preview[0].toInt(), preview[1].toInt(), preview[2].toInt(), preview[3].toInt())
            widget.render(guiGraphics, mouseX, mouseY, partialTick)
            return
        }
        //?} else {
        /*if (player == null) return
        *///?}
        CosmeticManager.setPreviewing(true)
        val x = preview[0].toInt()
        val y = preview[1].toInt()
        val w = preview[2].toInt()
        val h = preview[3].toInt()
        //? if >=1.21.11 {
        val state = mc.entityRenderDispatcher.extractEntity(player, 1f)
        if (state is LivingEntityRenderState) {
            state.bodyRot = 180f + preview[4]
            state.yRot = 0f
            state.xRot = 0f
            state.boundingBoxWidth /= state.scale
            state.boundingBoxHeight /= state.scale
            state.scale = 1f
        }
        state.lightCoords = 15728880
        state.shadowPieces.clear()
        state.outlineColor = 0
        guiGraphics.submitEntityRenderState(
            state, (h * 0.42f).toInt().toFloat(), Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f),
            Quaternionf().rotateZ(Math.PI.toFloat()), Quaternionf(), x, y, x + w, y + h
        )
        //?} else if >=1.20 {
        /*val bodyRot = player.yBodyRot
        val yRot = player.yRot
        val xRot = player.xRot
        val headRot = player.yHeadRot
        val oldHeadRot = player.yHeadRotO
        player.yBodyRot = 180f + preview[4]
        player.yRot = 180f + preview[4]
        player.xRot = 0f
        player.yHeadRot = player.yRot
        player.yHeadRotO = player.yRot
        try {
            val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
            val camera = Quaternionf()
            //? if >=1.21.8 {
            InventoryScreen.renderEntityInInventory(
                guiGraphics, x, y, x + w, y + h, (h * 0.42f).toInt().toFloat(),
                Vector3f(0f, player.bbHeight / 2f + 0.0625f, 0f), rotation, camera, player
            )
            //?} else if >=1.21.1 {
            InventoryScreen.renderEntityInInventory(
                guiGraphics, (x + w / 2).toFloat(), (y + h / 2).toFloat(), (h * 0.42f).toInt().toFloat(),
                Vector3f(0f, player.bbHeight / 2f + 0.0625f, 0f), rotation, camera, player
            )
            //?} else {
            InventoryScreen.renderEntityInInventory(guiGraphics, x + w / 2, y + h - 24,
                (h * 0.31f).toInt(), rotation, camera, player)
            //?}
        } finally {
            player.yBodyRot = bodyRot
            player.yRot = yRot
            player.xRot = xRot
            player.yHeadRot = headRot
            player.yHeadRotO = oldHeadRot
        }
        *///?}
    }
    //?}

    //? if <1.20 {
    /*// 1.19.2 没有 GuiGraphics，签名对不上上面那档，必须单独覆写一次。
    // 漏了这档的后果不是编译错，是界面「半哑」：ToolkitScreen.render 照常把 UI 画出来，
    // 但 renderItemPreviews 和玩家模型预览一个都不跑——商品卡是灰块、右侧舞台是空的。
    override fun render(poseStack: com.mojang.blaze3d.vertex.PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(poseStack, mouseX, mouseY, partialTick)
        val guiGraphics = GuiGraphics(poseStack)
        renderItemPreviews(guiGraphics)
        if (preview[2] <= 0f) return
        val player = mc.player ?: return
        CosmeticManager.setPreviewing(true)
        val x = preview[0].toInt()
        val y = preview[1].toInt()
        val w = preview[2].toInt()
        val h = preview[3].toInt()
        val centerX = x + w / 2
        val feetY = y + h - 24
        // 1.19.2 的 renderEntityInInventory 自己会覆写 yBodyRot/yRot（由 mouseX 推出来，
        // atan 夹在 ±90°，转不满一圈），所以拿不到任意朝向；只能在 modelView 上绕 Y 轴转。
        //
        // 支点的 z 和角度符号都不能随便写，这两处各踩过一次空舞台：
        // renderEntityInInventory 在我们这层矩阵之后乘的是
        //     U = T(x, y, 1050) · S(1,1,-1) · T(0, 0, 1000) · S(scale)
        // （字节码核过：translate(DDD) 1050 → scale(1,1,-1) → 内层 PoseStack translate
        //  1000 → scale），实体原点最终落在 z = 1050 - 1000 = +50，GUI 基准 modelView 是
        // translate(0,0,-2000)、ortho near=1000 far=3000，所以可见区间是 z∈[-1000,-3000]，
        // 面板本身在 -2000。
        //   支点写 0    → 合成后 z = -50 → 视图 -2050，落到面板背后被深度判掉；
        //   支点写 1050 → 合成后 z = +2050 → 视图 +50，直接被近裁剪面切掉。
        // 要既转模型又不动深度，只能用 A = U·R·U⁻¹：绕过 (x, y, 50) 的竖轴转，且因为 U 的
        // 线性部分含 S(1,1,-1)，共轭把角度取反（S·R_y(θ)·S⁻¹ = R_y(-θ)）。取反后拖拽方向
        // 也正好和 1.20+ 的 `yBodyRot = 180 + yaw` 一致——MC 的 yaw 增大是俯视顺时针，
        // 对应模型空间的 R_y(-yaw)。
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushPose()
        modelView.translate(centerX.toDouble(), feetY.toDouble(), PREVIEW_DEPTH)
        modelView.mulPose(LegacyVector3f.YP.rotationDegrees(-preview[4]))
        modelView.translate(-centerX.toDouble(), -feetY.toDouble(), -PREVIEW_DEPTH)
        RenderSystem.applyModelViewMatrix()
        try {
            InventoryScreen.renderEntityInInventory(centerX, feetY, (h * 0.31f).toInt(), 0f, 0f, player)
        } finally {
            modelView.popPose()
            RenderSystem.applyModelViewMatrix()
        }
    }
    *///?}

    private fun renderItemPreviews(guiGraphics: GuiGraphics) {
        itemPreviews.forEach { itemPreview ->
            val texture = if (itemPreview.item.builtin()) builtinWingTexture
            else CosmeticManager.textureFor(itemPreview.item.id()) ?: return@forEach
            when (itemPreview.item.category()) {
                // 翅膀这条在 1.21.5+ 走 PIP，裁剪是随 render state 一起提交的（见 previewScissor），
                // 不能再套一层 GUI scissor：PIP 的几何画进的是自己的离屏纹理，不吃这里的裁剪。
                "wings" -> renderWingThumbnail(guiGraphics, texture, itemPreview)
                "cape" -> withPreviewClip(guiGraphics, itemPreview) {
                    renderCapeThumbnail(guiGraphics, texture, itemPreview)
                }
            }
        }
    }

    /**
     * 按 paint 阶段记下的裁剪矩形补一次 scissor，再执行 [body]。
     *
     * 商品卡的缩略图不是在 `SharedCosmetics.draw` 里画的，而是记下坐标、等到 `render` /
     * `extractRenderState` 末尾再补画——那时列表容器的 clip 栈早就退干净了。结果是滚出可视区的
     * 卡片照样把模型画出来，糊在列表外面（实测 1.19.2：向上滚一行，上一行的翅膀盖在顶部标签栏上）。
     *
     * 这里取「容器裁剪 ∩ 卡片矩形」，完全不可见就直接不画。
     */
    private fun withPreviewClip(guiGraphics: GuiGraphics, itemPreview: ItemPreview, body: () -> Unit) {
        val clip = itemPreview.clip
        if (clip == null) {
            body()
            return
        }
        val x0 = kotlin.math.max(clip[0], itemPreview.x)
        val y0 = kotlin.math.max(clip[1], itemPreview.y)
        val x1 = kotlin.math.min(clip[0] + clip[2], itemPreview.x + itemPreview.w)
        val y1 = kotlin.math.min(clip[1] + clip[3], itemPreview.y + itemPreview.h)
        if (x1 <= x0 || y1 <= y0) {
            return
        }
        // 取整必须和 NovaCanvas.pushClip 用同一个函数：roundToInt 是半值向 +∞，
        // kotlin.math.round 是 rint（半值向偶数），坐标落在 .5 上时两者差 1px。
        val left = x0.roundToInt()
        val top = y0.roundToInt()
        val right = x1.roundToInt()
        val bottom = y1.roundToInt()
        //? if <1.20 {
        /*net.minecraft.client.gui.GuiComponent.enableScissor(left, top, right, bottom)
        try {
            body()
        } finally {
            net.minecraft.client.gui.GuiComponent.disableScissor()
        }
        *///?} else {
        guiGraphics.enableScissor(left, top, right, bottom)
        try {
            body()
        } finally {
            guiGraphics.disableScissor()
        }
        //?}
    }

    //? if >=1.21.5 {
    /**
     * PIP 的裁剪区：容器裁剪优先，没有就退回卡片自身矩形。
     *
     * `PictureInPictureRenderState.getBounds` 会拿它和 x0/y0/x1/y1 求交，
     * `PictureInPictureRenderer.blitTexture` 又用它给离屏纹理的 blit 设 scissor
     * （最终交给 `GuiRenderState.submitBlitToCurrentLayer`），所以把容器裁剪塞进来就够了，
     * 不需要额外的 GUI scissor。
     */
    private fun previewScissor(itemPreview: ItemPreview, x: Int, y: Int, width: Int, height: Int) =
        itemPreview.clip?.let {
            // 取整口径必须和 NovaCanvas.pushClip 一致：那边取整的是两条边（x、x+w）、
            // 用的是 roundToInt。这里如果各自取整宽高，round(x)+round(w) != round(x+w)，
            // 右/下边会差 1px；换成 kotlin.math.round 也会差 1px（rint 半值向偶数）。
            val x0 = it[0].roundToInt()
            val y0 = it[1].roundToInt()
            val x1 = (it[0] + it[2]).roundToInt()
            val y1 = (it[1] + it[3]).roundToInt()
            net.minecraft.client.gui.navigation.ScreenRectangle(
                x0, y0, (x1 - x0).coerceAtLeast(0), (y1 - y0).coerceAtLeast(0)
            )
        } ?: net.minecraft.client.gui.navigation.ScreenRectangle(x, y, width, height)
    //?}

    private fun renderCapeThumbnail(guiGraphics: GuiGraphics, texture: TextureId, itemPreview: ItemPreview) {
        val height = (itemPreview.h - 4f).toInt().coerceAtLeast(1)
        val width = (height * 10f / 16f).toInt().coerceAtLeast(1)
        val x = (itemPreview.x + (itemPreview.w - width) / 2f).toInt()
        val y = (itemPreview.y + (itemPreview.h - height) / 2f).toInt()
        blitRegion(guiGraphics, texture, x, y, width, height, 1f, 1f, 10, 16, 64, 32, false)
    }

    private fun renderWingThumbnail(guiGraphics: GuiGraphics, texture: TextureId, itemPreview: ItemPreview) {
        //? if >=26 {
        /*val x = itemPreview.x.toInt()
        val y = itemPreview.y.toInt()
        val width = itemPreview.w.toInt().coerceAtLeast(1)
        val height = itemPreview.h.toInt().coerceAtLeast(1)
        // 卡片整张滚出容器时不用在这里判：WingPreviewRenderState.bounds() 返 null，
        // GuiRenderState.addPicturesInPictureState 第一条指令就是 findAppropriateNode，
        // 拿到 null 直接 return，离屏 3D 渲染和纹理分配一次都不会发生。自己再判一遍纯属白算。
        val scissor = previewScissor(itemPreview, x, y, width, height)
        guiGraphics.addPicturesInPicture(
            WingPreviewRenderState(
                texture, x, y, x + width, y + height,
                kotlin.math.min(width / 3f, height * 0.75f),
                scissor
            )
        )*/
        //?} else if >=1.21.5 {
        val x = itemPreview.x.toInt()
        val y = itemPreview.y.toInt()
        val width = itemPreview.w.toInt().coerceAtLeast(1)
        val height = itemPreview.h.toInt().coerceAtLeast(1)
        // 卡片整张滚出容器时不用在这里判：WingPreviewRenderState.bounds() 返 null，
        // GuiRenderState.submitPicturesInPictureState 第一条指令就是 findAppropriateNode，
        // 拿到 null 直接 return，离屏 3D 渲染和纹理分配一次都不会发生。自己再判一遍纯属白算。
        val scissor = previewScissor(itemPreview, x, y, width, height)
        val access = guiGraphics as IGuiGraphics
        access.fpsmasterGuiRenderState().submitPicturesInPictureState(
            WingPreviewRenderState(
                texture, x, y, x + width, y + height,
                kotlin.math.min(width / 3f, height * 0.75f),
                scissor
            )
        )
        //?} else {
        /*withPreviewClip(guiGraphics, itemPreview) {
            renderWingThumbnail3d(guiGraphics, texture, itemPreview)
        }
        *///?}
    }

    //? if <1.21.5 {
    /*/**
     * 商品卡里的翅膀缩略图：直接在 GUI 里做一次 3D 渲染，而不是拿翼膜贴图拼平面图标。
     *
     * 这里原先是 renderWingThumbnail2d——把 (0,8)-(10,18) 和 (0,18)-(10,28) 两块 10x10 区域
     * 斜 ±12 度贴上去。那两块是翼膜盒子的**上表面** UV：u 是展向、v 是弦向，而且 u=10 那侧才是
     * 靠近身体的翼根（Edge 的 `setTextureOffset("wing.skin", -10, 8)` + `addBox(..., 10, 0, 10)`
     * 决定的）。原代码两块都没镜像，等于把翼根摆在外侧；又把翼尖那块放在翼根块的右边，于是整只
     * 翅膀里外颠倒——这就是「歪歪斜斜、好像颠倒了」。而且方向修对了也没用：翼膜的俯视图本来就
     * 拼不出后视角的翅膀。
     *
     * Edge 的对应实现（CosmeticsScreen.renderItemPreviews -> DragonWingsRenderer.renderPreview）
     * 走的是真 3D，1.21.11+ 的 PIP 分支也是同一套几何。这里照抄 Edge 的变换，四代口径一致。
     */
    private fun renderWingThumbnail3d(guiGraphics: GuiGraphics, texture: TextureId, itemPreview: ItemPreview) {
        val size = kotlin.math.max(12f, itemPreview.h * 0.4f)
        val centerX = itemPreview.x + itemPreview.w * 0.5f
        val anchorY = itemPreview.y + itemPreview.h - 1f
        val pose = guiGraphics.pose()
        pose.pushPose()
        // Edge: translate(x, y, 50) -> scale(-size, size, size) -> rotateY(180) -> scale(ws)
        //       -> translate(0, -1.45/ws, 0.2/ws)
        // scale(-1,1,1) 后接 rotateY(180) 等于 scale(1,1,-1)，所以并成一次 scale(size, size, -size)，
        // 顺带省掉 1.19.2(Vector3f) 与 1.20+(Axis) 的四元数 API 分歧。
        //
        // z 取 50 和玩家模型预览是同一个深度口径：GUI 的 modelView 是 translate(0,0,-2000)、
        // ortho near=1000 far=3000，本地 z 越大越靠近相机，50 刚好压在面板前面一点。
        pose.translate(centerX.toDouble(), anchorY.toDouble(), GUI_MODEL_DEPTH)
        pose.scale(size, size, -size)
        pose.scale(WING_THUMB_SCALE, WING_THUMB_SCALE, WING_THUMB_SCALE)
        pose.translate(0.0, (-1.45f / WING_THUMB_SCALE).toDouble(), (0.2f / WING_THUMB_SCALE).toDouble())
        val bufferSource = mc.renderBuffers().bufferSource()
        Lighting.setupForEntityInInventory()
        DragonWingsRenderer.render(
            pose.last(),
            bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture, false)),
            FULL_BRIGHT
        )
        bufferSource.endBatch()
        Lighting.setupFor3DItems()
        pose.popPose()
    }
    *///?}

    private fun blitRegion(
        guiGraphics: GuiGraphics,
        texture: TextureId,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
        regionWidth: Int,
        regionHeight: Int,
        textureWidth: Int,
        textureHeight: Int,
        mirrored: Boolean
    ) {
        val pose = guiGraphics.pose()
        //? if >=1.21.5 {
        pose.pushMatrix()
        if (mirrored) {
            pose.translate((x * 2 + width).toFloat(), 0f)
            pose.scale(-1f, 1f)
        }
        guiGraphics.blit(
            texture, x, y, x + width, y + height,
            u / textureWidth, (u + regionWidth) / textureWidth,
            v / textureHeight, (v + regionHeight) / textureHeight
        )
        pose.popMatrix()
        //?} else {
        /*pose.pushPose()
        pose.translate((if (mirrored) x + width else x).toDouble(), y.toDouble(), 0.0)
        pose.scale((if (mirrored) -1f else 1f) * width / regionWidth, height.toFloat() / regionHeight, 1f)
        guiGraphics.blit(texture, 0, 0, u, v, regionWidth, regionHeight, textureWidth, textureHeight)
        pose.popPose()
        *///?}
    }

    override fun handleEscape(): Boolean { closeToParent(); return true }

    override fun removed() {
        CosmeticManager.setPreviewing(false)
        CosmeticManager.clearPreview()
        CosmeticLoadoutClient.flush()
        super.removed()
    }

    private fun closeToParent() {
        CosmeticManager.setPreviewing(false)
        CosmeticManager.clearPreview()
        CosmeticLoadoutClient.flush()
        ConfigManager.saveDefault()
        mc.setScreenCompat(parent)
    }

    private inner class NovaCosmeticsBridge : CosmeticsBridge {
        @Volatile private var purchasing = false
        @Volatile private var status = ""

        override fun i18n(key: String): String = Language.get(key)
        override fun playerName(): String = mc.player?.name?.string ?: "Steve"
        override fun items(): List<CosmeticsBridge.Item> = CosmeticManager.allOptions().map { option ->
            val builtin = option.id == CosmeticManager.BUILTIN_WINGS_ID
            CosmeticsBridge.Item(
                option.id,
                if (builtin) Language.get("cosmetics.wings.builtin") else option.name,
                option.description,
                option.category,
                option.price,
                CosmeticManager.isOwned(option.id),
                CosmeticManager.isEquipped(option.id),
                builtin,
                option.defaultScale,
                option.scaleAdjustable,
                option.minScale,
                option.maxScale
            )
        }
        override fun previewItem(id: String) { CosmeticManager.preview(id) }
        override fun equipItem(id: String) { CosmeticManager.equip(id) }
        override fun signedIn(): Boolean = AuthService.isLoggedIn()
        override fun purchasePending(): Boolean = purchasing
        override fun statusMessage(): String = status
        override fun syncStatus(): String = CosmeticLoadoutClient.statusId()
        override fun openCustomFolder() { CosmeticManager.openCustomDirectory() }
        override fun purchaseItem(id: String) {
            val itemId = id.toLongOrNull() ?: return
            if (purchasing) return
            purchasing = true
            status = Language.get("cosmetics.purchasing")
            FPSMasterApiClient.purchaseItem(itemId).whenComplete { result, exception ->
                purchasing = false
                if (exception == null && result?.success == true) {
                    CosmeticManager.grantPurchasedAndEquip(id)
                    CosmeticManager.refreshOwned()
                    status = Language.get("cosmetics.purchase.success")
                } else {
                    status = exception?.message ?: result?.message ?: Language.get("cosmetics.purchase.failed")
                }
            }
        }
        override fun capeEnabled(): Boolean = CosmeticManager.capeAnimationEnabled
        override fun setCapeEnabled(enabled: Boolean) { CosmeticManager.setCapeAnimationEnabled(enabled) }
        override fun wingScale(): Float = CosmeticManager.wingScale
        override fun setWingScale(scale: Float) { CosmeticManager.setWingScale(scale) }
        override fun wingScaleAdjustable(): Boolean = CosmeticManager.wingScaleAdjustable
        override fun paintItemPreview(ui: UiFrame, item: CosmeticsBridge.Item,
                                      x: Float, y: Float, w: Float, h: Float) {
            itemPreviews.add(
                ItemPreview(item, x, y, w, h, (ui.canvas() as? NovaCanvas)?.currentClip())
            )
        }
        override fun paintPlayerPreview(ui: UiFrame, x: Float, y: Float, w: Float, h: Float, yaw: Float) {
            preview = floatArrayOf(x, y, w, h, yaw)
        }
    }

    //? if <1.21.5 {
    /*private companion object {
        /** renderEntityInInventory 把实体原点放到的深度：1050 - 1000。见上面的支点推导。 */
        const val PREVIEW_DEPTH = 50.0

        /** GUI 里直接画模型时压在面板前面的深度，和 Edge 的 `glTranslatef(x, y, 50f)` 同口径。 */
        const val GUI_MODEL_DEPTH = 50.0

        /** 商品卡缩略图固定的翅膀缩放，和 Edge `renderPreview(..., 0.78f)` 对齐（不跟用户设置走）。 */
        const val WING_THUMB_SCALE = 0.78f

        /** packedLight 全亮：GUI 里没有世界光照。 */
        const val FULL_BRIGHT = 15728880
    }
    *///?}

    private data class ItemPreview(
        val item: CosmeticsBridge.Item,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        /** paint 阶段生效的裁剪矩形 `[x, y, w, h]`，补画缩略图时要拿回来。见 [withPreviewClip]。 */
        val clip: FloatArray?
    )
}
