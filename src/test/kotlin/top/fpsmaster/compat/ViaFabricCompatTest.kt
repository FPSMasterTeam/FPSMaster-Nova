package top.fpsmaster.compat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViaFabricCompatTest {
    private val repoRoot: File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "src/main/resources/fabric.mod.json").isFile }

    @Test
    fun publishedModMetadataDoesNotForceFabricApi() {
        val fabricModJson = File(repoRoot, "src/main/resources/fabric.mod.json").readText()
        assertFalse(
            fabricModJson.contains("\"fabric-api\""),
            "Nova must not hard-depend on fabric-api; the umbrella pulls fabric-registry-sync-v0, " +
                "which ViaFabric documents as incompatible and whose MixinRegistrySyncManager " +
                "crashes mixin apply in named/Loom environments"
        )
        assertTrue(fabricModJson.contains("\"viafabric\""), "Suggest ViaFabric so launchers surface compat")
        assertTrue(fabricModJson.contains("\"viaversion\""), "Suggest ViaVersion so players override ViaFabric's Java-8 JiJ")
        assertTrue(fabricModJson.contains("\"viafabricplus\""), "Suggest ViaFabricPlus as the alternative (breaks ViaFabric)")
    }

    @Test
    fun novaMixinsDoNotTargetViaFabricHandshakeOrPipeline() {
        val mixinDir = File(repoRoot, "src/main/resources")
        val mixinConfigs = mixinDir.listFiles { file -> file.name.endsWith(".mixins.json") }.orEmpty()
        assertTrue(mixinConfigs.isNotEmpty(), "Expected mixin configs under src/main/resources")

        val forbidden = listOf(
            "MixinConnection",
            "MixinClientIntentionPacket",
            "MixinServerAddress",
            "MixinRegistrySyncManager"
        )
        mixinConfigs.forEach { config ->
            val text = config.readText()
            forbidden.forEach { name ->
                assertFalse(
                    text.contains("\"$name\""),
                    "${config.name} must not claim $name (ViaFabric owns handshake/pipeline/registry-sync)"
                )
            }
        }
    }

    @Test
    fun mixinConfigsThatClaimLivingEntityDeclareTheCompatPlugin() {
        val mixinDir = File(repoRoot, "src/main/resources")
        val mixinConfigs = mixinDir.listFiles { file -> file.name.endsWith(".mixins.json") }.orEmpty()
        mixinConfigs.forEach { config ->
            val text = config.readText()
            if (text.contains("\"MixinLivingEntity\"")) {
                assertTrue(
                    text.contains("\"plugin\": \"top.fpsmaster.mixin.FpsmasterMixinPlugin\""),
                    "${config.name} lists MixinLivingEntity and must skip it when ViaFabricPlus is loaded"
                )
            }
        }
        assertTrue(
            File(repoRoot, "src/main/java/top/fpsmaster/mixin/FpsmasterMixinPlugin.java").isFile,
            "FpsmasterMixinPlugin must exist so ViaFabricPlus visuals can own LivingEntity.tick -> Mth.abs"
        )
    }
}
