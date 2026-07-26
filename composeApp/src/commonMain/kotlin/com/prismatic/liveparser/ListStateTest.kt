package com.prismatic.liveparser

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy

private var nextId = 1L

class Entity(name: String) {
    val id = nextId++
    var data by mutableStateOf(name)
    val num by derivedStateOf(structuralEqualityPolicy()) { data.toIntOrNull() ?: 0 }
}

class EntityList {
    private var sourceState = mutableStateOf("")
    val _items = mutableStateListOf<Entity>()
    val items: List<Entity> get() = _items
    var source: String
        get() = sourceState.value
        set(value) {
            if( sourceState.value == value ) return
            sourceState.value = value
            val rawItems = value.split(",").map { it.trim() }
            var targetIndex = 0
            rawItems.indices.forEach { index ->
                val rawItem = rawItems[index]
                val nextRawItem = if(index+1 < rawItems.size) rawItems[index+1] else ""
                val addedItems = rawItems.size - _items.size
                val currentItem = if(targetIndex < _items.size) _items[targetIndex] else null
                val nextItem = if(targetIndex+1 < _items.size) _items[targetIndex+1] else null
                if(targetIndex >= _items.size) {
                    _items.add(Entity(rawItem))
                    targetIndex++
                } else if(currentItem?.data == rawItem) {
                    targetIndex++
                } else if(addedItems==1 && currentItem?.data == nextRawItem) {
                    _items.add(targetIndex, Entity(rawItem))
                    targetIndex++
                } else if(addedItems==-1 && rawItem == nextItem?.data ) {
                    _items.removeAt(targetIndex)
                    targetIndex++
                } else {
                    _items[targetIndex].data = rawItem
                    targetIndex++
                }
            }
            if(targetIndex < _items.size) {
                _items.removeRange(targetIndex, _items.size)
            }
        }
}