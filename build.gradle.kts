import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm") version "2.4.0"
    // 26.x needs the Loom 1.17 line (26.2 ships Java 25 bytecode + is unobfuscated). We pin the stable
    // release; unobfuscation is opted into per-node via the `fabric.loom.disableObfuscation` Gradle
    // property (set for 26.x in settings.gradle.kts) rather than relying on the newer dev-snapshot's
    // auto-detection. Still builds the obfuscated 1.x nodes with their Mojang-mapped layered mappings.
    id("fabric-loom") version "1.17.14"
    id("maven-publish")
    kotlin("plugin.lombok") version "2.4.0"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

// Per-version build parameters, selected by the Stonecutter version node currently building.
data class VersionSpec(
    val loader: String,
    val api: String,
    val parchment: String?,
    val java: Int,
)

val mcVersion: String = stonecutter.current.version
val spec: VersionSpec = when (mcVersion) {
    // 26.2: Mojang's new year-based scheme (26.x = 2026). Post-1.21.11 render era (submit-node) plus the
    // 26.2 breaking changes (Blaze3D/Vulkan backend, gui.setScreen, BlockIds/ItemIds registry split).
    // No Parchment published for 26.2 yet → null (Mojang official names only). Requires Loom 1.17 and,
    // unlike every prior version, a JDK 25 toolchain (MC 26.2 ships Java 25 bytecode).
    "26.2" -> VersionSpec("0.19.3", "0.156.0+26.2", null, 25)
    "1.21.11" -> VersionSpec("0.19.3", "0.141.6+1.21.11", "org.parchmentmc.data:parchment-1.21.11:2025.12.20@zip", 21)
    "1.21.8" -> VersionSpec("0.19.3", "0.136.1+1.21.8", null, 21)
    "1.21.1" -> VersionSpec("0.16.14", "0.116.15+1.21.1", null, 21)
    "1.20.1" -> VersionSpec("0.16.14", "0.92.11+1.20.1", "org.parchmentmc.data:parchment-1.20.1:2023.09.03@zip", 17)
    "1.19.2" -> VersionSpec("0.16.14", "0.77.0+1.19.2", null, 17)
    else -> error("Unsupported Minecraft version: $mcVersion")
}
// MC 26.x (Mojang's year-based scheme) ships an *unobfuscated* game — Mojang no longer publishes
// client_mappings, and the jar already carries real names. Loom 1.17 consumes it with NO `mappings`
// dependency (the official fabric-example-mod for 26.x omits it too). The old Mojang-mapping (mojmap)
// names the codebase is written against are exactly these unobfuscated names, so source mostly matches
// bar the 26.2 API deltas (handled via `//? if >=26.2` swaps).
val isUnobfuscated = (mcVersion.substringBefore('.').toIntOrNull() ?: 0) >= 26
// Versions predating the 1.21.5 render rewrite share the 1.20.1 "legacy render" config: immediate-mode
// CEF path, the 1.20.1 access widener (no RenderPipeline AW) and the 1.20.1 mixin subset. 1.21.5+ use
// the modern config (GuiRenderState/RenderPipeline). Keep this set in sync with the >=1.21.5 swaps.
val isLegacyRender = mcVersion in setOf("1.19.2", "1.20.1", "1.21.1")
// The custom-width composite RenderType helpers (FpsmasterFishingLine/FpsmasterBlockOverlay) use the
val usesLegacyHelpers = mcVersion in setOf("1.19.2", "1.20.1", "1.21.1", "1.21.8")
// Per-version mixin config. Strategy: prioritise HUD/UI; complex render-pipeline mixins are gated off
// on versions where they'd need a bespoke variant (kept simple to move fast). 1.21.1 reuses the legacy
// subset minus the helper-dependent render mixins.
val mixinConfig = when (mcVersion) {
    "26.2" -> "fpsmaster-26.2.mixins.json"
    "1.21.1" -> "fpsmaster-1.21.1.mixins.json"
    "1.21.8" -> "fpsmaster-1.21.8.mixins.json"
    "1.19.2" -> "fpsmaster-1.19.2.mixins.json"
    else -> if (isLegacyRender) "fpsmaster-1.20.1.mixins.json" else "fpsmaster.mixins.json"
}
// The unobfuscated 26.x node needs its access widener in the `official` namespace (same members as the
// modern named AW, different header); the obfuscated nodes use the `named` variants.
val accessWidenerFile = when {
    isUnobfuscated -> "src/main/resources/fpsmaster-26.2.accesswidener"
    // 1.21.1 shares the legacy render bridge but 1.21 changed EntityRenderer.renderNameTag's
    // signature, so it needs the 1.20.1 widener minus that (now unresolvable, gated-out) entry.
    mcVersion == "1.21.1" -> "src/main/resources/fpsmaster-1.21.1.accesswidener"
    isLegacyRender -> "src/main/resources/fpsmaster-1.20.1.accesswidener"
    else -> "src/main/resources/fpsmaster.accesswidener"
}

