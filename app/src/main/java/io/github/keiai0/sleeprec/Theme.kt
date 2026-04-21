package io.github.keiai0.sleeprec

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

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
    errorContainer = Color(0xFF4A2226),
    onErrorContainer = Color(0xFFFFD9D6),
    onError = Color(0xFF0B1020),
    // 背景は最も暗く、カードはそれより明るくして、まとまりの範囲が一目で分かるようにする
    background = Color(0xFF080D1A),
    onBackground = Color(0xFFDDE1EE),
    surface = Color(0xFF080D1A),
    onSurface = Color(0xFFDDE1EE),
    surfaceVariant = Color(0xFF232C4D),
    onSurfaceVariant = Color(0xFFB2B9D2), // 補足・ラベルの文字。本文より淡いが、読める明るさを保つ
    surfaceContainer = Color(0xFF141C38),
    surfaceContainerHigh = Color(0xFF1B2444),
    surfaceContainerHighest = Color(0xFF232C4D),
    outline = Color(0xFF7D86A6),
    outlineVariant = Color(0xFF34406A), // カードの枠線
)

// 文字の階層をはっきりさせる: 見出しは太く、値は大きく、補足は小さく。日本語が詰まって見えないよう、行間を広めに取る
private val SleepRecTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Medium),
    displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, lineBreak = LineBreak.Paragraph),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, lineBreak = LineBreak.Paragraph),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, lineBreak = LineBreak.Paragraph),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun SleepRecTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SleepRecColors, typography = SleepRecTypography, content = content)
}
