plugins {
    id("com.android.application")
}

android {
    namespace = "info.hannes.cvscanner.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "devliving.online.cvscannersample"
        minSdk = 23
        targetSdk = 37
        versionCode = 3
        versionName = "1.1"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.getbase:floatingactionbutton:1.10.1")
    implementation(project(":cvscanner"))
}

