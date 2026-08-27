package top.fpsmaster.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdgeConfigDetectionTest {
    @Test
    fun `requires schema version one and a modules object`() {
        val edge = JsonObject()
        edge.addProperty("schemaVersion", 1)
        edge.add("modules", JsonObject())
        assertTrue(ConfigManager.isEdgeConfig(edge))
    }

    @Test
    fun `rejects a modules object without schema version one`() {
        val objectOnly = JsonObject()
        objectOnly.add("modules", JsonObject())
        assertFalse(ConfigManager.isEdgeConfig(objectOnly))

        val wrongVersion = JsonObject()
        wrongVersion.addProperty("schemaVersion", 0)
        wrongVersion.add("modules", JsonObject())
        assertFalse(ConfigManager.isEdgeConfig(wrongVersion))
    }

    @Test
    fun `rejects schema version one with a nova modules array`() {
        val nova = JsonObject()
        nova.addProperty("schemaVersion", 1)
        nova.add("modules", JsonArray())
        assertFalse(ConfigManager.isEdgeConfig(nova))
    }
}
