import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cl.inacap.riegoiot"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.inacap.riegoiot"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        val mqttHost = localProperties.getProperty("MQTT_HOST") ?: "10.0.2.2"
        buildConfigField("String", "MQTT_HOST", "\"$mqttHost\"")
        // SIMULADO=true en local.properties: usa la simulacion de la U2 (sin broker ni Arduino)
        val simulado = localProperties.getProperty("SIMULADO")?.toBoolean() ?: false
        buildConfigField("boolean", "SIMULADO", "$simulado")
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hivemq.mqtt.client)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}