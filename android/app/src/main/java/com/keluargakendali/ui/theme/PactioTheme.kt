package com.keluargakendali.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PactioColorScheme = lightColorScheme(
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

private val PactioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Tema visual Pactio — pengganti MaterialTheme bawaan, sesuai mockup yang disetujui. */
@Composable
fun PactioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PactioColorScheme,
        typography = PactioTypography,
        shapes = PactioShapes,
        content = content
    )
}
