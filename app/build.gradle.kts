import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Persistent signing key in the project's .local/ (never the default debug
// keystore, which is regenerated when the build machine is replaced).
val signingPropsFile = rootProject.file(".local/signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}
val hasPersistentSigning = signingPropsFile.exists() &&
    rootProject.file(".local/mochimori.keystore").exists()

android {
    namespace = "com.taigashinohara.mochimori"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.taigashinohara.mochimori"
        minSdk = 26
        targetSdk = 35
        versionCode = 36
        versionName = "0.4.3"
    }

    signingConfigs {
        if (hasPersistentSigning) {
            create("persistent") {
                storeFile = rootProject.file(".local/mochimori.keystore")
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasPersistentSigning) signingConfig = signingConfigs.getByName("persistent")
        }
        release {
            isMinifyEnabled = false
            if (hasPersistentSigning) signingConfig = signingConfigs.getByName("persistent")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

android {
    testOptions {
        // Robolectric needs the merged resources to launch the activity.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Material Design 3 (Views): Theme.Material3, Navigation Bar, Material
    // Button/Card/Switch, Dynamic Colors. Resolved through the local mirror
    // on the author's build VM (see settings.gradle.kts).
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    // Pre-delivery UI verification on the JVM (no emulator in the build VM):
    // Robolectric launches the real activity; Roborazzi screenshots it.
    // Same recipe as the sibling apps (Migakeru). See docs/UI_VERIFICATION.md.
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.0")
    // roborazzi-junit-rule is deliberately NOT used: in 1.40 the rule's
    // CaptureType requires an Espresso ViewInteraction captureRoot, which
    // does not fit plain Robolectric + a real activity (measured
    // 2026-11-23). The tests call the core captureRoboImage(view, file,
    // options) directly instead. See MainActivityScreenTest.
}

// In this build VM, loopback TCP via IPv6 is intercepted and breaks the
// Gradle test worker; force IPv4 for the test JVM.
tasks.withType<Test>().configureEach {
    jvmArgs("-Djava.net.preferIPv4Stack=true")
    // 1280m (sibling apps use 1536m): this VM's memory is shared by every
    // app chat's builds, and the larger test JVM kept drawing the OOM
    // killer during the 2026-11-23 rollout; 1280m is enough here.
    maxHeapSize = "1280m"
    // Robolectric resolves its android-all runtime at test time; the test
    // JVM has no usable network here, so point it at the pre-downloaded
    // jar in .local/robolectric-deps/ (offline mode).
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        rootProject.file(".local/robolectric-deps").absolutePath,
    )
    // Deterministic screenshots: pin the timezone (the sandbox clock is
    // fixed, so time-dependent rendering is identical on every run).
    systemProperty("user.timezone", "Asia/Tokyo")
    // Roborazzi: verify against goldens by default; re-record with
    // ROBORAZZI_RECORD=1 in the environment.
    val record = System.getenv("ROBORAZZI_RECORD") == "1"
    systemProperty("roborazzi.test.record", record.toString())
    systemProperty("roborazzi.test.verify", (!record).toString())
}
