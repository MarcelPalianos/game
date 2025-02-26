package com.example.myapplication

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun Match3Game() {
    val gridSize = 5
    val tileSizeDp = 60.dp
    val paddingDp = 4.dp // 🔹 Add padding between tiles
    val density = LocalDensity.current
    val tileSizePx = with(density) { tileSizeDp.toPx() }
    val paddingPx = with(density) { paddingDp.toPx() }
    val totalTileSize = tileSizePx + paddingPx // 🔹 Tiles + padding

    var grid by remember { mutableStateOf(generateGrid(gridSize)) }
    var draggedTile by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val col = ((offset.x - (size.width - gridSize * totalTileSize) / 2) / totalTileSize).toInt()
                        val row = ((offset.y - (size.height - gridSize * totalTileSize) / 2) / totalTileSize).toInt()
                        if (row in 0 until gridSize && col in 0 until gridSize) {
                            draggedTile = Pair(row, col)
                            dragOffset = Offset.Zero
                        }
                    },
                    onDragEnd = {
                        draggedTile?.let { (row, col) ->
                            val direction = getSwipeDirection(dragOffset.x, dragOffset.y)
                            val targetPos = getNewPosition(row, col, direction)
                            if (targetPos != null) {
                                grid = swapTiles(grid, Pair(row, col), targetPos)
                            }
                        }
                        draggedTile = null
                        dragOffset = Offset.Zero
                    },
                    onDrag = { _, dragAmount ->
                        // 🔹 Restrict dragging within one tile
                        val maxDrag = totalTileSize
                        val newOffsetX = (dragOffset.x + dragAmount.x).coerceIn(-maxDrag, maxDrag)
                        val newOffsetY = (dragOffset.y + dragAmount.y).coerceIn(-maxDrag, maxDrag)

                        dragOffset = Offset(newOffsetX, newOffsetY)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = (size.width - gridSize * totalTileSize) / 2
            val startY = (size.height - gridSize * totalTileSize) / 2

            // Draw static tiles
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    if (draggedTile != Pair(row, col)) {
                        drawRect(
                            color = grid[row][col].color,
                            topLeft = Offset(
                                startX + col * totalTileSize,
                                startY + row * totalTileSize
                            ),
                            size = androidx.compose.ui.geometry.Size(tileSizePx, tileSizePx)
                        )
                    }
                }
            }

            // Draw dragged tile on top
            draggedTile?.let { (row, col) ->
                drawRect(
                    color = grid[row][col].color,
                    topLeft = Offset(
                        startX + col * totalTileSize + dragOffset.x,
                        startY + row * totalTileSize + dragOffset.y
                    ),
                    size = androidx.compose.ui.geometry.Size(tileSizePx, tileSizePx)
                )
            }
        }
    }
}

data class Tile(val color: Color)

typealias Grid = List<MutableList<Tile>>

fun generateGrid(size: Int): Grid {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
    return List(size) { row ->
        MutableList(size) { col ->
            Tile(colors[(row + col) % colors.size])
        }
    }
}

fun swapTiles(grid: Grid, from: Pair<Int, Int>, to: Pair<Int, Int>): Grid {
    val newGrid = grid.map { it.toMutableList() }
    val temp = newGrid[from.first][from.second]
    newGrid[from.first][from.second] = newGrid[to.first][to.second]
    newGrid[to.first][to.second] = temp
    return newGrid
}

fun getSwipeDirection(x: Float, y: Float): String? {
    return when {
        abs(x) > abs(y) && x > 50 -> "RIGHT"
        abs(x) > abs(y) && x < -50 -> "LEFT"
        abs(y) > abs(x) && y > 50 -> "DOWN"
        abs(y) > abs(x) && y < -50 -> "UP"
        else -> null
    }
}

fun getNewPosition(row: Int, col: Int, direction: String?): Pair<Int, Int>? {
    return when (direction) {
        "RIGHT" -> if (col < 4) Pair(row, col + 1) else null
        "LEFT" -> if (col > 0) Pair(row, col - 1) else null
        "DOWN" -> if (row < 4) Pair(row + 1, col) else null
        "UP" -> if (row > 0) Pair(row - 1, col) else null
        else -> null
    }
}