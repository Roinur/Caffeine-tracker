// Forked from Sauce Tracker Rewrite; media/network/source dependencies removed.
import java.util.Properties
import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localBrandArtworkDir = rootProject.file("artwork/source/pre-release-private")
val generatedBrandResDir = layout.buildDirectory.dir("generated/res/localBrandArtwork")
val syncLocalBrandArtwork by tasks.registering(Sync::class) {
    from(localBrandArtworkDir) {
        include("*.png")
    }
    into(generatedBrandResDir.map { it.dir("drawable-nodpi") })
}

android {
    namespace = "com.roinur.caffeinetracker"
    compileSdk = 34
    if (localBrandArtworkDir.isDirectory) {
        sourceSets.getByName("main").res.srcDir(generatedBrandResDir)
    }

    val buildNumberProperties = Properties().apply {
        rootProject.file("build-number.properties").inputStream().use { load(it) }
    }
    val resolvedVersionCode = buildNumberProperties.getProperty("versionCode")
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it in 1..2_100_000_000 }
        ?: error("build-number.properties must contain a valid versionCode")

    defaultConfig {
        applicationId = "com.roinur.caffeinetracker"
        minSdk = 26
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = "1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    val releaseStore = providers.environmentVariable("CAFFEINE_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("CAFFEINE_STORE_PASSWORD").orNull
    val releaseAlias = providers.environmentVariable("CAFFEINE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("CAFFEINE_KEY_PASSWORD").orNull
    val releaseSigning = if (listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
            .all { !it.isNullOrBlank() }) {
        signingConfigs.create("production") {
            storeFile = rootProject.file(requireNotNull(releaseStore))
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    } else null

    buildTypes {
        debug { versionNameSuffix = "-debug" }
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:strongSkipping=true"
        )
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
if (localBrandArtworkDir.isDirectory) {
    tasks.named("preBuild") {
        dependsOn(syncLocalBrandArtwork)
    }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-auth:21.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestCompileOnly(files("${android.sdkDirectory}/platforms/android-${android.compileSdk}/optional/android.test.runner.jar", "${android.sdkDirectory}/platforms/android-${android.compileSdk}/optional/android.test.base.jar"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
