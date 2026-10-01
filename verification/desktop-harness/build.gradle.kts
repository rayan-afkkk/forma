import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val appSources = rootProject.file("../app/src/main/kotlin")

sourceSets {
    main {
        kotlin {
            // Shared, platform-neutral UI from the Android app plus this harness's platform seams.
            // Files named *.android.kt contain Android-only implementations and are excluded.
            srcDir(appSources)
            include("app/forma/ui/**", "app/forma/harness/**")
            exclude("**/*.android.kt")
        }
        resources {
            srcDir(rootProject.file("../app/src/main/res/font"))
        }
    }
}

dependencies {
    implementation(project(":presentation"))
    implementation(libs.cmp.desktop.linux.x64)
    implementation(libs.cmp.material3)

    testImplementation(libs.cmp.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Screenshots are written here by ScreenshotTest.
    systemProperty("forma.screenshotDir", rootProject.file("../docs/screenshots").absolutePath)
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "2g"
}
