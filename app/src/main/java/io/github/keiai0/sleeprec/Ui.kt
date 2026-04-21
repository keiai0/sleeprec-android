package io.github.keiai0.sleeprec

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * 画面の共通部品。すべての画面で同じ部品を使い、見た目の強弱と、まとまりの範囲をそろえる。
 * - まとまり(SectionCard): 枠線付きのカード 1 枚 = 1 つの話題。カードの間は同じ間隔をあける。
 * - 値と説明(InfoRow / StatusRow / MetricTile): ラベルは小さく淡く、値は大きく太く。
 * - 長い説明(ExpandableNote): 要点を 1 行で見せ、詳しい説明は「詳しく」で開く。
 */

/** 画面の左右の余白と、カード同士の間隔。 */
object Spacing {
    val screen = 16.dp
    val cards = 16.dp
    val inCard = 12.dp
}

/** 1 つのまとまりを囲むカード。title があれば、カードの先頭に見出しを置く。onClick があれば、カード全体がタップできる。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Spacing.inCard)) {
            if (title != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
            }
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(), shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { body() }
    }
}

/** 画面の見出し。 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier)

/** 補足の文。小さく淡く。長くなる場合は、呼び出し側で短くするか、ExpandableNote を使う。 */
@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )

/** 状態などを示す小さなラベル(色だけでなく、言葉で示す)。 */
enum class Tone { GOOD, WARN, INFO, NEUTRAL }

@Composable
fun Pill(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        Tone.GOOD -> cs.primaryContainer to cs.onPrimaryContainer
        Tone.WARN -> cs.errorContainer to cs.onErrorContainer
        Tone.INFO -> cs.surfaceVariant to cs.onSurfaceVariant
        Tone.NEUTRAL -> cs.surfaceVariant to cs.onSurface
    }
    Surface(modifier, shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), maxLines = 1)
    }
}

/** 「ラベル(左・淡い) — 値(右・太い)」の 1 行。 */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun CardDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

/**
 * 状態つきの 1 項目(開始前の確認、権限の案内など)。左に名前と 1 行の補足、右に状態のラベル。
 * 対処が要るときは、その下に、短いボタンを置く。
 */
@Composable
fun StatusRow(
    title: String, tone: Tone, statusText: String,
    sub: String? = null, actionLabel: String? = null, onAction: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (sub != null) MutedText(sub)
            }
            Pill(statusText, tone)
        }
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** 値を大きく見せる、小さなカード(統計・指標)。 */
@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Surface(
        modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            MutedText(label, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (sub != null) MutedText(sub, maxLines = 1)
        }
    }
}

/**
 * 長い説明の折りたたみ。要点(summary)は常に 1 行で見せ、詳しい説明(detail)は「詳しく」を押すと開く。
 * 診断ではないことなどの、省けない注意は、summary に短く書いて、常に見える形にする。
 */
@Composable
fun ExpandableNote(summary: String, detail: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable(summary) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clickable { open = !open }) {
        Row(verticalAlignment = Alignment.Top) {
            MutedText(summary, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (open) R.string.note_less else R.string.note_more),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        AnimatedVisibility(open) { MutedText(detail, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** 値のある棒(スコアの要素など)。全体に対する割合を、細い棒で見せる。 */
@Composable
fun ValueBar(fraction: Float, modifier: Modifier = Modifier) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** 時間の長さを、単位付きで短く表す(「7 時間 30 分」「12 分 34 秒」「34 秒」)。分秒か時分かで迷わないようにする。 */
fun formatDurationJa(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s >= 3600 -> "%d 時間 %d 分".format(s / 3600, s % 3600 / 60)
        s >= 60 -> if (s % 60 == 0L) "%d 分".format(s / 60) else "%d 分 %d 秒".format(s / 60, s % 60)
        else -> "%d 秒".format(s)
    }
}
