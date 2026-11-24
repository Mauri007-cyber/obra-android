import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val keystorePropsFile = rootProject.file("keystore.properties")

// Dirección del Worker propio que genera la imagen (plan gratuito de Cloudflare).
// Por defecto apunta al Worker del proyecto: una APK recién compilada nunca sale sin servicio.
// Para usar otro Worker: -PimagenUrl=https://otro.workers.dev
// Un -PimagenUrl= vacío también cae en el Worker del proyecto: si no, la APK sale desconectada
// y el botón de generar imagen queda apagado sin explicación (lo vigila ConfiguracionTest).
val urlImagen = providers.gradleProperty("imagenUrl").orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "https://obra-imagen.obra-reformas.workers.dev"

android {
    namespace = "es.obra.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "es.obra.app"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Solo ARM en la release normal. Con -Puniversal=true se incluye x86_64,
        // que es lo que exige el emulador para poder probar ARCore.
        if (providers.gradleProperty("universal").orNull != "true") {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
        versionCode = 15
        versionName = "0.9.6"

        // Manifiesto de autoactualización. Cambiar por la URL real del release.
        buildConfigField(
            "String",
            "UPDATE_URL",
            "\"https://raw.githubusercontent.com/Mauri007-cyber/obra-android/main/update.json\""
        )

        buildConfigField("String", "IMAGEN_URL", "\"$urlImagen\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            val props = Properties().apply { load(FileInputStream(keystorePropsFile)) }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // La APK de pruebas convive con la app instalada y no sustituye sus datos.
            applicationIdSuffix = ".prueba"
            versionNameSuffix = "-prueba"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin clave de producción el APK queda sin firmar; nunca usar la clave debug.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")

    // Room 2.7 es la primera que funciona con KSP2 (Kotlin 2.2).
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // AR: SceneView envuelve ARCore + Filament (Apache-2.0). 4.18.0 es la última que compila
    // con AGP 8 / compileSdk 36; la 4.69 exige AGP 9.4 y compileSdk 37.
    implementation("io.github.sceneview:arsceneview:4.18.0")
    implementation("com.google.ar:core:1.45.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // Pruebas de interfaz sobre las pantallas reales (copia, planos y presupuesto).
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
