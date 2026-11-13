rootProject.name = "PawQuest"

pluginManagement {
    repositories {
        // Optional verified local artifacts for builds on a metered connection.
        val localRepository = file(".gradle/local-maven")
        if (localRepository.isDirectory) {
            maven { url = uri(localRepository) }
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
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Native rendering AARs must come from Maven Central; the optional mirror
        // used below blocks SceneView binary artifacts in this environment.
        exclusiveContent {
            forRepository { maven { url = uri("https://repo1.maven.org/maven2") } }
            filter {
                includeGroup("io.github.sceneview")
                includeGroup("com.google.android.filament")
            }
        }
        // Browser-downloaded artifacts; restrict this to manually supplied dependencies.
        val manualRepository = file(".gradle/manual-maven")
        if (manualRepository.isDirectory) {
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
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://repo1.maven.org/maven2") }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
