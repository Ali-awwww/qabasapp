package com.qabas.app

/**
 * قوالب مشاريع بادئة حقيقية (12.15): كل قالب 3 ملفات Kotlin أولية
 * فعلية (نموذج بيانات + شاشة + منطق)، وينشئ مشروع فايب كودينج جديداً
 * عبر [VibeCodingProjects.createFromTemplate] بملفاته الأولية.
 *
 * لا قوالب وهمية: نصوص الأذكار منسوخة حرفياً من AzkarScreen.kt في
 * التطبيق نفسه (مع المصدر والعدد)، وقائمة السور موسومة صراحةً بأنها
 * قائمة بادئة بأول 5 سور تُكمل إلى 114 مع الوكيل، والكتب الثلاثة
 * موجودة فعلاً في أصول التطبيق.
 */
data class VibeTemplate(
    val id: String,
    val title: String,
    val description: String,
    val files: Map<String, String>,
    val startPrompt: String
)

object VibeTemplates {

    val MUSHAF: VibeTemplate = VibeTemplate(
        id = "mushaf_starter",
        title = "تطبيق مصحف بسيط",
        description = "شاشة رئيسية بقائمة سور + قارئ بتنقل صفحات. قائمة بادئة بأول 5 سور فقط — تُكمل إلى 114 سورة مع الوكيل، ومستودع السور واجهة تُربط بمصدر موثق.",
        files = mapOf(
            "Surah.kt" to """
                package com.example.mushaf

                /** سورة: الاسم وعدد الآيات. قائمة بادئة بأول 5 سور فقط — تُكمل إلى 114 مع الوكيل. */
                data class Surah(val id: Int, val name: String, val ayahCount: Int)

                val STARTER_SURAHS = listOf(
                    Surah(1, "الفاتحة", 7),
                    Surah(2, "البقرة", 286),
                    Surah(3, "آل عمران", 200),
                    Surah(4, "النساء", 176),
                    Surah(5, "المائدة", 120)
                )

                /** واجهة مستودع السور — تُربط بمصدر نص موثق (لا نص مختلق). */
                interface SurahRepository {
                    suspend fun surahText(surahId: Int): String
                }
            """.trimIndent(),
            "HomeScreen.kt" to """
                package com.example.mushaf

                import androidx.compose.foundation.clickable
                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.fillMaxWidth
                import androidx.compose.foundation.layout.padding
                import androidx.compose.foundation.lazy.LazyColumn
                import androidx.compose.foundation.lazy.items
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp

                /** الشاشة الرئيسية: قائمة السور البادئة (5 سور) — تُكمل إلى 114 مع الوكيل. */
                @Composable
                fun HomeScreen(onSurahSelected: (Surah) -> Unit) {
                    LazyColumn {
                        items(STARTER_SURAHS, key = { it.id }) { surah ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSurahSelected(surah) }
                                    .padding(16.dp)
                            ) {
                                Text(surah.name)
                                Text("عدد الآيات: " + surah.ayahCount)
                            }
                        }
                    }
                }
            """.trimIndent(),
            "ReaderScreen.kt" to """
                package com.example.mushaf

                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.Row
                import androidx.compose.material3.Button
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.runtime.getValue
                import androidx.compose.runtime.mutableStateOf
                import androidx.compose.runtime.remember
                import androidx.compose.runtime.setValue

                /** قارئ بتنقل صفحات: 604 صفحات (مصحف المدينة) — الصفحة حالة حقيقية. */
                @Composable
                fun ReaderScreen(surah: Surah, repository: SurahRepository) {
                    var currentPage by remember { mutableStateOf(1) }
                    val totalPages = 604
                    Column {
                        Text(surah.name + " — الصفحة " + currentPage + " من " + totalPages)
                        Row {
                            Button(
                                onClick = { if (currentPage > 1) currentPage-- },
                                enabled = currentPage > 1
                            ) { Text("السابقة") }
                            Button(
                                onClick = { if (currentPage < totalPages) currentPage++ },
                                enabled = currentPage < totalPages
                            ) { Text("التالية") }
                        }
                    }
                }
            """.trimIndent()
        ),
        startPrompt = "أنشئ تطبيق مصحف بسيط: شاشة رئيسية بقائمة سور، وقارئ بتنقل صفحات (604 صفحات). أكمل قائمة السور إلى 114 سورة من مصدر موثق، واربط مستودع السور بنص قرآني موثق."
    )

