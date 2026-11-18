package com.maze3d.core

/** Scales the number of cells (and so the length of the route): easy is over in a minute, hard takes minutes. */
enum class Difficulty(val label: String, val scale: Float, val blurb: String) {
    EASY("Easy", 0.55f, "Small maze, about a minute"),
    MEDIUM("Medium", 1f, "Standard maze, a few minutes"),
    HARD("Hard", 1.75f, "Huge maze, several minutes"),
    ULTRA("Ultra", 1.75f, "Hard-sized, and no hints")
}
