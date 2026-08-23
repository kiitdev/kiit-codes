plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.skie)
    id("signing")
}

// Single source of truth for the published version — feeds Maven Central (below), the JS
// package.json (in the js(IR) block below), and the release workflow's printVersion task, so
// the git tag, GitHub release, Maven artifact, and npm package version can never drift apart.
val libraryVersion = "1.0.2"

kotlin {
    jvm {
        // src/jvmTest/java (see JavaInteropTest) is compiled automatically — Kotlin's jvm()
        // target compiles Java sources by default now, no withJava() needed.
        compilerOptions {
            // JVM 21 so Kotlin emits PermittedSubclasses for the sealed Status/Err/StatusException
            // hierarchies, enabling exhaustive Java pattern-matching `switch` for JVM consumers.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    js(IR) {
        browser()
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()

        // Sets the npm-facing package identity. The raw compiled filenames stay
        // "kiit-codes-kiit-codes.{js,d.ts}" (rootProject-subproject concatenation) — that's purely
        // internal; package.json's "main"/"types" fields below already point at them correctly,
        // and consumers only ever interact via `import { kiit } from '@kiit/codes'`, resolved
        // through package.json, never by referencing the raw filename directly. (compilerOptions'
        // `moduleName` was tried for cosmetically renaming the filename too, but confirmed via a
        // clean build to have no effect on either the filename or the `.d.ts`'s `export as
        // namespace` identifier — not worth chasing further since it has zero consumer impact.)
        compilations["main"].packageJson {
            name = "@kiit/codes"
            version = libraryVersion
        }
    }

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework {
            baseName = "KiitCodes"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // No kiit dependencies — core library
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Disabled: SKIE's default analytics upload sends git/hardware/project data to Touchlab — off
// until that's something we explicitly want, not because it's a default worth silently keeping.
skie {
    analytics {
        enabled.set(false)
    }
}

android {
    namespace = "kiit.codes"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Store the following in ~/.gradle/gradle.properties
 *
 * signingInMemoryKeyPassword=
 * signingInMemoryKey=
 * signing.gnupg.keyName=
 * signing.gnupg.passphrase=
 *
 * Maven local: ~/.m2/repository/dev/kiit/kiit-codes/
 */
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    coordinates(
        groupId = "dev.kiit",
        artifactId = "kiit-codes",
        version = libraryVersion,
    )
    pom {
        name = "kiit-codes"
        description = "Typed status and error codes used by kiit-result for structured success and failure classification"
        url = "https://kiit.dev"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "codehelix"
                name = "CodeHelix"
                url = "https://kiit.dev"
            }
        }
        scm {
            url = "https://github.com/kiitdev/kiit-codes"
            connection = "scm:git:git://github.com/kiitdev/kiit-codes.git"
            developerConnection = "scm:git:ssh://git@github.com/kiitdev/kiit-codes.git"
        }
    }
}

// Kotlin's jvm() target compiles Java sources by default (see the jvm{} block above), but the
// resulting JavaCompile tasks don't inherit jvmTarget automatically — point them at a JDK 21
// toolchain (auto-provisioned via the foojay resolver in settings.gradle.kts if not installed
// locally) so src/jvmTest/java can use JDK 21 syntax (e.g. pattern-matching switch).
tasks.withType<JavaCompile>().configureEach {
    if (name == "compileJvmMainJava" || name == "compileJvmTestJava") {
        javaCompiler.set(
            javaToolchains.compilerFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            },
        )
    }
}

// The compiled jvmTest classes are now JDK 21 bytecode (see above) — run them on a matching JVM.
tasks.named<Test>("jvmTest") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}

detekt {
    config.setFrom("$projectDir/detekt.yml")
    buildUponDefaultConfig = true
    source.setFrom(
        "src/commonMain/kotlin",
        "src/jsMain/kotlin",
        "src/iosMain/kotlin",
    )
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

// Read by the release workflow (`./gradlew :kiit-codes:printVersion -q`) to derive the git tag
// and GitHub release name from the same version published to Maven Central.
tasks.register("printVersion") {
    doLast { println(libraryVersion) }
}
