// Top-level build.gradle.kts
plugins {
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // Pulls Firebase config from app/google-services.json — user drops that file in.
    id("com.google.gms.google-services") version "4.4.2" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}