    val AZKAR: VibeTemplate = VibeTemplate(
        id = "azkar_starter",
        title = "تطبيق أذكار",
        description = "أذكار صباحية بعدّاد يعمل فعلاً. 3 أذكار منسوخة حرفياً من التطبيق نفسه مع المصدر والعدد — تُكمل القائمة مع الوكيل.",
        files = mapOf(
            "AzkarItem.kt" to """
                package com.example.azkar

                /** ذكر: النص والمصدر والعدد المستهدف — منسوخ حرفياً من تطبيق خادم المسلم. */
                data class AzkarItem(
                    val id: String,
                    val title: String,
                    val text: String,
                    val source: String,
                    val targetCount: Int
                )

                val STARTER_AZKAR = listOf(
                    AzkarItem(
                        id = "m3",
                        title = "سيّد الاستغفار",
                        text = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
                        source = "رواه البخاري في صحيحه",
                        targetCount = 1
                    ),
                    AzkarItem(
                        id = "m6",
                        title = "اللهم بك أصبحنا وبك أمسينا",
                        text = "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ.",
                        source = "رواه الترمذي وصححه الألباني",
                        targetCount = 1
                    ),
                    AzkarItem(
                        id = "m8",
                        title = "رضيت بالله رباً وبالإسلام ديناً",
                        text = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا وَرَسُولًا.",
                        source = "رواه أحمد وأبو داود والترمذي",
                        targetCount = 3
                    )
                )
            """.trimIndent(),
            "AzkarScreen.kt" to """
                package com.example.azkar

                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.fillMaxWidth
                import androidx.compose.foundation.layout.padding
                import androidx.compose.foundation.lazy.LazyColumn
                import androidx.compose.foundation.lazy.items
                import androidx.compose.material3.Button
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.runtime.collectAsState
                import androidx.compose.runtime.getValue
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp
                import androidx.lifecycle.viewmodel.compose.viewModel

                /** شاشة الأذكار: قائمة + عدّاد يعمل فعلاً لكل ذكر. */
                @Composable
                fun AzkarScreen(viewModel: AzkarViewModel = viewModel()) {
                    val counts by viewModel.counts.collectAsState()
                    LazyColumn {
                        items(STARTER_AZKAR, key = { it.id }) { item ->
                            val count = counts[item.id] ?: 0
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(item.title)
                                Text(item.text)
                                Text("المصدر: " + item.source)
                                Text("العدد: " + count + " / " + item.targetCount)
                                Button(
                                    onClick = { viewModel.increment(item.id) },
                                    enabled = count < item.targetCount
                                ) { Text("أحصيت مرة") }
                                Button(onClick = { viewModel.reset(item.id) }) { Text("إعادة") }
                            }
                        }
                    }
                }
            """.trimIndent(),
            "AzkarViewModel.kt" to """
                package com.example.azkar

                import androidx.lifecycle.ViewModel
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow
                import kotlinx.coroutines.flow.update

                /** منطق العدّاد: زيادة حتى العدد المستهدف ثم إيقاف، وإعادة تصفير. */
                class AzkarViewModel : ViewModel() {
                    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
                    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

                    fun increment(id: String) {
                        val target = STARTER_AZKAR.firstOrNull { it.id == id }?.targetCount ?: return
                        _counts.update { current ->
                            val next = (current[id] ?: 0) + 1
                            if (next <= target) current + (id to next) else current
                        }
                    }

                    fun reset(id: String) {
                        _counts.update { it - id }
                    }
                }
            """.trimIndent()
        ),
        startPrompt = "أنشئ تطبيق أذكار: قائمة أذكار صباحية بعدّاد يعمل (زيادة حتى العدد المستهدف وإعادة تصفير). أكمل القائمة بأذكار موثقة من مصادرها، وأضف تصنيفات (صباح/مساء)."
    )

