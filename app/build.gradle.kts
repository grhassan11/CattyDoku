import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    // namespace and applicationId are both com.tm.meow.sudoku.puzzle
    namespace = "com.tm.meow.sudoku.puzzle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tm.meow.sudoku.puzzle"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ---- Ads / UMP (phase 9). ----
        // ALL builds (debug, release, nonMinifiedRelease, benchmarkRelease) = Google TEST IDs, defined ONCE here.
        // SWITCH TO REAL IDs BEFORE RELEASE BUILD: edit ONLY the ids below (single place). No per-buildType ad ids exist.
        // REAL IDs (pub-4314093941046206), swap in for the TEST values below:
        //   app        ca-app-pub-4314093941046206~9747457418
        //   interstitial ca-app-pub-4314093941046206/9472125240
        //   native     ca-app-pub-4314093941046206/4495130734
        //   app open   ca-app-pub-4314093941046206/4609671647
        //   banner     ca-app-pub-4314093941046206/7814419481
        //   rewarded   ca-app-pub-4314093941046206/9921708076
        // Placeholder guard (PLACEHOLDER_MARK contains-check) passes for both sets, so ads stay enabled.
        // No setTestDeviceIds is set: with REAL IDs do NOT run the release build on a dev device (invalid-traffic / policy risk).
        val admobAppId = "ca-app-pub-3940256099942544~3347511713"
        val admobInterstitialId = "ca-app-pub-3940256099942544/1033173712"
        val admobNativeId = "ca-app-pub-3940256099942544/2247696110"
        val admobAppOpenId = "ca-app-pub-3940256099942544/9257395921"
        val admobBannerId = "ca-app-pub-3940256099942544/9214589741"
        val admobRewardedId = "ca-app-pub-3940256099942544/5224354917"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_NATIVE_ID", "\"$admobNativeId\"")
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"$admobAppOpenId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobRewardedId\"")
        // UMP_DEBUG_GEOGRAPHY (debug builds only): "" = real location, "EEA", "NOT_EEA", "US_STATE".
        // UMP_TEST_DEVICE_HASHED_ID: logcat prints it on first UMP request; blank = none.
        buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"\"")
        buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"\"")
    }

    buildTypes {
        // Ad IDs: defaultConfig only (all build types inherit). Nothing ad-related here.
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        // Exported Room schemas (app/schemas) as androidTest assets: MigrationTestHelper reads them from there.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    androidResources {
        // App ships these 11 languages (see AppLanguages / res/xml/locales_config.xml): drop every other library translation
        // (Material, Play Services, Firebase, ...) from the APK/AAB. zh-rTW = Traditional Chinese.
        localeFilters += listOf("en", "ja", "zh-rTW", "nl", "de", "ko", "it", "es", "fr", "ru", "tr")
    }

    packaging {
        resources {
            // License/notice duplicates + coroutines debug probes: dead weight in the APK.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/DebugProbesKt.bin"
        }
    }

    lint {
        // Release lint (lintVital on assembleRelease / bundleRelease) must fail the build on fatal issues.
        abortOnError = true
        checkReleaseBuilds = true
    }
}

// Baseline Profile (generate: ./gradlew :app:generateBaselineProfile with a device attached, see :baselineprofile).
// The plugin adds nonMinifiedRelease + benchmarkRelease via initWith(release) (ad ids come from defaultConfig like every other build type).
// Normal builds do not need a generated profile: without a generated profile (app/src/main/generated/baselineProfiles/) the build is unchanged.
baselineProfile {
    // Dex layout optimisation from the startup profile (no-op until the profile is generated).
    dexLayoutOptimization = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ADS POLICY: GMA Next-Gen SDK only. Legacy play-services-ads family is excluded from EVERY configuration, including
// anything pulled in transitively (Next-Gen ships the compat shims).
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-api")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

ksp {
    // Room schema export (kept in VCS for migration tests)
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat) // per-app language: AppCompatDelegate.setApplicationLocales + AppCompatActivity
    implementation(libs.androidx.profileinstaller) // installs the baseline profile on first run
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process) // ProcessLifecycleOwner (resume-only app-open ads)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime) // streak-at-risk reminder (one-time work chain, re-evaluated at fire time)
    implementation(libs.kotlinx.coroutines.android)

    // Ads + consent (phase 9): Next-Gen SDK, UMP. AdMob only (no mediation adapters).
    implementation(libs.ads.mobile.sdk)
    implementation(libs.ump)

    // Shop: Google Play Billing (KTX). Merges the BILLING permission + <queries> + ProxyBillingActivity; ships its own R8 rules.
    implementation(libs.billing.ktx)

    // Firebase (phase 10): Analytics + Crashlytics + Remote Config, versions from the BoM. Compiles without google-services.json;
    // at runtime the code falls back to no-ops when no default FirebaseApp exists (see firebase/FirebaseAvailability.kt).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    // Instrumented Room migration tests (app/src/androidTest). Run: ./gradlew :app:connectedDebugAndroidTest
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// Firebase Gradle plugins: applied ONLY when the real config file is present, so the project builds without it.
// Drop the file at app/google-services.json (package must match applicationId), then re-sync.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("google-services.json not found in :app -> Firebase plugins skipped (Firebase runs as no-op).")
}
