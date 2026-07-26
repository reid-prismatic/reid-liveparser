package com.prismatic.liveparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@Composable
@Preview
fun App() {
    MaterialTheme {
        ScribeDocTest()
//        val fooList = remember { EntityList() }
//
//        Column {
//            TextField(fooList.source, {fooList.source = it}, Modifier.fillMaxWidth().height(100.dp))
//            Row {
//                LazyColumn(Modifier.weight(1f)) {
//                    items(fooList.items, key = { it.id }) { item ->
//                        ItemInfo(item)
//                    }
//                }
//            }
//        }
    }
}

val PerformanceTestDispatcher = Dispatchers.Default.limitedParallelism(1)

expect fun blockingSleep(millis: Long)

@Composable
fun ItemInfo(item: Entity) {
    var compCount by remember { mutableStateOf(0) }
    SideEffect {
        compCount++
    }
    Row {
        Text("#${item.id}:", Modifier.width(50.dp))
        Text(item.data, Modifier.weight(1f))
        Text(item.num.toString(), Modifier.weight(1f))
        RecompCount(Snapshot.withoutReadObservation { compCount })
    }
}

@Composable
fun RecompCount(compCount: Int) {
    var recomp by remember { mutableStateOf(false) }
    LaunchedEffect(compCount) {
        recomp = true
        try {
            yield()
            withContext(PerformanceTestDispatcher) {
                blockingSleep(100)
            }
        } finally {
            recomp = false
        }
    }
    Text("$compCount", Modifier.widthIn(min=30.dp).background(if (recomp) Color(1.0f, 0.565f, 0.102f, 1.0f) else Color.Transparent))
}
