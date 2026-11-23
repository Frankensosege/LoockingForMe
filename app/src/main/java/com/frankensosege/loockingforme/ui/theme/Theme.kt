package com.frankensosege.loockingforme.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary           = Primary40,
    onPrimary         = androidx.compose.ui.graphics.Color.White,
    primaryContainer  = Primary80,
    secondary         = Secondary40,
    secondaryContainer = Secondary80,
    surface           = SurfaceLight,
    background        = SurfaceLight,
    error             = ErrorColor,
    onError           = OnErrorColor,
)

private val DarkColorScheme = darkColorScheme(
    primary           = PrimaryDark,
    onPrimary         = Primary20,
    primaryContainer  = Primary20,
    secondary         = Secondary80,
    secondaryContainer = Secondary20,
    surface           = SurfaceDark,
    background        = BackgroundDark,
    error             = ErrorColor,
    onError           = OnErrorColor,
)

/**
 * 앱 전역 테마
 * Android 12+ Dynamic Color를 지원하되, 하위 버전은 커스텀 팔레트 사용
 */
@Composable
fun LoockingForMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
