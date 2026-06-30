import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.0"
    id("fabric-loom") version "1.16.1"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

// Per-version build parameters, selected by the Stonecutter version node currently building.
data class VersionSpec(
    val loader: String,
    val parchment: String?,
    val java: Int,
)
val mcVersion: String = stonecutter.current.version
val spec: VersionSpec = when (mcVersion) {
    "1.21.11" -> VersionSpec("0.19.3", "org.parchmentmc.data:parchment-1.21.11:2025.12.20@zip", 21)
    "1.20.1" -> VersionSpec("0.16.14", "org.parchmentmc.data:parchment-1.20.1:2023.09.03@zip", 17)
    else -> error("Unsupported Minecraft version: $mcVersion")
}
// 1.20.1 predates the 1.21.5 render rewrite, so the RenderPipeline access widener is invalid there.
val accessWidenerFile = if (mcVersion == "1.20.1") "src/main/resources/fpsmaster-1.20.1.accesswidener"
                        else "src/main/resources/fpsmaster.accesswidener"

val targetJavaVersion = spec.java
java {
    // Compile every version with the installed JDK 21 toolchain; bytecode target is per-version
    // (release/jvmTarget below), so 1.20.1 still emits Java 17 classes without needing a JDK 17.
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

val bundledRuntime by configurations.creating {
    isTransitive = true
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
    if (mcVersion == "1.20.1") {
        java.exclude(
            "top/fpsmaster/web/GuiElementRenderState.java",
            "top/fpsmaster/web/TexQuadGuiElementRenderState.java",
            "top/fpsmaster/web/cef/BrowserDirectTexture.java"
        )
    } else {
        // 1.20.1-only helper that assembles a custom-width line render type via the legacy
        // RenderType composite API (lives in the renderer package to reach protected members).
        java.exclude("net/minecraft/client/renderer/FpsmasterFishingLine.java")
    }
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    mavenLocal() // version-agnostic CEF fork (FPSMasterTeam/mcef-nova) during development
    maven("https://jitpack.io")
    maven("https://maven.parchmentmc.org")
    maven("https://repo.viaversion.com/")
    maven("https://api.modrinth.com/maven")

}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.layered {
        officialMojangMappings()
        spec.parchment?.let { parchment(it) }
    })
    modImplementation("net.fabricmc:fabric-loader:${spec.loader}")
    // Version-agnostic CEF fork: a plain library (no net.minecraft), so no Loom remapping.
    implementation("com.github.FPSMasterTeam:mcef-nova:1.0.0")

//    modRuntimeOnly(group = "maven.modrinth", name = "ImmediatelyFast", version = "1.14.2+1.21.11-fabric")
//    modApi(group = "maven.modrinth", name = "sodium", version = "mc1.21.11-0.8.12-fabric")
//    modApi(group = "com.viaversion", name = "viafabricplus-api", version = "4.4.1")
//    modRuntimeOnly(group = "com.viaversion", name = "viafabricplus", version = "4.4.1")

    implementation("io.netty:netty-all:4.1.135.Final")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    implementation("io.github.vlouboos:standaloneevent-common:1.3")
    bundledRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    bundledRuntime("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.0")
    bundledRuntime("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.0")
    bundledRuntime("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    bundledRuntime("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
    bundledRuntime("com.github.FPSMasterTeam:mcef-nova:1.0.0")
    bundledRuntime("io.github.vlouboos:standaloneevent-common:1.3")
}

val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val buildUi by tasks.registering(Exec::class) {
    workingDir = rootProject.file("ui")
    commandLine(npmCommand, "run", "build")

    inputs.files(rootProject.fileTree("ui") {
        exclude("node_modules/**", "dist/**")
    })
    outputs.dir(rootProject.file("ui/dist"))
}

tasks.processResources {
    dependsOn(buildUi)

    inputs.property("version", project.version)
    inputs.property("minecraft_version", mcVersion)
    inputs.property("loader_version", spec.loader)
    filteringCharset = "UTF-8"

    from(rootProject.file("ui/dist")) {
        into("webui")
    }

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to mcVersion,
            "loader_version" to spec.loader,
            "kotlin_loader_version" to project.property("kotlin_loader_version").toString(),
            "mixins_config" to if (mcVersion == "1.20.1") "fpsmaster-1.20.1.mixins.json" else "fpsmaster.mixins.json",
            "access_widener" to if (mcVersion == "1.20.1") "fpsmaster-1.20.1.accesswidener" else "fpsmaster.accesswidener"
        )
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
