#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#define LOG_TAG "xdelta3_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Declared in xdelta3.c when XD3_MAIN=1 and XD3_INCLUDE_MAIN=1
extern "C" int xd3_main_cmdline(int argc, char **argv);

extern "C" JNIEXPORT jint JNICALL
Java_com_qabas_app_XdeltaPatchEngine_applyPatchNative(
    JNIEnv *env, jclass,
    jstring jBaseApk, jstring jPatchFile, jstring jOutApk) {

    if (!jBaseApk || !jPatchFile || !jOutApk) {
        LOGE("xdelta3 JNI: null arguments passed");
        return -1;
    }

    const char *basePath = env->GetStringUTFChars(jBaseApk, nullptr);
    const char *patchPath = env->GetStringUTFChars(jPatchFile, nullptr);
    const char *outPath = env->GetStringUTFChars(jOutApk, nullptr);

    LOGI("xdelta3 JNI: Applying patch. Base=%s, Patch=%s, Out=%s", basePath, patchPath, outPath);

    // Command line args corresponding to: xdelta3 -d -f -s <basePath> <patchPath> <outPath>
    char *argv[] = {
        (char *)"xdelta3",
        (char *)"-d",
        (char *)"-f",
        (char *)"-s",
        (char *)basePath,
        (char *)patchPath,
        (char *)outPath,
        nullptr
    };

    int ret = xd3_main_cmdline(7, argv);

    env->ReleaseStringUTFChars(jBaseApk, basePath);
    env->ReleaseStringUTFChars(jPatchFile, patchPath);
    env->ReleaseStringUTFChars(jOutApk, outPath);

    if (ret != 0) {
        LOGE("xdelta3 JNI: Patch application failed with code %d", ret);
    } else {
        LOGI("xdelta3 JNI: Patch application succeeded successfully (exit code 0)");
    }

    return (jint)ret;
}
