package com.prismatic.liveparser

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        WeaveLiveParseTestUI()
    }
}

expect fun blockingSleep(millis: Long)
