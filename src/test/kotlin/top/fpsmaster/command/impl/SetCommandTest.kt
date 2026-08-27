package top.fpsmaster.command.impl

import top.fpsmaster.command.CommandExecutionException
import top.fpsmaster.module.value.impl.ChoiceValue
import top.fpsmaster.module.value.impl.NumberValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SetCommandTest {
    @Test
    fun `rejects a number outside the setting range`() {
        val value = NumberValue("fov", 70.0, 30.0, 110.0, 5.0)
        val failure = assertFailsWith<CommandExecutionException> {
            SetCommand().applyValue("custom-fov", value, "200")
        }
        assertTrue("越界" in failure.message.orEmpty())
        assertEquals(70.0, value.getValue())
    }

    @Test
    fun `rejects an unknown choice instead of keeping silent`() {
        val value = ChoiceValue("mode", listOf("vanilla", "custom"), "vanilla")
        val failure = assertFailsWith<CommandExecutionException> {
            SetCommand().applyValue("animation", value, "nope")
        }
        assertTrue("无效选项" in failure.message.orEmpty())
        assertEquals("vanilla", value.getValue())
    }
}
