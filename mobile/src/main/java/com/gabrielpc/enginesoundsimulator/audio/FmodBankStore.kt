package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import android.util.JsonReader
import com.gabrielpc.enginesoundsimulator.simulation.AssettoPhysics
import com.gabrielpc.enginesoundsimulator.simulation.AssettoPhysicsLoader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

internal data class FmodBankImportResult(
    val importedPackCount: Int,
    val alreadyInstalledPackCount: Int,
    val failures: List<String>,
) {
    val foundPacks: Boolean get() = importedPackCount > 0 || alreadyInstalledPackCount > 0 || failures.isNotEmpty()
}

/**
 * Atomically publishes file-manager-staged FMOD Studio banks. Runtime playback
 * only receives an already verified on-disk bank path; it never reads archives
 * or decodes audio on its control thread.
 */
internal class FmodBankStore(
    filesDirectory: File,
    private val stagedImportDirectory: File? = null,
    private val externalPacksDirectory: File? = null,
) {
    private val packsDirectory = File(filesDirectory, "fmod-banks")
    private val installedPackRoots = listOfNotNull(
        packsDirectory,
        externalPacksDirectory,
    ).distinctBy { it.absolutePath }

    fun installedPackIds(): Set<String> = installedPackRoots
        .flatMap(::packDirectories)
        .filter { File(it, MANIFEST_NAME).isFile }
        .filter { directory ->
            runCatching {
                val manifest = File(directory, MANIFEST_NAME).inputStream().use(::readManifest)
            manifest.id == directory.name && manifest.group == directory.parentFile?.name
            }.getOrDefault(false)
        }
        .mapTo(linkedSetOf()) { "${it.parentFile?.name}/${it.name}" }

    fun diagnoseStorage(): FmodBankStorageDiagnostics {
        val lines = mutableListOf<String>()
        val validPackIds = linkedSetOf<String>()
        var issueCount = 0

        installedPackRoots.forEachIndexed { index, root ->
            val label = if (index == 0) "PRIVATE RUNTIME" else "ANDROID/DATA DIRECT"
            lines += "$label: ${root.absolutePath}"
            if (!root.exists()) {
                lines += "  NOT PRESENT — this optional bank source is not in use"
                return@forEachIndexed
            }
            if (!root.isDirectory || !root.canRead()) {
                lines += "  ERROR — path is not a readable directory"
                issueCount++
                return@forEachIndexed
            }

            val rootPacks = packDirectories(root)
            lines += "  Found ${rootPacks.size} pack directories"
            SUPPORTED_IMPORT_GROUPS.sorted().forEach { group ->
                val groupDirectory = File(root, group)
                val count = groupDirectory.listFiles().orEmpty().count(File::isDirectory)
                lines += "  $group: $count"
            }

            rootPacks.sortedBy { "${it.parentFile?.name}/${it.name}" }.forEach { directory ->
                val packLabel = "${directory.parentFile?.name}/${directory.name}"
                runCatching {
                    val manifestFile = File(directory, MANIFEST_NAME)
                    require(manifestFile.isFile) { "manifest.json is missing" }
                    val manifest = manifestFile.inputStream().use(::readManifest)
                    require(manifest.group == directory.parentFile?.name) {
                        "manifest group is ${manifest.group}"
                    }
                    require(manifest.id == directory.name) {
                        "manifest id is ${manifest.id}"
                    }
                    require(manifest.files.count { file ->
                        file.path.startsWith("bank/") && file.path.endsWith(".bank")
                    } == 1) {
                        "exactly one bank file is required"
                    }
                    manifest.files.forEach { entry ->
                        val payload = safeDestination(directory, entry.path)
                        require(payload.isFile) { "${entry.path} is missing" }
                        require(payload.length() == entry.bytes) {
                            "${entry.path} has ${payload.length()} bytes; expected ${entry.bytes}"
                        }
                    }
                    validPackIds += packLabel
                }.onFailure { error ->
                    issueCount++
                    lines += "  ERROR $packLabel — ${error.message ?: error::class.java.simpleName}"
                }
            }
        }

        lines += "VALID PACKS: ${validPackIds.size}"
        lines += "ISSUES: $issueCount"
        return FmodBankStorageDiagnostics(
            validPackCount = validPackIds.size,
            issueCount = issueCount,
            lines = lines,
        )
    }

    fun bankFile(profile: FmodBankProfile): File = bankFile(profile.packGroup, profile.bankPackId, profile.displayName)

    fun sharedBankFile(packId: String): File = bankFile(FmodBankProfiles.originalCarsPackId, packId, "required shared FMOD")

    fun physicsFile(profile: FmodBankProfile): File {
        val directory = requireNotNull(installedDirectory(profile.packGroup, profile.bankPackId)) {
            "Install the ${profile.displayName} bank before playing it."
        }
        val manifest = File(directory, MANIFEST_NAME).inputStream().use(::readManifest)
        val expectedPath = "profiles/${profile.id}/physics.json"
        require(manifest.files.any { it.path == expectedPath }) {
            "Installed ${profile.displayName} package has no matching Assetto physics."
        }
        return safeDestination(directory, expectedPath).also {
            require(it.isFile) { "Installed ${profile.displayName} physics is missing." }
        }
    }

    fun previewFile(profile: FmodBankProfile): File? = runCatching {
        val directory = installedDirectory(profile.packGroup, profile.bankPackId) ?: return null
        val manifest = File(directory, MANIFEST_NAME).inputStream().use(::readManifest)
        val path = manifest.files.firstOrNull { it.path.startsWith("preview/") }?.path ?: return null
        safeDestination(directory, path).takeIf(File::isFile)
    }.getOrNull()

    fun calibrationInputHashes(profile: FmodBankProfile): FmodCalibrationInputHashes {
        val carDirectory = requireNotNull(installedDirectory(profile.packGroup, profile.bankPackId)) {
            "Install the ${profile.displayName} bank before calibrating it."
        }
        val carManifest = File(carDirectory, MANIFEST_NAME).inputStream().use(::readManifest)
        val carBank = carManifest.files.single { it.path.startsWith("bank/") && it.path.endsWith(".bank") }
        val physics = carManifest.files.single { it.path == "profiles/${profile.id}/physics.json" }
        val commonBank = sharedBankManifest(FmodBankProfiles.commonPackId)
        val commonStringsBank = sharedBankManifest(FmodBankProfiles.commonStringsPackId)

        return FmodCalibrationInputHashes(
            carBankSha256 = carBank.sha256,
            physicsSha256 = physics.sha256,
            commonBankSha256 = commonBank.sha256,
            commonStringsBankSha256 = commonStringsBank.sha256,
        )
    }

    fun hasStagedPacks(): Boolean = stagedImportDirectory
        ?.takeIf(File::isDirectory)
        ?.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .filter { it.name in SUPPORTED_IMPORT_GROUPS }
        .any { groupDirectory ->
            groupDirectory.listFiles()
                .orEmpty()
                .any { archive -> archive.isFile && archive.extension.equals(ARCHIVE_EXTENSION, ignoreCase = true) }
        }

    /**
     * Consumes `.bydbank` archives copied to the app-specific external-storage staging folder.
     *
     * This deliberately replaces the companion installer as the normal vehicle workflow. The
     * files remain user-copyable through a file manager, but are still SHA-256-validated and
     * atomically published into private app storage before FMOD can load them. Successfully
     * consumed archives are removed to avoid keeping a second multi-gigabyte copy on the head
     * unit.
     */
    @Synchronized
    fun importStagedPacks(): FmodBankImportResult {
        val root = stagedImportDirectory?.takeIf(File::isDirectory)
            ?: return FmodBankImportResult(0, 0, emptyList())
        var imported = 0
        var alreadyInstalled = 0
        val failures = mutableListOf<String>()
        root.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .sortedBy { it.name }
            .forEach { groupDirectory ->
                if (groupDirectory.name !in SUPPORTED_IMPORT_GROUPS) return@forEach
                groupDirectory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension.equals(ARCHIVE_EXTENSION, ignoreCase = true) }
                    .sortedBy { it.name }
                    .forEach { archive ->
                        runCatching {
                            val archiveManifest = ZipFile(archive).use { zip ->
                                val manifestEntry = requireNotNull(zip.getEntry(MANIFEST_NAME)) {
                                    "FMOD bank package has no manifest"
                                }
                                zip.getInputStream(manifestEntry).use(::readManifest)
                            }
                            require(archiveManifest.group == groupDirectory.name) {
                                "Archive group does not match ${groupDirectory.name}"
                            }
                            val existingManifest = installedDirectory(
                                archiveManifest.group,
                                archiveManifest.id,
                            )?.let { directory ->
                                File(directory, MANIFEST_NAME).inputStream().use(::readManifest)
                            }
                            if (existingManifest == archiveManifest) {
                                alreadyInstalled++
                            } else {
                                FileInputStream(archive).use { source ->
                                    install(archiveManifest.group, archiveManifest.id, source)
                                }
                                imported++
                            }
                            // The Mac/source copy remains the recovery artifact. The car-side
                            // staging copy is disposable once it has been verified and published.
                            archive.delete()
                        }.onFailure { error ->
                            failures += "${groupDirectory.name}/${archive.name}: ${error.message ?: error::class.java.simpleName}"
                        }
                    }
            }
        return FmodBankImportResult(imported, alreadyInstalled, failures)
    }

    private fun bankFile(group: String, packId: String, displayName: String): File {
        val directory = requireNotNull(installedDirectory(group, packId)) {
            "Install the $displayName bank before playing it."
        }
        val manifest = File(directory, MANIFEST_NAME).inputStream().use(::readManifest)
        val bank = manifest.files.singleOrNull { it.path.startsWith("bank/") && it.path.endsWith(".bank") }
            ?: error("Installed $displayName package has no playable bank.")
        return safeDestination(directory, bank.path).also {
            require(it.isFile) { "Installed $displayName bank is missing ${bank.path}." }
        }
    }

    private fun sharedBankManifest(packId: String): FmodBankFile {
        val directory = requireNotNull(installedDirectory(FmodBankProfiles.originalCarsPackId, packId)) {
            "Install the required shared FMOD bank before calibrating."
        }
        val manifest = File(directory, MANIFEST_NAME).inputStream().use(::readManifest)

        return manifest.files.single { it.path.startsWith("bank/") && it.path.endsWith(".bank") }
    }

    @Synchronized
    fun install(group: String, packId: String, source: InputStream) {
        require(SAFE_PACK_ID.matches(group) && SAFE_PACK_ID.matches(packId)) { "Invalid FMOD bank id" }
        packsDirectory.mkdirs()
        val groupDirectory = File(packsDirectory, group).apply { mkdirs() }
        val incoming = File.createTempFile(".$packId-", ".bydbank", groupDirectory)
        try {
            FileOutputStream(incoming).use { output -> copyInterruptibly(source, output) }
            installArchive(group, packId, incoming)
        } finally {
            incoming.delete()
        }
    }

    @Synchronized
    fun deleteAll() {
        packsDirectory.listFiles()?.forEach(::deleteRecursively)
    }

    private fun installArchive(expectedGroup: String, expectedPackId: String, archive: File) {
        val groupDirectory = File(packsDirectory, expectedGroup).apply { mkdirs() }
        val stage = File(groupDirectory, ".staging-$expectedPackId-${System.nanoTime()}")
        check(stage.mkdirs()) { "Could not create FMOD bank staging directory" }
        try {
            ZipFile(archive).use { zip ->
                val manifestEntry = requireNotNull(zip.getEntry(MANIFEST_NAME)) { "FMOD bank package has no manifest" }
                val manifest = zip.getInputStream(manifestEntry).use(::readManifest)
                require(manifest.id == expectedPackId) { "FMOD bank id does not match destination" }
                require(manifest.group == expectedGroup) { "FMOD bank group does not match destination" }
                manifest.files.forEach { entry ->
                    val sourceEntry = requireNotNull(zip.getEntry(entry.path)) { "FMOD bank package is missing ${entry.path}" }
                    val destination = safeDestination(stage, entry.path)
                    destination.parentFile?.mkdirs()
                    zip.getInputStream(sourceEntry).use { source ->
                        FileOutputStream(destination).use { output -> copyInterruptibly(source, output) }
                    }
                    require(destination.length() == entry.bytes) { "FMOD bank file length differs: ${entry.path}" }
                    require(sha256(destination) == entry.sha256) { "FMOD bank checksum differs: ${entry.path}" }
                }
                require(manifest.files.count { it.path.startsWith("bank/") && it.path.endsWith(".bank") } == 1) {
                    "FMOD bank package must contain exactly one bank"
                }
                zip.getInputStream(manifestEntry).use { source ->
                    FileOutputStream(File(stage, MANIFEST_NAME)).use { output -> copyInterruptibly(source, output) }
                }
            }

            val target = File(groupDirectory, expectedPackId)
            val backup = File(groupDirectory, ".previous-$expectedPackId-${System.nanoTime()}")
            if (target.exists() && !target.renameTo(backup)) {
                throw IllegalStateException("Could not replace the existing FMOD bank package")
            }
            if (!stage.renameTo(target)) {
                backup.renameTo(target)
                throw IllegalStateException("Could not publish FMOD bank package")
            }
            deleteRecursively(backup)
        } finally {
            deleteRecursively(stage)
        }
    }

    private fun installedDirectory(group: String, packId: String): File? {
        if (!SAFE_PACK_ID.matches(group) || !SAFE_PACK_ID.matches(packId)) return null
        return installedPackRoots.firstNotNullOfOrNull { root ->
            val directory = File(File(root, group), packId)
            directory.takeIf { File(it, MANIFEST_NAME).isFile }
        }
    }

    private fun packDirectories(root: File): List<File> = root.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .filter { it.name in SUPPORTED_IMPORT_GROUPS }
        .flatMap { group ->
            group.listFiles().orEmpty().filter(File::isDirectory)
        }

    private fun readManifest(input: InputStream): FmodBankManifest = JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
        reader.beginObject()
        var schema: String? = null
        var id: String? = null
        var group: String? = null
        var version: Int? = null
        var files: List<FmodBankFile>? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schema" -> schema = reader.nextString()
                "id" -> id = reader.nextString()
                "group" -> group = reader.nextString()
                "version" -> version = reader.nextInt()
                "files" -> files = readFiles(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        require(schema == SCHEMA) {
            "This is an old or unsupported audio pack. Replace the staged fmod-bank-import folder with a current bundle and reopen the app."
        }
        require(SAFE_PACK_ID.matches(requireNotNull(id))) { "Invalid FMOD bank id" }
        require(SAFE_PACK_ID.matches(requireNotNull(group))) { "Invalid FMOD bank group" }
        require(requireNotNull(version) > 0) { "Invalid FMOD bank package version" }
        val parsedFiles = requireNotNull(files)
        require(parsedFiles.map(FmodBankFile::path).distinct().size == parsedFiles.size) {
            "FMOD bank package has duplicate files"
        }
        parsedFiles.forEach { file ->
            require(
                file.path.startsWith("bank/") ||
                    file.path.startsWith("profiles/") ||
                    file.path.startsWith("preview/"),
            ) {
                "FMOD bank package path is outside its payload"
            }
            require(isSafeRelativePath(file.path)) { "FMOD bank package has unsafe path" }
            require(file.bytes > 0L && SHA256.matches(file.sha256)) { "FMOD bank package has invalid file metadata" }
        }
        FmodBankManifest(requireNotNull(id), requireNotNull(group), requireNotNull(version), parsedFiles)
    }

    private fun readFiles(reader: JsonReader): List<FmodBankFile> {
        val files = mutableListOf<FmodBankFile>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var path: String? = null
            var bytes: Long? = null
            var sha256: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "path" -> path = reader.nextString()
                    "bytes" -> bytes = reader.nextLong()
                    "sha256" -> sha256 = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            files += FmodBankFile(requireNotNull(path), requireNotNull(bytes), requireNotNull(sha256))
        }
        reader.endArray()
        return files
    }

    private fun safeDestination(root: File, relative: String): File {
        require(isSafeRelativePath(relative)) { "FMOD bank package has unsafe path" }
        val destination = File(root, relative).canonicalFile
        require(destination.path.startsWith(root.canonicalPath + File.separator)) { "FMOD bank package escapes destination" }
        return destination
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && !path.contains("\\") &&
            path.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun copyInterruptibly(source: InputStream, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Bank preparation cancelled")
            val count = source.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Bank verification cancelled")
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }

    private data class FmodBankManifest(val id: String, val group: String, val version: Int, val files: List<FmodBankFile>)
    private data class FmodBankFile(val path: String, val bytes: Long, val sha256: String)

    private companion object {
        const val MANIFEST_NAME = "manifest.json"
        const val ARCHIVE_EXTENSION = "bydbank"
        const val SCHEMA = "byd-fmod-bank-pack-v3"
        const val COPY_BUFFER_BYTES = 256 * 1024
        val SAFE_PACK_ID = Regex("^[a-z0-9][a-z0-9._-]{0,95}$")
        val SHA256 = Regex("^[0-9a-f]{64}$")
        val SUPPORTED_IMPORT_GROUPS = setOf(
            FmodBankProfiles.originalCarsPackId,
            FmodBankProfiles.moddedCarsPackId,
        )
    }
}

