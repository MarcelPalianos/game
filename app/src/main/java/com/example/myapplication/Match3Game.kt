package com.example.myapplication

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
import kotlinx.coroutines.delay
import kotlin.math.abs

// --- Game State Definition ---
sealed class GameState {
    object WaitingForMove : GameState()
    object Flashing : GameState()
    object RemovingMatches : GameState()
    object FallingTiles : GameState()
    object CheckingNewMatches : GameState()
    object Reshuffle : GameState()
}

// --- Data Model ---
data class Tile(val color: Color)
typealias Grid = List<MutableList<Tile>>

@Composable
fun Match3Game() {
    val gridSize = 5
    val tileSizeDp = 60.dp
    val paddingDp = 4.dp
    val density = LocalDensity.current
    val tileSizePx = with(density) { tileSizeDp.toPx() }
    val paddingPx = with(density) { paddingDp.toPx() }
    val totalTileSize = tileSizePx + paddingPx

    // Additional state for falling animation offsets (in pixels)
    var fallingOffsets by remember { mutableStateOf<Map<Pair<Int, Int>, Float>>(emptyMap()) }

    var gameState by remember { mutableStateOf<GameState>(GameState.WaitingForMove) }
    var grid by remember { mutableStateOf(generateRandomGrid(gridSize)) }
    var draggedTile by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var flashingTiles by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                if (gameState is GameState.WaitingForMove) {
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
                                    gameState = GameState.Flashing
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
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = (size.width - gridSize * totalTileSize) / 2
            val startY = (size.height - gridSize * totalTileSize) / 2

            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    val isFlashing = flashingTiles.contains(Pair(row, col))
                    val tileColor = if (isFlashing) Color.White else grid[row][col].color

                    // If this tile is falling, add its falling offset (default to 0 if not falling)
                    val extraYOffset = fallingOffsets[Pair(row, col)] ?: 0f

                    if (draggedTile != Pair(row, col)) {
                        drawRect(
                            color = tileColor,
                            topLeft = Offset(startX + col * totalTileSize, startY + row * totalTileSize + extraYOffset),
                            size = androidx.compose.ui.geometry.Size(tileSizePx, tileSizePx)
                        )
                    }
                }
            }
            draggedTile?.let { (row, col) ->
                // For the dragged tile, we apply dragOffset (no falling animation)
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

    // 🚀 Run game logic when gameState changes
    LaunchedEffect(gameState) {
        processGameLogic(
            currentGrid = grid,
            currentGameState = gameState,
            totalTileSize = totalTileSize,
            updateGameState = { newGameState -> gameState = newGameState },
            updateGrid = { newGrid -> grid = newGrid },
            updateFlashingTiles = { flashingTiles = it },
            updateFallingOffsets = { fallingOffsets = it }  // New updater for falling offsets
        )
    }
}

// Modified processGameLogic to animate falling tiles during FallingTiles state.
suspend fun processGameLogic(
    currentGrid: Grid,
    currentGameState: GameState,
    updateGameState: (GameState) -> Unit,
    totalTileSize: Float,
    updateGrid: (Grid) -> Unit,
    updateFlashingTiles: (Set<Pair<Int, Int>>) -> Unit,
    updateFallingOffsets: (Map<Pair<Int, Int>, Float>) -> Unit
) {
    var grid = currentGrid
    var gameState = currentGameState


    while (true) {
        delay(200) // Short delay for animations

        when (gameState) {
            is GameState.Flashing -> {
                val matches = findMatches(grid)
                if (matches.isNotEmpty()) {
                    repeat(3) {
                        updateFlashingTiles(matches.toSet()) // Show flashing
                        delay(300)
                        updateFlashingTiles(emptySet()) // Hide flashing
                        delay(300)
                    }
                }
                gameState = GameState.RemovingMatches
            }
            is GameState.RemovingMatches -> {
                grid = removeMatches(grid)
                gameState = GameState.FallingTiles
            }
            is GameState.FallingTiles -> {
                // Animate falling before updating grid
                val oldGrid = grid
                val newGrid = applyGravity(grid)
                // Compute falling distances (in pixels) for tiles that moved.
                val fallingMap = computeFallingOffsets(oldGrid, totalTileSize)
                animateFallingTiles(fallingMap) { updateFallingOffsets(it) }
                grid = newGrid
                gameState = GameState.CheckingNewMatches
            }
            is GameState.CheckingNewMatches -> {
                if (!hasPossibleMoves(grid)) {
                    gameState = GameState.Reshuffle
                } else {
                    val newMatches = findMatches(grid)
                    gameState = if (newMatches.isNotEmpty()) GameState.Flashing else GameState.WaitingForMove
                }
            }
            is GameState.Reshuffle -> {
                grid = reshuffleGrid(grid)
                gameState = GameState.CheckingNewMatches
            }
            else -> return
        }

        updateGameState(gameState)
        updateGrid(grid)
    }
}

