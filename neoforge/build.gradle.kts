plugins {
    id("net.neoforged.moddev")
    kotlin("jvm")
    id("net.darkhax.curseforgegradle")
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

val curseProjectId = (findProperty("curseforge_project_id") as? String
    ?: System.getenv("CURSEFORGE_PROJECT_ID")
    ?: "0").trim()
val curseApiKey = (findProperty("curseforge_token") as? String
    ?: System.getenv("CURSEFORGE_TOKEN")
    ?: "").trim()

tasks.register<net.darkhax.curseforgegradle.TaskPublishCurseForge>("curseforge") {
    group = "publishing"
    description = "Publishes the NeoForge mod JAR to CurseForge."

    apiToken = curseApiKey

    val changelogFile = rootProject.layout.buildDirectory.file("release_changelog.md").get().asFile
    val mainFile = upload(if (curseProjectId.toLongOrNull() != null) curseProjectId else "0", tasks.named("jar"))

    mainFile.displayName = "${project.property("archives_base_name")}-neoforge-${project.version}.jar"
    mainFile.releaseType = "release"
    mainFile.changelog = changelogFile
    mainFile.changelogType = "markdown"

    mainFile.addGameVersion("1.21.1")
    mainFile.addModLoader("NeoForge")
    mainFile.addJavaVersion("Java 21")

    mainFile.addRequirement("cobblemon", "journeymap", "architectury-api")

    doFirst {
        if (curseApiKey.isBlank()) {
            throw GradleException("CURSEFORGE_TOKEN is required to publish to CurseForge. Set it as an environment variable or gradle property.")
        }
        if (curseProjectId.toLongOrNull() == null || curseProjectId == "0") {
            throw GradleException("curseforge_project_id is required to publish to CurseForge. Set it in gradle.properties or CURSEFORGE_PROJECT_ID env var.")
        }
        if (!changelogFile.exists()) {
            throw GradleException("Changelog file not found at ${changelogFile.absolutePath}. Generate it before publishing.")
        }
    }
}
