package com.example.pfptodo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun <T> ReorderableLazyColumn(
    items: List<T>,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    key: (T) -> Any = { it as Any },
    itemContent: @Composable (T, Boolean) -> Unit
) {
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableStateOf(0f) }

    LazyColumn(
        // Stable key: keying on `items` recreated the detector on every reorder,
        // cancelling the drag gesture mid-gesture.
        modifier = modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    state.layoutInfo.visibleItemsInfo
                        .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                        ?.also { draggedItemIndex = it.index }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    draggingOffset += dragAmount.y

                    val currentDraggedIndex = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                    val visibleItems = state.layoutInfo.visibleItemsInfo
                    val draggedVisibleItem = visibleItems.find { it.index == currentDraggedIndex }
                        ?: return@detectDragGesturesAfterLongPress

                    val draggedCenterY = draggedVisibleItem.offset + (draggedVisibleItem.size / 2) + draggingOffset

                    val targetItem = visibleItems.find { item ->
                        item.index != currentDraggedIndex &&
                        draggedCenterY.toInt() in item.offset..(item.offset + item.size)
                    }

                    if (targetItem != null) {
                        onReorder(currentDraggedIndex, targetItem.index)
                        draggedItemIndex = targetItem.index
                        draggingOffset = 0f
                    }
                },
                onDragEnd = {
                    draggedItemIndex = null
                    draggingOffset = 0f
                },
                onDragCancel = {
                    draggedItemIndex = null
                    draggingOffset = 0f
                }
            )
        },
        state = state,
        contentPadding = contentPadding
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val isDragging = index == draggedItemIndex
            val shadowElevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "DragShadow")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .shadow(shadowElevation)
            ) {
                itemContent(item, isDragging)
            }
        }
    }
}
