import java.util.Properties

plugins {
    `java-library`
    kotlin("jvm")
    id("net.darkhax.curseforgegradle")
}

base.archivesName.set("${property("archives_base_name")}-fabric")

sourceSets.main.get().kotlin.srcDirs(project(":common").file("src/main/kotlin"))
sourceSets.main.get().resources.srcDirs(project(":common").file("src/main/resources"))

evaluationDependsOn(":neoforge")

val neoforgeSourceSets = project(":neoforge").extensions.getByType<SourceSetContainer>()
sourceSets.main.get().compileClasspath += neoforgeSourceSets["main"].compileClasspath

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
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

fun resolveCurseProperty(key: String, envKey: String): String {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        val props = Properties()
        localFile.inputStream().use { props.load(it) }
        val value = props.getProperty(key)?.trim()
        if (!value.isNullOrBlank()) return value
    }
    val extProp = (project.findProperty(key) as? String)?.trim()
    if (!extProp.isNullOrBlank()) return extProp
    return System.getenv(envKey)?.trim() ?: ""
}

val curseProjectId = resolveCurseProperty("curseforge_project_id", "CURSEFORGE_PROJECT_ID")
val curseApiKey = resolveCurseProperty("curseforge_token", "CURSEFORGE_TOKEN")

val targetJarTask = if (tasks.names.contains("remapJar")) tasks.named("remapJar") else tasks.named("jar")

tasks.register<net.darkhax.curseforgegradle.TaskPublishCurseForge>("curseforge") {
    group = "publishing"
    description = "Publishes the Fabric mod JAR to CurseForge."

    apiToken = curseApiKey

    val changelogFile = rootProject.layout.buildDirectory.file("release_changelog.md").get().asFile
    val mainFile = upload(if (curseProjectId.toLongOrNull() != null) curseProjectId else "0", targetJarTask)

    mainFile.displayName = "${project.property("archives_base_name")}-fabric-${project.version}.jar"
    mainFile.releaseType = "release"
    mainFile.changelog = changelogFile
    mainFile.changelogType = "markdown"

    mainFile.addGameVersion("1.21.1")
    mainFile.addModLoader("Fabric")
    mainFile.addJavaVersion("Java 21")
    mainFile.addEnvironment("Client")

    mainFile.addRequirement("fabric-api", "cobblemon", "journeymap", "architectury-api")

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
