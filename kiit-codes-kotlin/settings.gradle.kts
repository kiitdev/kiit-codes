pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle auto-provision a JDK 21 toolchain for compiling src/jvmTest/java (Java 21
    // pattern-matching switch syntax) when only an older JDK is installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kiit-codes-kotlin"

include(":kiit-codes")
include(":sample-kotlin")
include(":sample-java")

// sample-kotlin and sample-java stay in the shared ./samples/ folder alongside sample-swift and
// sample-ts, one level up from this settings file, rather than living under kiit-codes-kotlin/ —
// see _prd/260910-kiit-codes-typescript/kiit-codes-structure.md.
project(":sample-kotlin").projectDir = file("../samples/sample-kotlin")
project(":sample-java").projectDir = file("../samples/sample-java")
