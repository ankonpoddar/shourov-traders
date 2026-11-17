package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalShouravDarkTheme = staticCompositionLocalOf { false }

private val ShouravLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldDeep,
    secondary = ForestHeroEnd,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantSlate,
    onSecondaryContainer = SlateInk,
    tertiary = HarvestAmber,
    onTertiary = Color.White,
    tertiaryContainer = HarvestAmberContainer,
    onTertiaryContainer = HarvestAmber,
    background = SlateBackground,
    onBackground = SlateInk,
    surface = SurfaceWhite,
    onSurface = SlateInk,
    surfaceVariant = SurfaceVariantSlate,
    onSurfaceVariant = SlateMuted,
    outline = HairlineSlate,
    outlineVariant = HairlineSlate,
    error = CrimsonDue,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonDue,
)

internal val ShouravDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = DarkOnSurface,
    secondary = ForestHeroEnd,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnBackground,
    tertiary = HarvestAmber,
    onTertiary = Color.White,
    tertiaryContainer = DarkHarvestAmberContainer,
    onTertiaryContainer = HarvestAmber,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    error = CrimsonDue,
    onError = Color.White,
    errorContainer = DarkCrimsonContainer,
    onErrorContainer = CrimsonDue,
)

val ShouravShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

object ShouravColors {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalShouravDarkTheme.current

    val emeraldInk: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkEmeraldPrimary else EmeraldDeep

    val emeraldIconTint: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkEmeraldPrimary else EmeraldPrimary

    val emeraldBorder: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkEmeraldBorder else EmeraldSoftBorder

    val crimsonBorder: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkCrimsonBorder else CrimsonBorder

    val amberBorder: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkHarvestAmberBorder else HarvestAmberBorder

    val skyContainer: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkSkyContainer else SkyChartContainer

    val plumContainer: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkPlumContainer else PlumBadgeContainer

    val subtleText: Color
        @Composable @ReadOnlyComposable get() = if (isDark) DarkOnSurfaceMuted else SlateSubtle
}

@Composable
fun ShouravTradersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalShouravDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) ShouravDarkColorScheme else ShouravLightColorScheme,
            typography = ShouravTypography,
            shapes = ShouravShapes,
            content = content,
        )
    }
}
