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

abstract class CopyChessgroundPieceAssetsTask : DefaultTask() {
  @get:InputFile
  abstract val packageConfigFile: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun copy() {
    val configFile = packageConfigFile.get().asFile
    check(configFile.exists()) {
      "Missing $configFile — run `flutter pub get` from the project root first."
    }

    val config = JsonSlurper().parse(configFile) as Map<*, *>
    @Suppress("UNCHECKED_CAST")
    val packages = config["packages"] as List<Map<*, *>>
    val chessground = packages.first { it["name"] == "chessground" }
    val packageDir = File(URI(chessground["rootUri"] as String))
    val sourceDir = File(packageDir, "assets/piece_sets/cburnett")
    check(sourceDir.exists()) { "Could not find cburnett piece assets at $sourceDir" }

    val outDir = File(outputDirectory.get().asFile, "drawable-nodpi")
    outDir.deleteRecursively()
    outDir.mkdirs()

    sourceDir.listFiles { f -> f.isFile && f.extension == "webp" }
      ?.forEach { source ->
        source.copyTo(File(outDir, "piece_cburnett_${source.nameWithoutExtension.lowercase()}.webp"))
      }
  }
}

val copyChessgroundPieceAssets = tasks.register<CopyChessgroundPieceAssetsTask>("copyChessgroundPieceAssets") {
  packageConfigFile.set(file("../../.dart_tool/package_config.json"))
  outputDirectory.set(layout.buildDirectory.dir("generated/chessgroundAssets/res"))
}

androidComponents {
  onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(
      copyChessgroundPieceAssets,
      CopyChessgroundPieceAssetsTask::outputDirectory
    )
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
