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

// ARSCLib embarque ses propres copies de classes android.* et org.xmlpull.* qui entrent en conflit
// avec apktool (revanced-patcher) et existent déjà dans Android : on les retire du jar.
val arscLib: Configuration by configurations.creating { isTransitive = false }
val strippedArscLib = tasks.register<Jar>("strippedArscLib") {
    archiveFileName.set("ARSCLib-stripped.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped"))
    from({ arscLib.map { zipTree(it) } }) {
        exclude("android/**", "org/xmlpull/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/versions/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    arscLib("io.github.reandroid:ARSCLib:1.4.0")
    // Fusion des APK scindés (.apks / .xapk / .apkm)
    api(files(strippedArscLib))
    // Parsing HTML (APKMirror / Uptodown)
    api("org.jsoup:jsoup:1.21.2")
    // org.json est fourni par Android ; nécessaire seulement pour compiler et tester sur JVM.
    compileOnly("org.json:json:20250517")

    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20250517")
    // Classes retirées d'ARSCLib, nécessaires hors Android.
    testRuntimeOnly("xmlpull:xmlpull:1.1.3.1")
    testRuntimeOnly("io.github.reandroid:ARSCLib:1.4.0")
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
