package com.qabas.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ⚡ XdeltaPatchEngine — محرك الترقيع التفاضلي المدمج عبر JNI Native C++
 * يقوم بتطبيق رقع xdelta3 مباشرة في ذاكرة ومعالج التطبيق دون الاعتماد على ProcessBuilder خارجي.
 */
object XdeltaPatchEngine {
    private const val TAG = "XdeltaPatchEngine"
    private const val PRIMARY_LIB = "qabas_xdelta"
    private const val FALLBACK_LIB = "xdelta3"

    @Volatile
    private var isLoaded = false
    @Volatile
    private var loadError: String? = null

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        try {
            System.loadLibrary(PRIMARY_LIB)
            isLoaded = true
            Log.i(TAG, "✅ Loaded native library: $PRIMARY_LIB")
        } catch (e: Throwable) {
            try {
                System.loadLibrary(FALLBACK_LIB)
                isLoaded = true
                Log.i(TAG, "✅ Loaded fallback native library: $FALLBACK_LIB")
            } catch (e2: Throwable) {
                isLoaded = false
                loadError = "${e.message} | ${e2.message}"
                Log.w(TAG, "⚠️ Native xdelta3 libraries not loaded into runtime: $loadError")
            }
        }
    }

    fun isNativeEngineAvailable(): Boolean {
        if (!isLoaded) {
            loadNativeLibrary()
        }
        return isLoaded
    }

    @JvmStatic
    private external fun applyPatchNative(
        baseApkPath: String,
        patchPath: String,
        outputApkPath: String
    ): Int

    /**
     * تطبيق رقعة xdelta3 على الـ APK الأساسي لإنتاج الـ APK الجديد.
     */
    suspend fun applyPatch(
        baseApk: File,
        patchFile: File,
        outputApk: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!baseApk.exists() || baseApk.length() == 0L) {
            val err = "ملف التطبيق المصدر غير موجود أو فارغ: ${baseApk.path}"
            Log.e(TAG, "❌ $err")
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (!patchFile.exists() || patchFile.length() == 0L) {
            val err = "ملف رقعة الدلتا غير موجود أو فارغ: ${patchFile.path}"
            Log.e(TAG, "❌ $err")
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (outputApk.exists()) {
            outputApk.delete()
        }
        outputApk.parentFile?.mkdirs()

        if (isNativeEngineAvailable()) {
            try {
                Log.i(TAG, "⚡ Starting in-process xdelta3 JNI patching...")
                val startTime = System.currentTimeMillis()

                val exitCode = applyPatchNative(
                    baseApkPath = baseApk.absolutePath,
                    patchPath = patchFile.absolutePath,
                    outputApkPath = outputApk.absolutePath
                )

                val elapsed = System.currentTimeMillis() - startTime
                if (exitCode == 0 && outputApk.exists() && outputApk.length() > 0L) {
                    Log.i(TAG, "✅ JNI xdelta3 patch finished successfully in ${elapsed}ms (Result size: ${outputApk.length()} bytes)")
                    return@withContext Result.success(Unit)
                } else {
                    val msg = "xdelta3 JNI returned failure code: $exitCode (output exists: ${outputApk.exists()}, size: ${outputApk.length()})"
                    Log.e(TAG, "❌ $msg")
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Exception during JNI patch execution: ${t.message}", t)
                return@withContext Result.failure(t)
            }
        } else {
            val msg = "المحرك الأصلي C++ xdelta3 غير متوفر: $loadError"
            Log.w(TAG, "⚠️ $msg")
            return@withContext Result.failure(IllegalStateException(msg))
        }
    }
}