/** Helper: Compute falling offsets (in pixels) for each tile that moved.
This iterates column by column (bottom-up) and for any non-transparent tile that has empty spaces below,
it maps its new grid position to the fall distance (emptySpaces * totalTileSize). */
fun computeFallingOffsets(oldGrid: Grid, totalTileSize: Float): Map<Pair<Int, Int>, Float> {
    val offsets = mutableMapOf<Pair<Int, Int>, Float>()
    val size = oldGrid.size
    for (col in 0 until size) {
        var emptyCount = 0
        // Process column from bottom-up
        for (row in (size - 1) downTo 0) {
            if (oldGrid[row][col].color == Color.Transparent) {
                emptyCount++
            } else if (emptyCount > 0) {
                // For a tile that existed in oldGrid, record its fall distance.
                offsets[Pair(row + emptyCount, col)] = emptyCount * totalTileSize
            }
        }
        // If the top rows were cleared, assign a default falling offset for new tiles.
        for (row in 0 until emptyCount) {
            // Only assign if no offset is already set (i.e. for cells that are entirely new)
            if (offsets[Pair(row, col)] == null) {
                offsets[Pair(row, col)] = totalTileSize // you could adjust this value as desired
            }
        }
    }
    return offsets
}


/** Helper: Animate falling offsets over a fixed duration.
Calls the update callback repeatedly with the current fraction of the total fall distance. */
suspend fun animateFallingTiles(
    fallingMap: Map<Pair<Int, Int>, Float>,
    update: (Map<Pair<Int, Int>, Float>) -> Unit
) {
    val duration = 300L // Total animation duration in milliseconds
    val steps = 20     // Number of steps for the animation
    for (step in 1..steps) {
        val fraction = step / steps.toFloat()
        val newOffsets = fallingMap.mapValues { (_, totalOffset) -> totalOffset * fraction }
        update(newOffsets)
        delay(duration / steps)
    }
    update(emptyMap())
}

/** Below are your other helper functions (applyGravity, removeMatches, findMatches, etc.)
They remain mostly unchanged. */

fun applyGravity(grid: Grid): Grid {
    val newGrid = grid.map { it.toMutableList() }

    for (col in newGrid[0].indices) {
        var emptySpaces = 0
        for (row in newGrid.indices.reversed()) {
            if (newGrid[row][col].color == Color.Transparent) {
                emptySpaces++
            } else if (emptySpaces > 0) {
                newGrid[row + emptySpaces][col] = newGrid[row][col]
                newGrid[row][col] = Tile(Color.Transparent)
            }
        }
    }

    for (col in newGrid[0].indices) {
        for (row in 0 until newGrid.size) {
            if (newGrid[row][col].color == Color.Transparent) {
                newGrid[row][col] = Tile(generateRandomColor())
            }
        }
    }

    return newGrid
}

fun removeMatches(grid: Grid): Grid {
    val newGrid = grid.map { it.toMutableList() }
    val matches = findMatches(newGrid)
    matches.forEach { (row, col) -> newGrid[row][col] = Tile(Color.Transparent) }
    return newGrid
}

fun isValidSwap(grid: Grid, row: Int, col: Int, target: Pair<Int, Int>): Boolean {
    val tempGrid = swapTiles(grid.map { it.toMutableList() }, Pair(row, col), target)
    return findMatches(tempGrid).isNotEmpty()
}

fun findMatches(grid: Grid): List<Pair<Int, Int>> {
    val matchedTiles = mutableSetOf<Pair<Int, Int>>()
    val gridSize = grid.size
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

fun generateRandomGrid(size: Int): Grid {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
    val grid = MutableList(size) { MutableList(size) { Tile(colors.random()) } }
    var attempt = 0
    val maxAttempts = 500
    do {
        attempt++
        for (row in 0 until size) {
            for (col in 0 until size) {
                do {
                    val newColor = colors.random()
                    grid[row][col] = Tile(newColor)
                } while (createsMatch(grid, row, col))
            }
        }
        if (hasPossibleMoves(grid) || attempt >= maxAttempts) break
    } while (true)
    return grid
}

fun createsMatch(grid: Grid, row: Int, col: Int): Boolean {
    val color = grid[row][col].color
    if (col >= 2 && grid[row][col - 1].color == color && grid[row][col - 2].color == color) return true
    if (row >= 2 && grid[row - 1][col].color == color && grid[row - 2][col].color == color) return true
    return false
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

fun generateRandomColor(): Color {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta)
    return colors.random()
}

fun hasPossibleMoves(grid: Grid): Boolean {
    val size = grid.size
    for (row in 0 until size) {
        for (col in 0 until size) {
            if (col < size - 1) {
                val swappedRight = swapTiles(grid, Pair(row, col), Pair(row, col + 1))
                if (findMatches(swappedRight).isNotEmpty()) return true
            }
            if (row < size - 1) {
                val swappedDown = swapTiles(grid, Pair(row, col), Pair(row + 1, col))
                if (findMatches(swappedDown).isNotEmpty()) return true
            }
        }
    }
    return false
}

fun reshuffleGrid(grid: Grid): Grid {
    val size = grid.size
    val allTiles = grid.flatten().toMutableList()
    var newGrid: Grid
    var attempts = 0
    do {
        attempts++
        allTiles.shuffle()
        newGrid = List(size) { row ->
            MutableList(size) { col ->
                allTiles[row * size + col]
            }
        }
    } while (!hasPossibleMoves(newGrid) && attempts < 1000)
    return newGrid
}