package com.spop.poverlay.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.spop.poverlay.ui.theme.PTONOverlayTheme


@Composable
fun StatCard(name: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Light
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0x000000)
@Composable
fun StatCardPreview() {
    PTONOverlayTheme {
        StatCard(
            name = "Speed",
            value = "42",
            unit = "mph",
            modifier = Modifier,
        )
    }
}

