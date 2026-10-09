package com.qabas.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qabas.app.ui.theme.CairoFont
import com.qabas.app.ui.theme.DeepSlate
import com.qabas.app.ui.theme.GoldPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * محرك أجواء السكينة الطبيعية ثلاثية الأبعاد لقسم الريلز (Spiritual Soundscape & Nature Ambience)
 * بديل راقٍ ومطمئن للضوضاء والموسيقى، يمنح تجربة استماع خشوعية فريدة ممزوجة بأصوات الطبيعة.
 */
object ReelAmbienceSoundscapeEngine {

    private const val TAG = "SoundscapeEngine"

    data class AmbienceTrack(
        val id: String,
        val name: String,
        val icon: String,
        val url: String,
        val description: String
    )

    val SOUNDSCAPES = listOf(
        AmbienceTrack(
            id = "rain_calm",
            name = "مطر الحرم 🌧️",
            icon = "🌧️",
            url = "https://cdn.pixabay.com/download/audio/2022/05/16/audio_db6591201e.mp3",
            description = "صوت قطرات المطر الهادئة لراحة القلب والخشوع"
        ),
        AmbienceTrack(
            id = "birds_dawn",
            name = "عصافير الفجر 🕊️",
            icon = "🕊️",
            url = "https://cdn.pixabay.com/download/audio/2021/09/06/audio_834015f818.mp3",
            description = "تغريد طيور الصباح عند بزوغ الفجر"
        ),
        AmbienceTrack(
            id = "river_stream",
            name = "خرير الماء 🌊",
            icon = "🌊",
            url = "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c3527e30e2.mp3",
            description = "هدير ماء عذب يبعث على التأمل والسكينة"
        ),
        AmbienceTrack(
            id = "breeze_wind",
            name = "نسيم السحر 🍃",
            icon = "🍃",
            url = "https://cdn.pixabay.com/download/audio/2022/01/18/audio_d0a13f69d2.mp3",
            description = "حفيف أوراق الأشجار ونسيم عليل هادئ"
        ),
        AmbienceTrack(
            id = "night_calm",
            name = "سكون الليل 🌌",
            icon = "🌌",
            url = "https://cdn.pixabay.com/download/audio/2021/08/09/audio_88447d79b2.mp3",
            description = "هدوء الليل وجو التهجد والمناجاة"
        )
    )

    private var mediaPlayer: MediaPlayer? = null
    private val _activeSoundscape = MutableStateFlow<AmbienceTrack?>(null)
    val activeSoundscape: StateFlow<AmbienceTrack?> = _activeSoundscape.asStateFlow()

    private val _volume = MutableStateFlow(0.35f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun toggleSoundscape(context: Context, track: AmbienceTrack) {
        if (_activeSoundscape.value?.id == track.id && _isPlaying.value) {
            stop()
        } else {
            play(context, track)
        }
    }

    fun play(context: Context, track: AmbienceTrack) {
        stop()
        _activeSoundscape.value = track
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(track.url)
                isLooping = true
                val vol = _volume.value
                setVolume(vol, vol)
                setOnPreparedListener {
                    it.start()
                    _isPlaying.value = true
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error $what, $extra")
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start soundscape: ${e.message}")
            _isPlaying.value = false
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.setVolume(clamped, clamped)
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _activeSoundscape.value = null
    }
}

/**
 * شريط التحكم السريع في أجواء السكينة الطبيعية (Soundscape Sheet & Bar)
 */
@Composable
fun SoundscapeQuickBar(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeTrack by ReelAmbienceSoundscapeEngine.activeSoundscape.collectAsState()
    val isPlaying by ReelAmbienceSoundscapeEngine.isPlaying.collectAsState()
    val volume by ReelAmbienceSoundscapeEngine.volume.collectAsState()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = GoldPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "🌿", fontSize = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "أجواء السكينة الطبيعية (3D Soundscapes)",
                            fontFamily = CairoFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                        Text(
                            text = "خلفيات طبيعية نقية مهدئة تمزج مع التلاوة والمشاهد",
                            fontFamily = CairoFont,
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Soundscape Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReelAmbienceSoundscapeEngine.SOUNDSCAPES) { track ->
                    val isSelected = activeTrack?.id == track.id && isPlaying
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.clickable {
                            ReelAmbienceSoundscapeEngine.toggleSoundscape(context, track)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = track.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = track.name,
                                fontFamily = CairoFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp,
                                color = if (isSelected) DeepSlate else Color.White
                            )
                        }
                    }
                }
            }

            // Volume Slider when playing
            if (isPlaying && activeTrack != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeDown, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Slider(
                        value = volume,
                        onValueChange = { ReelAmbienceSoundscapeEngine.setVolume(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = GoldPrimary,
                            activeTrackColor = GoldPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { ReelAmbienceSoundscapeEngine.stop() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = "إيقاف", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
