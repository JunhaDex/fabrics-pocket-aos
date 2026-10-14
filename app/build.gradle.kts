import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 커밋하지 않는 기기별 덮어쓰기. debug.startUrl로 Debug 시작 URL을 바꾼다(예: bridge-test).
// configuration cache가 파일 변경을 감지하도록 providers로 읽는다.
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

android {
    namespace = "com.fabrics.pocket"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 런타임 버전. 앱마다 바뀌지 않으며 릴리스 태그와 같은 커밋에서 올린다.
        buildConfigField("String", "RUNTIME_VERSION", "\"0.1.0\"")
    }

    flavorDimensions += "app"
    productFlavors {
        create("pocket") {
            dimension = "app"
            applicationId = "com.fabrics.pocket"
            // UA에 덧붙는 앱 토큰. 웹·네트워크 층에 보이므로 래퍼 이름을 쓰지 않는다.
            buildConfigField("String", "APP_TOKEN", "\"StylistApp\"")
            manifestPlaceholders["screenOrientation"] = "unspecified"
        }
    }

    buildTypes {
        debug {
            val startUrl = localProperties.getProperty("debug.startUrl") ?: "http://localhost:3000"
            buildConfigField("String", "START_URL", "\"$startUrl\"")
            buildConfigField("String[]", "ALLOWED_HOSTS", "{\"localhost\"}")
        }
        release {
            buildConfigField("String", "START_URL", "\"\"")
            buildConfigField("String[]", "ALLOWED_HOSTS", "{}")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}