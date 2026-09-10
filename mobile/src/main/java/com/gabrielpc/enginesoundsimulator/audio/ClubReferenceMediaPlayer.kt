package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File

internal data class ClubReferenceToggleResult(
    val playing: Boolean,
    val errorMessage: String? = null,
)

/**
 * Plays the club reference track from the app private files directory at Android media volume
 * (STREAM_MUSIC), matching what the vehicle head unit uses for normal media playback.
 */
internal class ClubReferenceMediaPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    fun isPlaying(): Boolean = player?.isPlaying == true

    @Synchronized
    fun toggle(): ClubReferenceToggleResult {
        if (isPlaying()) {
            stop()
            return ClubReferenceToggleResult(playing = false)
        }

        val referenceFile = resolveReferenceFile()
        if (referenceFile == null) {
            val message = "Could not find in_the_club.mp3 in the app files folder."
            Log.w(TAG, message)
            return ClubReferenceToggleResult(playing = false, errorMessage = message)
        }

        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(referenceFile.absolutePath)
                isLooping = true
                setOnCompletionListener { completed ->
                    completed.release()
                    synchronized(this@ClubReferenceMediaPlayer) {
                        if (player === completed) {
                            player = null
                        }
                    }
                }
                prepare()
                start()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to play ${referenceFile.name}", error)
        }.getOrNull()

        if (!isPlaying()) {
            val message = "Could not play ${referenceFile.name}."
            return ClubReferenceToggleResult(playing = false, errorMessage = message)
        }

        return ClubReferenceToggleResult(playing = true)
    }

    @Synchronized
    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    @Synchronized
    fun release() {
        stop()
    }

    fun referenceFilePath(): String = resolveReferenceFile()?.absolutePath.orEmpty()

    private fun resolveReferenceFile(): File? {
        val filesDir = appContext.filesDir
        val candidates = listOf(
            File(filesDir, REFERENCE_FILE_NAME),
            File(filesDir, REFERENCE_FILE_BASENAME),
        )
        candidates.firstOrNull { file ->
            file.isFile && !file.name.startsWith("._")
        }?.let { return it }

        return filesDir.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    !file.name.startsWith("._") &&
                    (
                        file.name.equals(REFERENCE_FILE_NAME, ignoreCase = true) ||
                            file.name.equals(REFERENCE_FILE_BASENAME, ignoreCase = true)
                    )
            }
            ?.firstOrNull()
    }

    private companion object {
        const val TAG = "ClubReferenceMedia"
        const val REFERENCE_FILE_NAME = "in_the_club.mp3"
        const val REFERENCE_FILE_BASENAME = "in_the_club"
    }
}
