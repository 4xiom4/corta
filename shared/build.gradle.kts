plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(libs.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.core)
            // SQLDelight's pure-JVM driver lets commonTest exercise the
            // repository on the host JVM. The native SQLite is bundled
            // inside the sqlite-driver artefact, so no extra setup is needed.
            implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.sqldelight.android)
            implementation(libs.sqlcipher)
            implementation(libs.androidx.sqlite)
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.security.crypto)
            implementation(libs.timber)
        }
    }
}

android {
    namespace = "com.corta.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("CortaDatabase") {
            packageName.set("com.corta.db")
            // Migrations scaffolding: when the first real schema change is
            // needed, switch `deriveSchemaFromMigrations` to `true` and add a
            // `1.sqm` (or beyond) under
            //   shared/src/commonMain/sqldelight/databases/
            // For now the `.sq` file remains the source of truth, so we keep
            // verifyMigrations off to avoid spurious CI failures. See Oleada
            // 3.8 of AGENTS.md for the full migration plan.
            deriveSchemaFromMigrations.set(false)
            verifyMigrations.set(false)
            schemaOutputDirectory.set(file("${rootProject.projectDir}/shared/src/commonMain/sqldelight/databases"))
        }
    }
}
