package dev.vinny.gsd.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.BuildConfig
import dev.vinny.gsd.R
import dev.vinny.gsd.model.FeedbackCategory
import dev.vinny.gsd.model.FeedbackPayload
import dev.vinny.gsd.model.Roadmap
import dev.vinny.gsd.model.Sentiment
import dev.vinny.gsd.sync.FeedbackFailure
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }

private val categoryOptions = listOf(
    FeedbackCategory.IDEA to R.string.feedback_category_idea,
    FeedbackCategory.PRAISE to R.string.feedback_category_praise,
    FeedbackCategory.GRIPE to R.string.feedback_category_gripe,
    FeedbackCategory.BUG to R.string.feedback_category_bug,
)

/** Anonymous opt-in feedback (parity spec P§6.1–§6.2): sentiment, category, message, roadmap votes. */
@Composable
fun FeedbackScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: FeedbackViewModel = viewModel {
        FeedbackViewModel(
            api = container.feedbackApi,
            prefs = container.prefs,
            appVersion = "android-${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
            now = { container.clock.instant() },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gsd = LocalGsdColors.current
    val draft = state.draft

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.feedback_title), style = GsdType.title, color = gsd.ink)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.sentiment == Sentiment.UP,
                onClick = { viewModel.setSentiment(if (draft.sentiment == Sentiment.UP) null else Sentiment.UP) },
                label = { Text(stringResource(R.string.feedback_sentiment_up), style = GsdType.body) },
            )
            FilterChip(
                selected = draft.sentiment == Sentiment.DOWN,
                onClick = { viewModel.setSentiment(if (draft.sentiment == Sentiment.DOWN) null else Sentiment.DOWN) },
                label = { Text(stringResource(R.string.feedback_sentiment_down), style = GsdType.body) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categoryOptions.forEach { (category, labelRes) ->
                FilterChip(
                    selected = draft.category == category,
                    onClick = { viewModel.setCategory(if (draft.category == category) null else category) },
                    label = { Text(stringResource(labelRes), style = GsdType.metadata) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                if (draft.message.isEmpty()) {
                    Text(stringResource(R.string.feedback_message_hint), style = GsdType.body, color = gsd.ink3)
                }
                BasicTextField(
                    value = draft.message,
                    onValueChange = viewModel::setMessage,
                    textStyle = GsdType.body.copy(color = gsd.ink),
                    cursorBrush = gsd.cursor,
                    modifier = Modifier.fillMaxWidth().testTag("feedback_message"),
                )
            }
            Text(
                "${draft.message.length} / ${FeedbackPayload.MAX_MESSAGE_LENGTH}",
                style = GsdType.caption,
                color = gsd.ink3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.feedback_votes_header).uppercase(), style = GsdType.caption, color = gsd.ink3)
            Roadmap.items.forEach { item ->
                val selected = item.slug in draft.votes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { viewModel.toggleVote(item.slug) }
                        .testTag("feedback_vote_${item.slug}"),
                ) {
                    Checkbox(checked = selected, onCheckedChange = { viewModel.toggleVote(item.slug) })
                    Column {
                        Text(item.label, style = GsdType.body, color = gsd.ink)
                        Text(item.description, style = GsdType.metadata, color = gsd.ink2)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.feedback_disclosure_header).uppercase(), style = GsdType.caption, color = gsd.ink3)
            Text(
                prettyJson.encodeToString(JsonObject.serializer(), state.preview),
                style = GsdType.metadata.copy(fontFamily = FontFamily.Monospace),
                color = gsd.ink2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(gsd.sunken)
                    .padding(12.dp)
                    .testTag("feedback_disclosure"),
            )
            Text(stringResource(R.string.feedback_disclosure_privacy), style = GsdType.caption, color = gsd.ink2)
        }

        val sendState = state.sendState
        TextButton(
            onClick = viewModel::send,
            enabled = !draft.isEmpty && sendState != FeedbackSendState.Sending,
            modifier = Modifier.testTag("feedback_send"),
        ) {
            Text(
                stringResource(R.string.feedback_send),
                style = GsdType.cardTitle,
                color = if (!draft.isEmpty) gsd.tint else gsd.ink3,
            )
        }

        when (sendState) {
            FeedbackSendState.Sent -> Text(stringResource(R.string.feedback_sent), style = GsdType.body, color = gsd.success)
            is FeedbackSendState.Failed -> Text(
                stringResource(
                    when (sendState.kind) {
                        FeedbackFailure.OFFLINE -> R.string.feedback_failed_offline
                        FeedbackFailure.RATE_LIMITED -> R.string.feedback_failed_rate_limited
                        FeedbackFailure.SERVER -> R.string.feedback_failed_server
                        FeedbackFailure.REJECTED -> R.string.feedback_failed_rejected
                    },
                ),
                style = GsdType.body, color = gsd.alert,
            )
            else -> Unit
        }
    }
}
