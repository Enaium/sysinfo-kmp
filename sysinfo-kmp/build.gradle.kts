import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

val rustDir = rootProject.file("rust")
val cargoExecutable: String = providers.exec {
    commandLine("bash", "-lc", "command -v cargo || ls \$HOME/.cargo/bin/cargo 2>/dev/null")
}.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() } ?: "cargo"

// Rust targets (uninstalled ones are skipped; the cinterop klib is still
// produced, just without the embedded static library).
val nativeTriples = mapOf(
    "macosArm64" to "aarch64-apple-darwin",
    "macosX64" to "x86_64-apple-darwin",
    "linuxX64" to "x86_64-unknown-linux-gnu",
    "linuxArm64" to "aarch64-unknown-linux-gnu",
    "mingwX64" to "x86_64-pc-windows-gnu",
    "androidNativeArm64" to "aarch64-linux-android",
    "androidNativeArm32" to "armv7-linux-androideabi",
    "androidNativeX64" to "x86_64-linux-android",
    "androidNativeX86" to "i686-linux-android",
)

// Cached set of installed rust targets, queried once at configuration time.
val rustupExecutable: String = providers.exec {
    commandLine("bash", "-lc", "command -v rustup || ls \$HOME/.cargo/bin/rustup 2>/dev/null")
}.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() } ?: "rustup"

val installedRustTargets: Set<String> by lazy {
    runCatching {
        providers.exec { commandLine(rustupExecutable, "target", "list", "--installed") }
            .standardOutput.asText.get().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrElse { emptySet() }
}

fun canBuildNativeTarget(triple: String): Boolean = triple in installedRustTargets

val hostOs = OperatingSystem.current()

kotlin {
    // ==================== JVM ====================
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // ==================== Native ====================
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()
    androidNativeArm64()
    androidNativeArm32()
    androidNativeX64()
    androidNativeX86()

    // ==================== cinterop for all native targets ====================
    targets.withType<KotlinNativeTarget>().configureEach {
        val targetName = name
        val triple = nativeTriples[targetName]
        compilations.getByName("main") {
            cinterops {
                create("syskmp") {
                    defFile(project.file("src/nativeInterop/cinterop/syskmp.def"))
                    includeDirs(project.file("src/nativeInterop/cinterop"), rustDir.resolve("include"))
                    compilerOpts("-ferror-limit=0")
                    if (triple != null && canBuildNativeTarget(triple)) {
                        val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
                        extraOpts(
                            "-libraryPath", outputDir.absolutePath,
                            "-staticLibrary", "libsyskmp.a",
                        )
                    }
                }
            }
        }
    }

    // ==================== Source sets ====================
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        jvmMain {
            dependencies {
                runtimeOnly(project(":jni-jvm-linux-x86_64"))
                runtimeOnly(project(":jni-jvm-linux-aarch64"))
                runtimeOnly(project(":jni-jvm-darwin-x86_64"))
                runtimeOnly(project(":jni-jvm-darwin-aarch64"))
                runtimeOnly(project(":jni-jvm-windows-x86_64"))
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// ==================== Native: build the static syskmp library per target ====================
nativeTriples.forEach { (targetName, triple) ->
    val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
    val cargoOutput = rustDir.resolve("target/$triple/release/libsyskmp.a")
    val destFile = outputDir.resolve("libsyskmp.a")
    val buildTask = tasks.register<Exec>("cargoBuild_$targetName") {
        onlyIf { canBuildNativeTarget(triple) }
        doFirst { outputDir.mkdirs() }
        workingDir = rustDir
        commandLine(
            cargoExecutable, "rustc",
            "--release",
            "--target", triple,
            "--crate-type", "staticlib",
            "--manifest-path", rustDir.resolve("Cargo.toml").absolutePath,
        )
        inputs.files(rustDir.resolve("Cargo.toml"), rustDir.resolve("src"))
        outputs.file(cargoOutput)
        outputs.file(destFile)
        doLast {
            if (cargoOutput.isFile) {
                cargoOutput.copyTo(destFile, overwrite = true)
            }
        }
    }

    if (canBuildNativeTarget(triple)) {
        tasks.matching {
            it.name.startsWith("cinteropSyskmp") && it.name.endsWith(targetName.replaceFirstChar { c -> c.uppercase() })
        }.configureEach {
            dependsOn(buildTask)
            inputs.file(destFile)
        }
    }
}

// JVM JNI resources are declared in the jvmMain source set above.

// ==================== Publishing ====================
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = "sysinfo-kmp",
        version = null,
    )

    pom {
        name.set("sysinfo-kmp")
        description.set(
            "Kotlin Multiplatform bindings for the Rust sysinfo crate. " +
                "JVM loads a self-contained JNI shared library built from the Rust source; " +
                "native targets embed the statically compiled syskmp library into the published klib.",
        )
        url.set("https://github.com/Enaium/sysinfo-kmp")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer { id.set("Enaium") }
        }

        scm {
            url.set("https://github.com/Enaium/sysinfo-kmp")
            connection.set("scm:git:git@github.com:Enaium/sysinfo-kmp.git")
            developerConnection.set("scm:git:git@github.com:Enaium/sysinfo-kmp.git")
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/Enaium/sysinfo-kmp/issues")
        }
    }
}
