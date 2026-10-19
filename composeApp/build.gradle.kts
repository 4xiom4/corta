plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            implementation(libs.work.runtime.ktx)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
            implementation(libs.timber)
            implementation(libs.androidx.paging.runtime)
            implementation(libs.androidx.paging.compose)
            // Enables the baseline profile installer runtime. The actual
            // baseline profile is generated separately via Macrobenchmark
            // (planned for Oleada 3.7 follow-up). See
            //   https://developer.android.com/topic/performance/baselineprofiles
            implementation(libs.profileinstaller)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)
        }
    }
}

android {
    namespace = "com.corta.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/main/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.corta.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
        }
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // Per "no aggressive refactor" preference we start with a baseline
        // to silence the existing 30+ known issues, then trim it incrementally.
        baseline = file("lint-baseline.xml")
        checkAllWarnings = false
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            "MissingTranslation",
            "ExtraTranslation",
            "VectorPath",
            "InvalidVectorPath"
        )
        // ContentDescription: enabled so every new icon-only control must
        // carry a meaningful label. Existing 43 known offenders are
        // silenced by the baseline; they will be fixed one by one as the
        // calls are migrated to CortaIconButton / CortaDecorativeIcon.
    }
    dependencies {
        debugImplementation(compose.uiTooling)
        testImplementation("junit:junit:4.13.2")
        testImplementation("androidx.test:core:1.5.0")
        testImplementation("app.cash.sqldelight:sqlite-driver:2.0.2")
        testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.0")
        testImplementation("androidx.compose.ui:ui-test-junit4:1.6.7")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
        androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.7")
        androidTestImplementation("app.cash.sqldelight:android-driver:2.0.2")
        debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.7")
    }
}
