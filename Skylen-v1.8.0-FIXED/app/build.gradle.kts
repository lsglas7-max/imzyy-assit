plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun configValue(property: String, environment: String, fallback: String): String =
    providers.gradleProperty(property).orNull?.takeIf { it.isNotBlank() }
        ?: System.getenv(environment)?.takeIf { it.isNotBlank() }
        ?: fallback

fun quoteBuildConfig(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val apiOne = quoteBuildConfig(configValue(
    "galaxyAiApiUrl", "GALAXY_AI_API_URL", "https://api.kyio.web.id/api/v2/ai/gpt-5?q="
))
val apiTwo = quoteBuildConfig(configValue(
    "galaxyAiApiUrl2", "GALAXY_AI_API_URL_2", "https://api.kyio.web.id/api/v2/ai/gpt-6-luna?q="
))

android {
    namespace = "com.imzz.galaxyassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.imzz.galaxyassistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.8.0"
        buildConfigField("String", "AI_API_URL", "\"$apiOne\"")
        buildConfigField("String", "AI_API_URL_2", "\"$apiTwo\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}
