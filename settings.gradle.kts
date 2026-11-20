rootProject.name = "PawQuest"

// Release and CI builds pass -Ppawquest.officialRepositoriesOnly=true (or set
// PAWQUEST_OFFICIAL_REPOSITORIES_ONLY=true) so every artifact comes from Google Maven or
// Maven Central. Local mirrors below exist only for development on a metered connection;
// gradle/verification-metadata.xml pins checksums regardless of which repository served a file.
val officialRepositoriesOnly = (providers.gradleProperty("pawquest.officialRepositoriesOnly").orNull
    ?: providers.environmentVariable("PAWQUEST_OFFICIAL_REPOSITORIES_ONLY").orNull) == "true"

pluginManagement {
    val officialOnly = (providers.gradleProperty("pawquest.officialRepositoriesOnly").orNull
        ?: providers.environmentVariable("PAWQUEST_OFFICIAL_REPOSITORIES_ONLY").orNull) == "true"
    repositories {
        // Optional local copies of official artifacts (checksums verified) for metered connections.
        val localRepository = file(".gradle/local-maven")
        if (!officialOnly && localRepository.isDirectory) {
            maven { url = uri(localRepository) }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Maven Central (official host URL; kept so existing caches stay valid).
        maven { url = uri("https://repo1.maven.org/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Native rendering AARs must come from Maven Central.
        exclusiveContent {
            forRepository { maven { url = uri("https://repo1.maven.org/maven2") } }
            filter {
                includeGroup("io.github.sceneview")
                includeGroup("com.google.android.filament")
            }
        }
        // Development only: browser-downloaded copies of official artifacts, restricted to
        // the modules below. Never used by release/CI builds.
        val manualRepository = file(".gradle/manual-maven")
        if (!officialRepositoriesOnly && manualRepository.isDirectory) {
            maven {
                url = uri(manualRepository)
                content {
                    includeVersion("androidx.lifecycle", "lifecycle-runtime-ktx-android", "2.9.4")
                    includeVersion("androidx.lifecycle", "lifecycle-runtime-compose-android", "2.9.4")
                    includeVersion("androidx.lifecycle", "lifecycle-livedata-core", "2.9.4")
                    includeVersion("androidx.lifecycle", "lifecycle-runtime-android", "2.9.4")
                    includeVersion("androidx.lifecycle", "lifecycle-viewmodel-android", "2.9.4")
                    includeVersion("androidx.core", "core", "1.2.0")
                    includeVersion("androidx.core", "core-ktx", "1.2.0")
                    includeVersion("androidx.lifecycle", "lifecycle-runtime", "2.11.0")
                    includeVersion("androidx.versionedparcelable", "versionedparcelable", "1.1.0")
                    includeVersion("org.jetbrains.kotlin", "kotlin-build-tools-impl", "2.4.21")
                    includeVersion("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.21")
                    includeVersion("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10")
                    includeModule("androidx.lifecycle", "lifecycle-viewmodel-savedstate-android")
                    includeModule("org.jetbrains.androidx.lifecycle", "lifecycle-viewmodel-savedstate-android")
                    includeModule("org.jetbrains.androidx.lifecycle", "lifecycle-runtime-compose-android")
                    includeModule("org.jetbrains.androidx.lifecycle", "lifecycle-viewmodel-compose-android")
                }
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven { url = uri("https://repo1.maven.org/maven2") }
        mavenCentral()
        // Development only: third-party mirror for networks where Maven Central is slow.
        if (!officialRepositoriesOnly) maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

include(":androidApp")
include(":shared")
