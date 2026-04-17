package io.github.keiai0.sleeprec

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 夜に使うので、暗い背景・落ち着いた青系・少数のアクセントで統一する(UX.md §7)。
// 文字は真っ白ではなく、少し暗めのグレーにして、暗闇で眩しくないようにする。
// 色は MaterialTheme.colorScheme 経由で使い、後から差し替えやすくする。
private val SleepRecColors = darkColorScheme(
    primary = Color(0xFF8AA4FF),
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF26325E),
    onPrimaryContainer = Color(0xFFD5DDFF),
    secondaryContainer = Color(0xFF26325E), // 下部タブの選択中の丸などに使われる
    onSecondaryContainer = Color(0xFFD5DDFF),
    secondary = Color(0xFF9FB3D9),
    onSecondary = Color(0xFF0B1020),
    tertiary = Color(0xFFC3A6F5),
    onTertiary = Color(0xFF0B1020),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF0B1020),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFD0D5E3),
    surface = Color(0xFF0B1020),
    onSurface = Color(0xFFD0D5E3),
    surfaceVariant = Color(0xFF1B2340),
    onSurfaceVariant = Color(0xFFA9B0C8),
    surfaceContainer = Color(0xFF121933),
    surfaceContainerHigh = Color(0xFF19213F),
    surfaceContainerHighest = Color(0xFF212A4B),
    outline = Color(0xFF7D86A6),
    outlineVariant = Color(0xFF343D60),
)

@Composable
fun SleepRecTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SleepRecColors, content = content)
}
