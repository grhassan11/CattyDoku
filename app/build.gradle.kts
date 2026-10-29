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
    // PLACEHOLDER: namespace + applicationId (rename together with package dirs)
    namespace = "com.threetech.catpuzzle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.threetech.catpuzzle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // ---- Ads / UMP (phase 9). Source holds NO real IDs. ----
        // Debug = Google official TEST IDs. Release = PLACEHOLDERS (all-zero publisher id) until the user supplies the
        // real AdMob app id + interstitial / native (Home) / app-open unit ids: ads stay disabled in code while the placeholder is present.
        // UMP_DEBUG_GEOGRAPHY (debug builds only): "" = real location, "EEA", "NOT_EEA", "US_STATE".
        // UMP_TEST_DEVICE_HASHED_ID: logcat prints it on first UMP request; blank = none.
        buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"\"")
        buildConfigField("String", "UMP_TEST_DEVICE_HASHED_ID", "\"\"")
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            // Google official TEST units: native advanced + app open.
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
            // Google official TEST units: adaptive banner + rewarded.
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        release {
            // TBD(user): replace both with the real AdMob IDs (and keep them out of VCS if desired, e.g. gradle.properties).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-0000000000000000~0000000000"
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-0000000000000000~0000000000\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-0000000000000000/0000000000\"")
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-0000000000000000/0000000000\"")
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"ca-app-pub-0000000000000000/0000000000\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-0000000000000000/0000000000\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-0000000000000000/0000000000\"")
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
// The plugin adds nonMinifiedRelease + benchmarkRelease via initWith(release) (inherits placeholder ad ids = ads off).
// Normal builds do not need a generated profile: without src/main/baseline-prof.txt the build is unchanged.
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
// what the Meta / Unity mediation adapters pull in transitively (Next-Gen ships the compat shims).
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
    implementation(libs.androidx.profileinstaller) // installs the baseline profile on first run
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process) // ProcessLifecycleOwner (resume-only app-open ads)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime) // streak-at-risk reminder (one-time work chain, re-evaluated at fire time)
    implementation(libs.kotlinx.coroutines.android)

    // Ads + consent (phase 9): Next-Gen SDK, console-mediation adapters (Meta + Unity bidding), UMP.
    implementation(libs.ads.mobile.sdk)
    implementation(libs.ads.mediation.facebook)
    implementation(libs.unity.ads)
    implementation(libs.ads.mediation.unity)
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
}

// Firebase Gradle plugins: applied ONLY when the real config file is present, so the project builds without it.
// Drop the file at app/google-services.json (package must match applicationId), then re-sync.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("google-services.json not found in :app -> Firebase plugins skipped (Firebase runs as no-op).")
}
