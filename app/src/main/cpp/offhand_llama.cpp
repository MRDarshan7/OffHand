// OFFHAND JNI wrapper around llama.cpp (pinned tag b4658).
// One model, one context, greedy decoding under a GBNF grammar.
// Logs carry token counts and timings only — never prompt or output text.

#include <jni.h>
#include <android/log.h>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "offhand_llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
};

std::mutex g_mutex; // one inference at a time

std::string jstr(JNIEnv * env, jstring s) {
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    env->ReleaseStringUTFChars(s, c);
    return out;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_offhand_llm_LlamaEngine_nativeInit(JNIEnv * env, jobject, jstring modelPath, jint nCtx, jint nThreads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string path = jstr(env, modelPath);

    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU inference is the product

    const auto t0 = std::chrono::steady_clock::now();
    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (model == nullptr) {
        LOGW("model load failed");
        return 0;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nCtx;
    cp.n_batch = (uint32_t) nCtx;
    cp.n_threads = nThreads;
    cp.n_threads_batch = nThreads;

    llama_context * ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        LOGW("context init failed");
        llama_model_free(model);
        return 0;
    }

    const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - t0).count();
    LOGI("model loaded in %lld ms, n_ctx=%d, threads=%d", (long long) ms, nCtx, nThreads);

    auto * e = new Engine();
    e->model = model;
    e->ctx = ctx;
    e->vocab = llama_model_get_vocab(model);
    return (jlong) e;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offhand_llm_LlamaEngine_nativeParse(JNIEnv * env, jobject, jlong handle, jstring promptJ, jstring grammarJ, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto * e = (Engine *) handle;
    if (e == nullptr || e->ctx == nullptr) return nullptr;

    const std::string prompt = jstr(env, promptJ);
    const std::string grammar = jstr(env, grammarJ);
    const auto t0 = std::chrono::steady_clock::now();

    // Fresh state per request.
    llama_kv_cache_clear(e->ctx);

    // Tokenize (parse ChatML special tokens).
    std::vector<llama_token> tokens(prompt.size() + 16);
    int n = llama_tokenize(e->vocab, prompt.c_str(), (int32_t) prompt.size(),
                           tokens.data(), (int32_t) tokens.size(),
                           /*add_special*/ true, /*parse_special*/ true);
    if (n < 0) {
        LOGW("tokenize failed (%d)", n);
        return nullptr;
    }
    tokens.resize(n);
    if ((uint32_t) (n + maxTokens) > llama_n_ctx(e->ctx)) {
        LOGW("prompt too long: %d tokens", n);
        return nullptr;
    }

    // Grammar-constrained greedy sampling (temperature 0 by construction).
    llama_sampler * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler * gs = llama_sampler_init_grammar(e->vocab, grammar.c_str(), "root");
    if (gs == nullptr) {
        LOGW("grammar rejected");
        llama_sampler_free(chain);
        return nullptr;
    }
    llama_sampler_chain_add(chain, gs);
    llama_sampler_chain_add(chain, llama_sampler_init_greedy());

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    std::string out;
    int generated = 0;
    bool failed = false;

    while (generated < maxTokens) {
        if (llama_decode(e->ctx, batch) != 0) {
            LOGW("decode failed at token %d", generated);
            failed = true;
            break;
        }
        llama_token tok = llama_sampler_sample(chain, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, tok)) break;

        char piece[128];
        const int len = llama_token_to_piece(e->vocab, tok, piece, sizeof(piece), 0, true);
        if (len > 0) out.append(piece, len);
        ++generated;

        batch = llama_batch_get_one(&tok, 1);
    }

    llama_sampler_free(chain);

    const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - t0).count();
    LOGI("parse: prompt_tokens=%d gen_tokens=%d latency_ms=%lld", n, generated, (long long) ms);

    if (failed || out.empty()) return nullptr;
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_offhand_llm_LlamaEngine_nativeFree(JNIEnv *, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto * e = (Engine *) handle;
    if (e == nullptr) return;
    if (e->ctx) llama_free(e->ctx);
    if (e->model) llama_model_free(e->model);
    delete e;
}
