plugins {
    id("com.palantir.git-version") version "3.1.0"
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.darkhax.curseforgegradle") version "1.1.25" apply false
}

val gitVersion: groovy.lang.Closure<String> by extra

fun computeVersion(): String {
    val raw = gitVersion().trim().removePrefix("v").removePrefix("V")
    val cleanRaw = raw.removeSuffix(".dirty")
    val isHashOnly = cleanRaw.matches(Regex("^[0-9a-f]{7,40}$"))
    return if (isHashOnly) {
        "0.1.0-SNAPSHOT+$cleanRaw"
    } else {
        val match = Regex("""^(.+?)-(\d+)-g([0-9a-f]+)$""").find(cleanRaw)
        if (match != null) {
            val (base, distance, hash) = match.destructured
            "$base-SNAPSHOT+$distance.$hash"
        } else {
            cleanRaw
        }
    }
}

val projectVersion = computeVersion()

group = "fr.k0bus"
version = projectVersion

val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    java.util.Properties().apply {
        localPropertiesFile.inputStream().use { load(it) }
        forEach { (k, v) ->
            val keyStr = k.toString()
            val valStr = v.toString()
            rootProject.extensions.extraProperties.set(keyStr, valStr)
            subprojects {
                extensions.extraProperties.set(keyStr, valStr)
            }
        }
    }
}

subprojects {
    group = "fr.k0bus"
    version = rootProject.version

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
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
        }
    }

    tasks.withType<ProcessResources>().configureEach {
        inputs.property("version", project.version)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand("version" to project.version)
        }
    }
}

tasks.register("curseforge") {
    group = "publishing"
    description = "Publishes both NeoForge and Fabric artifacts to CurseForge."
    dependsOn(":neoforge:curseforge", ":fabric:curseforge")
}