internal data class FmodBankStorageDiagnostics(
    val validPackCount: Int,
    val issueCount: Int,
    val lines: List<String>,
)

internal class FmodBankResolver(context: Context) {
    private val appContext = context.applicationContext
    private val externalFilesDirectory = appContext.getExternalFilesDir(null)
    private val diagnosticLogFile = externalFilesDirectory?.resolve(BANK_DIAGNOSTIC_LOG_NAME)
        ?: File(appContext.filesDir, BANK_DIAGNOSTIC_LOG_NAME)
    private val store = FmodBankStore(
        filesDirectory = appContext.filesDir,
        stagedImportDirectory = externalFilesDirectory?.resolve(STAGED_IMPORT_DIRECTORY_NAME),
        externalPacksDirectory = externalFilesDirectory?.resolve(INSTALLED_BANK_DIRECTORY_NAME),
    )

    fun importStagedPacks(): FmodBankImportResult = store.importStagedPacks()

    fun hasStagedPacks(): Boolean = store.hasStagedPacks()

    @Synchronized
    fun diagnose(): FmodBankDiagnostics {
        val generatedAt = System.currentTimeMillis()
        val storage = store.diagnoseStorage()
        val recognizedCarCount = FmodBankProfiles.all.count(::isInstalled)
        val scanLines = buildList {
            add("=== BANK SCAN $generatedAt ===")
            addAll(storage.lines)
            add("RECOGNIZED CARS: $recognizedCarCount / ${FmodBankProfiles.all.size}")
            add("DIAGNOSTIC LOG: ${diagnosticLogFile.absolutePath}")
        }
        appendDiagnosticScan(scanLines)

        return FmodBankDiagnostics(
            generatedAtEpochMillis = generatedAt,
            recognizedCarCount = recognizedCarCount,
            validPackCount = storage.validPackCount,
            issueCount = storage.issueCount,
            logLines = diagnosticLogFile.readLines().takeLast(MAX_DIAGNOSTIC_LOG_LINES),
        )
    }