val targetJavaVersion = spec.java
java {
    // Compile with a JDK toolchain >= the bytecode target: JDK 21 for the 1.x versions (Java 17/21
    // targets cross-compile down via release/jvmTarget below, so 1.20.1 still emits Java 17 without a
    // JDK 17), and JDK 25 for MC 26.2, whose class files are Java 25 and can't be read by a 21 compiler.
    toolchain.languageVersion = JavaLanguageVersion.of(maxOf(spec.java, 21))
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

val bundledRuntime by configurations.creating {
    isTransitive = true
}

val viaFabricOfficial by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val viaVersionOfficial by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val viaFabricPlusOfficial by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

loom {
    accessWidenerPath = rootProject.file(accessWidenerFile)
}

// Full multi-version migration: the entire codebase compiles on both 1.20.1 and 1.21.11. Per-version
// API differences are handled inline via Stonecutter //? swaps; mixins that target the 1.21.5+ render
// rewrite are gated off on 1.20.1 (see fpsmaster-1.20.1.mixins.json for the applicable subset).
// These three files are the 1.21.5+ GuiRenderState/TextureSetup CEF render bridge (used only by the
// >=1.21.5 branch of ClientBrowser). They carry license/Javadoc block comments, so they cannot be
// Stonecutter-gated (one-way gating wraps content in /* */, which breaks on nested block comments);
// they have no 1.20.1 equivalent, so they are excluded from the 1.20.1 source set instead.
sourceSets.named("main") {
    // 1.21.5+ GuiRenderState/TextureSetup CEF render bridge — only on the modern (1.21.5..1.21.11) era.
    // Legacy lacks it; 26.2's deferred-render rewrite changed the submit-node/render-state API, so the
    // accelerated web-UI draw is deferred there too (the CEF quad draw is gated off in ClientBrowser).
    if (isLegacyRender || isUnobfuscated) {
        java.exclude(
            "top/fpsmaster/web/GuiElementRenderState.java",
            "top/fpsmaster/web/TexQuadGuiElementRenderState.java",
            "top/fpsmaster/web/cef/BrowserDirectTexture.java"
        )
    }
    // 26.2 (unobfuscated, deferred-render rewrite): the render/screen mixins all have 26.2 variants now,
    // so only the two genuinely 26-incompatible helpers stay excluded. IGuiGraphics is the 1.21.5..1.21.11
    // submit-node accessor (26.2 uses IGuiGraphicsExtractor instead), and the RenderTypes helper builds
    // pre-26 composite RenderTypes that 26.2's submitHitOutline/submitBlockOutline path does not use.
    if (isUnobfuscated) {
        java.exclude(
            "top/fpsmaster/mixin/interfaces/IGuiGraphics.java",
            "top/fpsmaster/render/FpsmasterBlockOverlayRenderTypes.java"
        )
    }
    // GuiGraphics26 shim references GuiGraphicsExtractor (26.x only) — keep it off the obfuscated
    // (pre-26) source sets; it is the active GuiGraphics on 26.x via the import alias.
    if (!isUnobfuscated) {
        kotlin.exclude("top/fpsmaster/compat/GuiGraphics26.kt")
    }
    // Composite-RenderType helpers use the pre-1.20.5 API; exclude everywhere except 1.20.1/1.19.2.
    if (!usesLegacyHelpers) {
        java.exclude("net/minecraft/client/renderer/FpsmasterFishingLine.java")
        java.exclude("net/minecraft/client/renderer/FpsmasterBlockOverlay.java")
    }
    // 1.19.2 GuiGraphics shim (GuiGraphics is 1.20+). Only compiled on pre-1.20 versions.
    if (mcVersion != "1.19.2") {
        java.exclude("top/fpsmaster/compat/GuiGraphics.java")
    }
    // 1.19.2 / 1.21.1 / 1.21.8 product-gap excludes removed; render adapters compile on these eras.


}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    mavenLocal() // local mcef-nova iteration (./gradlew publishToMavenLocal in the mcef-nova repo)
    // Transitional offline fallback: a checked-in copy of the private mcef-nova artifact, resolvable
    // with no credentials. Kept in sync on each mcef version bump. Once GitHub Packages access is
    // confirmed working in CI, this and the vendor/maven dir can be removed.
    maven { url = uri("${rootDir}/vendor/maven") }
    // Private mcef-nova distribution (GitHub Packages). CI injects the built-in GITHUB_ACTOR +
    // GITHUB_TOKEN (needs `packages: read` and the package granted access to this repo); locally, set
    // gpr.user / gpr.key (a PAT with read:packages) in ~/.gradle/gradle.properties.
    maven {
        url = uri("https://maven.pkg.github.com/FPSMasterTeam/mcef-nova")
        // Only this group lives here. Without a filter Gradle probes the repo for every
        // coordinate (including top.fpsmaster:ui), and a null username/password pair
        // crashes resolution with "Username must not be null!".
        content {
            includeGroup("com.github.FPSMasterTeam")
        }
        val gprUser = (project.findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
        val gprKey = (project.findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
        if (!gprUser.isNullOrBlank() && !gprKey.isNullOrBlank()) {
            credentials {
                username = gprUser
                password = gprKey
            }
        }
    }
    maven("https://jitpack.io")
    maven("https://maven.parchmentmc.org")
    maven("https://repo.viaversion.com/")
    maven("https://api.modrinth.com/maven")
    mavenCentral()

}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$mcVersion")
    // Unobfuscated 26.x: NO `mappings` dependency. With fabric.loom.disableObfuscation=true (set for
    // this node in settings.gradle.kts) Loom registers neither the `mappings` nor the remap
    // configurations (modImplementation/...), so we must reference those by string name via add(...)
    // rather than the type-safe accessors, which aren't generated for this node. Obfuscated 1.x:
    // official Mojang names + optional Parchment param names, and the loader is remapped.
    if (!isUnobfuscated) {
        add("mappings", loom.layered {
            officialMojangMappings()
            spec.parchment?.let { parchment(it) }
        })
    }
    // Remapping is a no-op on the unobfuscated node — the loader goes on plain `implementation` there
    // (matching the fabric-example-mod), and `modImplementation` on the obfuscated nodes.
    add(if (isUnobfuscated) "implementation" else "modImplementation", "net.fabricmc:fabric-loader:${spec.loader}")
    // Nova does not call Fabric API. Keep the umbrella on the ordinary runClient
    // classpath only. `-PwithViaFabric` must not pull fabric-registry-sync-v0:
    // ViaFabric's MixinRegistrySyncManager (remap=false, intermediary MC types in
    // the injector) fails mixin apply against named/Loom, and excluding that one
    // module from the umbrella breaks other FAPI modules that depend on it.
    // ViaFabric-mc* only requires fabric-resource-loader-v0.
    if (project.hasProperty("withViaFabric") && project.hasProperty("withViaFabricPlus")) {
        error("ViaFabric and ViaFabricPlus break each other; use only one of -PwithViaFabric / -PwithViaFabricPlus")
    }
    if (project.hasProperty("withViaFabric")) {
        val resourceLoader = when (mcVersion) {
            "26.2" -> "3.3.20+4fc5413f9e"
            "1.21.11" -> "3.3.4+4fc5413f3e"
            "1.21.8" -> "3.1.12+020423442c"
            "1.21.1" -> "1.3.1+5b5275af19"
            "1.20.1" -> "0.11.12+fb82e9d777"
            "1.19.2" -> "0.8.4+edbdcddb90"
            else -> error("No fabric-resource-loader-v0 pin for $mcVersion")
        }
        add(
            if (isUnobfuscated) "implementation" else "modRuntimeOnly",
            "net.fabricmc.fabric-api:fabric-resource-loader-v0:$resourceLoader"
        )
    } else if (project.hasProperty("withViaFabricPlus")) {
        // Plus nests the Fabric API modules it depends on (including
        // fabric-registry-sync-v0). Do not also pull the umbrella — duplicate
        // module ids fail loader resolution.
    } else {
        add(if (isUnobfuscated) "implementation" else "modRuntimeOnly", "net.fabricmc.fabric-api:fabric-api:${spec.api}")
    }

    // Version-agnostic CEF fork: a plain library (no net.minecraft), so no Loom remapping.
    implementation("com.github.FPSMasterTeam:mcef-nova:1.0.1")

//    modRuntimeOnly(group = "maven.modrinth", name = "ImmediatelyFast", version = "1.14.2+1.21.11-fabric")
//    modApi(group = "maven.modrinth", name = "sodium", version = "mc1.21.11-0.8.12-fabric")
//    modApi(group = "com.viaversion", name = "viafabricplus-api", version = "4.4.1")
//    modRuntimeOnly(group = "com.viaversion", name = "viafabricplus", version = "4.4.1")

    // Optional ViaFabric on the runClient classpath (player-style install). Enable with
    // `-PwithViaFabric`. Versions are the latest Modrinth artifacts that actually list each
    // Nova MC version; current ViaFabric no longer publishes nodes for 1.19.2 / 1.20.1 /
    // 1.21.1 / 1.21.8, so those use the last artifact that did.
    val viaFabricVersion: String? = when (mcVersion) {
        "1.21.11" -> "0.4.21+181-1.14-1.21"
        "26.2" -> "0.4.21+182-26.x"
        "1.21.8" -> "0.4.19+118-main"
        "1.21.1" -> "0.4.15+84-main"
        "1.20.1" -> "0.4.18+109-main"
        "1.19.2" -> "0.4.9+21-main"
        else -> null
    }
    if (project.hasProperty("withViaFabric") && viaFabricVersion != null) {
        // Do not use modRuntimeOnly: Loom remaps those artifacts and strips nested
        // jars (Fabric docs). That drops ViaVersion and leaves viafabric-mc* in
        // intermediary, so named runClient dies with ClassNotFoundException
        // (class_1132). Copy the official jar into run/mods like a player would;
        // Fabric Loader then remaps it with development mappings.
        viaFabricOfficial("maven.modrinth:viafabric:$viaFabricVersion")
        // ViaFabric 0.4.15+ nests a Java-8-downgraded ViaVersion whose jvmdg
        // stubs crash on Java 21 (NoSuchMethodError Runtime.Version.feature).
        // ViaVersion's own install docs: put ViaVersion in mods/ to override.
        // 0.4.9 (1.19.2) still nests ViaVersion 4.x; leave that JiJ alone.
        if (mcVersion != "1.19.2") {
            viaVersionOfficial("maven.modrinth:viaversion:5.12.0-SNAPSHOT+1053")
        }
    }

    // Optional ViaFabricPlus on run/mods (player-style). Mutually exclusive with
    // ViaFabric (`viafabricplus` breaks `viafabric`). Versions are the latest
    // Modrinth artifacts that actually list each Nova MC version. 1.19.2 has none.
    val viaFabricPlusVersion: String? = when (mcVersion) {
        "26.2" -> "4.6.2"
        "1.21.11" -> "4.4.15"
        "1.21.8" -> "4.2.5"
        "1.21.1" -> "3.4.9"
        "1.20.1" -> "2.8.7"
        else -> null
    }
    if (project.hasProperty("withViaFabricPlus") && viaFabricPlusVersion != null) {
        viaFabricPlusOfficial("maven.modrinth:viafabricplus:$viaFabricPlusVersion")
    }

    implementation("com.google.code.gson:gson:2.14.0")
    // Cadence：网易云/QQ 音乐数据客户端（原 top.fpsmaster.web.music 抽出，JitPack 托管）。
    // 纯 JDK + gson 实现，不含 net.minecraft，故无需 Loom remap。
    implementation("com.github.FPSMasterTeam:Cadence:v0.1.1")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("top.fpsmaster:prism:0.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    implementation("io.github.vlouboos:standaloneevent-common:1.6")
    bundledRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    bundledRuntime("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.0")
    bundledRuntime("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.0")
    bundledRuntime("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    bundledRuntime("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    bundledRuntime("com.github.FPSMasterTeam:mcef-nova:1.0.1")
    bundledRuntime("io.github.vlouboos:standaloneevent-common:1.6")
    // 只打 Cadence 自己的类：它的两个传递依赖 kotlin-stdlib（上面已 bundle）与 gson（MC 自带）
    // 都已在运行时就位，transitive 打进来只会重复/覆盖。
    bundledRuntime("com.github.FPSMasterTeam:Cadence:v0.1.1") { isTransitive = false }
    bundledRuntime("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    bundledRuntime("top.fpsmaster:prism:0.2.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Optional in-game IME positioning (see docs/ime-support.md). GLFW's preedit APIs
// (glfwSetPreeditCursorRectangle / glfwSetPreeditCallback, etc.) live in libglfw and require
// LWJGL-glfw >= 3.3.4; MC 1.21.11 ships 3.3.3. Bump ONLY lwjgl-glfw to 3.3.4 (jar + natives in
// lockstep) and leave the rest of Mojang's exact runtime alone — in particular lwjgl-freetype,
// which Mojang ships under a custom `natives-macos-patch` classifier that stock LWJGL 3.3.4 does
// not publish (forcing it breaks resolution). LWJGL modules don't cross-check patch versions at
// runtime, so glfw 3.3.4 coexists with core 3.3.3. Only the modern (>=1.21.5) versions carry the
// preedit code (the call sites are Stonecutter-gated), so only they get the bump; legacy-render
// versions keep MC's bundled LWJGL. 26.2 is EXCLUDED: it ships LWJGL 3.4.1-snapshot (which already has
// the preedit API), and forcing glfw down to 3.3.4 there mismatches 3.4.1 core — the LWJGL Callback API
// changed, so GLFWErrorCapture.getDescriptor() becomes abstract → AbstractMethodError at GLFW init.
if (!isLegacyRender && !isUnobfuscated) {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.lwjgl" && requested.name == "lwjgl-glfw") {
                useVersion("3.3.4")
                because("GLFW IME preedit API (glfwSetPreeditCursorRectangle) needs lwjgl-glfw >= 3.3.4")
            }
        }
    }
}

// MC 26.2 bumped netty to 4.2, whose Gradle module metadata makes the transitive
// netty-transport-native-epoll (pulled in by netty-transport-classes-epoll) resolve a platform-native
// variant — e.g. `linux-riscv64` — that Mojang's maven mirror doesn't host, breaking runClient on
// non-Linux (and even on unlisted Linux arches). Minecraft pins the correct per-OS natives directly
// (kqueue on macOS, epoll x86_64/aarch_64 on Linux) via the version manifest, and netty falls back to
// NIO where a native is absent, so we drop the metadata-driven transitive epoll native. 26.2-only —
// older versions ship netty 4.1 without these riscv64 variants.
if (isUnobfuscated) {
    configurations.all {
        exclude(group = "io.netty", module = "netty-transport-native-epoll")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", mcVersion)
    inputs.property("loader_version", spec.loader)
    inputs.property("api_version", spec.api)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to mcVersion,
            "loader_version" to spec.loader,
            "api_version" to spec.api,
            "kotlin_loader_version" to project.property("kotlin_loader_version").toString(),
            "mixins_config" to mixinConfig,
            "access_widener" to accessWidenerFile.substringAfterLast('/')
        )
    }

    from({
        bundledRuntime
            .filter { it.name.startsWith("prism-") }
            .map { zipTree(it) }
    }) {
        include("assets/fpsmaster/textures/gui/icons/**")
    }
}

fun stripNestedViaVersion(source: File, dest: File) {
    ZipFile(source).use { zip ->
        val fabricEntry = zip.getEntry("fabric.mod.json") ?: run {
            source.copyTo(dest, overwrite = true)
            return
        }
        val original = zip.getInputStream(fabricEntry).bufferedReader().readText()
        val stripped = original.replace(
            Regex("""\{\s*"file"\s*:\s*"META-INF/jars/viaversion[^"]+"\s*\}\s*,?\s*"""),
            ""
        ).replace(Regex(""",\s*]"""), "]")
        ZipOutputStream(dest.outputStream()).use { out ->
            val buffer = ByteArray(16 * 1024)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("META-INF/jars/viaversion") && entry.name.endsWith(".jar")) {
                    continue
                }
                out.putNextEntry(ZipEntry(entry.name))
                if (entry.name == "fabric.mod.json") {
                    out.write(stripped.toByteArray(Charsets.UTF_8))
                } else if (!entry.isDirectory) {
                    zip.getInputStream(entry).use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
                out.closeEntry()
            }
        }
    }
}

