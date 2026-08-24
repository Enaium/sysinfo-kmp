import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.ListProperty
import org.gradle.internal.os.OperatingSystem
import java.io.File
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library") version "9.3.1"
    id("com.vanniktech.maven.publish") version "0.37.0"
}



val rustDir = rootProject.file("rust")
// Resolve cargo without spawning a shell (Windows has no bash): prefer the
// CARGO_HOME binary, then well-known locations, then bare PATH lookup.
val cargoExecutable: String = run {
    val isWin = OperatingSystem.current().isWindows
    val exe = if (isWin) "cargo.exe" else "cargo"
    val home = System.getenv("CARGO_HOME") ?: (System.getProperty("user.home") + "/.cargo")
    sequenceOf(File(home, "bin/$exe"), File("/opt/homebrew/bin", exe), File("/usr/local/bin", exe))
        .firstOrNull { it.isFile }?.absolutePath ?: exe
}

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
val rustupExecutable: String = run {
    val isWin = OperatingSystem.current().isWindows
    val exe = if (isWin) "rustup.exe" else "rustup"
    val home = System.getenv("CARGO_HOME") ?: (System.getProperty("user.home") + "/.cargo")
    sequenceOf(File(home, "bin/$exe"), File("/opt/homebrew/bin", exe), File("/usr/local/bin", exe))
        .firstOrNull { it.isFile }?.absolutePath ?: exe
}

