// JVM-only verification build.
//
// The Android app (:app) needs the Android SDK and Google's Maven repository. This build needs
// neither: it compiles and tests the pure-Kotlin core modules, and compiles the app's shared
// Compose UI (app/src/main/kotlin/app/forma/ui/**) against JetBrains Compose for Desktop so that
// screens, view models and navigation can be exercised and rendered on a plain JVM.
//
// Run from the repository root:  ./gradlew -p verification check
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "forma-verification"

include(":core:model")
project(":core").projectDir = file("../core")
project(":core:model").projectDir = file("../core/model")
include(":core:engine")
project(":core:engine").projectDir = file("../core/engine")
include(":core:domain")
project(":core:domain").projectDir = file("../core/domain")
include(":presentation")
project(":presentation").projectDir = file("../presentation")
include(":desktop-harness")
