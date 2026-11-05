package top.fpsmaster.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdgeConfigImportTest {
    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    private fun profile(module: String, settings: String, key: Int = 0) = json(
        """{"schemaVersion":1,"modules":{"$module":{"key":$key,"settings":$settings}}}"""
    )

    private fun settings(result: ConfigManager.EdgeImport, module: String) =
        result.root.getAsJsonObject("modules").getAsJsonObject(module).getAsJsonObject("settings")

    @Test
    fun `maps module shortcut setting and autotext keys by physical meaning`() {
        val source = profile("ClientSettings", """{
            "ClickGuiKey":{"type":"bind","value":54},
            "ZoomBind":{"type":"number","value":29}
        }""", 46)
        source.add("shortcuts", JsonParser.parseString("""[{"name":"test","key":59,"actions":[]}]"""))
        source.getAsJsonObject("modules").add("AutoText", json("""{"settings":{
            "Entries":{"type":"autoText","value":[{"msg":"gg","key":34},{"msg":"none","key":0}]}
        }}"""))
        val result = ConfigManager.prepareEdgeImport(source)
        val module = result.root.getAsJsonObject("modules").getAsJsonObject("ClientSettings")
        assertEquals(67, module.get("key").asInt) // C, not GLFW period (46)
        assertEquals(344, settings(result, "ClientSettings").getAsJsonObject("clickguikey").get("value").asInt)
        assertEquals(341, settings(result, "ClientSettings").getAsJsonObject("zoombind").get("value").asInt)
        assertEquals(290, result.root.getAsJsonArray("shortcuts")[0].asJsonObject.get("key").asInt)
        val rows = settings(result, "AutoText").getAsJsonObject("entries").getAsJsonArray("value")
        assertEquals(71, rows[0].asJsonObject.get("key").asInt)
        assertEquals("gg", rows[0].asJsonObject.get("msg").asString)
        assertEquals(0, rows[1].asJsonObject.get("key").asInt)
        assertTrue(result.issues.isEmpty())
        assertEquals(46, source.getAsJsonObject("modules").getAsJsonObject("ClientSettings").get("key").asInt)
    }

    @Test
    fun `maps navigation modifiers punctuation keypad and function keys distinctly`() {
        val pairs = mapOf(
            0 to 0, 1 to 256, 11 to 48, 28 to 257, 42 to 340, 54 to 344,
            56 to 342, 157 to 345, 184 to 346, 39 to 59, 43 to 92,
            71 to 327, 156 to 335, 181 to 331, 59 to 290, 88 to 301,
            113 to 308, 200 to 265, 203 to 263, 211 to 261, 219 to 343
        )
        pairs.forEach { (old, expected) ->
            val result = ConfigManager.prepareEdgeImport(profile("Sprint", "{}", old))
            assertEquals(expected, result.root.getAsJsonObject("modules").getAsJsonObject("Sprint").get("key").asInt)
        }
    }

    @Test
    fun `skips unknown mouse and fractional keys without activating actions`() {
        val source = profile("FreeLook", """{"Bind":{"type":"bind","value":999}}""", -100)
        source.add("shortcuts", JsonParser.parseString("""[{"key":999},{"key":46.5},{"key":0}]"""))
        source.getAsJsonObject("modules").add("AutoText", json("""{"settings":{
            "Entries":{"type":"autoText","value":[{"msg":"no","key":999},{"msg":"ok","keyCode":34}]}
        }}"""))
        val result = ConfigManager.prepareEdgeImport(source)
        assertFalse(result.root.getAsJsonObject("modules").getAsJsonObject("FreeLook").has("key"))
        assertFalse(settings(result, "FreeLook").has("bind"))
        assertEquals(1, result.root.getAsJsonArray("shortcuts").size())
        val rows = settings(result, "AutoText").getAsJsonObject("entries").getAsJsonArray("value")
        assertEquals(1, rows.size())
        assertEquals(71, rows[0].asJsonObject.get("keyCode").asInt)
        assertEquals(5, result.issues.size)
        assertTrue(result.issues.all { it.reason == "unsupported-key" })
    }

    @Test
    fun `inverts nametags background in both directions`() {
        listOf(true, false).forEach { background ->
            val result = ConfigManager.prepareEdgeImport(profile("Nametags",
                """{"Background":{"type":"boolean","value":$background}}"""))
            assertEquals(!background, settings(result, "Nametags").getAsJsonObject("disablebackground").get("value").asBoolean)
            assertFalse(settings(result, "Nametags").has("background"))
        }
    }

    @Test
    fun `maps all six missing color prefixes without changing channels`() {
        val pairs = mapOf(
            "ClockDisplay" to ("TextColor" to "text-color"),
            "ServerAddressDisplay" to ("TextColor" to "text-color"),
            "PlayTime" to ("TextColor" to "text-color"),
            "PerformanceHUD" to ("TextColor" to "text-color"),
            "SaturationDisplay" to ("BarColor" to "bar-color"),
            "CustomFog" to ("Color" to "color")
        )
        pairs.forEach { (module, mapping) ->
            val result = ConfigManager.prepareEdgeImport(profile(module,
                """{"${mapping.first}":{"type":"color","value":{"h":0.25,"s":0.5,"b":0.75,"a":0.8}}}"""))
            val color = settings(result, module).getAsJsonObject(mapping.second).getAsJsonObject("value")
            assertEquals(0.25, color.get("h").asDouble)
            assertEquals(0.8, color.get("a").asDouble)
        }
        val existing = ConfigManager.prepareEdgeImport(profile("Crosshair",
            """{"Color":{"type":"color","value":{"h":0.1}},"BackgroundColor":{"type":"color","value":{"a":0.2}}}"""))
        assertTrue(settings(existing, "Crosshair").has("color-color"))
        assertTrue(settings(existing, "Crosshair").has("background-color"))
    }

    @Test
    fun `maps all eight missing HUD aliases and preserves placement`() {
        val pairs = mapOf(
            "ClockDisplay" to "clock", "PerformanceHUD" to "performance",
            "PlayTime" to "play_time", "SaturationDisplay" to "saturation",
            "ServerAddressDisplay" to "server_address", "LyricsDisplay" to "lyrics",
            "TNTTimerHUD" to "tnt_timer", "DamageIndicatorHUD" to "damage_indicator"
        )
        val source = profile("Sprint", "{}")
        source.add("components", JsonArray().apply {
            pairs.keys.forEach { add(json("""{"module":"$it","x":0.2,"y":0.3,"scale":1.5,"position":"RB"}""")) }
        })
        val result = ConfigManager.prepareEdgeImport(source)
        result.root.getAsJsonArray("components").forEachIndexed { index, element ->
            val component = element.asJsonObject
            assertEquals(pairs.values.toList()[index], component.get("module").asString)
            assertEquals(0.2, component.get("x").asDouble)
            assertEquals(1.5, component.get("scale").asDouble)
            assertEquals("RB", component.get("position").asString)
        }
    }

    @Test
    fun `maps only explicitly known item and metadata pairs`() {
        val result = ConfigManager.prepareEdgeImport(profile("ItemCountDisplay", """{
            "Items":{"type":"multiItem","value":[
                {"id":262,"meta":0},{"id":322,"meta":0},{"id":322,"meta":1},{"id":368,"meta":0},
                {"id":373,"meta":16421},{"id":999,"meta":0},{"id":322,"meta":2},
                {"id":262},{"id":262.5,"meta":0}
            ]}
        }"""))
        val items = settings(result, "ItemCountDisplay").getAsJsonObject("items").getAsJsonArray("value")
        assertEquals(listOf("minecraft:arrow", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
            "minecraft:ender_pearl"), items.map(JsonElement::getAsString))
        assertEquals(5, result.issues.size)
        assertTrue(result.issues.all { it.reason == "unsupported-item-or-metadata" })
    }

    @Test
    fun `preserves current items if all legacy items are rejected but allows explicit empty list`() {
        val rejected = ConfigManager.prepareEdgeImport(profile("ItemCountDisplay",
            """{"Items":{"type":"multiItem","value":[{"id":373,"meta":0}]}}"""))
        assertFalse(settings(rejected, "ItemCountDisplay").has("items"))
        assertEquals(1, rejected.issues.size)
        val empty = ConfigManager.prepareEdgeImport(profile("ItemCountDisplay",
            """{"Items":{"type":"multiItem","value":[]}}"""))
        assertEquals(0, settings(empty, "ItemCountDisplay").getAsJsonObject("items").getAsJsonArray("value").size())
    }

    @Test
    fun `reports incompatible optimization names including fallback spellings`() {
        val names = listOf("IgnoreStands", "IgnoreArmorStand", "EntitiesOptimize", "EntityCulling",
            "ParticlesLimit", "ParticleLimitation")
        val input = JsonObject().apply {
            names.forEach { add(it, json("""{"type":"boolean","value":true}""")) }
            add("FPSLimit", json("""{"type":"number","value":30}"""))
        }
        val result = ConfigManager.prepareEdgeImport(profile("Performance", input.toString()))
        assertEquals(setOf("fpslosingfocus"), settings(result, "Performance").keySet())
        assertEquals(6, result.issues.size)
        assertTrue(result.issues.all { it.reason == "incompatible-semantics" })
    }

    @Test
    fun `does not redirect removed aliases to dead targets`() {
        val optimization = ConfigManager.prepareEdgeImport(profile("Performance",
            """{"FontOptimize":{"type":"boolean","value":true}}"""))
        assertFalse(settings(optimization, "Performance").has("fontoptimization"))
        val animation = ConfigManager.prepareEdgeImport(profile("OldAnimations",
            """{"OldBlock":{"type":"boolean","value":true}}"""))
        assertFalse(settings(animation, "OldAnimations").has("oldblocking"))
        val renamed = ConfigManager.prepareEdgeImport(profile("NameProtect",
            """{"Name":{"type":"text","value":"Alias"}}"""))
        assertEquals("Alias", settings(renamed, "NameProtect").getAsJsonObject("replacement").get("value").asString)
    }
}