    val LIBRARY: VibeTemplate = VibeTemplate(
        id = "books_library_starter",
        title = "مكتبة كتب",
        description = "قائمة كتب + بحث + تفاصيل. 3 كتب حقيقية موجودة في أصول التطبيق — محتواها يُربط بملفات الأصول مع الوكيل.",
        files = mapOf(
            "Book.kt" to """
                package com.example.library

                /** كتاب: ملف الأصل موجود فعلاً في أصول تطبيق خادم المسلم.
                 * ملاحظة (ق182): ملفا الحديث في الشجرة ‎.json.gz لكن أداة
                 * التغليف تخزّنهما في الـ APK مفكوكَي الضغط باسم ‎.json —
                 * فيُطلب ‎.json ويُقرأ JSON عادياً بلا فك ضغط (انظر
                 * QabasBukhariRepository)؛ طلب ‎.json.gz يفشل بصمت. */
                data class Book(
                    val id: String,
                    val title: String,
                    val author: String,
                    val assetFile: String
                )

                val STARTER_BOOKS = listOf(
                    Book("bukhari", "صحيح البخاري", "الإمام البخاري", "hadith/sahih_bukhari.json"),
                    Book("muslim", "صحيح مسلم", "الإمام مسلم", "hadith/sahih_muslim.json"),
                    Book("muyassar", "تفسير الميسر", "نخبة من العلماء", "quran/tafsir_muyassar.json")
                )
            """.trimIndent(),
            "LibraryScreen.kt" to """
                package com.example.library

                import androidx.compose.foundation.clickable
                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.fillMaxWidth
                import androidx.compose.foundation.layout.padding
                import androidx.compose.foundation.lazy.LazyColumn
                import androidx.compose.foundation.lazy.items
                import androidx.compose.material3.OutlinedTextField
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.runtime.getValue
                import androidx.compose.runtime.mutableStateOf
                import androidx.compose.runtime.remember
                import androidx.compose.runtime.setValue
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp

                /** مكتبة الكتب: بحث حي بالعنوان أو المؤلف + اختيار كتاب لعرض تفاصيله. */
                @Composable
                fun LibraryScreen(onBookSelected: (Book) -> Unit) {
                    var query by remember { mutableStateOf("") }
                    val filtered = STARTER_BOOKS.filter {
                        query.isBlank() || it.title.contains(query) || it.author.contains(query)
                    }
                    Column {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("ابحث في المكتبة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                        LazyColumn {
                            items(filtered, key = { it.id }) { book ->
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onBookSelected(book) }
                                        .padding(16.dp)
                                ) {
                                    Text(book.title)
                                    Text(book.author)
                                }
                            }
                        }
                    }
                }
            """.trimIndent(),
            "BookDetailScreen.kt" to """
                package com.example.library

                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.padding
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp

                /** تفاصيل الكتاب: العنوان والمؤلف وملف الأصل — المحتوى يُقرأ من ملف الأصل مع الوكيل. */
                @Composable
                fun BookDetailScreen(book: Book) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(book.title)
                        Text("المؤلف: " + book.author)
                        Text("ملف الأصل: " + book.assetFile)
                        Text("محتوى الكتاب يُقرأ من ملف الأصل (JSON) — اربطه بقارئ مع الوكيل.")
                    }
                }
            """.trimIndent()
        ),
        startPrompt = "أنشئ تطبيق مكتبة كتب: قائمة كتب مع بحث حي بالعنوان والمؤلف، وشاشة تفاصيل لكل كتاب. اربط محتوى كل كتاب بملف أصله (JSON) واعرض فصوله."
    )

    val ALL: List<VibeTemplate> = listOf(MUSHAF, AZKAR, LIBRARY)

    fun byId(id: String): VibeTemplate? = ALL.firstOrNull { it.id == id }
}
