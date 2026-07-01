package com.raised.uikit.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Family = FontFamily.Default

val RaisedTypography = Typography(
    displayLarge = TextStyle(fontFamily = Family, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 80.sp),
    displayMedium = TextStyle(fontFamily = Family, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 64.sp),
    displaySmall = TextStyle(fontFamily = Family, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontFamily = Family, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = Family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
)
