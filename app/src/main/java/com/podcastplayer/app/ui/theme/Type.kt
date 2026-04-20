package com.podcastplayer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.podcastplayer.app.R

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val InterTightGoogleFont = GoogleFont("Inter Tight")
private val JetBrainsMonoGoogleFont = GoogleFont("JetBrains Mono")

val InterTight = FontFamily(
    Font(googleFont = InterTightGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = InterTightGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = InterTightGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InterTightGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
    Font(googleFont = InterTightGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.ExtraBold),
)

val JetBrainsMono = FontFamily(
    Font(googleFont = JetBrainsMonoGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMonoGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = JetBrainsMonoGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
)

// Type scale — tight letter-spacing, Inter Tight for UI, Mono for timestamps/metadata.
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp,
    ),
)
