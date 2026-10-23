import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseStoreFile = System.getenv("CYBERMETA_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("CYBERMETA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("CYBERMETA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("CYBERMETA_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
val cybermetaVersionCode = providers.gradleProperty("cybermetaVersionCode").orNull?.toIntOrNull() ?: 300
val cybermetaVersionName = providers.gradleProperty("cybermetaVersionName").orNull ?: "0.3.0"
if (releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    throw GradleException("Release signing requires all CYBERMETA_RELEASE_* environment variables.")
}

// ---- Rust engine (cybermeta) via cargo-ndk + UniFFI -------------------------
val rustDir = rootProject.file("rust")
val rustTarget = rootProject.file("rust/target")
val jniLibs = file("src/main/jniLibs")
val bindingsDir = layout.buildDirectory.dir("generated/uniffi/kotlin")
val cargoEnv = mapOf("CARGO_TARGET_DIR" to rustTarget.absolutePath)

val cargoNdk = tasks.register<Exec>("cargoNdk") {
    description = "Builds libcybermeta_ffi.so for each ABI into src/main/jniLibs"
    workingDir = rustDir
    environment(cargoEnv)
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "x86_64", "-o", jniLibs.absolutePath, "build", "--release", "--lib")
    inputs.dir(rustDir.resolve("src"))
    inputs.files(rustDir.resolve("Cargo.toml"), rustDir.resolve("Cargo.lock"))
    outputs.dir(jniLibs)
}

// The host build serves two purposes: UniFFI reads the interface from it, and
// JVM unit tests load it through JNA.
val cargoHost = tasks.register<Exec>("cargoHost") {
    description = "Builds the host libcybermeta_ffi.so (bindgen + unit tests)"
    workingDir = rustDir
    environment(cargoEnv)
    commandLine("cargo", "build", "--release", "--lib")
    inputs.dir(rustDir.resolve("src"))
    inputs.files(rustDir.resolve("Cargo.toml"), rustDir.resolve("Cargo.lock"))
    outputs.file(rustTarget.resolve("release/libcybermeta_ffi.so"))
}

val uniffiBindings = tasks.register<Exec>("uniffiBindings") {
    description = "Generates the Kotlin bindings for the Rust engine"
    dependsOn(cargoHost)
    workingDir = rustDir
    environment(cargoEnv)
    val out = bindingsDir.get().asFile
    commandLine(
        "cargo", "run", "--release", "--bin", "uniffi-bindgen", "--", "generate",
        "--library", rustTarget.resolve("release/libcybermeta_ffi.so").absolutePath,
        "--language", "kotlin", "--no-format", "--out-dir", out.absolutePath,
    )
    inputs.file(rustTarget.resolve("release/libcybermeta_ffi.so"))
    outputs.dir(out)
}

android {
    namespace = "dev.cybercore.cybermeta"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    // AGP strips the (unstripped, see rust/Cargo.toml) native libraries with this NDK.
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "dev.cybercore.cybermeta"
        minSdk = 30
        targetSdk = 36
        versionCode = cybermetaVersionCode
        versionName = cybermetaVersionName
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    sourceSets {
        // A plain File (not a Provider): preBuild generates it, see uniffiBindings.
        getByName("main") { java.directories.add(bindingsDir.get().asFile.absolutePath) }
    }
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = false } }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.systemProperty("jna.library.path", rustTarget.resolve("release").absolutePath)
            it.dependsOn(cargoHost)
        }
    }
    lint {
        warningsAsErrors = true
        informational += setOf("OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.named("preBuild") { dependsOn(cargoNdk, uniffiBindings) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // UniFFI's Kotlin bindings call the Rust library through JNA.
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.java.dev.jna:jna:5.17.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
