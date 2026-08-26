package top.fpsmaster.module.value

import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.ColorValue
import top.fpsmaster.module.value.impl.ListValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValueTypesTest {
    @Test
    fun `item lists stop at the edge cap of seven`() {
        val list = ListValue.items("items", listOf("a"))

        repeat(10) { list.add() }
        assertEquals(ListValue.MAX_ITEMS, list.getValue().size)
        assertFalse(list.add())
    }

    @Test
    fun `auto text lists keep key and message per entry up to twenty`() {
        val list = ListValue.autoText("lines")

        assertTrue(list.add())
        assertTrue(list.setText(0, "gg"))
        assertTrue(list.setKey(0, 71))
        assertEquals(ListValue.Entry("gg", 71), list.getValue()[0])

        repeat(30) { list.add() }
        assertEquals(ListValue.MAX_AUTO_TEXT, list.getValue().size)

        // Out-of-range edits are ignored rather than throwing at the ClickGUI boundary.
        assertFalse(list.setText(99, "x"))
        assertFalse(list.removeAt(-1))
    }

    @Test
    fun `choices store the option id so reordering cannot repoint a profile`() {
        val choice = ChoiceValue("mode", listOf("potions", "combat", "custom"))

        choice.select(2)
        assertEquals("custom", choice.getValue())
        assertTrue(choice.isSelected("custom"))

        // Unknown ids and out-of-range indices leave the current selection untouched.
        choice.setValue("nope")
        choice.select(7)
        assertEquals("custom", choice.getValue())

        assertEquals(1, ChoiceValue("mode", listOf("potions", "combat"), "combat").index)
    }

    @Test
    fun `static colours round trip through hsb`() {
        val color = ColorValue.ofRgba("tint", 105.0, 180.0, 255.0, 220.0)

        assertEquals(0xDC69B4FF.toInt(), color.staticArgb())
        assertEquals(ColorValue.Mode.STATIC, color.getValue().mode)

        color.setMode(ColorValue.Mode.RAINBOW)
        // Animated modes still expose a fully opaque-preserving alpha channel.
        assertEquals(220, (color.argb() ushr 24) and 255)
    }
}
