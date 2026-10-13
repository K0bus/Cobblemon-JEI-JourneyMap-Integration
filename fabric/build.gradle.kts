plugins {
    id("fabric-loom")
    kotlin("jvm")
}

val minecraftVersion = property("minecraft_version") as String
val loaderVersion = property("loader_version") as String
val fabricVersion = property("fabric_version") as String
val fabricKotlinVersion = property("fabric_kotlin_version") as String
val cobblemonVersion = property("cobblemon_version") as String
val jeiVersion = property("jei_version") as String

base.archivesName.set("${property("archives_base_name")}-fabric")

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    modImplementation("com.cobblemon:fabric:$cobblemonVersion")
    modCompileOnly("mezz.jei:jei-1.21.1-fabric-api:$jeiVersion")
    modRuntimeOnly("mezz.jei:jei-1.21.1-fabric:$jeiVersion")

    implementation(project(":common"))
}

sourceSets.main.get().kotlin.srcDirs(project(":common").file("src/main/kotlin"))
sourceSets.main.get().resources.srcDirs(project(":common").file("src/main/resources"))

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

kotlin {
    jvmToolchain(21)
}
