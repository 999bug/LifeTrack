buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 自带 Kotlin 编译，不再应用 org.jetbrains.kotlin.android；
        // 但其内置 KGP 版本较旧，这里显式提到 2.4.21，与 Compose 编译器插件对齐。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
