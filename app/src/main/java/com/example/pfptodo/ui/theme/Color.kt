package com.example.pfptodo.ui.theme

import androidx.compose.ui.graphics.Color

// "Productive Day" design tokens (Penpot build — teal + orange + peach)
val PDTeal = Color(0xFF0F3D4E)
val PDTealDark = Color(0xFF0A2C39)
val PDTealMid = Color(0xFF175263)
val PDOrange = Color(0xFFF5A83B)
val PDOrangeDeep = Color(0xFFE8930C)
val PDPeach = Color(0xFFFCE7C8)
val PDPeachLight = Color(0xFFFEF3E0)
val PDAppBg = Color(0xFFF6F7F5)
val PDSurfaceWhite = Color(0xFFFFFFFF)
val PDInk = Color(0xFF17262C)
val PDMuted = Color(0xFF71838A)
val PDLine = Color(0xFFE6EBE9)
val PDWarmBrown = Color(0xFF3A2E1E)
val PDMutedDark = Color(0xFFA7B8BC)

// Light Theme — Productive Day: orange primary, teal secondary, peach containers
val PrimaryLight = PDOrange
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = PDPeach
val OnPrimaryContainerLight = PDInk

val SecondaryLight = PDTeal
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = PDPeachLight
val OnSecondaryContainerLight = PDInk

val TertiaryLight = PDTealMid
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFDCE9EC)
val OnTertiaryContainerLight = PDTealDark

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = PDAppBg
val OnBackgroundLight = PDInk
val SurfaceLight = PDAppBg
val OnSurfaceLight = PDInk
val SurfaceVariantLight = PDLine
val OnSurfaceVariantLight = PDMuted
val SurfaceContainerLowestLight = PDSurfaceWhite
val SurfaceContainerLowLight = Color(0xFFEFF2F0)
val SurfaceContainerLight = PDSurfaceWhite
val SurfaceContainerHighLight = PDSurfaceWhite
val SurfaceContainerHighestLight = Color(0xFFE9EDEB)
val OutlineLight = PDMuted
val OutlineVariantLight = PDLine

// Dark Theme — Productive Day: teal-tinted dark, orange stays primary,
// peach cards become deep warm brown (#3A2E1E via primaryContainer)
val PrimaryDark = PDOrange
val OnPrimaryDark = Color(0xFFFFFFFF)
val PrimaryContainerDark = PDWarmBrown
val OnPrimaryContainerDark = PDPeach

val SecondaryDark = PDTeal
val OnSecondaryDark = Color(0xFFFFFFFF)
val SecondaryContainerDark = Color(0xFF2A2119)
val OnSecondaryContainerDark = PDOrange

val TertiaryDark = Color(0xFF7FB3C2)
val OnTertiaryDark = PDTealDark
val TertiaryContainerDark = PDTealMid
val OnTertiaryContainerDark = Color(0xFFFFFFFF)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = PDTealDark
val OnBackgroundDark = Color(0xFFFFFFFF)
val SurfaceDark = PDTealDark
val OnSurfaceDark = Color(0xFFFFFFFF)
val SurfaceVariantDark = PDTealMid
val OnSurfaceVariantDark = PDMutedDark
val SurfaceContainerLowestDark = Color(0xFF07222D)
val SurfaceContainerLowDark = Color(0xFF0C3444)
val SurfaceContainerDark = PDTeal
val SurfaceContainerHighDark = PDTealMid
val SurfaceContainerHighestDark = Color(0xFF1D6076)
val OutlineDark = Color(0xFF5E7A82)
val OutlineVariantDark = PDTealMid
