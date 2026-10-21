package com.qabas.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 🛰️ NotificationActionReceiver — مستقبل أحداث وأدوات الإشعارات التفاعلية في تطبيق قبس
 * يقوم بتنفيذ أدوات الإشعار مباشرة (الاستماع الفوري، المشاركة، تأكيد القراءة، والتأجيل).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SHARE = "com.qabas.app.ACTION_NOTIFICATION_SHARE"
        const val ACTION_MARK_DONE = "com.qabas.app.ACTION_NOTIFICATION_MARK_DONE"
        const val ACTION_LISTEN = "com.qabas.app.ACTION_NOTIFICATION_LISTEN"
        const val ACTION_SNOOZE = "com.qabas.app.ACTION_NOTIFICATION_SNOOZE"

        const val EXTRA_SHARE_TEXT = "extra_share_text"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_AUDIO_URL = "extra_audio_url"
        const val EXTRA_AUDIO_TITLE = "extra_audio_title"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        when (intent.action) {
            // ACTION_SHARE نُقل إلى نشاطي مباشر في QabasNotificationHelper.addActionShare
            // (قفزات الإشعارات عبر BroadcastReceiver محجوبة في أندرويد 12+)

            ACTION_MARK_DONE -> {
                val msg = intent.getStringExtra(EXTRA_MESSAGE)
                    ?: "تقبل الله طاعتكم 🌿 تم تسجيل إتمام الورد اليومي بنجاح"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                NotificationPreferencesManager.recordWirdCompleted(context)
                if (notifId != -1) notifManager?.cancel(notifId)
            }

            ACTION_LISTEN -> {
                val title = intent.getStringExtra(EXTRA_AUDIO_TITLE) ?: "تلاوة خاشعة مباركة"
                val url = intent.getStringExtra(EXTRA_AUDIO_URL)
                    ?: "https://server8.mp3quran.net/afs/001.mp3"
                Toast.makeText(context, "جاري تشغيل الاستماع في الخلفية 🎧...", Toast.LENGTH_SHORT).show()
                val track = PlayableAudioTrack(
                    id = "notif_track_${System.currentTimeMillis()}",
                    title = title,
                    artist = "استوديو قبس",
                    audioUrl = url,
                    category = "تلاوة تفاعلية"
                )
                AudioPlaybackManager.playTrack(context, track)
                if (notifId != -1) notifManager?.cancel(notifId)
            }

            ACTION_SNOOZE -> {
                if (notifId != -1) notifManager?.cancel(notifId)
                Toast.makeText(context, "تم تأجيل التنبيه 15 دقيقة ⏰", Toast.LENGTH_SHORT).show()
                NotificationPreferencesManager.scheduleSnooze(context, 15)
            }

        }
    }
}
