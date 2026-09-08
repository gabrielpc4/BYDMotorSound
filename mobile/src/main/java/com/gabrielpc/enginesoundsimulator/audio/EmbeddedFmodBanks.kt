package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.simulation.AssettoPhysics
import com.gabrielpc.enginesoundsimulator.simulation.AssettoPhysicsLoader
import java.io.InputStream

/**
 * APK metadata stays separate from the large archives. Selected audio is published into the
 * same private [FmodBankStore] that update-sized APKs read after this install is overwritten.
 */
internal class EmbeddedFmodBanks(
    context: Context,
    private val store: FmodBankStore,
) {
    private val assets = context.assets
    private val packIds by lazy { assets.list(ROOT).orEmpty().toSet() }

    fun contains(profile: FmodBankProfile): Boolean =
        profile.bankPackId in packIds &&
            FmodBankProfiles.commonPackId in packIds &&
            FmodBankProfiles.commonStringsPackId in packIds

    fun bankFiles(profile: FmodBankProfile): FmodBankFiles = synchronized(preparationLock) {
        require(contains(profile)) { "The APK does not contain ${profile.displayName}." }
        listOf(
            FmodBankProfiles.originalCarsPackId to FmodBankProfiles.commonStringsPackId,
            FmodBankProfiles.originalCarsPackId to FmodBankProfiles.commonPackId,
            profile.packGroup to profile.bankPackId,
        ).forEach { (group, id) ->
            store.prepareEmbeddedPack(group, id, assets, "$ROOT/$id")
        }

        FmodBankFiles(
            commonStrings = store.sharedBankFile(FmodBankProfiles.commonStringsPackId),
            common = store.sharedBankFile(FmodBankProfiles.commonPackId),
            car = store.bankFile(profile),
            physics = store.physicsFile(profile),
        )
    }

    /**
     * Installs every embedded pack into app-private storage so a later smaller APK can keep
     * using the catalog. Each pack uses the verified publish path; already-matching packs are
     * left in place.
     */
    fun installAllPacks(): FmodBankImportResult {
        var importedPackCount = 0
        var alreadyInstalledPackCount = 0
        val failures = mutableListOf<String>()

        packIds.sorted().forEach { packId ->
            runCatching {
                if (store.prepareEmbeddedPack(assets, "$ROOT/$packId")) {
                    importedPackCount += 1
                } else {
                    alreadyInstalledPackCount += 1
                }
            }.onFailure { error ->
                failures += error.message ?: error.toString()
            }
        }

        return FmodBankImportResult(
            importedPackCount = importedPackCount,
            alreadyInstalledPackCount = alreadyInstalledPackCount,
            failures = failures,
        )
    }

    fun physics(profile: FmodBankProfile): AssettoPhysics =
        AssettoPhysicsLoader.load(assets.open("$ROOT/${profile.bankPackId}/physics.json"))

    fun openPreview(profile: FmodBankProfile): InputStream? =
        runCatching { assets.open("$ROOT/${profile.bankPackId}/preview") }.getOrNull()

    private companion object {
        const val ROOT = "embedded_banks"
        val preparationLock = Any()
    }
}
