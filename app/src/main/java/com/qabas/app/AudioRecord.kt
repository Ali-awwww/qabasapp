package com.qabas.app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * نموذج بيانات التسجيل الصوتي والدروس العلمية المتوافق مع Supabase.
 * يدعم الفرز الزمني (created_at)، الحفظ السحابي، وإدارة الرفع الخاصة بالمطور/المشرف (Admin-only).
 */
@Immutable
@Serializable
data class AudioRecord(
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerialName("title")
    val title: String,

    @SerialName("category")
    val category: String = "دروس علمية ومحاضرات",

    @SerialName("author")
    val author: String = "المطور",

    @SerialName("artist")
    val artist: String = "",

    @SerialName("file_url")
    val fileUrl: String = "",

    @SerialName("audio_url")
    val audioUrl: String = fileUrl,

    @SerialName("duration")
    val duration: String = "0:00",

    @SerialName("badge")
    val badge: String = "سحابي",

    @SerialName("likes_count")
    val likesCount: Int = 0,

    @SerialName("is_active")
    val isActive: Boolean = true,

    @SerialName("is_admin_upload")
    val isAdminUpload: Boolean = true,

    @SerialName("user_id")
    val userId: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)
