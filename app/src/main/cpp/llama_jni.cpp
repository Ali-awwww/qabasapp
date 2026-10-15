// llama_jni.cpp — JNI bridge for on-device GGUF inference.
// Real backend (LLAMA_REAL_BACKEND=1) uses llama.cpp C API; otherwise an honest stub.
#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef LLAMA_REAL_BACKEND
#include "llama.h"
#include "common.h"

struct Ctx {
    llama_model *model = nullptr;
    llama_context *lctx = nullptr;
    const llama_vocab *vocab = nullptr;
    int n_ctx = 2048;
};

static bool endsWithStop(const std::string &s, const std::vector<std::string> &stops) {
    for (auto &st : stops) {
        if (!st.empty() && s.size() >= st.size() &&
            s.compare(s.size() - st.size(), st.size(), st) == 0) return true;
    }
    return false;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_qabas_app_LlamaCppService_llamaInit(JNIEnv *env, jobject, jstring jPath, jint nCtx, jint) {
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    std::string modelPath(path ? path : "");
    env->ReleaseStringUTFChars(jPath, path);

    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-only baseline; GPU offload is a follow-up
    llama_model *model = llama_model_load_from_file(modelPath.c_str(), mparams);
    if (!model) { LOGE("model load failed: %s", modelPath.c_str()); return 0; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 0 ? (uint32_t)nCtx : 2048;
    cparams.n_batch = 512;
    llama_context *lctx = llama_init_from_model(model, cparams);
    if (!lctx) { LOGE("context init failed"); llama_model_free(model); return 0; }

    auto *ctx = new Ctx{model, lctx, llama_model_get_vocab(model), (int)cparams.n_ctx};
    LOGI("initialized %s ctx=%d", modelPath.c_str(), ctx->n_ctx);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_qabas_app_LlamaCppService_llamaGenerate(JNIEnv *env, jobject, jlong ptr,
    jstring jPrompt, jint nPredict, jfloat temp, jfloat topP, jobjectArray jStops) {
    auto *ctx = reinterpret_cast<Ctx *>(ptr);
    if (!ctx || !ctx->lctx) return env->NewStringUTF("");

    const char *p = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(p ? p : "");
    env->ReleaseStringUTFChars(jPrompt, p);

    std::vector<std::string> stops;
    if (jStops) {
        jsize n = env->GetArrayLength(jStops);
        for (jsize i = 0; i < n; i++) {
            auto js = (jstring)env->GetObjectArrayElement(jStops, i);
            const char *c = env->GetStringUTFChars(js, nullptr);
            stops.emplace_back(c ? c : "");
            env->ReleaseStringUTFChars(js, c);
            env->DeleteLocalRef(js);
        }
    }

    const int n_prompt = -llama_tokenize(ctx->vocab, prompt.c_str(), (int)prompt.size(),
        nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(ctx->vocab, prompt.c_str(), (int)prompt.size(),
        tokens.data(), (int)tokens.size(), true, true) < 0) {
        return env->NewStringUTF("");
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
    std::string out;
    out.reserve(1024);
    int n_gen = 0;
    llama_token eos = llama_vocab_eos(ctx->vocab);

    auto *chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_greedy());

    while (n_gen < nPredict) {
        if (llama_decode(ctx->lctx, batch) != 0) break;
        llama_token tok = llama_sampler_sample(chain, ctx->lctx, -1);
        if (tok == eos) break;
        char buf[256] = {0};
        int n = llama_token_to_piece(ctx->vocab, tok, buf, sizeof(buf), 0, true);
        if (n > 0) out.append(buf, n);
        if (endsWithStop(out, stops)) break;
        batch = llama_batch_get_one(&tok, 1);
        n_gen++;
    }
    llama_sampler_free(chain);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_qabas_app_LlamaCppService_llamaFree(JNIEnv *, jobject, jlong ptr) {
    auto *ctx = reinterpret_cast<Ctx *>(ptr);
    if (!ctx) return;
    if (ctx->lctx) llama_free(ctx->lctx);
    if (ctx->model) llama_model_free(ctx->model);
    delete ctx;
}

#else
// Stub: honest unavailable signal.
extern "C" JNIEXPORT jlong JNICALL
Java_com_qabas_app_LlamaCppService_llamaInit(JNIEnv *, jobject, jstring, jint, jint) { return 0; }
extern "C" JNIEXPORT jstring JNICALL
Java_com_qabas_app_LlamaCppService_llamaGenerate(JNIEnv *env, jobject, jlong, jstring, jint, jfloat, jfloat, jobjectArray) {
    return env->NewStringUTF("");
}
extern "C" JNIEXPORT void JNICALL
Java_com_qabas_app_LlamaCppService_llamaFree(JNIEnv *, jobject, jlong) {}
#endif
