@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// ---------------------------------------------------------------------------------------------
// Native core: ARMSX2 (PCSX2 fork with native ARM64 JIT recompilers) lives in the `core/`
// git submodule. We compile its Android JNI library (libemucore_4k.so) from source with the
// same CMake entry point and flags the ARMSX2 project uses, so nothing in the core is forked.
// ---------------------------------------------------------------------------------------------
val coreDir = rootProject.layout.projectDirectory.dir("core")
val coreCMakeLists = coreDir.file("platforms/android/app/src/main/cpp/CMakeLists.txt").asFile
val skipNative = providers.gradleProperty("naruto5.skipNative").orElse("false").get() == "true"
val useCcache = providers.gradleProperty("naruto5.ccache").orElse("false").get() == "true"
// Xiaomi Pad 6 (and nearly every Android 13/14 device) uses 4 KiB pages.
val hostPageSize = providers.gradleProperty("naruto5.hostPageSize").orElse("0x1000").get()
val emucoreName = if (hostPageSize == "0x4000") "emucore_16k" else "emucore_4k"

if (!skipNative && !coreCMakeLists.isFile) {
    throw GradleException(
        "ARMSX2 core submodule is missing. Run: git submodule update --init --depth 1 core"
    )
}

// Signing: a stable sideload key is committed so every CI build can be installed over the
// previous one without uninstalling (which would wipe app-private data). Override it with the
// NARUTO5_KEYSTORE* environment variables (GitHub secrets) for your own private key.
val sideloadKeystore = rootProject.file("keystore/naruto5-sideload.jks")
val envKeystore = System.getenv("NARUTO5_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile }

// Runtime resources the core needs at run time (GameDB, GLSL/SPIR-V shader sources, fonts,
// widescreen/patch database). Copied from the submodule at build time; never duplicated in git.
val generateCoreAssets by tasks.registering(Sync::class) {
    from(coreDir.dir("bin/resources")) {
        exclude("**/dx11/**")
        exclude("fonts/Twemoji*")
        exclude("RedumpDatabase.yaml") // only used by the desktop disc-dump verifier
    }
    from(coreDir.file("bin/resources-overlay/armsx2_overrides.yaml"))
    from(coreDir.file("platforms/android/app/src/main/assets/resources/patches.zip"))
    into(layout.buildDirectory.dir("generated/coreAssets/resources"))
}
val generateLicenseAssets by tasks.registering(Sync::class) {
    from(coreDir.dir("bin/docs")) { include("GPL.html", "ThirdPartyLicenses.html") }
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
tasks.matching { t ->
    t.name == "preBuild" || t.name.endsWith("Assets") || t.name.contains("Lint")
}.configureEach {
    if (name != "generateCoreAssets" && name != "generateLicenseAssets") {
        dependsOn(generateCoreAssets, generateLicenseAssets)
    }
}

android {
    namespace = "com.aldiian.naruto5"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.aldiian.naruto5"
        minSdk = 29
        targetSdk = 35
        versionCode = providers.gradleProperty("naruto5.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("naruto5.versionName").orNull ?: "1.0.0"
        buildConfigField("String", "CORE_COMMIT", "\"${providers.gradleProperty("naruto5.coreCommit").orElse("unknown").get()}\"")
        buildConfigField("boolean", "NATIVE_BUILT", "${!skipNative}")
        buildConfigField("String", "EMUCORE_LIB", "\"$emucoreName\"")
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("sideload") {
            if (envKeystore != null) {
                storeFile = envKeystore
                storePassword = System.getenv("NARUTO5_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NARUTO5_KEY_ALIAS")
                keyPassword = System.getenv("NARUTO5_KEY_PASSWORD")
            } else {
                storeFile = sideloadKeystore
                storePassword = "naruto5sideload"
                keyAlias = "naruto5"
                keyPassword = "naruto5sideload"
            }
        }
    }

    fun commonCmakeArgs(release: Boolean): List<String> = buildList {
        add("-DANDROID=true")
        add("-DANDROID_STL=c++_static")
        add("-DCMAKE_BUILD_TYPE=" + if (release) "Release" else "Debug")
        add("-DLTO_PCSX2_CORE=" + if (release) "ON" else "OFF")
        add("-DARMSX2_EMUCORE_LIBRARY_NAME=$emucoreName")
        add("-DARMSX2_ANDROID_HOST_PAGE_SIZE=$hostPageSize")
        add("-DARMSX2_ENABLE_LSFG=OFF")
        // Snapdragon 870 = Kryo 585 (Cortex-A77 + A55): ARMv8.2-A with FP16 and DotProd, the
        // same codegen tier ARMSX2 ships as its "a11/a13/a15" APKs. Pass
        // -Pnaruto5.march=armv8-a for a build that also runs on ARMv8.0 (Cortex-A53) devices.
        val march = providers.gradleProperty("naruto5.march").orElse("armv8.2-a+fp16+dotprod").get()
        val cpu = " -march=$march" + if (march == "armv8-a") " -moutline-atomics" else ""
        add("-DCMAKE_C_FLAGS=-O3$cpu")
        add("-DCMAKE_CXX_FLAGS=-O3$cpu")
        if (useCcache) {
            add("-DCMAKE_C_COMPILER_LAUNCHER=ccache")
            add("-DCMAKE_CXX_COMPILER_LAUNCHER=ccache")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("sideload")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!skipNative) externalNativeBuild { cmake { arguments += commonCmakeArgs(true) } }
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
            applicationIdSuffix = ".debug"
            if (!skipNative) externalNativeBuild { cmake { arguments += commonCmakeArgs(false) } }
        }
    }

    if (!skipNative) {
        externalNativeBuild {
            cmake {
                path = coreCMakeLists
                version = "3.31.6"
            }
        }
    }

    sourceSets.named("main") {
        assets.srcDir(layout.buildDirectory.dir("generated/coreAssets").get().asFile)
        assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets").get().asFile)
    }

    packaging {
        jniLibs {
            // libadrenotools needs its hook libraries extracted to nativeLibraryDir so it can
            // load a custom (Turnip) Vulkan driver through its linker-namespace bypass.
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests { isReturnDefaultValues = true }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