    fun bankFiles(profile: FmodBankProfile): FmodBankFiles {
        require(profile in FmodBankProfiles.all) { "Car is outside this app catalog" }

        return FmodBankFiles(
            commonStrings = store.sharedBankFile(FmodBankProfiles.commonStringsPackId),
            common = store.sharedBankFile(FmodBankProfiles.commonPackId),
            car = store.bankFile(profile),
            physics = store.physicsFile(profile),
        )
    }

    fun sharedBankFiles(): FmodSharedBankFiles = FmodSharedBankFiles(
        commonStrings = store.sharedBankFile(FmodBankProfiles.commonStringsPackId),
        common = store.sharedBankFile(FmodBankProfiles.commonPackId),
    )

    /**
     * A package is only selectable when its immutable physics contract belongs to the same
     * profile as its bank.  File presence alone is not sufficient: accepting a different car's
     * valid `physics.json` here would make the selected bank run with unrelated drivetrain data.
     */
    fun isInstalled(profile: FmodBankProfile): Boolean {
        if (profile !in FmodBankProfiles.all) {
            return false
        }

        return runCatching {
            store.bankFile(profile)
            store.sharedBankFile(FmodBankProfiles.commonStringsPackId)
            store.sharedBankFile(FmodBankProfiles.commonPackId)
            physics(profile)
        }.isSuccess
    }

