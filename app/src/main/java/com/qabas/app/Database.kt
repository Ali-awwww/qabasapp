package com.qabas.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val idea: String,
    val analysis: String,
    val resources: String, // serialized
    val settings: String, // serialized
    val script: String, // serialized
    val finalVideoPath: String,
    val cost: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val lastScreen: String,
    val synced: Boolean = true
)

@Entity(tableName = "taste_profile")
data class TasteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // e.g., "STYLE", "PACING", "TONE", "TOPIC"
    val preferredValue: String,
    val weight: Int,
    val updatedAt: Long
)

@Entity(tableName = "hadith_cards")
data class HadithCardEntity(
    @PrimaryKey val id: String,
    val text: String,
    val narrator: String,
    val source: String,
    val styleName: String,
    val aspectRatio: String,
    val imagePath: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audio_tracks")
data class AudioTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistOrVibe: String,
    val typeCategory: String, // "NASHEED", "RECITATION", "AI_VOICEOVER", "SFX"
    val audioUrlOrPath: String,
    val duration: String,
    val scriptText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reel_scripts")
data class ReelScriptEntity(
    @PrimaryKey val id: String,
    val topic: String,
    val hookText: String,
    val bodyText: String,
    val ctaText: String,
    val duration: String,
    val vibe: String,
    val captionStyle: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "series_records")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    val topicPreset: String,
    val episodeCount: Int,
    val format: String,
    val epilogTemplate: String,
    val episodesJson: String,
    val scheduleFrequency: String = "DAILY",
    val status: String = "READY",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memorization_records")
data class MemorizationRecord(
    @PrimaryKey val surahId: Int,
    val surahName: String,
    val lastVerseMemorized: Int,
    val totalVerses: Int,
    val progressPercent: Float,
    val isCompleted: Boolean = false,
    val lastActivityAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quran_challenges")
data class QuranChallengeRecord(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val goalValue: Int,
    val currentValue: Int,
    val type: String, // READING, MEMORIZATION, TAJWEED
    val hasanatReward: Int,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    /** قراءة مرة واحدة — أسرع من Flow.firstOrNull للقوائم المتكررة */
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentProjects(limit: Int): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentProjectsOnce(limit: Int): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<ProjectEntity>)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("DELETE FROM projects")
    suspend fun clearAllProjects()

    @Query("SELECT COUNT(*) FROM projects WHERE createdAt >= :start AND createdAt < :end")
    suspend fun countProjectsBetween(start: Long, end: Long): Int
}

@Dao
interface TasteDao {
    @Query("SELECT * FROM taste_profile ORDER BY weight DESC")
    suspend fun getAllTastes(): List<TasteEntity>

    @Query("SELECT * FROM taste_profile ORDER BY weight DESC")
    fun getAllTastesFlow(): Flow<List<TasteEntity>>

    @Query("SELECT * FROM taste_profile WHERE category = :category AND preferredValue = :value LIMIT 1")
    suspend fun getTaste(category: String, value: String): TasteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaste(taste: TasteEntity)

    @Query("UPDATE taste_profile SET weight = weight + :amount, updatedAt = :time WHERE id = :id")
    suspend fun updateTasteWeight(id: Int, amount: Int, time: Long)

    @Query("DELETE FROM taste_profile")
    suspend fun clearTastes()
}

@Dao
interface HadithCardDao {
    @Query("SELECT * FROM hadith_cards ORDER BY createdAt DESC")
    fun getAllHadithCards(): Flow<List<HadithCardEntity>>

    @Query("SELECT * FROM hadith_cards ORDER BY createdAt DESC")
    suspend fun getAllHadithCardsOnce(): List<HadithCardEntity>

    @Query("SELECT * FROM hadith_cards WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteCards(): Flow<List<HadithCardEntity>>

    @Query("SELECT * FROM hadith_cards WHERE id = :id")
    suspend fun getCardById(id: String): HadithCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: HadithCardEntity)

    @Update
    suspend fun updateCard(card: HadithCardEntity)

    @Query("DELETE FROM hadith_cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("SELECT COUNT(*) FROM hadith_cards WHERE createdAt >= :start AND createdAt < :end")
    suspend fun countCardsBetween(start: Long, end: Long): Int
}

@Dao
interface AudioTrackDao {
    @Query("SELECT * FROM audio_tracks ORDER BY createdAt DESC")
    fun getAllAudioTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks ORDER BY createdAt DESC")
    suspend fun getAllAudioTracksOnce(): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_tracks WHERE typeCategory = :category ORDER BY createdAt DESC")
    fun getTracksByCategory(category: String): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE id = :id")
    suspend fun getTrackById(id: String): AudioTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: AudioTrackEntity)

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: String)
}

