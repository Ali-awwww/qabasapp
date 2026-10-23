package com.qabas.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🛰️ SpatialCameraPreferencesManager
 * مدير تفضيلات وإعدادات زر الكاميرا الفضائي للبلاغات والمشاكل.
 * يدعم التخزين الفوري في SharedPreferences وتحديث الحالة التفاعلية لـ Compose بدون تأخير.
 */
object SpatialCameraPreferencesManager {
    private const val PREFS_NAME = "qabas_prefs"

    // مفاتيح التفضيلات
    const val KEY_CAMERA_ENABLED = "spatial_cam_enabled"
    const val KEY_COMPACT_MODE = "spatial_cam_compact_mode"
    const val KEY_DOCKED_MODE = "spatial_cam_docked_mode"
    const val KEY_TAP_INSTANT_SNAP = "spatial_cam_tap_instant_snap"
    const val KEY_SKIP_WHITE_FLASH = "spatial_cam_skip_white_flash"
    const val KEY_IDLE_ALPHA = "spatial_cam_idle_alpha"
    const val KEY_HAPTIC_ENABLED = "spatial_cam_haptic_feedback"
    const val KEY_OFFSET_X = "spatial_dev_cam_offset_x"
    const val KEY_OFFSET_Y = "spatial_dev_cam_offset_y"

    private val _cameraEnabled = MutableStateFlow(true)
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled.asStateFlow()

    private val _compactMode = MutableStateFlow(false)
    val compactMode: StateFlow<Boolean> = _compactMode.asStateFlow()

    private val _dockedMode = MutableStateFlow(false)
    val dockedMode: StateFlow<Boolean> = _dockedMode.asStateFlow()

    private val _tapInstantSnap = MutableStateFlow(false)
    val tapInstantSnap: StateFlow<Boolean> = _tapInstantSnap.asStateFlow()

    private val _skipWhiteFlash = MutableStateFlow(true)
    val skipWhiteFlash: StateFlow<Boolean> = _skipWhiteFlash.asStateFlow()

    private val _idleAlpha = MutableStateFlow(1.0f)
    val idleAlpha: StateFlow<Float> = _idleAlpha.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private var isInitialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _cameraEnabled.value = prefs.getBoolean(KEY_CAMERA_ENABLED, true)
        _compactMode.value = prefs.getBoolean(KEY_COMPACT_MODE, false)
        _dockedMode.value = prefs.getBoolean(KEY_DOCKED_MODE, false)
        _tapInstantSnap.value = prefs.getBoolean(KEY_TAP_INSTANT_SNAP, false)
        _skipWhiteFlash.value = prefs.getBoolean(KEY_SKIP_WHITE_FLASH, true)
        _idleAlpha.value = prefs.getFloat(KEY_IDLE_ALPHA, 1.0f)
        _hapticEnabled.value = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        isInitialized = true
    }

    fun setCameraEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        setCameraEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        _cameraEnabled.value = enabled
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_CAMERA_ENABLED, enabled)?.apply()
    }

    fun setCompactMode(context: Context, compact: Boolean) {
        appContext = context.applicationContext
        setCompactMode(compact)
    }

    fun setCompactMode(compact: Boolean) {
        _compactMode.value = compact
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_COMPACT_MODE, compact)?.apply()
    }

    fun setDockedMode(context: Context, docked: Boolean) {
        appContext = context.applicationContext
        setDockedMode(docked)
    }

    fun setDockedMode(docked: Boolean) {
        _dockedMode.value = docked
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_DOCKED_MODE, docked)?.apply()
    }

    fun setTapInstantSnap(context: Context, instant: Boolean) {
        appContext = context.applicationContext
        setTapInstantSnap(instant)
    }

    fun setTapInstantSnap(instant: Boolean) {
        _tapInstantSnap.value = instant
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_TAP_INSTANT_SNAP, instant)?.apply()
    }

    fun setSkipWhiteFlash(context: Context, skip: Boolean) {
        appContext = context.applicationContext
        setSkipWhiteFlash(skip)
    }

    fun setSkipWhiteFlash(skip: Boolean) {
        _skipWhiteFlash.value = skip
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_SKIP_WHITE_FLASH, skip)?.apply()
    }

    fun setIdleAlpha(context: Context, alpha: Float) {
        appContext = context.applicationContext
        setIdleAlpha(alpha)
    }

    fun setIdleAlpha(alpha: Float) {
        _idleAlpha.value = alpha
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putFloat(KEY_IDLE_ALPHA, alpha)?.apply()
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        setHapticEnabled(enabled)
    }

    fun setHapticEnabled(enabled: Boolean) {
        _hapticEnabled.value = enabled
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_HAPTIC_ENABLED, enabled)?.apply()
    }

    fun resetPosition(context: Context? = null) {
        val target = context?.applicationContext ?: appContext
        target?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_OFFSET_X)
            ?.remove(KEY_OFFSET_Y)
            ?.apply()
    }

    fun resetToDefaults(context: Context? = null) {
        context?.let { appContext = it.applicationContext }
        setCameraEnabled(true)
        setCompactMode(false)
        setDockedMode(false)
        setTapInstantSnap(false)
        setSkipWhiteFlash(true)
        setIdleAlpha(1.0f)
        setHapticEnabled(true)
        resetPosition(context)
    }
}