    fun physics(profile: FmodBankProfile): AssettoPhysics =
        AssettoPhysicsLoader.load(store.physicsFile(profile)).also { physics ->
            require(physics.profileId == profile.id) {
                "Installed ${profile.displayName} package has physics for ${physics.profileId}, not ${profile.id}."
            }
        }

    fun calibrationFingerprint(profile: FmodBankProfile): String {
        require(profile in FmodBankProfiles.all) { "Car is outside this app catalog" }
        val hashes = store.calibrationInputHashes(profile)

        return LoudnessCalibrationFingerprint.create(
            profileId = profile.id,
            algorithmVersion = LOUDNESS_CALIBRATION_ALGORITHM_VERSION,
            carBankSha256 = hashes.carBankSha256,
            physicsSha256 = hashes.physicsSha256,
            commonBankSha256 = hashes.commonBankSha256,
            commonStringsBankSha256 = hashes.commonStringsBankSha256,
        )
    }

    fun previewFile(profile: FmodBankProfile): File? = store.previewFile(profile)

    /** Installed bank previews win. Only official cars may fall back to APK-bundled artwork. */
    fun openCarPreviewInput(profile: FmodBankProfile): InputStream? {
        previewFile(profile)?.let { return FileInputStream(it) }
        if (profile.packGroup != FmodBankProfiles.originalCarsPackId) {
            return null
        }
        return runCatching { appContext.assets.open(profile.previewAssetName) }.getOrNull()
    }

