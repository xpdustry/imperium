/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.core.ui.menu

import com.xpdustry.foundation.mindustry.core.ui.AbstractTransformerInterface
import com.xpdustry.foundation.mindustry.core.ui.TransformerInterface
import com.xpdustry.foundation.mindustry.core.ui.action.Action
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.MUUID
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.ui.Menus

interface MenuInterface : TransformerInterface<MenuPane> {
    val exitAction: Action

    companion object {
        fun create(plugin: MindustryPlugin): MenuInterface {
            return MenuInterfaceImpl(plugin)
        }
    }
}

internal class MenuInterfaceImpl(plugin: MindustryPlugin) :
    AbstractTransformerInterface<MenuPane>(plugin, ::MenuPane), MenuInterface {
    override var exitAction: Action = Action.back()

    private val id = Menus.registerMenu { player: Player, option: Int ->
        val view = views[MUUID.of(player)]
        if (view == null) {
            this.plugin.logger.warn(
                "Received menu response from player {} (uuid: {}) but no view was found",
                player.plainName(),
                player.uuid(),
            )
        } else if (option == -1) {
            exitAction.accept(view)
        } else {
            val choice = getChoice(view.pane, option)
            if (choice == null) {
                this.plugin.logger.warn(
                    "Received invalid menu option {} from player {} (uuid: {})",
                    option,
                    player.plainName(),
                    player.uuid(),
                )
            } else {
                choice.action.accept(view)
            }
        }
    }

    override fun onViewOpen(view: SimpleView) {
        Call.followUpMenu(
            view.viewer.con(),
            id,
            view.pane.title,
            view.pane.content,
            view.pane.options.map { row ->
                row.map { obj -> obj.content }.toTypedArray()
            }.toTypedArray(),
        )
    }

    override fun onViewClose(view: SimpleView) {
        Call.hideFollowUpMenu(view.viewer.con(), id)
    }

    private fun getChoice(pane: MenuPane, id: Int): MenuOption? {
        var i = 0
        for (row in pane.options) {
            i += row.size
            if (i > id) {
                return row[id - i + row.size]
            }
        }
        return null
    }
}
