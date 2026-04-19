package io.github.keiai0.sleeprec

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** 起床時の気分(FR-2.11)の 5 段階。絵文字だけに頼らず、必ず言葉のラベルを付ける。 */
enum class Mood(val value: Int, val emoji: String, val labelRes: Int) {
    VERY_BAD(1, "😫", R.string.mood_1),
    BAD(2, "😕", R.string.mood_2),
    OKAY(3, "😐", R.string.mood_3),
    GOOD(4, "🙂", R.string.mood_4),
    VERY_GOOD(5, "😄", R.string.mood_5);

    companion object {
        fun of(value: Int?): Mood? = entries.firstOrNull { it.value == value }
    }
}

/** 気分を選ぶダイアログ。選ぶとすぐ保存される。「あとで」で閉じても、記録の詳細画面から入力できる。 */
@Composable
fun MoodDialog(current: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mood_title)) },
        text = {
            Column {
                Text(stringResource(R.string.mood_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                Mood.entries.reversed().forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(m.value) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 16.dp))
                        Text(
                            stringResource(m.labelRes) + if (m.value == current) "  ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mood_later)) } },
    )
}
