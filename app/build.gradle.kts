plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.liau.printeragent"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.liau.printeragent"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {

            // "io.github.smiley4:ktor-openapi:5.2.0" requires to exclude for build to successes
            excludes += arrayOf(
                "META-INF/*",
                "draftv3/schema",
                "draftv4/schema",
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Ktor Server Core
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.server.status.pages)

    //rooms
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    //workmanager
    implementation(libs.workmanager.ktx)

    //printer lib
    implementation(libs.dantsu.android.printer)

    implementation("com.sunmi:printerlibrary:1.0.24")

    implementation("com.sunmi:printerx:1.0.15")


}