val installedRustTargets: Set<String> by lazy {
    runCatching {
        providers.exec { commandLine(rustupExecutable, "target", "list", "--installed") }
            .standardOutput.asText.get().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrElse { emptySet() }
}

fun canBuildNativeTarget(triple: String): Boolean = triple in installedRustTargets

val hostOs = OperatingSystem.current()

kotlin {
    // ==================== Android (JVM/ART) ====================
    android {
        namespace = "cn.enaium.sysinfo"
        compileSdk = 36
        minSdk = 24
    }

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
        // Shared JNI loading code compiled into both desktop JVM and Android
        // targets (srcDir sharing keeps the default hierarchy intact).
        val jvmSharedDir = file("src/jvmSharedMain/kotlin")
        getByName("jvmMain") { kotlin.srcDir(jvmSharedDir) }
        getByName("androidMain") { kotlin.srcDir(jvmSharedDir) }
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

// ==================== Android (JVM/ART): per-ABI JNI cdylibs via NDK ========
//
// The .so files are written straight into src/androidMain/jniLibs/<abi>/ so
// AGP packages them into the AAR without extra variant wiring.

val androidSdkDir: File? = sequenceOf(
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
).mapNotNull { it?.takeIf { v -> v.isNotBlank() }?.let(::File) }.firstOrNull { it.isDirectory }
    ?: File(System.getProperty("user.home"), "Library/Android/sdk").takeIf { it.isDirectory }
    ?: File("/opt/android-sdk").takeIf { it.isDirectory }

val androidNdkDir: File? = System.getenv("ANDROID_NDK_HOME")?.let(::File)?.takeIf { it.isDirectory }
    ?: androidSdkDir?.resolve("ndk")?.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }

// ABI -> (rust triple, NDK clang prefix); minSdk 24 selects the API level.
// Note: the armv7 clang wrapper is named armv7a-..., unlike the rust triple.
val androidJniAbis = mapOf(
    "arm64-v8a" to Pair("aarch64-linux-android", "aarch64-linux-android"),
    "armeabi-v7a" to Pair("armv7-linux-androideabi", "armv7a-linux-androideabi"),
    "x86_64" to Pair("x86_64-linux-android", "x86_64-linux-android"),
    "x86" to Pair("i686-linux-android", "i686-linux-android"),
)

if (androidNdkDir != null) {
    val hostTag = when {
        OperatingSystem.current().isMacOsX -> "darwin-x86_64"
        OperatingSystem.current().isWindows -> "windows-x86_64"
        else -> "linux-x86_64"
    }
    val llvmBin = androidNdkDir.resolve("toolchains/llvm/prebuilt/$hostTag/bin")
    val jniRoot = layout.buildDirectory.dir("androidJniSrc").get().asFile

    val abiCargoTasks = androidJniAbis.map { (abi, pair) ->
        val (triple, clangPrefix) = pair
        val linker = llvmBin.resolve("${clangPrefix}24-clang").absolutePath
        val dest = jniRoot.resolve("$abi/libsyskmp.so")
        tasks.register<Exec>("cargoAndroidJni_$abi") {
            group = "build"
            description = "Builds libsyskmp.so for $abi (Android ART)."
            workingDir = rustDir
            environment(
                "CARGO_TARGET_${triple.uppercase().replace('-', '_')}_LINKER",
                linker,
            )
            commandLine(
                cargoExecutable, "rustc",
                "--release",
                "--target", triple,
                "--crate-type", "cdylib",
                "--manifest-path", rustDir.resolve("Cargo.toml").absolutePath,
            )
            inputs.files(rustDir.resolve("Cargo.toml"), rustDir.resolve("src"))
            outputs.file(dest)
            doLast {
                dest.parentFile.mkdirs()
                rustDir.resolve("target/$triple/release/libsyskmp.so")
                    .copyTo(dest, overwrite = true)
            }
        }
    }

    // Inject the .so files into the AAR right after bundling, so native
    // libraries are packaged only at packaging time (never in the source tree).
    val inject = tasks.register<InjectAndroidJni>("injectAndroidJni") {
        soFiles.set(provider {
            abiCargoTasks.flatMap { tp -> tp.get().outputs.files.files }
        })
        aarDir.set(layout.buildDirectory.dir("outputs/aar"))
    }
    inject.configure { dependsOn(abiCargoTasks) }
    tasks.matching { it.name == "bundleAndroidMainAar" }.configureEach { finalizedBy(inject) }
} else {
    logger.lifecycle(
        "sysinfo-kmp: Android NDK not found - skipping bundled libsyskmp.so for the " +
            "android (ART) target. Consumers must provide libsyskmp.so themselves or use the " +
            "androidNative* targets.",
    )
}

// Adds jni/<abi>/libsyskmp.so entries into the assembled AAR.

abstract class InjectAndroidJni : DefaultTask() {
    @get:InputFiles
    abstract val soFiles: ListProperty<File>

    @get:Internal
    abstract val aarDir: DirectoryProperty

    @TaskAction
    fun run() {
        val candidates = aarDir.get().asFile.listFiles { f -> f.name.endsWith(".aar") }
            ?: throw IllegalStateException("No AAR found under \${aarDir.get().asFile}")
        val aar = candidates.singleOrNull()
            ?: throw IllegalStateException("Expected exactly one AAR, found: \$candidates")

        val tmp = File(aar.parentFile, aar.nameWithoutExtension + "-jni.tmp")
        tmp.deleteRecursively(); tmp.mkdirs()
        ZipFile(aar).use { z ->
            val en = z.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                val f = File(tmp, e.name)
                if (e.isDirectory) f.mkdirs()
                else {
                    f.parentFile?.mkdirs()
                    z.getInputStream(e).use { input -> f.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        soFiles.get().forEach { so ->
            val abi = so.parentFile.name
            val dst = File(tmp, "jni/$abi").apply { mkdirs() }
            so.copyTo(File(dst, so.name), overwrite = true)
        }
        val out = File(aar.parentFile, aar.name + ".new")
        ZipOutputStream(out.outputStream()).use { zos ->
            tmp.walkTopDown().filter { it.isFile }.forEach { f ->
                zos.putNextEntry(ZipEntry(f.relativeTo(tmp).path))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        out.copyTo(aar, overwrite = true)
        tmp.deleteRecursively(); out.delete()
    }
}

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
