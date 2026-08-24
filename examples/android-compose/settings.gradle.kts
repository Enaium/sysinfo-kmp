// Standalone Gradle build: keeps AGP 9's built-in Kotlin application isolated
// from the root build's Kotlin Multiplatform classpath.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal() // consumes cn.enaium:sysinfo-kmp-android published locally
    }
}

rootProject.name = "sysinfo-android-compose"
