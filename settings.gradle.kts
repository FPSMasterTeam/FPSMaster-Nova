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
        versions("1.19.2", "1.20.1", "1.21.1", "1.21.8", "1.21.11")
        vcsVersion = "1.21.11"
    }
}
