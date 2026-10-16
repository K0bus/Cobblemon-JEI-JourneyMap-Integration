pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
        }
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
    }
}

rootProject.name = "Cobblemon_JEI"

include("common")
include("neoforge")
include("fabric")
