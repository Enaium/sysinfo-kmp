plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "cn.enaium"
    version = "1.0.0"
}
