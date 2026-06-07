package com.example.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ViaboardKeyboardScreen(
    onKeyPress: (String) -> Unit,
    onLongPressEnter: () -> Unit
) {
    val rows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"),
        listOf("?123", ",", "SPACE", ".", "ENTER")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2E2E32))
            .padding(vertical = 8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { key ->
                    val weight = when (key) {
                        "SPACE" -> 4f
                        "SHIFT", "DEL", "ENTER", "?123" -> 1.5f
                        else -> 1f
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .padding(horizontal = 2.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (key in listOf("SHIFT", "DEL", "ENTER", "?123")) Color(0xFF424248) else Color(0xFF55555C))
                            .pointerInput(key) {
                                detectTapGestures(
                                    onTap = { onKeyPress(key) },
                                    onLongPress = {
                                        if (key == "ENTER") {
                                            onLongPressEnter()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            color = Color.White,
                            fontSize = if (key.length > 2) 14.sp else 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
