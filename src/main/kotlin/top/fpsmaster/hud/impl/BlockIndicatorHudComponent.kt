package top.fpsmaster.hud.impl

//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics
//?} else {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.BlockIndicator

class BlockIndicatorHudComponent : HudComponent(
    id = "block_indicator",
    x = 10f,
    y = 488f
) {
    override fun shouldRender(): Boolean = visible && BlockIndicator.isActive() && targetBlock() != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize = HudSize(width = 174f, height = 46f)

    override fun render(guiGraphics: GuiGraphics, preview: Boolean) {
        if (preview) {
            super.render(guiGraphics, preview)
            return
        }

        val originalX = x
        val originalY = y
        x = (guiGraphics.guiWidth() - measure(preview).width * scale) / 2f
        y = BlockIndicator.yOffsetValue()
        super.render(guiGraphics, preview)
        x = originalX
        y = originalY
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val target = if (preview) PreviewBlock else targetBlock() ?: return
        guiGraphics.fill(0, 0, 174, 46, BlockIndicator.backgroundColor())
        guiGraphics.fill(7, 7, 10, 39, BlockIndicator.accentColor())
        guiGraphics.fill(16, 7, 50, 41, BlockIndicator.panelColor())
        if (!target.itemStack.isEmpty) {
            guiGraphics.renderFakeItem(target.itemStack, 25, 16)
        }
        guiGraphics.drawString(mc.font, target.name, 58, 8, 0xFFFFFFFF.toInt(), false)

        var y = 20
        if (BlockIndicator.showId.getValue()) {
            guiGraphics.drawString(mc.font, target.id, 58, y, 0xFFC8D0DA.toInt(), false)
            y += 10
        }
        if (BlockIndicator.showCoords.getValue()) {
            guiGraphics.drawString(mc.font, target.coords, 58, y, 0xFFC8D0DA.toInt(), false)
        }
    }

    private fun targetBlock(): TargetBlock? {
        val level = mc.level ?: return null
        val hitResult = mc.hitResult as? BlockHitResult ?: return null
        if (hitResult.type != HitResult.Type.BLOCK) {
            return null
        }

        val pos = hitResult.blockPos
        val state = level.getBlockState(pos)
        if (state.isAir) {
            return null
        }

        val block = state.block
        val itemStack = block.asItem().defaultInstance
        return TargetBlock(
            name = block.name.string,
            id = BuiltInRegistries.BLOCK.getKey(block).toString(),
            coords = "X ${pos.x}  Y ${pos.y}  Z ${pos.z}",
            itemStack = itemStack
        )
    }

    private data class TargetBlock(
        val name: String,
        val id: String,
        val coords: String,
        val itemStack: net.minecraft.world.item.ItemStack
    )

    companion object {
        private val PreviewBlock = TargetBlock(
            name = "Stone",
            id = "minecraft:stone",
            coords = "X 0  Y 64  Z 0",
            itemStack = net.minecraft.world.item.Items.STONE.defaultInstance
        )
    }
}
