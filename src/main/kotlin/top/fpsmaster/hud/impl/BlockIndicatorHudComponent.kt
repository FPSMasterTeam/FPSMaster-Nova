package top.fpsmaster.hud.impl

//? if >=26 {
/*import top.fpsmaster.compat.GuiGraphics26 as GuiGraphics*/
//?}
//? if >=1.20 && <26 {
import net.minecraft.client.gui.GuiGraphics
//?}
//? if <1.20 {
/*import top.fpsmaster.compat.GuiGraphics*/
//?}
//? if >=1.20 {
import net.minecraft.core.registries.BuiltInRegistries
//?} else {
/*import net.minecraft.core.Registry as BuiltInRegistries*/
//?}
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

    // Compact, content-sized card: [accent | icon | name / id / coords]. Freely draggable in the HUD
    // editor (no longer force-centred) so users can place it wherever they like.
    override fun measure(preview: Boolean): HudSize {
        val target = if (preview) PreviewBlock else targetBlock() ?: return HudSize(0f, 0f)
        val lines = extraLines(target)
        val textWidth = maxOf(mc.font.width(target.name), lines.maxOfOrNull { mc.font.width(it) } ?: 0)
        val width = (PADDING + ICON + GAP + textWidth + PADDING).toFloat()
        val height = (PADDING * 2 + maxOf(ICON, LINE * (1 + lines.size))).toFloat()
        return HudSize(width, height)
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val target = if (preview) PreviewBlock else targetBlock() ?: return
        val lines = extraLines(target)
        val size = measure(preview)
        val w = size.width.toInt()
        val h = size.height.toInt()

        guiGraphics.fill(0, 0, w, h, BlockIndicator.backgroundColor())
        guiGraphics.fill(0, 0, 2, h, BlockIndicator.accentColor())

        val iconY = (h - ICON) / 2
        if (!target.itemStack.isEmpty) {
            guiGraphics.renderFakeItem(target.itemStack, PADDING + 1, iconY)
        }

        val textX = PADDING + ICON + GAP
        var ty = (h - LINE * (1 + lines.size)) / 2
        guiGraphics.drawString(mc.font, target.name, textX, ty, 0xFFFFFFFF.toInt(), false)
        for (line in lines) {
            ty += LINE
            guiGraphics.drawString(mc.font, line, textX, ty, 0xFFB6C0CC.toInt(), false)
        }
    }

    private fun extraLines(target: TargetBlock): List<String> {
        val lines = mutableListOf<String>()
        if (BlockIndicator.showId.getValue()) lines.add(target.id)
        if (BlockIndicator.showCoords.getValue()) lines.add(target.coords)
        return lines
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
        private const val PADDING = 5
        private const val ICON = 16
        private const val GAP = 5
        private const val LINE = 10

        // Lazy: on MC 26.2, building an ItemStack (Item.getDefaultInstance) at class-load throws
        // "Components not bound yet" — the DataComponent registry binds later than mod init. Deferring to
        // first use (render/HUD-editor preview, long after components bind) fixes it on 26.2 and is
        // harmless on older versions.
        private val PreviewBlock by lazy {
            TargetBlock(
                name = "Stone",
                id = "minecraft:stone",
                coords = "X 0  Y 64  Z 0",
                itemStack = net.minecraft.world.item.Items.STONE.defaultInstance
            )
        }
    }
}
