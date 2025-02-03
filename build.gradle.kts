plugins {
    kotlin("jvm") version "1.9.20"
    application
    //id("com.github.johnrengelman.shadow") version "6.0.0"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
