package com.maze3d.core

/** What fills the maze besides air: flood water, drifting fog or creeping gas. */
enum class Medium(val label: String) {
    NONE(""), WATER("Water"), FOG("Fog"), GAS("Gas")
}
