pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libsu (dépendance de revanced-library)
        maven("https://jitpack.io")
        // ReVanced publie revanced-patcher / revanced-library sur GitHub Packages.
        // Un jeton GitHub (scope "read:packages") est nécessaire, même pour lire des paquets publics.
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}

rootProject.name = "EnderrModded"
include(":app", ":core")
