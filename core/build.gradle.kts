plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Parsing HTML (APKMirror / Uptodown)
    api("org.jsoup:jsoup:1.21.2")
    // Fusion des APK scindés (.apks / .xapk / .apkm)
    api("io.github.reandroid:ARSCLib:1.4.0")
    // org.json est fourni par Android ; nécessaire seulement pour compiler et tester sur JVM.
    compileOnly("org.json:json:20250517")

    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20250517")
}

tasks.test {
    useJUnitPlatform()
    // Les tests réseau (sources APK réelles) ne tournent que si NETWORK_TESTS=1.
    environment("NETWORK_TESTS", System.getenv("NETWORK_TESTS") ?: "0")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
