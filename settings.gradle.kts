pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.kikugie.dev/releases") {
            name = "KikuGie"
        }
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

stonecutter {
    create(rootProject) {
        versions("1.19.2", "1.20.1", "1.21.1", "1.21.8", "1.21.11", "26.2")
        vcsVersion = "1.21.11"
    }
}

// MC 26.x (Mojang's year-based scheme) ships an unobfuscated game. Loom 1.17.14 only enters that mode
// via the `fabric.loom.disableObfuscation` Gradle property, which it reads (through project.findProperty)
// while the plugin applies — i.e. before the version node's build script runs. So we must set it here,
// in beforeProject (fires before each node's build.gradle.kts is evaluated), and only for the >=26
// year-scheme nodes; the obfuscated 1.x nodes keep their normal Mojang-mapped remapping.
gradle.beforeProject {
    val major = name.substringBefore('.').toIntOrNull() ?: 0
    if (major >= 26) {
        extensions.extraProperties.set("fabric.loom.disableObfuscation", "true")
    }
}
