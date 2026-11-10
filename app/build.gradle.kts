plugins {
    // AGP 9 内置 Kotlin 编译，不再应用 org.jetbrains.kotlin.android
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.lifetrack"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.lifetrack"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 发布签名：仅当环境变量注入 keystore 时启用，本地没有密钥时仍能构建 unsigned 包。
    // CI 从 Repository Secrets 注入，见 .github/workflows/release.yml 与 docs/release-guide.md。
    val releaseKeystore = project.providers.environmentVariable("LIFETRACK_KEYSTORE").orNull
    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = project.providers.environmentVariable("LIFETRACK_KEYSTORE_PASSWORD").orNull
                keyAlias = project.providers.environmentVariable("LIFETRACK_KEY_ALIAS").orNull
                keyPassword = project.providers.environmentVariable("LIFETRACK_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        getByName("release") {
            // 未注入密钥时保持 unsigned；发布流水线用 apksigner verify 兜底拦截，避免误发
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    // B1：把导出的 Room schema 放进 androidTest 资源，MigrationTestHelper 才能按版本建库。
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

// APK 命名：LifeTrack-<版本名>-<buildType>.apk
// AGP 9 已移除 applicationVariants 等旧 Variant API，只能用 androidComponents。
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.orNull ?: "0.0.0"
            val buildType = variant.buildType ?: "unknown"
            output.outputFileName.set("LifeTrack-$versionName-$buildType.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // C3：整库静态加密。4.20.0 支持 Room 2 / compileSdk 37 / 16 KB 页对齐，选型见确认单 §2。
    implementation("net.zetetic:sqlcipher-android:4.20.0@aar")
    implementation("androidx.sqlite:sqlite:2.7.0")

    implementation("androidx.datastore:datastore-preferences:1.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
