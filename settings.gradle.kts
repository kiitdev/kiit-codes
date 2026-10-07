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

rootProject.name = "kiit-codes"

include(":kiit-codes")
include(":samples:sample-kotlin")
include(":samples:sample-java")
