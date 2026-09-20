plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "sample.SampleAppKt"
}

dependencies {
    implementation(project(":kiit-codes"))
    // Only used to show a Problem as JSON three ways in SampleApp2, kiit-codes has no serialization dependency
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)
}

// Runs the new sample (SampleApp2) until it replaces SampleApp
tasks.register<JavaExec>("runSample2") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "sample.SampleApp2Kt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}
