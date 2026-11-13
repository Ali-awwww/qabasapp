package com.qabas.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountdownRemoteViewsLayoutTest {

    @Test
    fun `countdown notification layouts use RemoteViews supported widgets`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val supportedWidgets = setOf("LinearLayout", "TextView", "FrameLayout", "Chronometer", "ProgressBar")
        val layouts = listOf(
            R.layout.notification_azan_countdown_compact,
            R.layout.notification_azan_countdown_expanded
        )

        layouts.forEach { layoutId ->
            val parser = context.resources.getLayout(layoutId)
            try {
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        assertTrue(
                            "Unsupported RemoteViews widget <${parser.name}> in layout resource $layoutId",
                            parser.name in supportedWidgets
                        )
                    }
                    event = parser.next()
                }
            } finally {
                parser.close()
            }
        }
    }
}