@Dao
interface ReelScriptDao {
    @Query("SELECT * FROM reel_scripts ORDER BY createdAt DESC")
    fun getAllReelScripts(): Flow<List<ReelScriptEntity>>

    @Query("SELECT * FROM reel_scripts WHERE id = :id")
    suspend fun getScriptById(id: String): ReelScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ReelScriptEntity)

    @Query("DELETE FROM reel_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: String)

    @Query("SELECT COUNT(*) FROM reel_scripts WHERE createdAt >= :start AND createdAt < :end")
    suspend fun countScriptsBetween(start: Long, end: Long): Int
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series_records ORDER BY updatedAt DESC")
    fun getAllSeries(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series_records WHERE id = :id")
    suspend fun getSeriesById(id: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: SeriesEntity)

    @Update
    suspend fun updateSeries(series: SeriesEntity)

    @Query("DELETE FROM series_records WHERE id = :id")
    suspend fun deleteSeriesById(id: String)
}

@Dao
interface MemorizationDao {
    @Query("SELECT * FROM memorization_records ORDER BY lastActivityAt DESC")
    fun getAllRecords(): Flow<List<MemorizationRecord>>

    @Query("SELECT * FROM memorization_records WHERE surahId = :surahId")
    suspend fun getRecordBySurah(surahId: Int): MemorizationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MemorizationRecord)

    @Query("SELECT SUM(progressPercent) / 114.0 FROM memorization_records")
    fun getTotalProgress(): Flow<Float?>
}

@Dao
interface QuranChallengeDao {
    @Query("SELECT * FROM quran_challenges WHERE isCompleted = 0")
    fun getActiveChallenges(): Flow<List<QuranChallengeRecord>>

    @Query("SELECT * FROM quran_challenges WHERE isCompleted = 0")
    suspend fun getActiveChallengesOnce(): List<QuranChallengeRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: QuranChallengeRecord)

    @Update
    suspend fun updateChallenge(challenge: QuranChallengeRecord)
}

@Entity(tableName = "tadabbur_records")
data class TadabburRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val surahId: Int,
    val surahName: String,
    val verseNumber: Int,
    val reflectionText: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface TadabburDao {
    @Query("SELECT * FROM tadabbur_records ORDER BY createdAt DESC")
    fun getAllReflections(): Flow<List<TadabburRecord>>

    @Query("SELECT * FROM tadabbur_records WHERE surahId = :surahId AND verseNumber = :verseNumber")
    suspend fun getReflectionForVerse(surahId: Int, verseNumber: Int): TadabburRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReflection(record: TadabburRecord)

    @Query("DELETE FROM tadabbur_records WHERE id = :id")
    suspend fun deleteReflection(id: Int)
}

@Entity(tableName = "workspace_drafts")
data class WorkspaceDraftEntity(
    @PrimaryKey val id: String = "active_ai_workspace",
    val workspaceType: String, // "AI_STUDIO" or "PRO_EDITOR"
    val projectId: String,
    val title: String,
    val contentText: String,
    val stateJson: String,
    val lastScreen: String,
    val lastSavedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "changelog_entries")
