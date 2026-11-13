import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.Base64
import java.net.URI
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val localSettings = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun publicSetting(name: String): String = providers.environmentVariable(name)
    .orElse(providers.gradleProperty(name)).getOrElse(localSettings.getProperty(name, ""))
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""
val supabaseUrl = publicSetting("SUPABASE_URL")
val supabaseKey = publicSetting("SUPABASE_PUBLISHABLE_KEY").ifBlank { publicSetting("SUPABASE_ANON_KEY") }
// Optional public development CA, scoped to this project's exact HTTPS host in DEBUG only.
// No certificate/private key is committed; release builds keep Android's normal system trust.
val developmentCaPath = publicSetting("PAWQUEST_DEBUG_CA_CERT")
val developmentCa = developmentCaPath.takeIf {it.isNotBlank()}?.let {rootProject.file(it)}
abstract class GeneratePawQuestDebugTrust : DefaultTask() {
    @get:Input abstract val projectHost: org.gradle.api.provider.Property<String>
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val developmentCertificate: org.gradle.api.file.RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    @TaskAction fun generate() {
        val directory=outputDirectory.get().asFile
        val raw=directory.resolve("raw/pawquest_development_ca.pem")
        val caFile=developmentCertificate.orNull?.asFile
        val host=projectHost.get()
        val projectTrust=if(caFile!=null) {
            require(host.isNotBlank() && host.matches(Regex("[a-zA-Z0-9.-]+"))) {"Development CA requires a valid Supabase host"}
            val certificate=caFile.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            certificate.checkValidity()
            require(certificate.basicConstraints>=0) {"Development certificate must be a CA"}
            raw.parentFile.mkdirs();caFile.copyTo(raw,overwrite=true)
            """<domain-config cleartextTrafficPermitted="false"><domain includeSubdomains="false">$host</domain>
                <trust-anchors><certificates src="system"/><certificates src="@raw/pawquest_development_ca"/></trust-anchors></domain-config>"""
        } else {Files.deleteIfExists(raw.toPath());""}
        directory.resolve("xml").mkdirs()
        directory.resolve("xml/pawquest_debug_network_security.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
            <network-security-config><base-config cleartextTrafficPermitted="false"/>
            <domain-config cleartextTrafficPermitted="true"><domain includeSubdomains="false">10.0.2.2</domain>
            <domain includeSubdomains="false">127.0.0.1</domain><domain includeSubdomains="false">localhost</domain></domain-config>
            $projectTrust</network-security-config>""".trimIndent())
    }
}
val generateDebugTrust=tasks.register<GeneratePawQuestDebugTrust>("generatePawQuestDebugTrust") {
    projectHost.set(runCatching {URI(supabaseUrl).host}.getOrNull().orEmpty())
    developmentCa?.let {developmentCertificate.set(it)}
    outputDirectory.set(layout.buildDirectory.dir("generated/pawquestDebugTrust/res"))
}
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateDebugTrust,GeneratePawQuestDebugTrust::outputDirectory)
    }
}
require(!supabaseKey.startsWith("sb_secret_")) { "Only a Supabase publishable/anon key belongs in the Android app." }
if (supabaseKey.isNotBlank() && !supabaseKey.startsWith("sb_publishable_")) {
    val role = runCatching {
        val payload = String(Base64.getUrlDecoder().decode(supabaseKey.split('.')[1]))
        Regex("\"role\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
    }.getOrNull()
    require(role == "anon") { "Only a Supabase publishable/anon key belongs in the Android app." }
}


kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":shared"))
    implementation(libs.androidx.work)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.koin.core)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.pawquest"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", quoted(supabaseUrl))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quoted(supabaseKey))
        applicationId = "com.pawquest"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = publicSetting("PAWQUEST_VERSION_CODE").ifBlank { "1" }.toInt()
        versionName = publicSetting("PAWQUEST_VERSION_NAME").ifBlank { "1.0.0" }
    }
    // Upload key comes from env/Gradle/local.properties only; keystores are git-ignored.
    val releaseStore = publicSetting("PAWQUEST_RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStore.isNotBlank()) create("release") {
            storeFile = rootProject.file(releaseStore)
            storePassword = publicSetting("PAWQUEST_RELEASE_STORE_PASSWORD")
            keyAlias = publicSetting("PAWQUEST_RELEASE_KEY_ALIAS")
            keyPassword = publicSetting("PAWQUEST_RELEASE_KEY_PASSWORD")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
