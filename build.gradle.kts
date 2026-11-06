buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 自带 Kotlin 编译，不再应用 org.jetbrains.kotlin.android；
        // 但其内置 KGP 版本较旧，这里显式提到 2.4.21，与 Compose 编译器插件对齐。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
}
