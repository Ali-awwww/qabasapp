package com.qabas.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 🛠️ PatchManager — مسئول عن تطبيق رقع التحديث (xdelta) والتحقق من سلامتها.
 */
object PatchManager {
    private const val TAG = "PatchManager"

    sealed class PatchState {
        object Idle : PatchState()
        object Verifying : PatchState()
        object Patching : PatchState()
        data class Success(val apkFile: File) : PatchState()
        data class Error(val message: String) : PatchState()
    }

    private val _patchState = MutableStateFlow<PatchState>(PatchState.Idle)
    val patchState: StateFlow<PatchState> = _patchState.asStateFlow()

    /**
     * التحقق من بصمة الملف (Checksum) لضمان سلامته قبل المعالجة.
     */
    fun verifyChecksum(file: File, expectedChecksum: String?): Boolean {
        if (expectedChecksum == null) return true // لا توجد بصمة للمقارنة
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            val inputStream = FileInputStream(file)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
            Log.d(TAG, "🔍 Checksum verification: Actual=$actualChecksum, Expected=$expectedChecksum")
            return actualChecksum.equals(expectedChecksum, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Checksum verification failed: ${e.message}")
            return false
        }
    }

    /**
     * تطبيق رقعة التحديث على الـ APK الحالي.
     */
    suspend fun applyXdeltaPatch(
        context: Context,
        update: UpdateManager.UpdateInfo,
        deltaFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        _patchState.value = PatchState.Verifying
        
        // 1. التحقق من سلامة ملف الدلتا
        if (!verifyChecksum(deltaFile, update.checksum)) {
            val errMsg = "ملف التحديث تالف أو لا يطابق البصمة المعتمدة"
            _patchState.value = PatchState.Error(errMsg)
            UpdateTelemetryManager.recordFailure(
                context = context,
                update = update,
                phase = "CHECKSUM_VERIFY",
                throwable = null,
                customMessage = "فشل التحقق من بصمة SHA-256 لملف الدلتا (${deltaFile.name})، البصمة المتوقعة: ${update.checksum}"
            )
            return@withContext false
        }

        _patchState.value = PatchState.Patching

        // 2. تحديد الملفات المطلوبة
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outputApk = File(updatesDir, "qabas-${update.versionName}-${update.versionCode}-patched.tmp")
        val finalApk = File(updatesDir, "qabas-${update.versionName}-${update.versionCode}-patched.apk")
        
        // المسار الأساسي هو الـ APK الحالي المثبت
        val sourceApk = File(context.packageCodePath)
        
        // 3. تنفيذ عملية الرقعة عبر JNI Native C++ أولاً (الأسرع والأكثر أماناً)
        var patchApplied = false
        if (XdeltaPatchEngine.isNativeEngineAvailable()) {
            val jniResult = XdeltaPatchEngine.applyPatch(sourceApk, deltaFile, outputApk)
            if (jniResult.isSuccess && outputApk.exists() && outputApk.length() > 1_000_000) {
                patchApplied = true
                Log.i(TAG, "⚡ Applied patch using in-process JNI Native C++ successfully")
            } else {
                Log.w(TAG, "⚠️ JNI Native patch failed, attempting fallback runner: ${jniResult.exceptionOrNull()?.message}")
            }
        }

        // المسار الاحتياطي: في حال تعذر JNI، محاولة تشغيل الثنائي
        if (!patchApplied) {
            val xdeltaBinary = ensureBinary(context, update)
            if (xdeltaBinary != null) {
                val result = UpdateManager.applyXdelta(xdeltaBinary, sourceApk, deltaFile, outputApk)
                if (result.isSuccess && outputApk.exists() && outputApk.length() > 1_000_000) {
                    patchApplied = true
                }
            }
        }

        if (patchApplied && outputApk.exists() && outputApk.length() > 1_000_000) {
            // 4. التحقق من الـ APK الناتج
            if (UpdateManager.isApkValid(context, outputApk, update.versionCode)) {
                if (outputApk.renameTo(finalApk)) {
                    _patchState.value = PatchState.Success(finalApk)
                    deltaFile.delete()
                    Log.d(TAG, "✅ Patch applied and verified: ${finalApk.name}")
                    return@withContext true
                }
            } else {
                UpdateTelemetryManager.recordFailure(
                    context = context,
                    update = update,
                    phase = "VALIDATE_PATCHED_APK",
                    throwable = null,
                    customMessage = "حزمة APK الناتجة بعد الرقعة غير صالحة برمجياً أو لا تطابق versionCode=${update.versionCode} (الحجم: ${outputApk.length()} بايت)"
                )
            }
        } else {
            val failureReason = "فشل تطبيق الرقعة عبر محرك JNI Native والثنائي الاحتياطي"
            outputApk.delete()
            _patchState.value = PatchState.Error(failureReason)
            UpdateTelemetryManager.recordFailure(
                context = context,
                update = update,
                phase = "PATCH_APPLY",
                throwable = null,
                customMessage = failureReason
            )
            return@withContext false
        }

        outputApk.delete()
        _patchState.value = PatchState.Error("فشلت معالجة الرقعة")
        false
    }

    private fun ensureBinary(context: Context, update: UpdateManager.UpdateInfo): File? {
        // نستخدم المحرك الموجود مسبقاً في التطبيق لضمان التوافق
        // نستخدم مقبض تنزيل وهمي لعملية التأمين فقط
        val dummyHandle = UpdateManager.DownloadHandle()
        return UpdateManager.ensureXdeltaBinary(context, update, null, dummyHandle)
    }

    fun reset() {
        _patchState.value = PatchState.Idle
    }
}
