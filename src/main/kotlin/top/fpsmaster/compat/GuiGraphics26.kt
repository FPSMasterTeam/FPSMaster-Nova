package top.fpsmaster.compat

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import top.fpsmaster.mixin.interfaces.IGuiGraphicsExtractor

/**
 * MC 26.2 removed `net.minecraft.client.gui.GuiGraphics`; the 2D drawing context is now
 * [GuiGraphicsExtractor] (built from a deferred `GuiRenderState`) with a few renamed methods
 * (`drawString`→`text`, `drawCenteredString`→`centeredText`). This shim mirrors the subset of the
 * 1.21.11 `GuiGraphics` API the FPSMaster HUD/UI uses and delegates to [GuiGraphicsExtractor], so the
 * HUD/UI Kotlin compiles unchanged via a Stonecutter import swap (only the import differs per version:
 * `import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics`).
 *
 * `pose()` needs no bridge — 1.21.11 already draws through the same 2D `Matrix3x2fStack`. Text/fill/
 * scissor/blit and the deferred item/item-decoration paths delegate directly to [GuiGraphicsExtractor].
 * The item methods therefore record real item render state; they do not use a placeholder or CPU
 * raster fallback.
 */
class GuiGraphics26(@JvmField val delegate: GuiGraphicsExtractor) {

    fun pose() = delegate.pose()
    fun guiWidth() = delegate.guiWidth()
    fun guiHeight() = delegate.guiHeight()
    // 26.2's deferred GUI pipeline has no explicit flush; drawing is submitted via the render state.
    fun flush() {}

    fun enableScissor(x1: Int, y1: Int, x2: Int, y2: Int) = delegate.enableScissor(x1, y1, x2, y2)
    fun disableScissor() = delegate.disableScissor()

    fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) = delegate.fill(x1, y1, x2, y2, color)
    fun fillGradient(x1: Int, y1: Int, x2: Int, y2: Int, from: Int, to: Int) =
        delegate.fillGradient(x1, y1, x2, y2, from, to)

    // drawString / drawCenteredString → text / centeredText. Return the drawn width (matching the old
    // GuiGraphics.drawString contract) so call sites that chain on the return value keep working.
    fun drawString(font: Font, text: String, x: Int, y: Int, color: Int): Int {
        delegate.text(font, text, x, y, color); return font.width(text)
    }

    fun drawString(font: Font, text: String, x: Int, y: Int, color: Int, shadow: Boolean): Int {
        delegate.text(font, text, x, y, color, shadow); return font.width(text)
    }

    fun drawString(font: Font, text: Component, x: Int, y: Int, color: Int): Int {
        delegate.text(font, text, x, y, color); return font.width(text)
    }

    fun drawString(font: Font, text: Component, x: Int, y: Int, color: Int, shadow: Boolean): Int {
        delegate.text(font, text, x, y, color, shadow); return font.width(text)
    }

    fun drawCenteredString(font: Font, text: String, x: Int, y: Int, color: Int) =
        delegate.centeredText(font, Component.literal(text), x, y, color)

    fun drawCenteredString(font: Font, text: Component, x: Int, y: Int, color: Int) =
        delegate.centeredText(font, text, x, y, color)

    // 2D GUI-texture blits — delegated (the RenderPipeline overloads exist on GuiGraphicsExtractor).
    fun blit(
        pipeline: RenderPipeline, texture: Identifier, x: Int, y: Int, u: Float, v: Float,
        width: Int, height: Int, textureWidth: Int, textureHeight: Int
    ) = delegate.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight)

    fun blit(
        pipeline: RenderPipeline, texture: Identifier, x: Int, y: Int, u: Float, v: Float,
        width: Int, height: Int, regionWidth: Int, regionHeight: Int, textureWidth: Int, textureHeight: Int
    ) = delegate.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight)

    fun blit(
        pipeline: RenderPipeline, texture: Identifier, x: Int, y: Int, u: Float, v: Float,
        width: Int, height: Int, regionWidth: Int, regionHeight: Int, textureWidth: Int, textureHeight: Int,
        color: Int
    ) = delegate.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color)

    fun blit(texture: Identifier, x0: Int, y0: Int, x1: Int, y1: Int,
             u0: Float, u1: Float, v0: Float, v1: Float) =
        delegate.blit(texture, x0, y0, x1, y1, u0, u1, v0, v1)

    fun blitSprite(pipeline: RenderPipeline, sprite: Identifier, x: Int, y: Int, width: Int, height: Int) =
        delegate.blitSprite(pipeline, sprite, x, y, width, height)

    fun renderItem(stack: net.minecraft.world.item.ItemStack, x: Int, y: Int) = delegate.item(stack, x, y)
    fun renderFakeItem(stack: net.minecraft.world.item.ItemStack, x: Int, y: Int) = delegate.fakeItem(stack, x, y)
    fun renderItemDecorations(font: Font, stack: net.minecraft.world.item.ItemStack, x: Int, y: Int) =
        delegate.itemDecorations(font, stack, x, y)

    fun addPicturesInPicture(state: PictureInPictureRenderState) {
        (delegate as IGuiGraphicsExtractor).fpsmasterGuiRenderState().addPicturesInPictureState(state)
    }
}
