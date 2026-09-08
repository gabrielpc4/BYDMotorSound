plugins {
    alias(libs.plugins.android.application)
}

import org.gradle.api.tasks.Exec

val generatedModdedPackAssets = file("build/generated/packAssets/modded")
val generatedOriginalPackAssets = file("build/generated/packAssets/original")
val fmodBankPacks = rootProject.file("fmod_bank_packs")
val prepareInstallerAssetsScript = rootProject.file("tools/prepare_installer_pack_assets.py")
listOf("original", "modded").forEach { group ->
    tasks.register<Exec>("prepare${group.replaceFirstChar(Char::uppercase)}PackAssets") {
        val output = file("build/generated/packAssets/$group")
        inputs.dir(fmodBankPacks)
        inputs.file(prepareInstallerAssetsScript)
        outputs.dir(output)
        commandLine("python3", prepareInstallerAssetsScript, "--group", group, "--output", output)
    }
}
tasks.named("preBuild").configure {
    dependsOn(
        tasks.named("prepareOriginalPackAssets"),
        tasks.named("prepareModdedPackAssets"),
    )
}


android {
    namespace = "com.gabrielpc.enginesoundsinstaller"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gabrielpc.enginesoundsinstaller"
        minSdk = 25
        targetSdk = 25
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { buildConfig = true }

    flavorDimensions += "payload"
    productFlavors {
        create("modded") {
            dimension = "payload"
            applicationIdSuffix = ".modded"
            buildConfigField("String", "PAYLOAD_GROUP", "\"modded_car_packs\"")
        }
        create("original") {
            dimension = "payload"
            applicationIdSuffix = ".original"
            buildConfigField("String", "PAYLOAD_GROUP", "\"original_cars_pack\"")
        }
    }

    signingConfigs {
        // The BYD sideload path rejects unsigned APKs. Keep the same stable local certificate
        // convention as the dashboard so reinstalling this helper does not require a manual
        // uninstall first.
        getByName("debug") { enableV2Signing = true }
    }


    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }

    sourceSets.getByName("modded").assets.srcDir(generatedModdedPackAssets)
    sourceSets.getByName("original").assets.srcDir(generatedOriginalPackAssets)

    lint {
        // These installers target the same BYD DiLink Android compatibility level as the
        // sideloaded simulator APK and are not distributed through Google Play.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("engine-sounds-audio-installer-${variant.name}.apk")
        }
    }
}
