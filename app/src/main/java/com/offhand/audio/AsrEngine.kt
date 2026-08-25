package com.offhand.audio

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Vosk streaming ASR. The model is initialised once at app start (master spec
 * §8) and reused for every push-to-talk press. 16 kHz mono PCM.
 *
 * Logging: timings and states only — never transcripts (privacy rule §9).
 */
class AsrEngine(private val context: Context) {

    sealed interface EngineState {
        data object NotReady : EngineState
        data object Ready : EngineState
        data class Error(val message: String) : EngineState
    }

    @Volatile var state: EngineState = EngineState.NotReady
        private set

    private var model: Model? = null
    private var speechService: SpeechService? = null

    /** Where `adb push` drops the model (documented in the README). */
    private fun modelDir(): File? {
        val candidates = listOfNotNull(
            context.getExternalFilesDir(null)?.resolve(MODEL_DIR_NAME),
            File(context.filesDir, MODEL_DIR_NAME),
        )
        return candidates.firstOrNull { File(it, "conf/model.conf").exists() || File(it, "am/final.mdl").exists() }
    }

    /** Blocking; call off the main thread. */
    fun initialize() {
        if (state == EngineState.Ready) return
        val dir = modelDir()
        if (dir == null) {
            state = EngineState.Error(
                "ASR model missing — push it with adb (see README)",
            )
            Log.i(TAG, "init failed: model directory not found")
            return
        }
        val started = System.currentTimeMillis()
        try {
            model = Model(dir.absolutePath)
            state = EngineState.Ready
            Log.i(TAG, "model loaded in ${System.currentTimeMillis() - started} ms")
        } catch (e: Exception) {
            state = EngineState.Error("ASR model failed to load")
            Log.i(TAG, "init failed: ${e.javaClass.simpleName}")
        }
    }

    /** Push-to-talk press: begin streaming from the microphone. */
    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit): Boolean {
        val m = model ?: return false
        stopListening() // never two services
        return try {
            val recognizer = Recognizer(m, SAMPLE_RATE)
            val service = SpeechService(recognizer, SAMPLE_RATE)
            speechService = service
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    extract(hypothesis, "partial")?.let(onPartial)
                }

                override fun onResult(hypothesis: String?) {
                    // Intermediate utterance end — treat like a partial so the
                    // user sees continuous text; final text arrives on stop.
                    extract(hypothesis, "text")?.let(onPartial)
                }

                override fun onFinalResult(hypothesis: String?) {
                    onFinal(extract(hypothesis, "text").orEmpty())
                }

                override fun onError(exception: Exception?) {
                    Log.i(TAG, "recognition error: ${exception?.javaClass?.simpleName}")
                    onFinal("")
                }

                override fun onTimeout() {
                    onFinal("")
                }
            })
            true
        } catch (e: Exception) {
            Log.i(TAG, "startListening failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** Push-to-talk release: stop the mic; the final result fires the callback. */
    fun stopListening() {
        speechService?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (_: Exception) {
            }
        }
        speechService = null
    }

    /**
     * Debug/verification path (and the same recogniser the mic uses): feed a
     * 16 kHz mono PCM16 WAV through the engine and return the transcript.
     */
    fun transcribeWavFile(path: String): String? {
        val m = model ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val pcm = if (bytes.size > 44) bytes.copyOfRange(44, bytes.size) else return null
            val recognizer = Recognizer(m, SAMPLE_RATE)
            var offset = 0
            val chunk = 4096
            while (offset < pcm.size) {
                val len = minOf(chunk, pcm.size - offset)
                recognizer.acceptWaveForm(pcm.copyOfRange(offset, offset + len), len)
                offset += len
            }
            val text = extract(recognizer.finalResult, "text")
            recognizer.close()
            text
        } catch (e: Exception) {
            Log.i(TAG, "wav transcription failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun extract(json: String?, key: String): String? = try {
        json?.let { JSONObject(it).optString(key).takeIf { s -> s.isNotBlank() } }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TAG = "AsrEngine"
        private const val SAMPLE_RATE = 16000.0f
        const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
    }
}
