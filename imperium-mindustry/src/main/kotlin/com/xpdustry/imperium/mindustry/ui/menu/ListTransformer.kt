/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.mindustry.ui.menu

import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.Action
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import com.xpdustry.imperium.mindustry.ui.transform.Transformer
import java.util.Objects
import java.util.function.Function
import kotlin.random.Random
import mindustry.gen.Iconc
import mindustry.gen.Player

class ListTransformer<E : Any>(
    var provider: Function<View, List<E>> = Function<View, List<E>> { emptyList() },
    var renderer: Function<E, String> = Function { Objects.toString(it) },
    var onChoice: BiAction<E> = Action.none().asBiAction(),
    var height: Int = 5,
    var width: Int = 1,
    var fill: Boolean = false,
) : Transformer<MenuPane> {

    val pageKey = stateKey<Int>("imperium:pagination-transformer-page:${Random.nextLong()}")
    val pageSize: Int
        get() = height * width

    override fun transform(view: View, pane: MenuPane) {
        val elements = provider.apply(view)
        if (elements.isEmpty()) {
            pane.options.addRow(MenuOption("Nothing", Action.none()))
            renderNavigation(pane, 0, false)
            return
        }
        var page = view.state[pageKey] ?: 0
        while (page > 0 && page * pageSize >= elements.size) {
            page -= 1
        }
        var cursor = 0
        for (i in 0 until height) {
            val options: MutableList<MenuOption> = ArrayList()
            for (j in 0 until width) {
                cursor = page * pageSize + i * width + j
                if (cursor < elements.size) {
                    val element = elements[cursor]
                    options.add(MenuOption(renderer.apply(element)) { v -> onChoice.accept(v, element) })
                } else if (fill) {
                    options.add(MenuOption())
                } else {
                    break
                }
            }
            if (options.isNotEmpty()) {
                pane.options.addRow(options)
            }
            if (cursor >= elements.size && !fill) {
                break
            }
        }
        renderNavigation(pane, page, cursor + 1 < elements.size)
    }

    private fun renderNavigation(pane: MenuPane, page: Int, hasNext: Boolean) {
        pane.options.addRow(
            enableIf(page > 0, Iconc.left, Action.open { state -> state[pageKey] = page - 1 }),
            MenuOption(Iconc.cancel.toString()) { it.back() },
            enableIf(hasNext, Iconc.right, Action.open { state -> state[pageKey] = page + 1 }),
        )
    }

    private fun enableIf(active: Boolean, icon: Char, action: Action): MenuOption {
        return MenuOption(if (active) icon.toString() else "[darkgray]$icon", (if (active) action else Action.open()))
    }
}

fun createPlayerListTransformer(action: BiAction<Player>): Transformer<MenuPane> =
    ListTransformer(
        provider = { view ->
            Entities.getPlayers()
                .asSequence()
                .filter { it != view.viewer }
                .sortedBy { it.name().stripMindustryColors() }
                .toList()
        },
        renderer = { it.name().stripMindustryColors() },
        height = 8,
        onChoice = action,
    )
