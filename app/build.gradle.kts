import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.endermythex.enderrmodded"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.endermythex.enderrmodded"
        minSdk = 26
        targetSdk = 36
        // Fournis par le workflow de release (tag vX.Y.Z), sinon valeurs de dev.
        versionCode = (System.getenv("VERSION_CODE") ?: "2").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.2.0"
        buildConfigField("String", "UPDATE_REPO", "\"EnderMythex/EnderrModded\"")

        ndk {
            // Mêmes architectures que les binaires aapt2 embarqués.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    val releaseKeystore = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // La minification casse le chargement dynamique des patchs : on la laisse désactivée.
            isMinifyEnabled = false
            isShrinkResources = false
            // Clé stable fournie par les secrets GitHub (nécessaire aux mises à jour de l'app).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // libprop_override : relève la limite mémoire du processus de patch (repris de ReVanced Manager).
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

    packaging {
        resources {
            excludes += "/XPP3_*_VERSION"
            excludes += "/smali.properties"
            excludes += "/baksmali.properties"
            excludes += "/properties/apktool.properties"
            excludes += "/org/antlr/**"
            excludes += "/org/bouncycastle/pqc/**.properties"
            excludes += "/org/bouncycastle/x509/**.properties"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/**/*.txt"
            excludes += "/META-INF/**/*.properties"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/prebuilt/**/*"
        }
        jniLibs {
            // aapt2 doit être extrait sur le disque pour pouvoir être exécuté.
            useLegacyPackaging = true
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // revanced-patcher est compilé avec des fonctionnalités Kotlin expérimentales.
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexplicit-backing-fields", "-Xskip-prerelease-check")
    }
}

configurations.all {
    // Conflit connu entre une dépendance de revanced-library et protobuf (non utilisé ici).
    exclude(group = "com.google.api.grpc", module = "proto-google-common-protos")
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.work:work-runtime-ktx:2.11.1")

    implementation(project(":core"))

    // Moteur de patch ReVanced (GPLv3)
    implementation("app.revanced:patcher-android:22.0.1")
    implementation("app.revanced:library-android:4.0.1")
}
