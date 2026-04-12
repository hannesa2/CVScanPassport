plugins {
    id("com.android.application")
}

android {
    namespace = "info.hannes.cvscanner.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "devliving.online.cvscannersample"
        minSdk = 23
        targetSdk = 34
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
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.getbase:floatingactionbutton:1.10.1")
    implementation(project(":cvscanner"))
}

