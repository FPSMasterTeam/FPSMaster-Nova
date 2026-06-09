package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.ArmorDisplay

class ArmorHudComponent : HudComponent(
    id = "armor",
    x = 10f,
    y = 164f
) {
    override fun shouldRender(): Boolean = visible && ArmorDisplay.isActive() && armorItems().any { !it.isEmpty }

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val spacing = ArmorDisplay.style.spacing.getValue().toFloat()
        return when (ArmorDisplay.modeIndex()) {
            1 -> HudSize(width = 70f, height = 70f + spacing * 3f)
            2 -> HudSize(width = durabilityWidth(preview).toFloat(), height = 70f + spacing * 3f)
            else -> HudSize(width = 70f + spacing * 3f, height = 18f)
        }
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val items = if (preview) {
            emptyList()
        } else {
            armorItems()
        }

        val mode = ArmorDisplay.modeIndex()
        val spacing = ArmorDisplay.style.spacing.getValue().toInt()
        for (index in 0 until 4) {
            val x = if (mode == 0) index * (18 + spacing) else 0
            val y = if (mode == 0) 0 else index * (18 + spacing)
            ArmorDisplay.style.fillBackground(guiGraphics, x, y, x + 16, y + 16)

            val itemStack = items.getOrNull(index) ?: continue
            if (!itemStack.isEmpty) {
                guiGraphics.renderItem(itemStack, x, y)
                guiGraphics.renderItemDecorations(mc.font, itemStack, x, y)
                if (mode == 2) {
                    renderDurability(guiGraphics, itemStack, x + 18, y)
                }
            }
        }
    }

    private fun renderDurability(guiGraphics: GuiGraphics, itemStack: ItemStack, x: Int, y: Int) {
        if (!itemStack.isDamageableItem || itemStack.maxDamage <= 0) {
            return
        }

        val text = durabilityText(itemStack)
        val width = mc.font.width(text) + 4
        ArmorDisplay.style.fillBackground(guiGraphics, x, y, x + width, y + 16)
        guiGraphics.drawString(mc.font, text, x + 2, y + 4, durabilityColor(itemStack), ArmorDisplay.style.fontShadow.getValue())
    }

    private fun durabilityWidth(preview: Boolean): Int {
        val textWidth = if (preview) {
            mc.font.width("999/999")
        } else {
            armorItems()
                .filter { !it.isEmpty && it.isDamageableItem && it.maxDamage > 0 }
                .maxOfOrNull { mc.font.width(durabilityText(it)) }
                ?: 48
        }
        return maxOf(70, 18 + textWidth + 4)
    }

    private fun durabilityText(itemStack: ItemStack): String {
        val durability = itemStack.maxDamage - itemStack.damageValue
        return "$durability/${itemStack.maxDamage}"
    }

    private fun durabilityColor(itemStack: ItemStack): Int {
        val ratio = (itemStack.maxDamage - itemStack.damageValue).toFloat() / itemStack.maxDamage.toFloat()
        return when {
            ratio < 0.2f -> 0xFFFF1414.toInt()
            ratio < 0.5f -> 0xFFFFFF14.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun armorItems(): List<ItemStack> {
        val player = mc.player ?: return emptyList()
        return listOf(
            player.getItemBySlot(EquipmentSlot.HEAD),
            player.getItemBySlot(EquipmentSlot.CHEST),
            player.getItemBySlot(EquipmentSlot.LEGS),
            player.getItemBySlot(EquipmentSlot.FEET)
        )
    }
}
