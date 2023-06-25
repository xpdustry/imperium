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
package com.xpdustry.foundation.mindustry.ui.input

import com.xpdustry.foundation.mindustry.ui.AbstractTransformerInterface
import com.xpdustry.foundation.mindustry.ui.TransformerInterface
import com.xpdustry.foundation.mindustry.ui.action.Action
import com.xpdustry.foundation.mindustry.ui.action.BiAction
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.MUUID
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.ui.Menus

interface TextInputInterface : TransformerInterface<TextInputPane> {
    var maxInputLength: Int
    var inputAction: BiAction<String>
    var exitAction: Action

    companion object {
        fun create(plugin: MindustryPlugin): TextInputInterface {
            return SimpleTextInputInterface(plugin)
        }
    }
}

private class SimpleTextInputInterface(
    plugin: MindustryPlugin,
) : AbstractTransformerInterface<TextInputPane>(plugin, ::TextInputPane), TextInputInterface {

    override var maxInputLength = 64
    override var inputAction: BiAction<String> = Action.none().asBiAction()
    override var exitAction: Action = Action.back()

    private val visible: MutableSet<MUUID> = HashSet()

    private val id: Int = Menus.registerTextInput { player: Player, text: String? ->
        val view = views[MUUID.of(player)]
        if (view == null) {
            this.plugin
                .logger
                .warn(
                    "Received text input from player {} (uuid: {}) but no view was found",
                    player.plainName(),
                    player.uuid(),
                )
            return@registerTextInput
        }

        // Simple trick to not reopen an interface when an action already does it.
        visible.remove(MUUID.of(player))
        if (text == null) {
            exitAction.accept(view)
        } else if (text.length > maxInputLength) {
            this.plugin
                .logger
                .warn(
                    "Received text input from player {} (uuid: {}) with length {} but the maximum length is {}",
                    player.plainName(),
                    player.uuid(),
                    text.length,
                    maxInputLength,
                )
            view.close()
        } else {
            inputAction.accept(view, text)
        }
        // The text input closes automatically when the player presses enter,
        // so reopen if it was not explicitly closed by the server.
        if (view.isOpen && !visible.contains(MUUID.of(player))) {
            view.open()
        }
    }

    override fun onViewOpen(view: SimpleView) {
        if (visible.add(MUUID.of(view.viewer))) {
            Call.textInput(
                view.viewer.con(),
                id,
                view.pane.title,
                view.pane.text,
                maxInputLength,
                view.pane.placeholder,
                false,
            )
        }
    }
}
