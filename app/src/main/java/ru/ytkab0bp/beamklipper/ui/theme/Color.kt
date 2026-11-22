package ru.ytkab0bp.beamklipper.ui.theme

import androidx.compose.ui.graphics.Color

val BrandPrimaryLight = Color(0xFF5B6BFF)
val BrandPrimaryDark = Color(0xFF9AA2FF)

val BrandSecondaryLight = Color(0xFF1BA6A6)
val BrandSecondaryDark = Color(0xFF4DD6C8)

val BrandTertiaryLight = Color(0xFFB043FF)
val BrandTertiaryDark = Color(0xFFD19BFF)

val BgLight = Color(0xFFE9EBF2)
val BgDark = Color(0xFF0B0F1A)

val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF151A2E)
val SurfaceVariantLight = Color(0xFFEBEDF3)
val SurfaceVariantDark = Color(0xFF1E2540)

val OnLight = Color(0xFF14161F)
val OnDark = Color(0xFFEFF0FF)

val MutedLight = Color(0xFF5D6270)
val MutedDark = Color(0xFFA7ADC2)

val OutlineLight = Color(0x1F14161F)
val OutlineDark = Color(0x26EFF0FF)

val ContainerLowestLight = Color(0xFFFFFFFF)
val ContainerLowLight = Color(0xFFF7F8FB)
val ContainerLight = Color(0xFFF1F2F7)
val ContainerHighLight = Color(0xFFEAECF3)
val ContainerHighestLight = Color(0xFFE3E6EF)

val ContainerLowestDark = Color(0xFF080B12)
val ContainerLowDark = Color(0xFF10141F)
val ContainerDark = Color(0xFF141927)
val ContainerHighDark = Color(0xFF1A2032)
val ContainerHighestDark = Color(0xFF212840)

val ErrorLight = Color(0xFFD92D56)
val ErrorDark = Color(0xFFFF5C7A)

val BadgeBlue = Color(0xFF4A78F0)
val BadgeIndigo = Color(0xFF5B6BFF)
val BadgePurple = Color(0xFF9B5BFF)
val BadgeTeal = Color(0xFF12B5A5)
val BadgeGreen = Color(0xFF35B558)
val BadgeOrange = Color(0xFFF2913C)
val BadgePink = Color(0xFFF0577F)
val BadgeSlate = Color(0xFF6E7A99)

fun badgeColorFor(index: Int): Color = when (((index % 8) + 8) % 8) {
    0 -> BadgeBlue
    1 -> BadgeGreen
    2 -> BadgePurple
    3 -> BadgeOrange
    4 -> BadgeTeal
    5 -> BadgePink
    6 -> BadgeIndigo
    else -> BadgeSlate
}
