package com.offhand.llm

import android.util.Log
import com.offhand.parse.ActionDraft
import com.offhand.parse.ActionParser
import com.offhand.parse.DeterministicActionParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Primary path: on-device LLM (llama.cpp + GBNF). Fallback: the deterministic
 * parser. Falling back is NEVER silent — it is logged and surfaced in the UI
 * via [status].
 */
class CompositeActionParser(
    private val engine: LlamaEngine,
    private val llama: LlamaActionParser,
    private val fallback: DeterministicActionParser,
) : ActionParser {

    private val _status = MutableStateFlow("Parser: warming up…")
    val status: StateFlow<String> = _status.asStateFlow()

    /** Call after engine.initialize() to publish the resolved state. */
    fun refreshStatus() {
        _status.value = when (val s = engine.state) {
            LlamaEngine.State.Ready -> "Parser: on-device LLM (Qwen2.5-1.5B)"
            is LlamaEngine.State.Unavailable -> "Parser: rules fallback — ${s.reason}"
            LlamaEngine.State.NotReady -> "Parser: warming up…"
        }
    }

    override fun parse(transcript: String): ActionDraft {
        if (engine.state == LlamaEngine.State.Ready) {
            try {
                val draft = llama.parse(transcript)
                _status.value = "Parser: on-device LLM (Qwen2.5-1.5B)"
                return draft
            } catch (e: LlmParseException) {
                // Loud, visible fallback — never silent.
                Log.w(TAG, "LLM parse fell back: ${e.message}")
                _status.value = "Parser: rules fallback — ${e.message}"
            }
        }
        return fallback.parse(transcript)
    }

    private companion object {
        const val TAG = "CompositeParser"
    }
}
