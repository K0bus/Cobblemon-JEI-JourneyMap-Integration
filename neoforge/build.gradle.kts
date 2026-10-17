plugins {
    id("net.neoforged.moddev")
    kotlin("jvm")
}

val neoforgeVersion = property("neoforge_version") as String
val cobblemonVersion = property("cobblemon_version") as String
val jeiVersion = property("jei_version") as String
val cobbleworkersVersion = property("cobbleworkers_version") as String

base.archivesName.set("${property("archives_base_name")}-neoforge")

neoForge {
    version = neoforgeVersion

    runs {
        create("client") {
            client()
        }
    }

    mods {
        create("cobblemon_jei") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("com.cobblemon:neoforge:$cobblemonVersion")
    compileOnly("mezz.jei:jei-1.21.1-neoforge:$jeiVersion")
    runtimeOnly("mezz.jei:jei-1.21.1-neoforge:$jeiVersion")
    compileOnly("info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1-SNAPSHOT")
    compileOnly("maven.modrinth:jade:15.10.6+neoforge")
    compileOnly("maven.modrinth:cobbleworkers:$cobbleworkersVersion")
    testImplementation("info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1-SNAPSHOT")
    testImplementation("maven.modrinth:jade:15.10.6+neoforge")
    testImplementation("maven.modrinth:cobbleworkers:$cobbleworkersVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

sourceSets.main.get().kotlin.srcDirs(project(":common").file("src/main/kotlin"))
sourceSets.main.get().resources.srcDirs(project(":common").file("src/main/resources"))

sourceSets.test.get().compileClasspath += sourceSets.main.get().compileClasspath
sourceSets.test.get().runtimeClasspath += sourceSets.main.get().runtimeClasspath

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