    private fun appendDiagnosticScan(lines: List<String>) {
        diagnosticLogFile.parentFile?.mkdirs()
        diagnosticLogFile.appendText(lines.joinToString(separator = "\n", postfix = "\n"))
        val retainedLines = diagnosticLogFile.readLines().takeLast(MAX_DIAGNOSTIC_LOG_LINES)
        diagnosticLogFile.writeText(retainedLines.joinToString(separator = "\n", postfix = "\n"))
    }

    private companion object {
        const val BANK_DIAGNOSTIC_LOG_NAME = "bank-import-diagnostics.log"
        const val MAX_DIAGNOSTIC_LOG_LINES = 500
    }
}

/**
 * File-manager destination below the shared internal-storage Android/data directory. Android
 * grants the owning app access without asking for broad storage permission, including on DiLink.
 */
internal const val STAGED_IMPORT_DIRECTORY_NAME = "fmod-bank-import"
internal const val INSTALLED_BANK_DIRECTORY_NAME = "fmod-banks"

internal data class FmodBankFiles(
    val commonStrings: File,
    val common: File,
    val car: File,
    val physics: File,
)

internal data class FmodSharedBankFiles(
    val commonStrings: File,
    val common: File,
)

internal data class FmodCalibrationInputHashes(
    val carBankSha256: String,
    val physicsSha256: String,
    val commonBankSha256: String,
    val commonStringsBankSha256: String,
)
