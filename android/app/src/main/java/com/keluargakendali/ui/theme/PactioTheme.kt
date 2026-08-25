package com.keluargakendali.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PactioLightColorScheme = lightColorScheme(
    primary = PactioCoral,
    onPrimary = PactioSurface,
    primaryContainer = PactioCoralSoft,
    onPrimaryContainer = PactioCoralDark,
    secondary = PactioGold,
    onSecondary = PactioGoldOn,
    secondaryContainer = PactioGoldSoft,
    onSecondaryContainer = PactioGoldOn,
    tertiary = PactioGreen,
    onTertiary = PactioSurface,
    tertiaryContainer = PactioGreenSoft,
    onTertiaryContainer = PactioGreenOn,
    error = PactioRed,
    onError = PactioSurface,
    errorContainer = PactioRedSoft,
    onErrorContainer = PactioRedOn,
    background = PactioBackground,
    onBackground = PactioTextPrimary,
    surface = PactioSurface,
    onSurface = PactioTextPrimary,
    surfaceVariant = PactioBorder,
    onSurfaceVariant = PactioTextMuted,
    outline = PactioBorder
)

// Hue aksen sama seperti mode terang (lihat catatan di Color.kt), cuma versi yang lebih terang
// supaya tetap kontras di atas latar gelap.
private val PactioDarkColorScheme = darkColorScheme(
    primary = PactioCoralOnDark,
    onPrimary = PactioBackgroundDark,
    primaryContainer = PactioCoralSoftDark,
    onPrimaryContainer = PactioCoralDarkOnDark,
    secondary = PactioGoldOnDark,
    onSecondary = PactioBackgroundDark,
    secondaryContainer = PactioGoldSoftDark,
    onSecondaryContainer = PactioGoldOnDark,
    tertiary = PactioGreenOnDark,
    onTertiary = PactioBackgroundDark,
    tertiaryContainer = PactioGreenSoftDark,
    onTertiaryContainer = PactioGreenOnDark,
    error = PactioRedOnDark,
    onError = PactioBackgroundDark,
    errorContainer = PactioRedSoftDark,
    onErrorContainer = PactioRedOnDark,
    background = PactioBackgroundDark,
    onBackground = PactioTextPrimaryDark,
    surface = PactioSurfaceDark,
    onSurface = PactioTextPrimaryDark,
    surfaceVariant = PactioBorderDark,
    onSurfaceVariant = PactioTextMutedDark,
    outline = PactioBorderDark
)

private val PactioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Warna di luar 12 slot standar Material3 ColorScheme (mis. track saklar "off" - lihat
 * PactioSwitchTrackOff/Dark di Color.kt) yang tetap perlu ikut berganti antara mode terang/gelap.
 * Diakses lewat MaterialTheme.pactioExtraColors di dalam PactioTheme, bukan referensi langsung
 * ke konstanta PactioSwitchTrackOff, supaya otomatis ikut tema aktif.
 */
data class PactioExtraColors(val switchTrackOff: Color)

private val LocalPactioExtraColors = staticCompositionLocalOf {
    PactioExtraColors(switchTrackOff = PactioSwitchTrackOff)
}

val MaterialTheme.pactioExtraColors: PactioExtraColors
    @Composable get() = LocalPactioExtraColors.current

/**
 * Tema visual TimeCraft - pengganti MaterialTheme bawaan, sesuai mockup yang disetujui.
 * darkTheme dikendalikan MANUAL lewat toggle di UI (SettingsStore), BUKAN otomatis mengikuti
 * tema sistem - sesuai permintaan eksplisit ada ikon ganti mode gelap yang bisa ditekan sendiri.
 */
@Composable
fun PactioTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val extraColors = if (darkTheme) {
        PactioExtraColors(switchTrackOff = PactioSwitchTrackOffDark)
    } else {
        PactioExtraColors(switchTrackOff = PactioSwitchTrackOff)
    }
    CompositionLocalProvider(LocalPactioExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) PactioDarkColorScheme else PactioLightColorScheme,
            typography = PactioTypography,
            shapes = PactioShapes,
            content = content
        )
    }
}
