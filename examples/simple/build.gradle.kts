import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
        mainRun { mainClass.set("example.MainKt") }
    }

    macosArm64 { binaries.executable { entryPoint = "example.main" } }
    macosX64 { binaries.executable { entryPoint = "example.main" } }
    linuxX64 { binaries.executable { entryPoint = "example.main" } }
    linuxArm64 { binaries.executable { entryPoint = "example.main" } }
    mingwX64 { binaries.executable { entryPoint = "example.main" } }

    sourceSets {
        commonMain {
            dependencies { implementation(project(":sysinfo-kmp")) }
        }
    }
}
