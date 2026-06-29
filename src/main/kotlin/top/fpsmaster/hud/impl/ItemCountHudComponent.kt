package top.fpsmaster.hud.impl

import net.minecraft.client.gui.GuiGraphics
//? if >=1.21.5 {
import net.minecraft.core.component.DataComponents
//?}
import net.minecraft.core.registries.BuiltInRegistries
//? if >=1.21.5 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation*/
//?}
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
//? if >=1.21.5 {
import net.minecraft.world.item.alchemy.PotionContents
//?} else {
/*import net.minecraft.world.item.alchemy.PotionUtils*/
//?}
import net.minecraft.world.item.alchemy.Potions
import top.fpsmaster.hud.HudComponent
import top.fpsmaster.hud.HudSize
import top.fpsmaster.mc
import top.fpsmaster.module.impl.ui.ItemCountDisplay

class ItemCountHudComponent : HudComponent(
    id = "item_count",
    x = 10f,
    y = 264f
) {
    override fun shouldRender(): Boolean = visible && ItemCountDisplay.isActive() && mc.player != null

    override fun shouldRenderInEditor(): Boolean = visible

    override fun measure(preview: Boolean): HudSize {
        val spacing = ItemCountDisplay.style.spacing.getValue().toFloat()
        return HudSize(width = items().size * ITEM_WIDTH + (items().size - 1).coerceAtLeast(0) * spacing, height = DISPLAY_HEIGHT)
    }

    override fun renderContent(guiGraphics: GuiGraphics, preview: Boolean) {
        val spacing = ItemCountDisplay.style.spacing.getValue().toInt()
        items().forEachIndexed { index, template ->
            val x = index * (ITEM_WIDTH.toInt() + spacing)
            val count = if (preview) 16 else countItem(template)
            ItemCountDisplay.style.fillBackground(guiGraphics, x, 0, x + ITEM_WIDTH.toInt(), DISPLAY_HEIGHT.toInt())
            guiGraphics.renderFakeItem(template, x, 0)
            val text = count.toString()
            guiGraphics.drawString(mc.font, text, x + 8 - mc.font.width(text) / 2, ITEM_WIDTH.toInt(), 0xFFFFFFFF.toInt(), ItemCountDisplay.style.fontShadow.getValue())
        }
    }

    private fun countItem(template: ItemStack): Int {
        val inventory = mc.player?.inventory ?: return 0
        var count = 0
        for (slot in 0 until 36) {
            val stack = inventory.getItem(slot)
            if (matchesTemplate(stack, template)) {
                count += stack.count
            }
        }
        return count
    }

    private fun matchesTemplate(stack: ItemStack, template: ItemStack): Boolean {
        if (!stack.`is`(template.item)) {
            return false
        }

        //? if >=1.21.5 {
        val templatePotion = template.get(DataComponents.POTION_CONTENTS)
        return templatePotion == null || stack.get(DataComponents.POTION_CONTENTS) == templatePotion
        //?} else {
        /*val templatePotion = PotionUtils.getPotion(template)
        return templatePotion == Potions.EMPTY || PotionUtils.getPotion(stack) == templatePotion*/
        //?}
    }

    private fun items(): List<ItemStack> {
        return when (ItemCountDisplay.mode.getValue().toInt()) {
            0 -> listOf(
                Items.ENDER_PEARL.defaultInstance,
                //? if >=1.21.5 {
                PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING),
                PotionContents.createItemStack(Items.POTION, Potions.STRONG_SWIFTNESS)
                //?} else {
                /*PotionUtils.setPotion(ItemStack(Items.SPLASH_POTION), Potions.STRONG_HEALING),
                PotionUtils.setPotion(ItemStack(Items.POTION), Potions.STRONG_SWIFTNESS)*/
                //?}
            )
            1 -> listOf(Items.GOLDEN_APPLE.defaultInstance, Items.ARROW.defaultInstance)
            else -> customItems()
        }
    }

    private fun customItems(): List<ItemStack> {
        return ItemCountDisplay.customItems.getValue()
            .split(",")
            .mapNotNull { rawId ->
                //? if >=1.21.5 {
                val id = Identifier.tryParse(rawId.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                //?} else {
                /*val id = ResourceLocation.tryParse(rawId.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null)
                    ?: return@mapNotNull null*/
                //?}
                BuiltInRegistries.ITEM.getOptional(id).orElse(null)?.defaultInstance
            }
            .ifEmpty { listOf(Items.ENDER_PEARL.defaultInstance) }
    }

    companion object {
        private const val DISPLAY_HEIGHT = 27f
        private const val ITEM_WIDTH = 17f
    }
}
