pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "sysinfo-kmp"

include(":sysinfo-kmp")

// Per-OS/arch JNI artifacts that bundle the prebuilt libsyskmp shared library
// as a classpath resource. NativeLoader extracts the matching one at runtime.
listOf(
    "linux-x86_64",
    "linux-aarch64",
    "darwin-x86_64",
    "darwin-aarch64",
    "windows-x86_64",
).forEach { classifier ->
    val name = ":jni-jvm-$classifier"
    include(name)
    project(name).projectDir = file("jni-jvm/$classifier")
}

// The Compose example is an isolated Gradle build (AGP 9 built-in Kotlin +
// Compose application): including it as a composite build keeps its plugin
// classpath separate from the root build's Kotlin Multiplatform classpath.
includeBuild("examples/android-compose")