data class ChangelogEntity(
    @PrimaryKey val id: String,
    val versionName: String,
    val versionCode: Int,
    val title: String,
    val releaseDate: String,
    val highlights: String, // Multiline points
    val isLatest: Boolean = false,
    val categoryBadge: String = "تحديث رئيسي ⭐",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface WorkspaceDraftDao {
    @Query("SELECT * FROM workspace_drafts WHERE id = :id")
    fun getDraftFlow(id: String): Flow<WorkspaceDraftEntity?>

    @Query("SELECT * FROM workspace_drafts WHERE id = :id")
    suspend fun getDraft(id: String): WorkspaceDraftEntity?

    @Query("SELECT * FROM workspace_drafts ORDER BY lastSavedAt DESC")
    fun getAllDrafts(): Flow<List<WorkspaceDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: WorkspaceDraftEntity)

    @Query("DELETE FROM workspace_drafts WHERE id = :id")
    suspend fun deleteDraft(id: String)

    @Query("DELETE FROM workspace_drafts")
    suspend fun clearAllDrafts()
}

@Dao
interface ChangelogDao {
    @Query("SELECT * FROM changelog_entries ORDER BY versionCode DESC, timestamp DESC")
    fun getAllChangelogsFlow(): Flow<List<ChangelogEntity>>

