plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
}

allprojects {
    group = property("maven_group") as String
    version = property("mod_version") as String

    repositories {
        mavenCentral()
        maven {
            name = "ImpactDev"
            url = uri("https://maven.impactdev.net/repository/development/")
        }
        maven {
            name = "ModMaven"
            url = uri("https://modmaven.dev")
        }
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
        maven {
            name = "JourneyMap"
            url = uri("https://jm.gserv.me/repository/maven-public")
        }
    }
}
