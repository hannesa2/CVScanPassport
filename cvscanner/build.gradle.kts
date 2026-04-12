import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.android.library")
    id("maven-publish")
}

version = "1.4"

android {
    namespace = "info.hannes.cvscanner"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
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
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.gms:play-services-basement:18.9.0")
    implementation("com.google.android.gms:play-services-vision:20.1.3")
    implementation("com.github.hannesa2:AndroidVisionPipeline:1.3")
    // The source code of OpenCV is here https://git.mxtracks.info/opencv/openCV-android-sdk
    // The code was too big for github, but the main problem was jitpack.io was not able to build
    // https://github.com/jitpack/jitpack.io/issues/4119
    api("OpenCV_all_together_samples:opencv:4.7.0.0")
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