    @Query("SELECT * FROM changelog_entries ORDER BY versionCode DESC, timestamp DESC")
    suspend fun getAllChangelogs(): List<ChangelogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChangelogs(list: List<ChangelogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChangelog(entry: ChangelogEntity)

    @Query("SELECT COUNT(*) FROM changelog_entries")
    suspend fun getCount(): Int
}

@Database(
    entities = [
        ProjectEntity::class,
        TasteEntity::class,
        HadithCardEntity::class,
        AudioTrackEntity::class,
        ReelScriptEntity::class,
        SeriesEntity::class,
        MemorizationRecord::class,
        QuranChallengeRecord::class,
        TadabburRecord::class,
        WorkspaceDraftEntity::class,
        ChangelogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun tasteDao(): TasteDao
    abstract fun hadithCardDao(): HadithCardDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun reelScriptDao(): ReelScriptDao
    abstract fun seriesDao(): SeriesDao
    abstract fun memorizationDao(): MemorizationDao
    abstract fun quranChallengeDao(): QuranChallengeDao
    abstract fun tadabburDao(): TadabburDao
    abstract fun workspaceDraftDao(): WorkspaceDraftDao
    abstract fun changelogDao(): ChangelogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qabas_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    fun getRecentProjects(limit: Int = 10): Flow<List<ProjectEntity>> =
        projectDao.getRecentProjects(limit)

    suspend fun getAllProjectsOnce(): List<ProjectEntity> =
        projectDao.getAllProjectsOnce()

    suspend fun getRecentProjectsOnce(limit: Int = 10): List<ProjectEntity> =
        projectDao.getRecentProjectsOnce(limit)

    suspend fun getProject(id: String) = projectDao.getProjectById(id)
    suspend fun insert(project: ProjectEntity) = projectDao.insertProject(project)
    suspend fun insertAll(projects: List<ProjectEntity>) {
        if (projects.isNotEmpty()) projectDao.insertProjects(projects)
    }
    suspend fun update(project: ProjectEntity) = projectDao.updateProject(project)
    suspend fun delete(id: String) = projectDao.deleteProjectById(id)
    suspend fun clearAll() = projectDao.clearAllProjects()
}

class SeriesRepository(private val seriesDao: SeriesDao) {
    val allSeries: Flow<List<SeriesEntity>> = seriesDao.getAllSeries()

    suspend fun getSeries(id: String) = seriesDao.getSeriesById(id)
    suspend fun insert(series: SeriesEntity) = seriesDao.insertSeries(series)
    suspend fun update(series: SeriesEntity) = seriesDao.updateSeries(series)
    suspend fun delete(id: String) = seriesDao.deleteSeriesById(id)
}

class TasteRepository(private val tasteDao: TasteDao) {
    val allTastesFlow: Flow<List<TasteEntity>> = tasteDao.getAllTastesFlow()

    suspend fun getAllTastes() = tasteDao.getAllTastes()

    suspend fun registerPreference(category: String, value: String, amount: Int = 1) {
        val existing = tasteDao.getTaste(category, value)
        val now = System.currentTimeMillis()
        if (existing != null) {
            tasteDao.updateTasteWeight(existing.id, amount, now)
        } else {
            tasteDao.insertTaste(
                TasteEntity(
                    category = category,
                    preferredValue = value,
                    weight = amount,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun clearAll() = tasteDao.clearTastes()
}

class HadithCardRepository(private val hadithCardDao: HadithCardDao) {
    val allCards: Flow<List<HadithCardEntity>> = hadithCardDao.getAllHadithCards()
    val favoriteCards: Flow<List<HadithCardEntity>> = hadithCardDao.getFavoriteCards()

    suspend fun getCard(id: String) = hadithCardDao.getCardById(id)
    suspend fun insert(card: HadithCardEntity) = hadithCardDao.insertCard(card)
    suspend fun update(card: HadithCardEntity) = hadithCardDao.updateCard(card)
    suspend fun delete(id: String) = hadithCardDao.deleteCardById(id)
}

class AudioTrackRepository(private val audioTrackDao: AudioTrackDao) {
    val allTracks: Flow<List<AudioTrackEntity>> = audioTrackDao.getAllAudioTracks()

    fun getCategoryTracks(category: String): Flow<List<AudioTrackEntity>> =
        audioTrackDao.getTracksByCategory(category)

    suspend fun getTrack(id: String) = audioTrackDao.getTrackById(id)
    suspend fun insert(track: AudioTrackEntity) = audioTrackDao.insertTrack(track)
    suspend fun delete(id: String) = audioTrackDao.deleteTrackById(id)
}

class ReelScriptRepository(private val reelScriptDao: ReelScriptDao) {
    val allScripts: Flow<List<ReelScriptEntity>> = reelScriptDao.getAllReelScripts()

    suspend fun getScript(id: String) = reelScriptDao.getScriptById(id)
    suspend fun insert(script: ReelScriptEntity) = reelScriptDao.insertScript(script)
    suspend fun delete(id: String) = reelScriptDao.deleteScriptById(id)
}

class WorkspaceRepository(private val workspaceDraftDao: WorkspaceDraftDao) {
    fun getDraftFlow(id: String = "active_ai_workspace"): Flow<WorkspaceDraftEntity?> =
        workspaceDraftDao.getDraftFlow(id)

    suspend fun getDraft(id: String = "active_ai_workspace"): WorkspaceDraftEntity? =
        workspaceDraftDao.getDraft(id)

    fun getAllDrafts(): Flow<List<WorkspaceDraftEntity>> =
        workspaceDraftDao.getAllDrafts()

    suspend fun saveWorkspace(
        id: String,
        workspaceType: String,
        projectId: String,
        title: String,
        contentText: String,
        stateJson: String,
        lastScreen: String
    ) {
        workspaceDraftDao.insertDraft(
            WorkspaceDraftEntity(
                id = id,
                workspaceType = workspaceType,
                projectId = projectId,
                title = title,
                contentText = contentText,
                stateJson = stateJson,
                lastScreen = lastScreen,
                lastSavedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDraft(id: String) = workspaceDraftDao.deleteDraft(id)
    suspend fun clearAllDrafts() = workspaceDraftDao.clearAllDrafts()
}

class ChangelogRepository(private val changelogDao: ChangelogDao) {
    val allChangelogsFlow: Flow<List<ChangelogEntity>> = changelogDao.getAllChangelogsFlow()

    suspend fun getAllChangelogs(): List<ChangelogEntity> {
        ensureDefaultChangelogs()
        return changelogDao.getAllChangelogs()
    }

    suspend fun insertChangelog(entry: ChangelogEntity) = changelogDao.insertChangelog(entry)

    suspend fun ensureDefaultChangelogs() {
        if (changelogDao.getCount() == 0) {
            val defaults = listOf(
                ChangelogEntity(
                    id = "v2_4_0",
                    versionName = "2.4.0",
                    versionCode = 240,
                    title = "هندسة الشاشات المستقلة ودعم الخوادم ومجتمع واتساب 🚀",
                    releaseDate = "26 سبتمبر 2026",
                    categoryBadge = "أحدث إصدار 🌟",
                    isLatest = true,
                    highlights = "• إعادة هندسة الإعدادات إلى شاشات فرعية مستقلة بنظام Material 3 كامل.\n• إضافة قسم الدعم والمساهمة المتكامل وكفالة الخوادم بالتعليقات والأسهم.\n• ربط مجتمع واتساب الرسمي مع إمكانية الدخول المباشر ونسخ الرابط بلمسة.\n• تزامن سحابي حي لبيانات الدعم والتحديثات عبر Firestore.\n• تسريع تشغيل الفيديو وتحديث واجهة مشكاة الهدى بتصميم زجاجي مذهب."
                ),
                ChangelogEntity(
                    id = "v2_3_0",
                    versionName = "2.3.0",
                    versionCode = 230,
                    title = "استوديو بطاقات الحديث النبوي ومكتبة الصوتيات 📜",
                    releaseDate = "15 سبتمبر 2026",
                    categoryBadge = "تحديث رئيسي ✦",
                    isLatest = false,
                    highlights = "• إطلاق استوديو بطاقات الحديث الشريف بـ 5 قوالب جمالية متقدمة.\n• مكتبة الصوتيات والتلاوات النادرة مع دعم التشغيل في الخلفية.\n• فحص التجويد الصوتي المباشر وحساب مواقيت الصلاة الفلكي الدقيق.\n• نظام التنزيل الذكي في الخلفية مع شريط التقدم ونسبة الإنجاز الحية."
                ),
                ChangelogEntity(
                    id = "v2_1_0",
                    versionName = "2.1.0",
                    versionCode = 210,
                    title = "حارس المحتوى الشرعي والوضع الليلي الملكي 🛡️",
                    releaseDate = "28 أغسطس 2026",
                    categoryBadge = "تحسينات أمان ⚡",
                    isLatest = false,
                    highlights = "• تفعيل فلتر المحتوى الذكي (Islamic ContentGuard) لضمان نقاء المحتوى.\n• دعم التبديل السلس بين النمط الليلي الفاخر والنمط الفاتح.\n• تحسين محرك التصدير السينمائي FFmpeg بدقة 1080p و 4K بدون تقطيع.\n• إضافة بوصلة القبلة التفاعلية وسجل الأذكار اليومية."
                ),
                ChangelogEntity(
                    id = "v1_0_0",
                    versionName = "1.0.0",
                    versionCode = 100,
                    title = "الإطلاق الرسمي لمنظومة قبس لصناعة المحتوى الإسلامي 🌟",
                    releaseDate = "1 أغسطس 2026",
                    categoryBadge = "الإطلاق الأولي 🕋",
                    isLatest = false,
                    highlights = "• المخرج الذكي بالذكاء الاصطناعي لتوليد نصوص وسيناريوهات الريلز الدعوية.\n• المصحف المرتل برواية حفص عن عاصم وتفسير الآيات.\n• قاعدة بيانات محلية فائقة السرعة مع تخزين مشفر للمشاريع والمفضلات."
                )
            )
            changelogDao.insertChangelogs(defaults)
        }
    }
}

