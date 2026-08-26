package top.fpsmaster.module.value

import top.fpsmaster.module.value.impl.NumberValue
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberValueTest {
    @Test
    fun `snaps from the minimum when the minimum is negative`() {
        val value = NumberValue("offset", 0.0, -0.5, 0.5, 0.25)

        value.setValue(-0.3)
        assertEquals(-0.25, value.getValue())

        value.setValue(-0.4)
        assertEquals(-0.5, value.getValue())

        // Anchoring on zero instead of the minimum would round this to 0.0 and lose a reachable step.
        value.setValue(-0.24)
        assertEquals(-0.25, value.getValue())
    }

    @Test
    fun `keeps tenths free of binary residue`() {
        val value = NumberValue("height", 0.0, 0.0, 0.7, 0.1)

        value.setValue(0.3)
        assertEquals(0.3, value.getValue())

        value.setValue(0.34)
        assertEquals(0.3, value.getValue())

        value.setValue(0.36)
        assertEquals(0.4, value.getValue())
    }

    @Test
    fun `snaps coarse increments to the nearest reachable step`() {
        val value = NumberValue("fov", 70.0, 30.0, 110.0, 5.0)

        value.setValue(72.0)
        assertEquals(70.0, value.getValue())

        value.setValue(73.0)
        assertEquals(75.0, value.getValue())

        // 33 has a remainder of 3 against the increment, which a remainder-vs-half test would
        // round the wrong way once the minimum is not a multiple of the increment.
        value.setValue(33.0)
        assertEquals(35.0, value.getValue())
    }

    @Test
    fun `clamps to the boundaries`() {
        val value = NumberValue("opacity", 0.5, 0.0, 1.0, 0.05)

        value.setValue(2.0)
        assertEquals(1.0, value.getValue())

        value.setValue(-2.0)
        assertEquals(0.0, value.getValue())

        // The last step must never overshoot the maximum, even when it is not a whole step away.
        val uneven = NumberValue("width", 1.0, 1.0, 2.5, 0.4)
        uneven.setValue(2.5)
        assertEquals(2.5, uneven.getValue())
    }

    @Test
    fun `passes values through when there is no increment`() {
        val value = NumberValue("raw", 0.0, 0.0, 10.0, 0.0)

        value.setValue(3.14159)
        assertEquals(3.14159, value.getValue())
    }
}
