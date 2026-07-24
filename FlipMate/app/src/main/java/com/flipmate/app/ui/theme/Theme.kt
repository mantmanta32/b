package com.flipmate.app.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
@Composable fun FlipMateTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF9C27B0)),content=content)}
