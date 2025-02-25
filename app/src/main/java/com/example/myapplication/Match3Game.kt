package com.example.myapplication
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun Match3Game() {
    val gridSize = 5 // 5x5 grid
    var grid by remember { mutableStateOf(generateGrid(gridSize)) }
    var selectedTile by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        for (row in 0 until gridSize) {
            Row {
                for (col in 0 until gridSize) {
                    val tile = grid[row][col]
                    TileView(tile, row, col) { from, to ->
                        grid = swapTiles(grid, from, to)
                        selectedTile = null
                    }
                }
            }
        }
    }
}

@Composable
fun TileView(tile: Tile, row: Int, col: Int, onSwap: (Pair<Int, Int>, Pair<Int, Int>) -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val animatedX by animateFloatAsState(targetValue = offsetX, label = "X Animation")
    val animatedY by animateFloatAsState(targetValue = offsetY, label = "Y Animation")

    Box(
        modifier = Modifier
            .size(60.dp)
            .padding(2.dp)
            .offset(x = animatedX.dp, y = animatedY.dp) // Moves the tile correctly
            .background(tile.color)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val direction = getSwipeDirection(offsetX, offsetY)
                        val targetPos = getNewPosition(row, col, direction)
                        targetPos?.let { onSwap(Pair(row, col), it) }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { _, dragAmount ->
                        offsetX += dragAmount.x / density
                        offsetY += dragAmount.y / density
                    }
                )
            }
    )
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
