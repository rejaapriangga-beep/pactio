package com.keluargakendali.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.keluargakendali.R

/**
 * Sora (judul) + Plus Jakarta Sans (isi) — sesuai mockup yang disetujui.
 * File font (variable font, lisensi OFL dari Google Fonts) dibundel langsung sebagai
 * resource di res/font/, BUKAN lewat Downloadable Fonts API — itu butuh sertifikat
 * Google Play Services yang tidak bisa saya jamin akurat kalau diketik dari ingatan
 * (salah satu digit saja bikin font gagal dimuat secara diam-diam di perangkat).
 */
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val SoraFamily = FontFamily(
    variableFont(R.font.sora_variable, FontWeight.SemiBold),
    variableFont(R.font.sora_variable, FontWeight.Bold),
    variableFont(R.font.sora_variable, FontWeight.ExtraBold)
)

val JakartaFamily = FontFamily(
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.Normal),
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.Medium),
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.SemiBold),
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.Bold)
)

val PactioTypography = Typography(
    headlineSmall = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = JakartaFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp)
)
