import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "tr.borsatakip.v5"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    testBuildType = "diagnostic"

    val defaultBackendUrl = listOf(
        providers.gradleProperty("borsa.backendUrl").orNull,
        providers.gradleProperty("BORSA_BACKEND_URL").orNull,
        providers.gradleProperty("BORSA_PRODUCTION_BACKEND_URL").orNull,
        System.getenv("BORSA_BACKEND_URL"),
        System.getenv("BORSA_PRODUCTION_BACKEND_URL")
    ).firstOrNull { !it.isNullOrBlank() }?.trim()?.removeSuffix("/").orEmpty()
    val escapedDefaultBackendUrl = defaultBackendUrl
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    val attestationPublicKeys = listOf(
        providers.gradleProperty("borsa.attestationPublicKeys").orNull,
        providers.gradleProperty("BORSA_ATTESTATION_PUBLIC_KEYS").orNull,
        System.getenv("BORSA_ATTESTATION_PUBLIC_KEYS")
    ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    val escapedAttestationPublicKeys = attestationPublicKeys
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    defaultConfig {
        applicationId = "tr.borsatakip.v5"
        minSdk = 26
        targetSdk = 35
        versionCode = 670
        versionName = "6.3.40"
        resValue("string", "app_name", "BIST / VİOP V${versionName}")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Backend URL gizli değildir; CI/repository variable ile sağlanabilir.
        // API anahtarı APK içine GÖMÜLMEZ, kullanıcı ayarından Android Keystore ile tutulur.
        buildConfigField("String", "DEFAULT_BACKEND_URL", "\"$escapedDefaultBackendUrl\"")
        buildConfigField("String", "ATTESTATION_PUBLIC_KEYS", "\"$escapedAttestationPublicKeys\"")
    }

    val releaseStorePath = System.getenv("BORSA_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("BORSA_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("BORSA_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("BORSA_KEY_PASSWORD")
    val hasReleaseSigning = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("releaseSecure") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        create("diagnostic") {
            initWith(getByName("debug"))
            // Keep Build 666 package identity and diagnostic signer for an in-place update.
            // Existing connection settings and local data remain in the same app sandbox.
            applicationIdSuffix = ".diagnostic.v668.delayed"
            resValue("string", "app_name", "Borsa 670 · Gecikmeli Grafik")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val isReleaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            if (isReleaseTaskRequested) {
                check(defaultBackendUrl.isNotBlank()) { "Release/production build için BORSA_BACKEND_URL veya -Pborsa.backendUrl zorunludur." }
                val releaseUri = runCatching { URI(defaultBackendUrl) }.getOrNull()
                check(releaseUri != null && releaseUri.scheme.equals("https", ignoreCase = true) && !releaseUri.host.isNullOrBlank() &&
                    releaseUri.userInfo == null && releaseUri.rawQuery == null && releaseUri.fragment == null &&
                    releaseUri.port in -1..65535 && releaseUri.port != 0 &&
                    releaseUri.rawPath.orEmpty().split('/').none { it == "." || it == ".." }) {
                    "Release/production backend URL geçerli bir HTTPS adresi olmalıdır."
                }
                check(releaseUri.host.lowercase() !in setOf("localhost", "127.0.0.1", "10.0.2.2")) { "Release/production backend localhost/emülatör adresi olamaz." }
                check(attestationPublicKeys.isNotBlank()) { "Release/production build için BORSA_ATTESTATION_PUBLIC_KEYS zorunludur." }
                check(attestationPublicKeys.split(';').filter { it.isNotBlank() }.all { entry ->
                    val parts = entry.split(':', limit = 4)
                    parts.size == 4 && parts[0].isNotBlank() && parts[1].isNotBlank() && (parts[2].toLongOrNull() ?: 0L) > 0L && parts[3].isNotBlank()
                }) { "BORSA_ATTESTATION_PUBLIC_KEYS biçimi providerId:keyId:keyGeneration:base64PublicKey olmalıdır." }
                check(hasReleaseSigning) { "Release APK üretimi için güvenli imzalama değişkenleri zorunludur." }
                signingConfig = signingConfigs.getByName("releaseSecure")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        allWarningsAsErrors = true
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}


tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Use Room's Kotlin generator for Kotlin DAOs, avoiding KSP 1 Java shadow copies
// that can collide when source checks are repeated.
ksp {
    arg("room.generateKotlin", "true")
}

// Source contract tests read these files directly, beyond their JVM classpath.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main"))
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
    inputs.file(rootProject.layout.projectDirectory.file("gradle.properties"))
}


