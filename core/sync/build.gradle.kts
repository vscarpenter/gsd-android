plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.vinny.gsd.sync"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:store"))
}
