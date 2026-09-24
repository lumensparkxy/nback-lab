plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val liveAds = providers.gradleProperty("nback.liveAds").orNull == "true"
val liveAppId = providers.gradleProperty("nback.admobAppId").orNull.orEmpty()
val liveUnitId = providers.gradleProperty("nback.admobUnitId").orNull.orEmpty()
val privacyUrl = providers.gradleProperty("nback.privacyUrl").orNull.orEmpty()
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testUnitId = "ca-app-pub-3940256099942544/1033173712"
if (liveAds) {
    require(liveAppId.matches(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")) && !liveAppId.contains("3940256099942544")) { "Owned AdMob app ID required" }
    require(liveUnitId.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")) && !liveUnitId.contains("3940256099942544")) { "Owned AdMob unit ID required" }
    require(privacyUrl.matches(Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[A-Za-z0-9/_.,~%#?=&+-]*)?"))) { "HTTPS privacy URL required" }
    require(providers.gradleProperty("nback.releaseReviewed").orNull == "true") { "Record ads/privacy release review before enabling live ads" }
}

room { schemaDirectory("$projectDir/schemas") }

android {
    namespace = "com.maswadkar.nback"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // Owner-selected application identity; keep stable after distribution.
        applicationId = "com.maswadkar.nback"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.maswadkar.nback.NBackTestRunner"
        manifestPlaceholders["admobAppId"] = testAppId
        buildConfigField("boolean", "ADS_ENABLED", "false")
        buildConfigField("String", "AD_UNIT_ID", "\"\"")
        buildConfigField("String", "PRIVACY_URL", "\"$privacyUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            // Debug ignores any supplied live IDs.
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "AD_UNIT_ID", "\"$testUnitId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            manifestPlaceholders["admobAppId"] = if (liveAds) liveAppId else testAppId
            buildConfigField("boolean", "ADS_ENABLED", liveAds.toString())
            buildConfigField("String", "AD_UNIT_ID", "\"${if (liveAds) liveUnitId else ""}\"")
        }
        create("releaseSmoke") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("boolean", "ADS_ENABLED", "false")
            buildConfigField("String", "AD_UNIT_ID", "\"\"")
        }
    }
    buildTypes.create("adsSmoke") {
        initWith(buildTypes.getByName("releaseSmoke"))
        applicationIdSuffix = ".adsqa"
        buildConfigField("boolean", "ADS_ENABLED", "true")
        buildConfigField("String", "AD_UNIT_ID", "\"$testUnitId\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        // Dependency upgrades are reviewed separately; version pinning is intentional.
        disable += "NewerVersionAvailable"
        disable += "GradleDependency"
        disable += "AndroidGradlePluginVersion"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.google.ads)
    implementation(libs.google.ump)
    testImplementation(libs.junit)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room.compiler)
    implementation(project(":engine"))
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
