import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val keyProperties = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun String?.orEmptyEnv(envName: String): String? = this?.takeIf { it.isNotBlank() }
    ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

val keystorePath = keyProperties.getProperty("keystorePath").orEmptyEnv("KEYSTORE_PATH")
val keystorePassword = keyProperties.getProperty("keystorePassword").orEmptyEnv("KEYSTORE_PASSWORD")
val keyAliasName = keyProperties.getProperty("keyAlias").orEmptyEnv("KEY_ALIAS")
val keyPasswordValue = keyProperties.getProperty("keyPassword").orEmptyEnv("KEY_PASSWORD")

android {
    namespace = "com.miappvideos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.miappvideos"
        minSdk = 24
        targetSdk = 34
        versionCode = 21
        versionName = "2.1.0"

        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${localProperties.getProperty("youtubeApiKey", "")}\""
        )
        buildConfigField(
            "String",
            "GOOGLE_SIGNIN_CLIENT_ID",
            "\"${localProperties.getProperty("googleSignInClientId", "")}\""
        )
        buildConfigField(
            "String",
            "PROXY_KEY",
            "\"${localProperties.getProperty("proxyKey", "")}\""
        )
        buildConfigField(
            "String",
            "PROXY_PUBLIC_URL",
            "\"${localProperties.getProperty("proxyPublicUrl", "https://mytube-proxy-q284.onrender.com")}\""
        )
    }

    signingConfigs {
        create("release") {
            val storeFile_ = keystorePath?.let { rootProject.file(it) }
            if (storeFile_ != null && storeFile_.exists() &&
                keystorePassword != null && keyAliasName != null && keyPasswordValue != null
            ) {
                storeFile = storeFile_
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.1")

    // Retrofit + Gson for Piped API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coil for images
    implementation("io.coil-kt:coil:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // RecyclerView + CardView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // YouTube Data API v3
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-youtube:v3-rev222-1.25.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // NewPipeExtractor
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.4")
    implementation("org.jsoup:jsoup:1.17.2")
}
