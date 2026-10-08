plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    // Only used to show a Problem as JSON in SampleApp, kiit-codes has no serialization dependency
    implementation(libs.kotlinx.serialization.json)
}

// Prints the docs table "Reference > Protocol mappings" from CodesToHttp and CodesToGrpc, see MappingTable.kt
tasks.register<JavaExec>("printMappingTable") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "sample.MappingTableKt"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}
