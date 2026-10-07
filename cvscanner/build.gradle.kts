import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.android.library")
    id("maven-publish")
}

version = "1.4"

android {
    namespace = "info.hannes.cvscanner"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        renderscriptTargetApi = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.gms:play-services-basement:18.11.0")
    implementation("com.google.android.gms:play-services-vision:20.1.3")
    implementation("com.github.hannesa2:AndroidVisionPipeline:1.3")
    api("org.opencv:opencv:5.0.0.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}
