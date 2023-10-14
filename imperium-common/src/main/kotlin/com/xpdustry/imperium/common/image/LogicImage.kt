/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.common.image

sealed class LogicImage(val resolution: Int) {
    class PixMap(resolution: Int, val pixels: Map<Int, Int>) : LogicImage(resolution)

    class Drawer(resolution: Int, val processors: List<Processor>) : LogicImage(resolution) {
        data class Processor(val x: Int, val y: Int, val instructions: List<Instruction>)

        sealed interface Instruction {
            data class Color(val r: Int, val g: Int, val b: Int, val a: Int) : Instruction

            data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) : Instruction

            data class Triangle(
                val x1: Int,
                val y1: Int,
                val x2: Int,
                val y2: Int,
                val x3: Int,
                val y3: Int
            ) : Instruction
        }
    }
}
