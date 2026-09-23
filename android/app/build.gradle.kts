import groovy.json.JsonSlurper
import java.util.Properties
import java.io.FileInputStream
import java.io.File
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val chessgroundAssetsDir = layout.buildDirectory.dir("generated/chessgroundAssets/res/drawable-nodpi")
val copyChessgroundPieceAssets by tasks.registering {
  val packageConfigFile = file("../../.dart_tool/package_config.json")
  val outputDir = chessgroundAssetsDir.get().asFile

  inputs.file(packageConfigFile)
  outputs.dir(outputDir)

  doLast {
    check(packageConfigFile.exists()) {
      "Missing $packageConfigFile — run `flutter pub get` from the project root first."
    }
    val config = JsonSlurper().parse(packageConfigFile) as Map<*, *>
    @Suppress("UNCHECKED_CAST")
    val packages = config["packages"] as List<Map<*, *>>
    val chessground = packages.first { it["name"] == "chessground" }
    val packageDir = File(URI(chessground["rootUri"] as String))
    val sourceDir = File(packageDir, "assets/piece_sets/cburnett")

    check(sourceDir.exists()) { "Could not find cburnett piece assets at $sourceDir" }

    outputDir.deleteRecursively()
    outputDir.mkdirs()

    sourceDir.listFiles { f -> f.isFile && f.extension == "webp" }
      ?.forEach { source ->
        source.copyTo(File(outputDir, "piece_cburnett_${source.nameWithoutExtension.lowercase()}.webp"))
      }
  }
}

android {
    namespace = "org.lichess.mobileV2"
    // compileSdk = flutter.compileSdkVersion
    // home_widget pulls in glance-appwidget and remote-creation-android, both of which
    // declare in their AAR metadata that all dependents (including the app) must compile
    // against SDK 37+. This cannot be suppressed — it is enforced by AGP at build time.
    compileSdk = 37
    ndkVersion = flutter.ndkVersion

    compileOptions {
        // Flag required by flutter_local_notifications package
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // Flag required by flutter_local_notifications package
        multiDexEnabled = true
        applicationId = "org.lichess.mobileV2"
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

  sourceSets {
    getByName("main") {
      res.srcDir(chessgroundAssetsDir.get().asFile)
    }
  }
}


kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Dependency required by flutter_local_notifications package
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.core:core-splashscreen:1.0.1")
}

tasks.matching { it.name.matches(Regex("merge.*Resources")) }.configureEach {
  dependsOn(copyChessgroundPieceAssets)
}
