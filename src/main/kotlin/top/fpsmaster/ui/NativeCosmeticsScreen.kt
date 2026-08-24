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
import top.fpsmaster.cosmetic.CosmeticManager
import top.fpsmaster.cosmetic.TextureId
//? if >=1.21.11 && <26 {
import top.fpsmaster.cosmetic.WingPreviewRenderState
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
import top.fpsmaster.ui.kit.ToolkitScreen

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
        *///?} else {
        /*val centerX = x + w / 2
        val feetY = y + h - 24
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushPose()
        modelView.translate(centerX.toDouble(), feetY.toDouble(), 0.0)
        modelView.mulPose(LegacyVector3f.YP.rotationDegrees(preview[4]))
        modelView.translate(-centerX.toDouble(), -feetY.toDouble(), 0.0)
        RenderSystem.applyModelViewMatrix()
        try {
            InventoryScreen.renderEntityInInventory(centerX, feetY, (h * 0.31f).toInt(), 0f, 0f, player)
        } finally {
            modelView.popPose()
            RenderSystem.applyModelViewMatrix()
        }
        *///?}
    }
    //?}

    private fun renderItemPreviews(guiGraphics: GuiGraphics) {
        itemPreviews.forEach { itemPreview ->
            val texture = if (itemPreview.item.builtin()) builtinWingTexture
            else CosmeticManager.textureFor(itemPreview.item.id()) ?: return@forEach
            when (itemPreview.item.category()) {
                "wings" -> renderWingThumbnail(guiGraphics, texture, itemPreview)
                "cape" -> renderCapeThumbnail(guiGraphics, texture, itemPreview)
            }
        }
    }

    private fun renderCapeThumbnail(guiGraphics: GuiGraphics, texture: TextureId, itemPreview: ItemPreview) {
        val height = (itemPreview.h - 4f).toInt().coerceAtLeast(1)
        val width = (height * 10f / 16f).toInt().coerceAtLeast(1)
        val x = (itemPreview.x + (itemPreview.w - width) / 2f).toInt()
        val y = (itemPreview.y + (itemPreview.h - height) / 2f).toInt()
        blitRegion(guiGraphics, texture, x, y, width, height, 1f, 1f, 10, 16, 64, 32, false)
    }

    private fun renderWingThumbnail(guiGraphics: GuiGraphics, texture: TextureId, itemPreview: ItemPreview) {
        //? if >=1.21.11 && <26 {
        val x = itemPreview.x.toInt()
        val y = itemPreview.y.toInt()
        val width = itemPreview.w.toInt().coerceAtLeast(1)
        val height = itemPreview.h.toInt().coerceAtLeast(1)
        val access = guiGraphics as IGuiGraphics
        access.fpsmasterGuiRenderState().submitPicturesInPictureState(
            WingPreviewRenderState(
                texture, x, y, x + width, y + height,
                kotlin.math.min(width / 3f, height * 0.75f),
                access.fpsmasterScissorArea() ?: net.minecraft.client.gui.navigation.ScreenRectangle(x, y, width, height)
            )
        )
        //?} else {
        /*renderWingThumbnail2d(guiGraphics, texture, itemPreview)
        *///?}
    }

    private fun renderWingThumbnail2d(guiGraphics: GuiGraphics, texture: TextureId, itemPreview: ItemPreview) {
        val panel = kotlin.math.min(itemPreview.w * 0.235f, itemPreview.h - 6f).toInt().coerceAtLeast(1)
        val step = (panel - 1).coerceAtLeast(1)
        val centerX = (itemPreview.x + itemPreview.w / 2f).toInt()
        val pivotY = (itemPreview.y + itemPreview.h * 0.76f).toInt()
        renderWingSide(guiGraphics, texture, centerX, pivotY, panel, step, false)
        renderWingSide(guiGraphics, texture, centerX, pivotY, panel, step, true)
    }

    private fun renderWingSide(
        guiGraphics: GuiGraphics,
        texture: TextureId,
        pivotX: Int,
        pivotY: Int,
        panel: Int,
        step: Int,
        mirrored: Boolean
    ) {
        val pose = guiGraphics.pose()
        val angle = if (mirrored) Math.toRadians(12.0).toFloat() else Math.toRadians(-12.0).toFloat()
        //? if >=1.21.5 {
        pose.pushMatrix()
        pose.translate(pivotX.toFloat(), pivotY.toFloat())
        pose.rotate(angle)
        if (mirrored) pose.scale(-1f, 1f)
        //?} else if >=1.20 {
        /*pose.pushPose()
        pose.translate(pivotX.toDouble(), pivotY.toDouble(), 0.0)
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(angle))
        if (mirrored) pose.scale(-1f, 1f, 1f)
        *///?} else {
        /*pose.pushPose()
        pose.translate(pivotX.toDouble(), pivotY.toDouble(), 0.0)
        pose.mulPose(LegacyVector3f.ZP.rotation(angle))
        if (mirrored) pose.scale(-1f, 1f, 1f)
        *///?}
        blitRegion(guiGraphics, texture, 0, -panel, panel, panel,
            0f, 8f, 10, 10, 30, 30, false)
        blitRegion(guiGraphics, texture, step, -panel, panel, panel,
            0f, 18f, 10, 10, 30, 30, false)
        //? if >=1.21.5 {
        pose.popMatrix()
        //?} else {
        /*pose.popPose()
        *///?}
    }

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
        super.removed()
    }

    private fun closeToParent() {
        CosmeticManager.setPreviewing(false)
        CosmeticManager.clearPreview()
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
                builtin
            )
        }
        override fun previewItem(id: String) { CosmeticManager.preview(id) }
        override fun equipItem(id: String) { CosmeticManager.equip(id) }
        override fun signedIn(): Boolean = AuthService.isLoggedIn()
        override fun purchasePending(): Boolean = purchasing
        override fun statusMessage(): String = status
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
            itemPreviews.add(ItemPreview(item, x, y, w, h))
        }
        override fun paintPlayerPreview(ui: UiFrame, x: Float, y: Float, w: Float, h: Float, yaw: Float) {
            preview = floatArrayOf(x, y, w, h, yaw)
        }
    }

    private data class ItemPreview(
        val item: CosmeticsBridge.Item,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    )
}
