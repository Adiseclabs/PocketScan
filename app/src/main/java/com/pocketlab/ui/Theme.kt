// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val scheme = darkColorScheme(
    primary = Color(0xFF4ADE80), onPrimary = Color(0xFF00210E), primaryContainer = Color(0xFF0F3D26), onPrimaryContainer = Color(0xFFB8F5CF),
    secondary = Color(0xFF22D3EE), onSecondary = Color(0xFF00363D),
    background = Color(0xFF0B1220), onBackground = Color(0xFFE6EDF7), surface = Color(0xFF0B1220), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1B2740), onSurfaceVariant = Color(0xFFA8B5CC),
    surfaceContainer = Color(0xFF121B2E), surfaceContainerHigh = Color(0xFF16213A), surfaceContainerHighest = Color(0xFF16213A),
    outline = Color(0xFF3A4A6B), error = Color(0xFFFF6B6B)
)

@Composable fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = Shapes(medium = RoundedCornerShape(16.dp), small = RoundedCornerShape(10.dp)), content = content)
}
