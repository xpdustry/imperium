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
package com.xpdustry.imperium.mindustry.ui.input

import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.mindustry.ui.AbstractTransformerInterface
import com.xpdustry.imperium.mindustry.ui.TransformerInterface
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.ui.Menus

interface TextInputInterface : TransformerInterface<TextInputPane> {
    companion object {
        fun create(plugin: MindustryPlugin): TextInputInterface {
            return SimpleTextInputInterface(plugin)
        }
    }
}

private class SimpleTextInputInterface(plugin: MindustryPlugin) :
    AbstractTransformerInterface<TextInputPane>(plugin, ::TextInputPane), TextInputInterface {
    private val visible: MutableSet<MUUID> = HashSet()

    private val id: Int =
        Menus.registerTextInput { player: Player, text: String? ->
            val view = views[MUUID.from(player)]
            if (view == null) {
                this.plugin.logger.warn(
                    "Received text input from player {} (uuid: {}) but no view was found",
                    player.plainName(),
                    player.uuid(),
                )
                return@registerTextInput
            }

            // Simple trick to not reopen an interface when an action already does it.
            visible.remove(MUUID.from(player))
            if (text == null) {
                view.pane.exitAction.accept(view)
            } else if (text.length > view.pane.length) {
                this.plugin.logger.warn(
                    "Received text input from player {} (uuid: {}) with length {} but the maximum length is {}",
                    player.plainName(),
                    player.uuid(),
                    text.length,
                    view.pane.length,
                )
                view.close()
            } else {
                view.pane.inputAction.accept(view, text)
            }
            // The text input closes automatically when the player presses enter,
            // so reopen if it was not explicitly closed by the server.
            if (view.isOpen && !visible.contains(MUUID.from(player))) {
                view.open()
            }
        }

    override fun onViewOpen(view: SimpleView) {
        if (visible.add(MUUID.from(view.viewer))) {
            Call.textInput(
                view.viewer.con(),
                id,
                view.pane.title,
                view.pane.description,
                view.pane.length,
                view.pane.placeholder,
                false,
            )
        }
    }
}
