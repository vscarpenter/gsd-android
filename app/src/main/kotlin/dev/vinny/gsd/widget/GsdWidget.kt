package dev.vinny.gsd.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.vinny.gsd.MainActivity
import dev.vinny.gsd.R
import dev.vinny.gsd.prefs.resetOnCorruption
import kotlinx.coroutines.flow.first

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gsd_widget",
    corruptionHandler = resetOnCorruption,
)
private val snapshotKey = stringPreferencesKey("today_focus_snapshot")

suspend fun writeWidgetSnapshot(context: Context, snapshot: WidgetSnapshot) {
    context.widgetDataStore.edit { it[snapshotKey] = WidgetSnapshot.encode(snapshot) }
    GsdWidget().updateAll(context)
}

class GsdWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GsdWidget()
}

/**
 * "Today's Focus" (spec §12.1): the one widget — Q1-active list from the
 * pushed snapshot, GSD paper/ink/Rust in both modes, deep-link taps.
 */
class GsdWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    private val paper = ColorProvider(Color(0xFFF4F1E9), Color(0xFF17150F))
    private val ink = ColorProvider(Color(0xFF211E1A), Color(0xFFF1ECE2))
    private val ink3 = ColorProvider(Color(0xFF6A655C), Color(0xFF948A79))
    private val rust = ColorProvider(Color(0xFFB23A2E), Color(0xFFE0705F))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshot.decode(
            context.widgetDataStore.data.first()[snapshotKey],
        )
        provideContent {
            // As many 36dp rows as the height fits; they were about 23dp (finding 28)
            val rows = widgetRows(LocalSize.current.height.value)
            fun deepLink(uri: String) = actionStartActivity(
                Intent(Intent.ACTION_VIEW, uri.toUri(), context, MainActivity::class.java),
            )
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(paper)
                    .cornerRadius(22.dp)
                    .padding(12.dp)
                    .clickable(deepLink("gsd://smart-view/today-focus")),
            ) {
                // The header with its crosshair glyph (spec §12.1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_focus),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(rust),
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        context.getString(R.string.widget_title),
                        style = TextStyle(color = rust, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
                if (snapshot.items.isEmpty()) {
                    Text(
                        context.getString(R.string.widget_all_clear),
                        style = TextStyle(color = ink3, fontSize = 13.sp),
                    )
                } else {
                    snapshot.items.take(rows).forEach { item ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(WIDGET_ROW_DP.dp)
                                .clickable(deepLink("gsd://task/${item.id}")),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.title,
                                style = TextStyle(color = ink, fontSize = 13.sp),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            if (item.dueLabel.isNotEmpty()) {
                                Text(
                                    item.dueLabel,
                                    style = TextStyle(color = ink3, fontSize = 11.sp),
                                )
                            }
                        }
                    }
                    val overflow = snapshot.totalActive - minOf(rows, snapshot.items.size)
                    if (overflow > 0) {
                        Text(
                            context.getString(R.string.widget_more, overflow),
                            style = TextStyle(color = ink3, fontSize = 11.sp),
                        )
                    }
                }
            }
        }
    }
}
