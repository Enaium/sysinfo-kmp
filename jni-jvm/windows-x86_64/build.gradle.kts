import org.gradle.api.file.DuplicatesStrategy
/*
 * Per-OS/arch JNI artifact: darwin-aarch64.
 * Ships libsyskmp.dylib as a classpath resource at
 * /cn/enaium/sysinfo/native/darwin-aarch64/, which NativeLoader
 * (in :sysinfo-kmp's jvmMain) extracts and System.load()s at runtime.
 */
import org.gradle.internal.os.OperatingSystem
import java.io.File

plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

val rustDir = rootProject.file("rust")
val classifier = "windows-x86_64"
val rustTarget = "x86_64-pc-windows-gnu"
val libFile = "syskmp.dll"
val resourceDir = "cn/enaium/sysinfo/native/$classifier"
val canBuildHere = OperatingSystem.current().isWindows

// Resolve cargo without spawning a shell (Windows has no bash): prefer the
// CARGO_HOME binary, then well-known locations, then bare PATH lookup.
val cargoExecutable: String = run {
    val isWin = OperatingSystem.current().isWindows
    val exe = if (isWin) "cargo.exe" else "cargo"
    val home = System.getenv("CARGO_HOME") ?: (System.getProperty("user.home") + "/.cargo")
    sequenceOf(File(home, "bin/$exe"), File("/opt/homebrew/bin", exe), File("/usr/local/bin", exe))
        .firstOrNull { it.isFile }?.absolutePath ?: exe
}

val nativeOutputDir = layout.buildDirectory.dir("jni-native/$classifier")
val libPath = nativeOutputDir.map { it.file(libFile) }

val cargoOutput = rustDir.resolve("target/$rustTarget/release/$libFile")
val destFile = nativeOutputDir.get().asFile.resolve(libFile)

val buildJniLibrary by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds $libFile for $classifier."
    onlyIf { canBuildHere }
    doFirst { nativeOutputDir.get().asFile.mkdirs() }
    workingDir = rustDir
    commandLine(
        cargoExecutable, "rustc",
        "--release",
        "--target", rustTarget,
        "--crate-type", "cdylib",
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

tasks.named<Copy>("processResources") {
    dependsOn(buildJniLibrary)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(destFile) {
        into(resourceDir)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates(
        groupId = rootProject.group.toString(),
        artifactId = "sysinfo-kmp-jni-jvm-$classifier",
        version = rootProject.version.toString(),
    )
    pom {
        name.set("sysinfo-kmp-jni-jvm-$classifier")
        description.set(
            "Prebuilt JNI shared library for sysinfo-kmp on $classifier. " +
                "Loaded automatically by NativeLoader; not intended to be depended on directly.",
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
        developers { developer { id.set("Enaium") } }
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
