package com.offhand.llm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Thin JNI binding to llama.cpp (vendored at tag b4658). The model loads once
 * at app start and is retained for the process lifetime (master spec §8).
 * Timing/token logs live on the native side; no content is ever logged.
 */
class LlamaEngine(private val context: Context) {

    sealed interface State {
        data object NotReady : State
        data object Ready : State
        data class Unavailable(val reason: String) : State
    }

    @Volatile var state: State = State.NotReady
        private set

    private var handle: Long = 0
    private lateinit var grammar: String
    private lateinit var promptTemplate: String

    /** Blocking; call off the main thread. */
    fun initialize() {
        if (state == State.Ready) return
        if (!NATIVE_LOADED) {
            state = State.Unavailable("native library not loaded")
            return
        }
        val modelFile = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
        if (!modelFile.exists()) {
            state = State.Unavailable("model file missing — push it with adb (see README)")
            return
        }
        grammar = context.assets.open("action_schema.gbnf").bufferedReader().readText()
        promptTemplate = context.assets.open("parser_prompt.txt").bufferedReader().readText()

        val started = System.currentTimeMillis()
        handle = nativeInit(modelFile.absolutePath, N_CTX, N_THREADS)
        if (handle == 0L) {
            state = State.Unavailable("model failed to load")
            Log.i(TAG, "init failed")
            return
        }
        state = State.Ready
        Log.i(TAG, "engine ready in ${System.currentTimeMillis() - started} ms")
    }

    /** Grammar-constrained generation. Returns raw JSON text or null. */
    fun generate(transcript: String): String? {
        if (state != State.Ready || handle == 0L) return null
        val prompt = promptTemplate.replace("{transcript}", transcript.trim())
        val started = System.currentTimeMillis()
        val result = nativeParse(handle, prompt, grammar, MAX_TOKENS)
        Log.i(TAG, "generate latency=${System.currentTimeMillis() - started} ms ok=${result != null}")
        return result
    }

    fun release() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0
            state = State.NotReady
        }
    }

    private external fun nativeInit(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeParse(handle: Long, prompt: String, grammar: String, maxTokens: Int): String?
    private external fun nativeFree(handle: Long)

    companion object {
        private const val TAG = "LlamaEngine"
        const val MODEL_FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"

        // Spec said n_ctx 1024, but the few-shot prompt alone is ~950 tokens;
        // 1024 would fail the prompt+output fit check on every call and force
        // the fallback parser. 2048 costs ~56 MB KV — a reported deviation.
        private const val N_CTX = 2048
        private const val N_THREADS = 4
        private const val MAX_TOKENS = 256

        val NATIVE_LOADED: Boolean = try {
            System.loadLibrary("offhand_llama")
            true
        } catch (t: Throwable) {
            Log.i(TAG, "native lib unavailable: ${t.javaClass.simpleName}")
            false
        }
    }
}
