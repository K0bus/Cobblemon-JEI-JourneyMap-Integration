plugins {
    `java-library`
    kotlin("jvm")
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