afterEvaluate {
    // Loom forks the game in its own JVM, so -Dfpsmaster.* on the Gradle command line would otherwise
    // only reach the daemon. Forward them so the documented acceptance command works as written:
    //   ./gradlew :<ver>:runClient -Dfpsmaster.api.baseUrl=http://127.0.0.1:8722 -Dfpsmaster.smoke=true
    tasks.withType<JavaExec>().configureEach {
        System.getProperties().stringPropertyNames()
            .filter { it.startsWith("fpsmaster.") }
            .forEach { systemProperty(it, System.getProperty(it)) }
    }
    tasks.findByName("runClient")?.doFirst {
        val withVia = project.hasProperty("withViaFabric")
        val withPlus = project.hasProperty("withViaFabricPlus")
        if (!withVia && !withPlus) {
            return@doFirst
        }
        val modsDir = project.file("run/mods")
        modsDir.mkdirs()
        modsDir.listFiles()
            ?.filter { it.isFile && (
                it.name.contains("viafabric", ignoreCase = true) ||
                    it.name.contains("viaversion", ignoreCase = true)
                ) }
            ?.forEach { it.delete() }
        if (withVia) {
            val overrideViaVersion = viaVersionOfficial.files.any { it.isFile }
            viaFabricOfficial.files.filter { it.isFile }.forEach { jar ->
                val dest = modsDir.resolve(jar.name)
                if (overrideViaVersion) {
                    stripNestedViaVersion(jar, dest)
                } else {
                    jar.copyTo(dest, overwrite = true)
                }
            }
            viaVersionOfficial.files.filter { it.isFile }.forEach { jar ->
                jar.copyTo(modsDir.resolve(jar.name), overwrite = true)
            }
        }
        if (withPlus) {
            val plusJars = viaFabricPlusOfficial.files.filter { it.isFile }
            check(plusJars.isNotEmpty()) {
                "ViaFabricPlus does not publish a jar that lists Minecraft $mcVersion"
            }
            plusJars.forEach { jar ->
                jar.copyTo(modsDir.resolve(jar.name), overwrite = true)
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from({
        bundledRuntime.map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    }) {
        exclude(
            "META-INF/MANIFEST.MF",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "fabric.mod.json",
            "*.mixins.json",
            "*.accesswidener"
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(rootProject.file("LICENSE")) {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
