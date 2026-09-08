plugins {
    alias(libs.plugins.android.application)
}

import groovy.json.JsonSlurper
import org.gradle.api.tasks.Sync

val generatedModdedPackAssets = file("build/generated/packAssets/modded")
val generatedOriginalPackAssets = file("build/generated/packAssets/original")
val fmodBankPacks = rootProject.file("fmod_bank_packs")
val prepareModdedPackAssets = tasks.register<Sync>("prepareModdedPackAssets") {
    from(fmodBankPacks) {
        include("modded-*.bydbank", "assetto-common*.bydbank", "index.json")
        into("packs")
    }
    into(generatedModdedPackAssets)
}
val prepareOriginalPackAssets = tasks.register("prepareOriginalPackAssets") {
    val output = generatedOriginalPackAssets
    inputs.dir(fmodBankPacks)
    outputs.dir(output)
    doLast {
        val indexFile = fmodBankPacks.resolve("index.json")
        require(indexFile.isFile) {
            "Missing $indexFile. Run python3 tools/build_fmod_bank_packs.py first."
        }
        @Suppress("UNCHECKED_CAST")
        val index = JsonSlurper().parse(indexFile) as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val packs = index["packs"] as List<Map<String, Any>>
        val selected = packs.filter { pack ->
            val active = pack["active"] as Boolean
            val group = pack["group"] as String
            val dependency = pack["dependency"] as? Boolean ?: false
            active && (group == "original_cars_pack" || dependency)
        }
        val destination = output.resolve("packs")
        if (destination.exists()) {
            destination.deleteRecursively()
        }
        destination.mkdirs()
        indexFile.copyTo(destination.resolve("index.json"), overwrite = true)
        selected.forEach { pack ->
            val asset = pack["asset"] as String
            val source = fmodBankPacks.resolve(asset)
            require(source.isFile) { "Missing original bank archive: $source" }
            source.copyTo(destination.resolve(asset), overwrite = true)
        }
    }
}
tasks.named("preBuild").configure { dependsOn(prepareModdedPackAssets, prepareOriginalPackAssets) }


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
