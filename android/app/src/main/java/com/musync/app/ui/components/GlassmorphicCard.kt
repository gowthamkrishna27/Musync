package com.musync.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musync.app.ui.theme.BorderStroke as BorderStrokeColor
import com.musync.app.ui.theme.CardElevated

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = CardElevated,
    borderColor: Color = BorderStrokeColor,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val border = BorderStroke(borderWidth, borderColor)

    Surface(
        modifier = modifier.clip(shape),
        shape = shape,
        color = backgroundColor,
        border = border,
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Box {
            content()
        }
    }
}

