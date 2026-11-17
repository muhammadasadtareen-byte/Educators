package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EducatorsLightColorScheme = lightColorScheme(
    primary = EducatorsBlue,
    onPrimary = PureWhite,
    primaryContainer = EducatorsSkyContainer,
    onPrimaryContainer = EducatorsNavy,
    secondary = EducatorsNavy,
    onSecondary = PureWhite,
    secondaryContainer = EducatorsBlueSurface,
    onSecondaryContainer = EducatorsNavy,
    tertiary = AcademicGold,
    onTertiary = PureWhite,
    tertiaryContainer = AcademicGoldContainer,
    onTertiaryContainer = SlateTextPrimary,
    background = SlateBackground,
    onBackground = SlateTextPrimary,
    surface = PureWhite,
    onSurface = SlateTextPrimary,
    surfaceVariant = EducatorsBlueSurface,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateBorder,
    error = CrimsonFail,
    onError = PureWhite,
    errorContainer = CrimsonFailContainer,
    onErrorContainer = CrimsonFail
)

private val EducatorsDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBg,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = EducatorsBlueLight,
    onSecondary = PureWhite,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = AcademicGold,
    onTertiary = DarkBg,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = AcademicGoldContainer,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = ColorErrorDark,
    onError = DarkBg
)

val EducatorsShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun EducatorsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EducatorsDarkColorScheme else EducatorsLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EducatorsTypography,
        shapes = EducatorsShapes,
        content = content
    )
}
