package io.github.teamclouday.androidMic.domain.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.teamclouday.androidMic.domain.service.AudioPacket
import io.github.teamclouday.androidMic.ProcessingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder


private const val TAG: String = "MicAM"

// manage microphone recording
class MicAudioManager(
    ctx: Context,
    val scope: CoroutineScope,
    val sampleRate: Int,
    val audioFormat: Int,
    val channelCount: Int,
    val audioSource: Int,
    private val processingMode: ProcessingMode,
    private val releaseCueEnabled: Boolean,
) {

    companion object {
        const val RECORD_DELAY_MS = 100L
    }

    private val recorder: AudioRecord
    private val recorderBufferSize: Int
    private val frameSamples: Int
    private val buffer: ByteArray
    private val bufferFloat: FloatArray
    private val bufferFloatConvert: ByteBuffer
    private var noiseSuppressor: NoiseSuppressor? = null
    private val filterInput = FloatArray(channelCount)
    private val filterOutput = FloatArray(channelCount)
    private val compressorEnvelope = FloatArray(channelCount)
    private val compressorGain = FloatArray(channelCount) { 1f }
    private val broadcastLevelGain = FloatArray(channelCount) { 1f }
    private val hpfAlpha = run {
        val cutoffHz = if (processingMode == ProcessingMode.CAR) 130f else 80f
        val rc = 1f / (2f * Math.PI.toFloat() * cutoffHz)
        rc / (rc + 1f / sampleRate)
    }
    private var streamJob: Job? = null
    private val cueSamples = sampleRate * 220 / 1000
    private var releaseTailRemaining = 0
    private var cueSampleOffset = cueSamples

    @Volatile
    private var isMuted = true

    init {
        // check microphone
        require(ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            "Microphone is not detected on this device"
        }
        require(
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "Microphone recording is not permitted"
        }

        // get minimum buffer size
        val channelConfig =
            if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat,
        )

        require(minBufferSize != AudioRecord.ERROR && minBufferSize != AudioRecord.ERROR_BAD_VALUE) {
            "Microphone buffer size ($minBufferSize) is invalid\nAudio format is likely not supported"
        }

        // Read and gate short 10 ms frames so a PTT release does not wait for a
        // large device minimum buffer to drain before silence reaches the transport.
        frameSamples = sampleRate / 100 * channelCount
        val bytesPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> 4
        }
        val frameBytes = frameSamples * bytesPerSample
        recorderBufferSize = maxOf(minBufferSize, frameBytes * 2)

        // init recorder
        recorder = AudioRecord(
            audioSource,
            sampleRate,
            channelConfig,
            audioFormat,
            recorderBufferSize,
        )

        // check if recorder is initialized
        require(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "Microphone recording failed to initialize"
        }

        if (processingMode != ProcessingMode.OFF && NoiseSuppressor.isAvailable()) {
            noiseSuppressor = runCatching {
                NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
            }.onFailure { Log.w(TAG, "Android NoiseSuppressor could not be enabled", it) }.getOrNull()
        }

        buffer = ByteArray(frameBytes)
        bufferFloat = FloatArray(frameSamples)
        bufferFloatConvert = ByteBuffer.allocate(frameSamples * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    }

    // audio stream publisher
    fun audioStream(): Flow<AudioPacket> = channelFlow {
        // launch in scope so infinite loop will be canceled when scope exits
        streamJob = scope.launch {
            while (true) {

                if (recorder.state != AudioRecord.STATE_INITIALIZED || recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    delay(RECORD_DELAY_MS)
                    continue
                }

                val readCount: Int // number of samples read (for float) or number of bytes read (for int)
                val packetBuffer: ByteArray

                if (audioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
                    readCount =
                        recorder.read(bufferFloat, 0, frameSamples, AudioRecord.READ_BLOCKING)

                    if (readCount > 0) {
                        bufferFloatConvert.clear()
                        bufferFloatConvert.asFloatBuffer().put(bufferFloat, 0, readCount)
                        packetBuffer = bufferFloatConvert.array().copyOf(readCount * Float.SIZE_BYTES)
                    } else {
                        packetBuffer = ByteArray(0)
                    }
                } else {
                    readCount = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)

                    if (readCount > 0) {
                        packetBuffer = ByteArray(readCount)
                        buffer.copyInto(packetBuffer, 0, 0, readCount)
                    } else {
                        packetBuffer = ByteArray(0)
                    }
                }

                if (readCount <= 0) {
                    delay(RECORD_DELAY_MS)
                    continue
                }

                // Keep transport timing stable while muted. On release, send a short
                // faded voice tail, then an optional two-tone cue, then only zero PCM.
                if (!isMuted) {
                    if (audioFormat == AudioFormat.ENCODING_PCM_16BIT && processingMode != ProcessingMode.OFF) {
                        processPcm16(packetBuffer)
                    }
                } else if (releaseTailRemaining > 0 && audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    if (processingMode != ProcessingMode.OFF) processPcm16(packetBuffer)
                    val fade = releaseTailRemaining.toFloat() / RELEASE_TAIL_FRAMES
                    applyPcm16Gain(packetBuffer, fade)
                    releaseTailRemaining--
                } else if (releaseCueEnabled && cueSampleOffset < cueSamples && audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    writeReleaseCue(packetBuffer)
                } else {
                    packetBuffer.fill(0)
                    resetProcessingState()
                }

                send(
                    AudioPacket(
                        buffer = packetBuffer,
                        sampleRate = sampleRate,
                        audioFormat = audioFormat,
                        channelCount = channelCount
                    )
                )
            }
        }

        awaitClose {
            streamJob?.cancel()
        }
    }

    fun mute() {
        if (!isMuted) {
            releaseTailRemaining = RELEASE_TAIL_FRAMES
            cueSampleOffset = 0
        }
        isMuted = true
    }

    fun unmute() {
        releaseTailRemaining = 0
        cueSampleOffset = cueSamples
        isMuted = false
    }

    private fun processPcm16(pcm: ByteArray) {
        val sampleCount = pcm.size / Short.SIZE_BYTES
        val attack = kotlin.math.exp(-1f / (sampleRate * 0.010f))
        val release = kotlin.math.exp(-1f / (sampleRate * 0.100f))
        if (processingMode == ProcessingMode.BROADCAST) updateBroadcastLevel(pcm, sampleCount)
        for (sampleIndex in 0 until sampleCount) {
            val channel = sampleIndex % channelCount
            val byteIndex = sampleIndex * Short.SIZE_BYTES
            val input = (((pcm[byteIndex + 1].toInt() shl 8) or (pcm[byteIndex].toInt() and 0xff)).toShort()).toFloat()
            var output = input

            if (processingMode == ProcessingMode.STANDARD || processingMode == ProcessingMode.CAR || processingMode == ProcessingMode.BROADCAST) {
                val highPassed = hpfAlpha * (filterOutput[channel] + input - filterInput[channel])
                filterInput[channel] = input
                filterOutput[channel] = highPassed
                output = highPassed
            }

            if (processingMode == ProcessingMode.CAR) {
                val level = kotlin.math.abs(output) / Short.MAX_VALUE.toFloat()
                val coefficient = if (level > compressorEnvelope[channel]) attack else release
                compressorEnvelope[channel] = coefficient * compressorEnvelope[channel] + (1f - coefficient) * level
                val threshold = 0.72f
                val targetGain = if (compressorEnvelope[channel] > threshold) {
                    (threshold + (compressorEnvelope[channel] - threshold) / 3f) / compressorEnvelope[channel]
                } else 1f
                val gainCoefficient = if (targetGain < compressorGain[channel]) attack else release
                compressorGain[channel] = gainCoefficient * compressorGain[channel] + (1f - gainCoefficient) * targetGain
                output *= compressorGain[channel]
            }

            if (processingMode == ProcessingMode.BROADCAST) {
                output *= broadcastLevelGain[channel]
                val level = kotlin.math.abs(output) / Short.MAX_VALUE.toFloat()
                val threshold = BROADCAST_COMPRESSOR_THRESHOLD
                if (level > threshold) {
                    output *= (threshold + (level - threshold) / BROADCAST_COMPRESSOR_RATIO) / level
                }
                output = output.coerceIn(-BROADCAST_PEAK_LIMIT * Short.MAX_VALUE, BROADCAST_PEAK_LIMIT * Short.MAX_VALUE)
            }

            val clipped = output.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort().toInt()
            pcm[byteIndex] = clipped.toByte()
            pcm[byteIndex + 1] = (clipped shr 8).toByte()
        }
    }

    private fun updateBroadcastLevel(pcm: ByteArray, sampleCount: Int) {
        val sums = FloatArray(channelCount)
        val counts = IntArray(channelCount)
        for (sampleIndex in 0 until sampleCount) {
            val channel = sampleIndex % channelCount
            val byteIndex = sampleIndex * Short.SIZE_BYTES
            val sample = (((pcm[byteIndex + 1].toInt() shl 8) or (pcm[byteIndex].toInt() and 0xff)).toShort()).toFloat() / Short.MAX_VALUE
            sums[channel] += sample * sample
            counts[channel]++
        }
        for (channel in 0 until channelCount) {
            val rms = kotlin.math.sqrt(sums[channel] / counts[channel].coerceAtLeast(1))
            // Avoid raising the noise floor; maximum gain is bounded to protect quiet sources.
            val targetGain = if (rms >= BROADCAST_NOISE_FLOOR) {
                (BROADCAST_TARGET_RMS / rms).coerceIn(BROADCAST_MIN_GAIN, BROADCAST_MAX_GAIN)
            } else 1f
            val timeConstant = if (targetGain < broadcastLevelGain[channel]) 0.080f else 0.650f
            val coefficient = kotlin.math.exp(-0.010f / timeConstant)
            broadcastLevelGain[channel] = coefficient * broadcastLevelGain[channel] + (1f - coefficient) * targetGain
        }
    }

    private fun applyPcm16Gain(pcm: ByteArray, gain: Float) {
        for (byteIndex in 0 until pcm.size - 1 step Short.SIZE_BYTES) {
            val input = (((pcm[byteIndex + 1].toInt() shl 8) or (pcm[byteIndex].toInt() and 0xff)).toShort()).toFloat()
            val sample = (input * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[byteIndex] = sample.toByte()
            pcm[byteIndex + 1] = (sample shr 8).toByte()
        }
    }

    private fun writeReleaseCue(pcm: ByteArray) {
        val sampleCount = pcm.size / Short.SIZE_BYTES
        val toneSamples = sampleRate * 80 / 1000
        val gapSamples = sampleRate * 60 / 1000
        val secondToneStart = toneSamples + gapSamples
        val totalSamples = secondToneStart + toneSamples
        for (sampleIndex in 0 until sampleCount) {
            val position = cueSampleOffset + sampleIndex
            val active = position < toneSamples || position in secondToneStart until totalSamples
            val frequency = if (position < toneSamples) 1000.0 else 1400.0
            val value = if (active) {
                val tonePosition = if (position < toneSamples) position else position - secondToneStart
                val edge = minOf(tonePosition, toneSamples - tonePosition - 1)
                val envelope = (edge.toFloat() / (sampleRate * 0.003f)).coerceIn(0f, 1f)
                kotlin.math.sin(2.0 * Math.PI * frequency * tonePosition / sampleRate).toFloat() * RELEASE_CUE_AMPLITUDE * envelope
            } else 0f
            val output = (value * Short.MAX_VALUE).toInt()
            val byteIndex = sampleIndex * Short.SIZE_BYTES
            pcm[byteIndex] = output.toByte()
            pcm[byteIndex + 1] = (output shr 8).toByte()
        }
        cueSampleOffset += sampleCount
    }

    private fun resetProcessingState() {
        filterInput.fill(0f)
        filterOutput.fill(0f)
        compressorEnvelope.fill(0f)
        compressorGain.fill(1f)
        broadcastLevelGain.fill(1f)
    }

    // start recording
    fun start() {
        recorder.startRecording()
        Log.d(TAG, "start")
    }

    // stop recording
    fun stop() {
        recorder.stop()
        Log.d(TAG, "stop")
    }

    // shutdown manager
    // should not call any methods after calling
    fun shutdown() {
        recorder.stop()
        recorder.release()
        noiseSuppressor?.release()
        noiseSuppressor = null
        streamJob?.cancel()
        Log.d(TAG, "shutdown")
    }

    private companion object {
        const val RELEASE_TAIL_FRAMES = 4
        const val RELEASE_CUE_AMPLITUDE = 0.12f
        const val BROADCAST_NOISE_FLOOR = 0.025f
        const val BROADCAST_TARGET_RMS = 0.16f
        const val BROADCAST_MIN_GAIN = 0.75f
        const val BROADCAST_MAX_GAIN = 2.5f
        const val BROADCAST_COMPRESSOR_THRESHOLD = 0.55f
        const val BROADCAST_COMPRESSOR_RATIO = 2.5f
        const val BROADCAST_PEAK_LIMIT = 0.92f
    }
}
