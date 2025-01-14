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

import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.mindustry.ui.AbstractTransformerInterface
import com.xpdustry.imperium.mindustry.ui.TransformerInterface
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.ui.Menus

interface MenuInterface : TransformerInterface<MenuPane> {
    companion object {
        fun create(plugin: MindustryPlugin): MenuInterface {
            return MenuInterfaceImpl(plugin)
        }
    }
}

private class MenuInterfaceImpl(plugin: MindustryPlugin) :
    AbstractTransformerInterface<MenuPane>(plugin, ::MenuPane), MenuInterface {

    private val id =
        Menus.registerMenu { player: Player, option: Int ->
            val view = views[MUUID.from(player)]
            if (view == null) {
                this.plugin.logger.warn(
                    "Received menu response from player {} (uuid: {}) but no view was found",
                    player.plainName(),
                    player.uuid(),
                )
            } else if (option == -1) {
                view.pane.exitAction.accept(view)
            } else {
                val choice = view.pane.options.getOption(option)
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
            view.pane.options.grid.map { row -> row.map { option -> option.content }.toTypedArray() }.toTypedArray(),
        )
    }

    override fun onViewClose(view: SimpleView) {
        Call.hideFollowUpMenu(view.viewer.con(), id)
    }
}
