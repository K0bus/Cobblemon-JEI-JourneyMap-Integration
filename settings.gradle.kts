pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
        }
    }
}

rootProject.name = "Cobblemon_JEI"

include("common")
include("neoforge")
