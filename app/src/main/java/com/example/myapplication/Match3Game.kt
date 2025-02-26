package com.example.myapplication

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun Match3Game() {
    val gridSize = 5
    val tileSizeDp = 60.dp
    val paddingDp = 4.dp
    val density = LocalDensity.current
    val tileSizePx = with(density) { tileSizeDp.toPx() }
    val paddingPx = with(density) { paddingDp.toPx() }
    val totalTileSize = tileSizePx + paddingPx

    var grid by remember { mutableStateOf(generateRandomGrid(gridSize)) }
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

                            if (targetPos != null && isValidSwap(grid, row, col, targetPos)) {
                                grid = swapTiles(grid, Pair(row, col), targetPos)
                            }
                        }
                        draggedTile = null
                        dragOffset = Offset.Zero
                    },
                    onDrag = { _, dragAmount ->
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

/** ✅ Only swap if it creates a match */
fun isValidSwap(grid: Grid, row: Int, col: Int, target: Pair<Int, Int>): Boolean {
    val tempGrid = swapTiles(grid.map { it.toMutableList() }, Pair(row, col), target)

    return findMatches(tempGrid).isNotEmpty()
}

/** 🔍 Find all matches (3+ adjacent tiles of the same color) */
fun findMatches(grid: Grid): List<Pair<Int, Int>> {
    val matchedTiles = mutableSetOf<Pair<Int, Int>>()
    val gridSize = grid.size

    // Check rows
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize - 2) {
            if (grid[row][col].color == grid[row][col + 1].color &&
                grid[row][col].color == grid[row][col + 2].color
            ) {
                matchedTiles.add(Pair(row, col))
                matchedTiles.add(Pair(row, col + 1))
                matchedTiles.add(Pair(row, col + 2))
            }
        }
    }

    // Check columns
    for (col in 0 until gridSize) {
        for (row in 0 until gridSize - 2) {
            if (grid[row][col].color == grid[row + 1][col].color &&
                grid[row][col].color == grid[row + 2][col].color
            ) {
                matchedTiles.add(Pair(row, col))
                matchedTiles.add(Pair(row + 1, col))
                matchedTiles.add(Pair(row + 2, col))
            }
        }
    }

    return matchedTiles.toList()
}

data class Tile(val color: Color)

typealias Grid = List<MutableList<Tile>>

fun generateRandomGrid(size: Int): Grid {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
    val grid = MutableList(size) { MutableList(size) { Tile(colors.random()) } }

    var attempt = 0
    val maxAttempts = 500 // Prevent infinite loops

    do {
        attempt++
        // Fill the grid with random tiles, ensuring no initial matches
        for (row in 0 until size) {
            for (col in 0 until size) {
                do {
                    val newColor = colors.random()
                    grid[row][col] = Tile(newColor)
                } while (createsMatch(grid, row, col))
            }
        }

        // Stop if we found a valid move or reached max attempts
        if (hasPossibleMoves(grid) || attempt >= maxAttempts) {
            break
        }

    } while (true)

    return grid
}


/** 🔍 Prevents immediate matches at grid generation */
fun createsMatch(grid: Grid, row: Int, col: Int): Boolean {
    val color = grid[row][col].color

    // Check left & right
    if (col >= 2 && grid[row][col - 1].color == color && grid[row][col - 2].color == color) return true

    // Check above & below
    if (row >= 2 && grid[row - 1][col].color == color && grid[row - 2][col].color == color) return true

    return false
}

/** ✅ Ensures at least one valid move exists */
fun hasPossibleMoves(grid: Grid): Boolean {
    val size = grid.size

    for (row in 0 until size) {
        for (col in 0 until size) {
            val tile = grid[row][col]

            // Try swapping with right neighbor
            if (col < size - 1) {
                swapTiles(grid, Pair(row, col), Pair(row, col + 1))
                if (findMatches(grid).isNotEmpty()) return true
                swapTiles(grid, Pair(row, col), Pair(row, col + 1)) // Swap back
            }

            // Try swapping with bottom neighbor
            if (row < size - 1) {
                swapTiles(grid, Pair(row, col), Pair(row + 1, col))
                if (findMatches(grid).isNotEmpty()) return true
                swapTiles(grid, Pair(row, col), Pair(row + 1, col)) // Swap back
            }
        }
    }
    return false // No valid moves found
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
