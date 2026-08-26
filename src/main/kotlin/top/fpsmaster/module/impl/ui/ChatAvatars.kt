package top.fpsmaster.module.impl.ui

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceExtractor
*///?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.network.chat.Component
import top.fpsmaster.chat.ChatAvatarResolver
import top.fpsmaster.module.Category
import top.fpsmaster.module.Module
import top.fpsmaster.module.value.impl.NumberValue
import top.fpsmaster.module.value.impl.OptionValue

/**
 * Draws the sender's head beside each chat line.
 *
 * The module owns which lines get a head and what it looks like; the chat mixin owns where it lands,
 * and calls [indentPixels] to reserve room and [drawFor] to paint. Sender attribution lives in
 * [ChatAvatarResolver] — see there for why a chat line is a guess and not a parse.
 */
class ChatAvatars : Module("chat-avatars", Category.UI) {
    init {
        values.addAll(arrayOf(size, gap, indentText, hideWithoutSkin))
    }

    override fun onEnable() {
        active = true
    }

    override fun onDisable() {
        active = false
        ChatAvatarResolver.clear()
    }

    companion object {
        private var active = false

        val size = NumberValue("size", 8.0, 6.0, 16.0, 1.0)
        val gap = NumberValue("gap", 2.0, 0.0, 8.0, 1.0)

        /** Off draws heads over the line's left edge instead of pushing the text across. */
        val indentText = OptionValue("indent-text", true)

        /**
         * Skips lines whose sender has no loaded skin. Off draws the fallback Steve/Alex head instead,
         * which keeps the chat's left edge even at the cost of showing a head for a player who is only
         * a name to the client.
         */
        val hideWithoutSkin = OptionValue("hide-without-skin", true)

        @JvmStatic
        fun isActive(): Boolean = active

        /** Horizontal room a chat line should leave for a head, in GUI pixels. */
        @JvmStatic
        fun indentPixels(): Int =
            if (!active || !indentText.getValue()) 0 else size.getValue().toInt() + gap.getValue().toInt()

        /**
         * Draws the head for [component]'s sender with its top-left at [x], [y].
         *
         * [alpha] is the chat line's own fade, 0..1. Silently does nothing when the sender cannot be
         * attributed — an unattributed line simply gets no head.
         */
        @JvmStatic
        fun drawFor(guiGraphics: GuiGraphics, component: Component, x: Int, y: Int, alpha: Float) {
            if (!active || alpha <= 0.02f) {
                return
            }
            val name = ChatAvatarResolver.senderOf(component) ?: return
            val info = ChatAvatarResolver.playerInfo(name)
            if (info == null && hideWithoutSkin.getValue()) {
                return
            }
            drawHead(guiGraphics, info, x, y, size.getValue().toInt(), alpha)
        }

        private fun drawHead(
            guiGraphics: GuiGraphics,
            info: net.minecraft.client.multiplayer.PlayerInfo?,
            x: Int,
            y: Int,
            size: Int,
            alpha: Float
        ) {
            if (info == null) {
                return
            }
            // PlayerInfo exposes a PlayerSkin from 1.20.2 on; before that it is a bare texture location.
            //? if >=1.20.2 {
            val skin = info.skin
            //?} else {
            /*val skin = info.skinLocation
            *///?}
            val tint = (alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24 or 0xFFFFFF
            //? if >=26 {
            /*PlayerFaceExtractor.extractRenderState(guiGraphics.delegate, skin, x, y, size, tint)
            *///?}
            //? if >=1.21.5 && <26 {
            PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size, tint)
            //?}
            //? if >=1.20 && <1.21.5 {
            /*// No tinted overload before 1.21.5; the head does not fade with the line there.
            PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size)
            *///?}
            // <1.20 has no GuiGraphics of its own in this tree, so it draws no heads yet.
        }
    }
}
