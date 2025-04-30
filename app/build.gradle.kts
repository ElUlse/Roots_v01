plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.roots_d01"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.roots_d01"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // *** CORRECTED LINES ***
        // Pass the property value directly, Gradle handles quoting for String fields
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", property("MAPBOX_PUBLIC_TOKEN").toString())
        buildConfigField("String", "VALHALLA_URL", property("VALHALLA_URL").toString())
        // *** END OF CORRECTED LINES ***
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // MapLibre dependency
    implementation("org.maplibre.gl:android-sdk:11.8.6")

    // Add Mapbox Java SDK (includes GeoJSON classes) - Use a recent version
    implementation("com.mapbox.maps:android:11.11.0") // Or check for the latest 6.x version

    // Location services for location access
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Other dependencies
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation ("com.google.code.gson:gson:2.8.8")
    implementation ("com.google.android.material:material:1.12.0") // Or the latest version

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Use the latest stable version
    implementation("androidx.activity:activity-ktx:1.9.0") // Often used with ViewModelProvider

// Retrofit for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0") // Use the latest version
// Gson converter for Retrofit (to automatically handle JSON)
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // Use the same version as Retrofit
// OkHttp Logging Interceptor (optional, but helpful for debugging)
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0") // Use latest OkHttp3/4 version
    implementation("com.github.skydoves:colorpickerview:2.3.0")



    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

}
