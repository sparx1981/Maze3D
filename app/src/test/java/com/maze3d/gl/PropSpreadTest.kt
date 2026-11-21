package com.maze3d.gl

import com.maze3d.core.Maze
import com.maze3d.core.ShapeKind
import org.junit.Assert.assertTrue
import org.junit.Test

/** Props must cover the whole shape, not just the part of it that happens to come first in cell order. */
class PropSpreadTest {
    private fun octants(kind: ShapeKind, themeId: Int, scale: Float): IntArray {
        val maze = Maze(kind.createMesh(scale), 11)
        val pal = Palette.all.first { it.themeId == themeId }
        val b = SceneBuilder(maze, pal)
        b.planProps(kind.outerRadius)
        val mb = b.props()
        val v = mb.vertices(); val stride = MeshBuilder.STRIDE
        val cx = FloatArray(3); var n = 0
        for (i in 0 until v.size / stride) { for (k in 0..2) cx[k] += v[i * stride + k]; n++ }
        for (k in 0..2) cx[k] /= n.toFloat()
        val o = IntArray(8)
        for (i in 0 until v.size / stride) {
            val idx = (if (v[i * stride] > cx[0]) 1 else 0) + (if (v[i * stride + 1] > cx[1]) 2 else 0) + (if (v[i * stride + 2] > cx[2]) 4 else 0)
            o[idx]++
        }
        println("spread ${kind.name} theme $themeId scale $scale cells ${maze.cellCount}: ${o.toList()}")
        return o
    }

    @Test fun propsReachEveryPartOfBigShapes() {
        for (kind in listOf(ShapeKind.SPHERE, ShapeKind.values().first { it.name.contains("TREFOIL") || it.name.contains("KNOT") })) for (theme in intArrayOf(10, 15, 9)) {
            val o = octants(kind, theme, 1.75f)
            val max = o.max(); val min = o.min()
            assertTrue("${kind.name} theme $theme: octant counts ${o.toList()}", min * 6 >= max)
        }
    }
}
