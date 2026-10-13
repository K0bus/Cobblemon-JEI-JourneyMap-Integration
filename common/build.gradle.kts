plugins {
    `java-library`
    kotlin("jvm")
}

// Le sous-projet common sert de conteneur de sources partagées,
// compilé directement par les sous-projets loader (neoforge, fabric).
tasks.compileKotlin { enabled = false }
tasks.compileJava { enabled = false }
tasks.jar { enabled = false }
