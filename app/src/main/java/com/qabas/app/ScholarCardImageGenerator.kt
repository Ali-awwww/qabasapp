package com.qabas.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ScholarCardImageGenerator {

    enum class CardStyle {
        FULL_EPIC,       // بطاقة السيرة الملحمية الكاملة بجميع التفاصيل والمحطات
        HIGHLIGHT_STORY  // بطاقة القصة السريعة المخصصة للحالات والقصص (9:16)
    }

    enum class CardTheme {
        CLASSIC_MANUSCRIPT, // المخطوطة الأندلسية الكلاسيكية (ورق عتيق وحبر شرقي)
        EMERALD_GOLD,       // الديباج الأخضر الزمردي والذهب الخالص
        OBSIDIAN_LUXURY     // الرخام الملكي الأسود والذهب الساطع
    }

    /**
     * Builds the complete formatted text of the scholar's biography with all details.
     */
    fun buildFullBiographyText(scholar: ScholarBiography): String {
        val sb = StringBuilder()
        sb.append("✨ سِيَرُ الأَكَابِرِ • مَنَارَةُ الثَّبَاتِ وَاليَقِينِ ✨\n\n")
        sb.append("📖 الإمام: ${scholar.name}")
        if (!scholar.period.isNullOrBlank()) {
            sb.append(" (${scholar.period})")
        }
        sb.append("\n")

        scholar.enduranceQuote?.let { quote ->
            sb.append("\n🌟 من درر كلماته الخالدة:\n« $quote »\n")
        }

        sb.append("\n📜 ١. تاريخ الإمام ومكانته في الأمة:\n")
        sb.append(scholar.brief)
        scholar.travelDistance?.let {
            sb.append("\n📍 الرحلة والطلب: $it")
        }
        sb.append("\n")

        if (scholar.customFacets.isNotEmpty()) {
            sb.append("\n⭐ محاور العبقرية والسيرة الخالدة:\n")
            scholar.customFacets.forEachIndexed { idx, facet ->
                sb.append("\n【${idx + 1}. ${facet.title}】\n")
                if (facet.badge.isNotBlank()) sb.append("• الوسم: ${facet.badge}\n")
                sb.append(facet.narrative).append("\n")
                facet.keyQuote?.let { sb.append("💬 الموقف والمقولة: $it\n") }
                facet.practicalInsight?.let { sb.append("💡 الأثر والتطبيق: $it\n") }
            }
            sb.append("\n")
        }

        scholar.whoHarmedHimAndHow?.let { harmed ->
            sb.append("\n⛓️ ٢. مَن الذين آذوه وكيف؟ (المحنة الكبرى):\n")
            sb.append(harmed)
            sb.append("\n")
        }

        if (!scholar.howHeEndured.isNullOrBlank() || !scholar.whyHeEndured.isNullOrBlank()) {
            sb.append("\n🛡️ ٣. كيف تحمّل ولماذا تحمّل؟ (سر الصمود الإيماني):\n")
            scholar.howHeEndured?.let {
                sb.append("• كيف تحمّل: $it\n")
            }
            scholar.whyHeEndured?.let {
                sb.append("• الغاية والقضية الكبرى: $it\n")
            }
        }

        if (!scholar.deathScene.isNullOrBlank() || !scholar.lastWords.isNullOrBlank()) {
            sb.append("\n🌅 ٤. مشهد الوفاة واللحظات الأخيرة:\n")
            scholar.deathScene?.let { sb.append(it).append("\n") }
            scholar.lastWords?.let {
                sb.append("🕊️ آخر كلماته قبل خروج الروح: « $it »\n")
            }
        }

        scholar.funeralImpact?.let { funeral ->
            sb.append("\n🕊️ ٥. مشهد الجنازة وأثر الفقد على الأمة:\n")
            sb.append(funeral)
            sb.append("\n")
        }

        scholar.globalImpact?.let { impact ->
            sb.append("\n🌍 ٦. الأثر التاريخي الخالد:\n")
            sb.append(impact)
            sb.append("\n")
        }

        scholar.messageToGrandson?.let { msg ->
            sb.append("\n💌 وصية الإمام لك يا حفيده:\n")
            sb.append("« $msg »\n")
        }

        scholar.modernChallenge?.let { ch ->
            sb.append("\n💡 تحدي الاقتداء المعاصر اليوم:\n")
            sb.append("• $ch\n")
        }

        sb.append("\n────────────────────\n")
        sb.append("📱 تطبيق قَبَس • نورٌ وسيرةٌ وثبات\n")
        sb.append("🤲 انشر هذه السيرة المباركة لتكون صدقة جارية لك ولأهلك")

        return sb.toString()
    }

    private data class ThemeColors(
        val bgColors: IntArray,
        val primaryText: Int,
        val secondaryText: Int,
        val goldAccent: Int,
        val cardBg: Int,
        val cardBorder: Int
    )

    /**
     * Generates a high-resolution, perfectly rendered Bitmap of the scholar's biography.
     */
    suspend fun generateScholarBitmap(
        context: Context,
        scholar: ScholarBiography,
        cardStyle: CardStyle = CardStyle.FULL_EPIC,
        cardTheme: CardTheme = CardTheme.CLASSIC_MANUSCRIPT
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = 1080
        val padding = 48
        val contentWidth = width - (padding * 2)

        val themeColors = when (cardTheme) {
            CardTheme.CLASSIC_MANUSCRIPT -> ThemeColors(
                bgColors = intArrayOf(Color.rgb(0xF8, 0xF2, 0xE2), Color.rgb(0xF2, 0xE7, 0xCF), Color.rgb(0xEB, 0xDC, 0xC1)),
                primaryText = Color.rgb(0x2C, 0x1E, 0x12),
                secondaryText = Color.rgb(0x5A, 0x40, 0x28),
                goldAccent = Color.rgb(0xB8, 0x86, 0x0B),
                cardBg = Color.rgb(0xFA, 0xF5, 0xEA),
                cardBorder = Color.argb(120, 0xB8, 0x86, 0x0B)
            )
            CardTheme.EMERALD_GOLD -> ThemeColors(
                bgColors = intArrayOf(Color.rgb(0x06, 0x26, 0x1B), Color.rgb(0x0A, 0x36, 0x26), Color.rgb(0x03, 0x1A, 0x12)),
                primaryText = Color.rgb(0xF0, 0xFD, 0xF4),
                secondaryText = Color.rgb(0xD1, 0xEA, 0xDE),
                goldAccent = Color.rgb(0xF6, 0xC8, 0x5F),
                cardBg = Color.rgb(0x0E, 0x3D, 0x2C),
                cardBorder = Color.argb(160, 0xF6, 0xC8, 0x5F)
            )
            CardTheme.OBSIDIAN_LUXURY -> ThemeColors(
                bgColors = intArrayOf(Color.rgb(0x10, 0x12, 0x17), Color.rgb(0x18, 0x1C, 0x24), Color.rgb(0x0A, 0x0C, 0x10)),
                primaryText = Color.rgb(0xF8, 0xFA, 0xFC),
                secondaryText = Color.rgb(0xCB, 0xD5, 0xE1),
                goldAccent = Color.rgb(0xFA, 0xCC, 0x15),
                cardBg = Color.rgb(0x1E, 0x22, 0x2C),
                cardBorder = Color.argb(160, 0xFA, 0xCC, 0x15)
            )
        }

        // Text Paints
        val paintOldInk = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.primaryText
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val paintTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.primaryText
            textSize = 42f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val paintHeaderGold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.goldAccent
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val paintQuote = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.goldAccent
            textSize = 30f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        }

        // Calculate dynamic height by pre-measuring blocks
        var totalHeight = 0
        val topHeaderHeight = 360
        totalHeight += topHeaderHeight

        // Pre-create layouts for measurement
        val introLayout = createStaticLayout(scholar.brief, paintOldInk, contentWidth - 40)
        var introCardH = introLayout.height + 80
        scholar.travelDistance?.let {
            val travelL = createStaticLayout("الرحلة والطلب: $it", paintOldInk, contentWidth - 40)
            introCardH += travelL.height + 20
        }
        totalHeight += introCardH + 30

        val harmedLayout = scholar.whoHarmedHimAndHow?.let {
            createStaticLayout(it, paintOldInk, contentWidth - 40)
        }
        val harmedCardH = if (harmedLayout != null) harmedLayout.height + 90 else 0
        if (harmedCardH > 0) totalHeight += harmedCardH + 30

        val howLayout = scholar.howHeEndured?.let {
            createStaticLayout(it, paintOldInk, contentWidth - 40)
        }
        val whyLayout = scholar.whyHeEndured?.let {
            createStaticLayout(it, paintOldInk, contentWidth - 40)
        }
        val enduranceCardH = if (howLayout != null || whyLayout != null) {
            (howLayout?.height ?: 0) + (whyLayout?.height ?: 0) + 160
        } else 0
        if (enduranceCardH > 0) totalHeight += enduranceCardH + 30

        val deathLayout = scholar.deathScene?.let {
            createStaticLayout(it, paintOldInk, contentWidth - 40)
        }
        val lastWordsLayout = scholar.lastWords?.let {
            createStaticLayout("« $it »", paintQuote, contentWidth - 60)
        }
        val funeralLayout = scholar.funeralImpact?.let {
            createStaticLayout(it, paintOldInk, contentWidth - 40)
        }
        val deathCardH = (deathLayout?.height ?: 0) + (lastWordsLayout?.height ?: 0) + (funeralLayout?.height ?: 0) + 200
        if (deathCardH > 0) totalHeight += deathCardH + 30

        val messageLayout = scholar.messageToGrandson?.let {
            createStaticLayout("« $it »", paintQuote, contentWidth - 60)
        }
        val challengeLayout = scholar.modernChallenge?.let {
            createStaticLayout(it, paintOldInk, contentWidth - 60)
        }
        val messageCardH = (messageLayout?.height ?: 0) + (challengeLayout?.height ?: 0) + 160
        if (messageCardH > 0) totalHeight += messageCardH + 30

        val footerHeight = 180
        totalHeight += footerHeight + 40

        // Minimum height constraint
        if (cardStyle == CardStyle.HIGHLIGHT_STORY) {
            totalHeight = 1920
        }

        // Create Canvas & Bitmap
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw Rich Antique Parchment Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), totalHeight.toFloat(),
                themeColors.bgColors,
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Draw Decorative Islamic Border
        drawIslamicBorder(canvas, width, totalHeight, padding, themeColors.goldAccent, themeColors.cardBorder)

        // Draw Header Section
        var currentY = padding + 24f

        // Basmala / Brand
        val basmala = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
        val basmalaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x9E, 0x78, 0x2F)
            textSize = 26f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(basmala, width / 2f, currentY + 24f, basmalaPaint)
        currentY += 50f

        // Tagline
        val brandTagline = "سِيَرُ الأَكَابِرِ • مَنَارَةُ الثَّبَاتِ وَاليَقِينِ"
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xB8, 0x86, 0x0B)
            textSize = 21f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(brandTagline, width / 2f, currentY + 18f, tagPaint)
        currentY += 45f

        // Scholar Name
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.primaryText
            textSize = 46f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(scholar.name, width / 2f, currentY + 36f, namePaint)
        currentY += 60f

        // Era / Period Badge
        scholar.period?.let { period ->
            val periodText = "العصر: $period"
            val pPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = themeColors.secondaryText
                textSize = 21f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(periodText, width / 2f, currentY + 14f, pPaint)
            currentY += 35f
        }

        // Quote Pill if available
        if (scholar.enduranceQuote != null) {
            val quote = scholar.enduranceQuote
            val quoteLayout = createStaticLayout("« $quote »", paintQuote, contentWidth - 60, Layout.Alignment.ALIGN_CENTER)
            val quoteCardH = quoteLayout.height + 40
            val quoteRect = RectF(padding.toFloat(), currentY, (width - padding).toFloat(), currentY + quoteCardH)
            
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(30, 0xB8, 0x86, 0x0B)
            }
            val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0xB8, 0x86, 0x0B)
                this.style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawRoundRect(quoteRect, 18f, 18f, pillPaint)
            canvas.drawRoundRect(quoteRect, 18f, 18f, pillBorder)

            canvas.save()
            canvas.translate(padding + 30f, currentY + 20f)
            quoteLayout.draw(canvas)
            canvas.restore()

            currentY += quoteCardH + 30f
        } else {
            currentY += 20f
        }

        // Section 1: Intro / Bio
        drawCardSection(
            canvas = canvas,
            x = padding.toFloat(),
            y = currentY,
            width = contentWidth.toFloat(),
            height = introCardH.toFloat(),
            title = "📜 تاريخ ومكانة الإمام في الأمة",
            titleColor = themeColors.goldAccent,
            borderColor = themeColors.cardBorder,
            fillColor = themeColors.cardBg
        ) { innerCanvas, _ ->
            var subY = 56f
            introLayout.draw(innerCanvas)
            subY += introLayout.height + 14f

            scholar.travelDistance?.let { travel ->
                val trPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = themeColors.secondaryText
                    textSize = 22f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                innerCanvas.drawText("📍 الرحلة والطلب: $travel", 10f, subY + 16f, trPaint)
            }
        }
        currentY += introCardH + 26f

        // Section 2: Harmed & Persecution
        if (harmedLayout != null && harmedCardH > 0) {
            drawCardSection(
                canvas = canvas,
                x = padding.toFloat(),
                y = currentY,
                width = contentWidth.toFloat(),
                height = harmedCardH.toFloat(),
                title = "⛓️ مَن الذين آذوه وكيف؟ (المحنة الكبرى)",
                titleColor = Color.rgb(0xEF, 0x44, 0x44),
                borderColor = Color.argb(160, 0xEF, 0x44, 0x44),
                fillColor = themeColors.cardBg
            ) { innerCanvas, _ ->
                harmedLayout.draw(innerCanvas)
            }
            currentY += harmedCardH + 26f
        }

        // Section 3: How & Why Endured
        if (enduranceCardH > 0) {
            drawCardSection(
                canvas = canvas,
                x = padding.toFloat(),
                y = currentY,
                width = contentWidth.toFloat(),
                height = enduranceCardH.toFloat(),
                title = "🛡️ كيف تحمّل ولماذا تحمّل؟ (سر الصمود والغاية)",
                titleColor = Color.rgb(0x10, 0xB9, 0x81),
                borderColor = Color.argb(160, 0x10, 0xB9, 0x81),
                fillColor = themeColors.cardBg
            ) { innerCanvas, _ ->
                var sY = 0f
                if (howLayout != null) {
                    val subTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(0x10, 0xB9, 0x81)
                        textSize = 24f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    innerCanvas.drawText("• كيف تحمّل الصعاب والتعذيب:", 0f, sY + 20f, subTitlePaint)
                    sY += 32f

                    innerCanvas.save()
                    innerCanvas.translate(0f, sY)
                    howLayout.draw(innerCanvas)
                    innerCanvas.restore()
                    sY += howLayout.height + 24f
                }

                if (whyLayout != null) {
                    val subTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = themeColors.goldAccent
                        textSize = 24f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    innerCanvas.drawText("• القضية الكبرى والغاية من الثبات:", 0f, sY + 20f, subTitlePaint)
                    sY += 32f

                    innerCanvas.save()
                    innerCanvas.translate(0f, sY)
                    whyLayout.draw(innerCanvas)
                    innerCanvas.restore()
                }
            }
            currentY += enduranceCardH + 26f
        }

        // Section 4: Death Scene, Last Words & Funeral
        if (deathCardH > 0) {
            drawCardSection(
                canvas = canvas,
                x = padding.toFloat(),
                y = currentY,
                width = contentWidth.toFloat(),
                height = deathCardH.toFloat(),
                title = "🌅 مشهد الوفاة وآخر كلماته وأثر الجنازة",
                titleColor = Color.rgb(0xA8, 0x55, 0xF7),
                borderColor = Color.argb(160, 0xA8, 0x55, 0xF7),
                fillColor = themeColors.cardBg
            ) { innerCanvas, innerWidth ->
                var sY = 0f
                if (deathLayout != null) {
                    deathLayout.draw(innerCanvas)
                    sY += deathLayout.height + 20f
                }

                if (lastWordsLayout != null) {
                    val boxH = lastWordsLayout.height + 46
                    val boxRect = RectF(0f, sY, innerWidth, sY + boxH)
                    val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(20, 0x6B, 0x21, 0xA8)
                    }
                    val boxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(0xB8, 0x86, 0x0B)
                        this.style = Paint.Style.STROKE
                        strokeWidth = 2f
                    }
                    innerCanvas.drawRoundRect(boxRect, 14f, 14f, boxFill)
                    innerCanvas.drawRoundRect(boxRect, 14f, 14f, boxBorder)

                    val lwTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(0xB8, 0x86, 0x0B)
                        textSize = 20f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    innerCanvas.drawText("🕊️ آخر كلماته قبل خروج الروح:", innerWidth / 2f, sY + 24f, lwTitlePaint)

                    innerCanvas.save()
                    innerCanvas.translate(10f, sY + 34f)
                    lastWordsLayout.draw(innerCanvas)
                    innerCanvas.restore()

                    sY += boxH + 20f
                }

                if (funeralLayout != null) {
                    val fnTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = themeColors.primaryText
                        textSize = 22f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    innerCanvas.drawText("مشهد الجنازة ووداع الأمة:", 0f, sY + 20f, fnTitlePaint)
                    sY += 30f

                    innerCanvas.save()
                    innerCanvas.translate(0f, sY)
                    funeralLayout.draw(innerCanvas)
                    innerCanvas.restore()
                }
            }
            currentY += deathCardH + 26f
        }

        // Section 5: Message & Modern Challenge
        if (messageCardH > 0) {
            drawCardSection(
                canvas = canvas,
                x = padding.toFloat(),
                y = currentY,
                width = contentWidth.toFloat(),
                height = messageCardH.toFloat(),
                title = "💌 وصية الإمام لك يا حفيده + التحدي المعاصر",
                titleColor = themeColors.goldAccent,
                borderColor = themeColors.cardBorder,
                fillColor = themeColors.cardBg
            ) { innerCanvas, _ ->
                var sY = 0f
                if (messageLayout != null) {
                    innerCanvas.save()
                    innerCanvas.translate(10f, sY)
                    messageLayout.draw(innerCanvas)
                    innerCanvas.restore()
                    sY += messageLayout.height + 24f
                }

                if (challengeLayout != null) {
                    val chTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = themeColors.goldAccent
                        textSize = 22f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    innerCanvas.drawText("💡 تحدي الاقتداء المعاصر اليوم:", 0f, sY + 20f, chTitlePaint)
                    sY += 30f

                    innerCanvas.save()
                    innerCanvas.translate(0f, sY)
                    challengeLayout.draw(innerCanvas)
                    innerCanvas.restore()
                }
            }
            currentY += messageCardH + 30f
        }

        // Footer & Watermark
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.primaryText
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val subFooterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.secondaryText
            textSize = 20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColors.goldAccent
            strokeWidth = 2f
        }
        canvas.drawLine(padding + 60f, currentY + 10f, width - padding - 60f, currentY + 10f, dividerPaint)
        canvas.drawCircle(width / 2f, currentY + 10f, 6f, dividerPaint)

        currentY += 45f
        canvas.drawText("تطبيق قَبَس • مَنَارَةُ النُّورِ وَسِيَرُ الثَّبَاتِ", width / 2f, currentY, footerPaint)
        currentY += 32f
        canvas.drawText("انشر هذه السيرة تؤجر • صدقة جارية للأمة والمؤمنين", width / 2f, currentY, subFooterPaint)

        bitmap
    }

    private fun createStaticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(100))
                .setAlignment(align)
                .setLineSpacing(10f, 1.25f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width.coerceAtLeast(100), align, 1.25f, 10f, true)
        }
    }

    private fun drawIslamicBorder(canvas: Canvas, width: Int, height: Int, padding: Int, goldColor: Int = Color.rgb(0xB8, 0x86, 0x0B), subColor: Int = Color.argb(100, 0x5A, 0x40, 0x28)) {
        val outerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldColor
            this.style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val innerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subColor
            this.style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        val margin = (padding / 2).toFloat()
        canvas.drawRect(margin, margin, width - margin, height - margin, outerBorder)
        canvas.drawRect(margin + 8f, margin + 8f, width - margin - 8f, height - margin - 8f, innerBorder)

        // Draw Ornate Corners
        val corners = listOf(
            PointF(margin + 8f, margin + 8f),
            PointF(width - margin - 8f, margin + 8f),
            PointF(margin + 8f, height - margin - 8f),
            PointF(width - margin - 8f, height - margin - 8f)
        )

        val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldColor
            this.style = Paint.Style.FILL
        }

        for (corner in corners) {
            canvas.drawCircle(corner.x, corner.y, 7f, cornerPaint)
        }
    }

    private fun drawCardSection(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        title: String,
        titleColor: Int,
        borderColor: Int,
        fillColor: Int = Color.rgb(0xFA, 0xF5, 0xEA),
        contentBlock: (Canvas, Float) -> Unit
    ) {
        val cardRect = RectF(x, y, x + width, y + height)

        // Card Fill & Shadow
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            this.style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        canvas.drawRoundRect(cardRect, 18f, 18f, fillPaint)
        canvas.drawRoundRect(cardRect, 18f, 18f, borderPaint)

        // Header Title
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textSize = 27f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(title, x + 20f, y + 42f, headerPaint)

        // Content Area
        val innerPadding = 20f
        val innerWidth = width - (innerPadding * 2)
        canvas.save()
        canvas.translate(x + innerPadding, y + 66f)
        contentBlock(canvas, innerWidth)
        canvas.restore()
    }

    /**
     * Saves the generated bitmap to the app cache and returns its shareable Content Uri.
     */
    suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap, scholarName: String): Uri = withContext(Dispatchers.IO) {
        val cleanName = scholarName.replace(" ", "_").replace("الإمام_", "")
        val cacheDir = File(context.cacheDir, "scholar_cards").apply { mkdirs() }
        val imageFile = File(cacheDir, "qabas_${cleanName}_card.png")

        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    /**
     * Saves the generated bitmap to the user's Pictures / Gallery.
     */
    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, scholarName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanName = scholarName.replace(" ", "_")
            val filename = "Qabas_${cleanName}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Qabas")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                    return@withContext true
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val qabasDir = File(picturesDir, "Qabas").apply { mkdirs() }
                val file = File(qabasDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Shares the scholar image via Android Share Sheet.
     */
    fun shareImageUri(context: Context, uri: Uri, scholar: ScholarBiography) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "سيرة الإمام ${scholar.name} - تطبيق قبس")
            putExtra(
                Intent.EXTRA_TEXT,
                "✨ سيرة الإمام ${scholar.name}\n${scholar.brief}\n\n« ${scholar.enduranceQuote ?: ""} »\n\n📱 تطبيق قبس للإنتاج والسير الإسلامية"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة بطاقة سيرة ${scholar.name}"))
    }

    /**
     * Shares the full biography as rich formatted text via Android Share Sheet.
     */
    fun shareText(context: Context, scholar: ScholarBiography) {
        val text = buildFullBiographyText(scholar)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "سيرة الإمام ${scholar.name}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة سيرة الإمام نصاً"))
    }
}
