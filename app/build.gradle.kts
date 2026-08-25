import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Secrets & environment config live in local.properties (git-ignored).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(key: String, default: String): String =
    localProps.getProperty(key)?.trim().takeUnless { it.isNullOrBlank() } ?: default

android {
    namespace = "com.offhand"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offhand"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // SMTP delivery config. Defaults target the local dev sink reached
        // from the emulator via 10.0.2.2; a real account (e.g. Gmail app
        // password) is a local.properties change, never a code change.
        buildConfigField("String", "SMTP_HOST", "\"${prop("offhand.smtp.host", "10.0.2.2")}\"")
        buildConfigField("int", "SMTP_PORT", prop("offhand.smtp.port", "2525"))
        buildConfigField("String", "SMTP_USER", "\"${prop("offhand.smtp.user", "")}\"")
        buildConfigField("String", "SMTP_PASS", "\"${prop("offhand.smtp.pass", "")}\"")
        buildConfigField("String", "SMTP_FROM", "\"${prop("offhand.smtp.from", "offhand@test.local")}\"")
        buildConfigField("boolean", "SMTP_STARTTLS", prop("offhand.smtp.starttls", "false"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // JavaMail artifacts ship overlapping license/notice files.
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    // Vosk on-device ASR (streaming, 16 kHz). JNA must be the aar packaging.
    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // SMTP delivery (JavaMail's Android build; STARTTLS-capable).
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
