package com.example.synth

import android.content.Context
import android.util.Log
import com.example.model.Track
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.exp

object LocalMusicSynthesizer {
    private const val TAG = "LocalMusicSynthesizer"
    private const val SAMPLE_RATE = 22050

    /**
     * Generates standard ambient WAV songs in internal cache and returns Track models.
     */
    fun generateSyntheticSongs(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()
        val outputDir = File(context.filesDir, "synthetic_music")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // Define tracks to synthesize
        val songDefinitions = listOf(
            SongDef("Aero Whisper", "Frutiger Dream", "Aero Atmospheres", 12),
            SongDef("Frutiger Aura", "Cyan Spark", "Aero Atmospheres", 10),
            SongDef("Dreamscape Vista", "Aero Design", "Retro System Chimes", 8)
        )

        for (def in songDefinitions) {
            val file = File(outputDir, "${def.title.replace(" ", "_").lowercase()}.wav")
            try {
                if (!file.exists() || file.length() < 1000) {
                    Log.d(TAG, "Synthesizing song: ${def.title}")
                    synthesizeWav(file, def)
                }
                tracks.add(
                    Track(
                        title = def.title,
                        artist = def.artist,
                        album = def.album,
                        duration = def.durationSec * 1000L,
                        path = file.absolutePath,
                        isFavorite = false,
                        isSynthetic = true
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to synthesize song ${def.title}", e)
            }
        }
        return tracks
    }

    private class SongDef(
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Int
    )

    private fun synthesizeWav(file: File, def: SongDef) {
        val durationSamples = SAMPLE_RATE * def.durationSec
        val totalBytes = 44 + (durationSamples * 2) // 16-bit mono PCM

        val pcmData = ShortArray(durationSamples)

        when (def.title) {
            "Aero Whisper" -> {
                // Ambient pad: soft evolving Eb major & Ab major chords
                // Frequencies:
                // Chord 1 (Eb Maj): Eb3 (155.56 Hz), G3 (196.00 Hz), Bb3 (233.08 Hz), Eb4 (311.13 Hz)
                // Chord 2 (Ab Maj): Ab3 (207.65 Hz), C4 (261.63 Hz), Eb4 (311.13 Hz), Ab4 (415.30 Hz)
                val chord1 = floatArrayOf(155.56f, 196.00f, 233.08f, 311.13f)
                val chord2 = floatArrayOf(207.65f, 261.63f, 311.13f, 415.30f)

                for (i in 0 until durationSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    // Cycle every 4 seconds
                    val cycle = t % 4f
                    val blend = if (cycle < 2f) {
                        // Blend up chord 1, down chord 2
                        sin(cycle * Math.PI.toFloat() / 4f)
                    } else {
                        // Blend down chord 1, up chord 2
                        sin((cycle - 2f) * Math.PI.toFloat() / 4f)
                    }

                    var val1 = 0f
                    for (f in chord1) {
                        val1 += sin(2f * Math.PI.toFloat() * f * t)
                    }
                    var val2 = 0f
                    for (f in chord2) {
                        val2 += sin(2f * Math.PI.toFloat() * f * t)
                    }

                    // Combined with a slow LFO (Low Frequency Oscillator) for filter sweeps
                    val lfo = 0.5f + 0.3f * sin(2f * Math.PI.toFloat() * 0.15f * t)
                    val sound = ((val1 / 4f) * (1f - blend) + (val2 / 4f) * blend) * lfo

                    // Apply soft entry and exit ramp
                    var envelope = 1f
                    if (t < 1.5f) {
                        envelope = t / 1.5f // Fade in
                    } else if (t > def.durationSec - 1.5f) {
                        envelope = (def.durationSec - t) / 1.5f // Fade out
                    }

                    // Write 16-bit PCM value (-32768 to 32767)
                    // Keep volume gentle to sound atmospheric
                    val sampleValue = (sound * 10000f * envelope).toInt()
                    pcmData[i] = sampleValue.coerceIn(-32767, 32767).toShort()
                }
            }
            "Frutiger Aura" -> {
                // Plucky bubble-bell retro melody with electronic echo
                // Frequencies in Pentatonic F Major for lovely consonant sound: F4 (349.23 Hz), G4 (392.00 Hz), A4 (440.00 Hz), C5 (523.25 Hz), D5 (587.33 Hz)
                val notes = floatArrayOf(349.23f, 392.00f, 440.00f, 523.25f, 587.33f)
                val melody = intArrayOf(0, 3, 4, 1, 3, 2, 0, 4) // Note indices
                val noteDurationSamples = (SAMPLE_RATE * 0.5f).toInt() // Eighth notes at 120BPM

                for (i in 0 until durationSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val noteIndex = (i / noteDurationSamples) % melody.size
                    val noteFreq = notes[melody[noteIndex]]

                    val sampleOffsetInNote = i % noteDurationSamples
                    val noteTime = sampleOffsetInNote.toFloat() / SAMPLE_RATE

                    // Decay envelope: very quick pluck bubble sound
                    val decay = exp(-12f * noteTime)
                    // Basic sound: central pitch + sub-octave + high shimmer
                    val voice1 = sin(2f * Math.PI.toFloat() * noteFreq * noteTime)
                    val voice2 = 0.5f * sin(2f * Math.PI.toFloat() * (noteFreq / 2f) * noteTime)
                    val voice3 = 0.3f * sin(2f * Math.PI.toFloat() * (noteFreq * 2f) * noteTime)

                    val bubble = (voice1 + voice2 + voice3) * decay

                    // Delay echo effects
                    var echo = 0f
                    val delaySamples1 = (SAMPLE_RATE * 0.3f).toInt() // 300ms delay
                    if (i > delaySamples1) {
                        val prevIndex = i - delaySamples1
                        val prevTimeWithinNote = prevIndex % noteDurationSamples
                        val prevDecay = exp(-12f * (prevTimeWithinNote.toFloat() / SAMPLE_RATE))
                        val prevFreq = notes[melody[(prevIndex / noteDurationSamples) % melody.size]]
                        val prevVoice = sin(2f * Math.PI.toFloat() * prevFreq * (prevTimeWithinNote.toFloat() / SAMPLE_RATE))
                        echo += prevVoice * prevDecay * 0.35f
                    }

                    val finalValue = bubble + echo

                    // Fade in/out nicely
                    var envelope = 1f
                    if (t < 0.5f) envelope = t / 0.5f
                    if (t > def.durationSec - 0.8f) envelope = (def.durationSec - t) / 0.8f

                    val sampleValue = (finalValue * 8500f * envelope).toInt()
                    pcmData[i] = sampleValue.coerceIn(-32767, 32767).toShort()
                }
            }
            "Dreamscape Vista" -> {
                // Beautiful airy chime cascade resembling Windows Vista / Win7 glass startups
                // Cascade of notes: E4 (329.63 Hz), A4 (440.00 Hz), B4 (493.88 Hz), E5 (659.25 Hz), F#5 (739.99 Hz)
                val chimeNotes = floatArrayOf(329.63f, 440.00f, 493.88f, 659.25f, 739.99f)
                val triggerTimes = floatArrayOf(0.0f, 0.25f, 0.45f, 0.70f, 1.0f) // Timings

                for (i in 0 until durationSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    var sound = 0f

                    for (n in chimeNotes.indices) {
                        val triggerTime = triggerTimes[n]
                        if (t >= triggerTime) {
                            val timeSinceTrigger = t - triggerTime
                            // Long evolving feedback ring
                            val envelope = exp(-1.8f * timeSinceTrigger) * (1f - exp(-10f * timeSinceTrigger)) // Soft attack, long decay
                            val sine = sin(2f * Math.PI.toFloat() * chimeNotes[n] * timeSinceTrigger)
                            // Bright metallic ring (frequency modulation style: high-freq shine)
                            val ringMod = 0.25f * sin(2f * Math.PI.toFloat() * (chimeNotes[n] * 3f) * timeSinceTrigger) * exp(-5f * timeSinceTrigger)
                            sound += (sine + ringMod) * envelope
                        }
                    }

                    // Wrap in atmospheric filter LFO
                    val lfo = 0.6f + 0.3f * sin(2f * Math.PI.toFloat() * 0.25f * t)
                    val outValue = sound * lfo / 3.5f

                    // Soft fade out at the very end
                    var envelope = 1f
                    if (t > def.durationSec - 1.5f) {
                        envelope = (def.durationSec - t) / 1.5f
                    }

                    val sampleValue = (outValue * 15000f * envelope).toInt()
                    pcmData[i] = sampleValue.coerceIn(-32767, 32767).toShort()
                }
            }
        }

        // Write the WAV header and raw PCM data
        FileOutputStream(file).use { fos ->
            val header = createWavHeader(totalBytes, durationSamples)
            fos.write(header)

            val pcmBuffer = ByteBuffer.allocate(durationSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                pcmBuffer.putShort(sample)
            }
            fos.write(pcmBuffer.array())
        }
        Log.d(TAG, "Written WAV file of size ${file.length()} to ${file.absolutePath}")
    }

    private fun createWavHeader(totalBytes: Int, durationSamples: Int): ByteArray {
        val header = ByteArray(44)
        val dataBytesSize = durationSamples * 2

        // "RIFF"
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'L'.toByte() // or 'F'

        // File size - 8
        val fileSizeMinus8 = totalBytes - 8
        header[4] = (fileSizeMinus8 and 0xFF).toByte()
        header[5] = ((fileSizeMinus8 shr 8) and 0xFF).toByte()
        header[6] = ((fileSizeMinus8 shr 16) and 0xFF).toByte()
        header[7] = ((fileSizeMinus8 shr 24) and 0xFF).toByte()

        // "WAVE"
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()

        // "fmt "
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()

        // Subchunk1Size = 16
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // AudioFormat = 1 (PCM)
        header[20] = 1
        header[21] = 0

        // NumChannels = 1 (Mono)
        header[22] = 1
        header[23] = 0

        // SampleRate
        header[24] = (SAMPLE_RATE and 0xFF).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xFF).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xFF).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xFF).toByte()

        // ByteRate
        val byteRate = SAMPLE_RATE * 1 * 2
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()

        // BlockAlign
        header[32] = 2
        header[33] = 0

        // BitsPerSample
        header[34] = 16
        header[35] = 0

        // "data"
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()

        // Subchunk2Size (raw data size)
        header[40] = (dataBytesSize and 0xFF).toByte()
        header[41] = ((dataBytesSize shr 8) and 0xFF).toByte()
        header[42] = ((dataBytesSize shr 16) and 0xFF).toByte()
        header[43] = ((dataBytesSize shr 24) and 0xFF).toByte()

        // Overwrite standard correct chars for format
        header[3] = 'F'.toByte()

        return header
    }
}
