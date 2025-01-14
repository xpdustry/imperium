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
package com.xpdustry.imperium.mindustry.world

import arc.Core
import arc.files.Fi
import arc.util.Strings
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.input.TextInputManager
import com.xpdustry.distributor.api.gui.menu.ListTransformer
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.functional.ImperiumResult
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.map.MapLoader
import com.xpdustry.imperium.mindustry.misc.NavigationTransformer
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.then
import com.xpdustry.imperium.mindustry.translation.LIGHT_GRAY
import com.xpdustry.imperium.mindustry.translation.ORANGE
import com.xpdustry.imperium.mindustry.translation.SCARLET
import com.xpdustry.imperium.mindustry.translation.gui_close
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import mindustry.Vars
import mindustry.gen.Iconc
import mindustry.io.SaveIO

class SaveCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val plugin = instances.get<MindustryPlugin>()
    private val text = TextInputManager.create(instances.get())
    private val menu = MenuManager.create(instances.get())

    init {
        menu.addTransformer(
            NavigationTransformer(
                SAVE_PAGE,
                SavePage.LIST,
                ListTransformer<Path>()
                    .setRenderer(::renderSaveName)
                    .setProvider { getSaveFiles() }
                    .setFillEmptySpace(true)
                    .setChoiceAction(
                        BiAction.compose(
                            BiAction.with(SAVE_FILE),
                            BiAction.from(Action.with(SAVE_PAGE, SavePage.VIEW).then(Window::show)),
                        )
                    )
                    .then { (pane, _) ->
                        pane.title = text("Save Manager")
                        pane.grid.addOption(
                            2,
                            pane.grid.options.size - 1,
                            MenuOption.of(
                                text(Iconc.add),
                                Action.with(SAVE_PAGE, SavePage.CREATE).then(Action.show(text)),
                            ),
                        )
                        pane.grid.addRow(MenuOption.of(gui_close(), Window::hide))
                    },
            )
        )

        menu.addTransformer(
            NavigationTransformer(SAVE_PAGE, SavePage.VIEW) { (pane, state) ->
                pane.content = renderSaveName(state[SAVE_FILE]!!)
                pane.grid.addRow(
                    MenuOption.of(text(Iconc.trash, ComponentColor.RED), Action.delegate(this::onDeleteSave)),
                    MenuOption.of(
                        text(Iconc.pencil, ORANGE),
                        Action.with(SAVE_PAGE, SavePage.RENAME).then(Action.show(text)),
                    ),
                    MenuOption.of(text(Iconc.play, ComponentColor.GREEN), Action.delegate(this::onLoadSave)),
                    MenuOption.of(Iconc.cancel, Action.with(SAVE_PAGE, SavePage.LIST).then(Window::show)),
                )
                pane.exitAction = Action.with(SAVE_PAGE, SavePage.LIST).then(Window::show)
            }
        )

        text.addTransformer { (pane) ->
            pane.maxLength = 32
            pane.exitAction = Action.with(SAVE_PAGE, SavePage.LIST).then(Action.back())
        }

        text.addTransformer(
            NavigationTransformer(SAVE_PAGE, SavePage.CREATE) { (pane) ->
                pane.title = text("Create a new save file")
                pane.description = text("Only alphanumeric characters are allowed.")
                pane.inputAction = BiAction.delegate(this::onCreateSaveAction)
            }
        )

        text.addTransformer(
            NavigationTransformer(SAVE_PAGE, SavePage.RENAME) { (pane) ->
                pane.title = text("Rename the save file")
                pane.description = text("Only alphanumeric characters are allowed.")
                pane.inputAction = BiAction.delegate(this::onRenameSaveAction)
            }
        )
    }

    @ImperiumCommand(["saves"], rank = Rank.OVERSEER)
    @ClientSide
    fun onSaveCommand(sender: CommandSender) {
        menu.create(sender.player).apply { state[SAVE_PAGE] = SavePage.LIST }.show()
    }

    private fun getSaveFiles(): List<Path> =
        Vars.saveDirectory
            .file()
            .toPath()
            .listDirectoryEntries()
            .filter {
                it.name.endsWith(SAVE_EXTENSION, ignoreCase = true) &&
                    !it.name.endsWith(SAVE_BACKUP_EXTENSION, ignoreCase = true)
            }
            .sortedBy { it.nameWithoutExtension }
            .toList()

    private fun onLoadSave(window: Window): Action {
        val file = window.state[SAVE_FILE]!!
        if (!SaveIO.isSaveValid(Fi(file.toFile()))) {
            return Action(Window::show)
                .then(Action.audience { it.sendAnnouncement(text("No valid save data found for slot.", SCARLET)) })
        }

        Core.app.post {
            try {
                MapLoader().use { loader ->
                    loader.load(file)
                    plugin.logger.info("Save {} loaded.", file.fileName)
                }
            } catch (exception: IOException) {
                plugin.logger.error("Failed to load save {} (Outdated or corrupt file).", file.fileName, exception)
            }
        }

        return Action.hideAll()
    }

    private fun onDeleteSave(window: Window) =
        try {
            window.state[SAVE_FILE]!!.deleteExisting()
            Action.compose(
                Action.with(SAVE_PAGE, SavePage.LIST),
                Window::show,
                Action.audience { it.sendAnnouncement(text("Save deleted.", ComponentColor.ACCENT)) },
            )
        } catch (exception: IOException) {
            Action.compose(
                Action.with(SAVE_PAGE, SavePage.LIST),
                Action.back(),
                Action.audience {
                    it.sendAnnouncement(
                        text("An error occurred while deleting the save: ${Strings.neatError(exception)}", SCARLET)
                    )
                },
            )
        }

    private fun onRenameSaveAction(window: Window, input: String) =
        when (val result = validateSave(input)) {
            is ImperiumResult.Failure ->
                Action(Window::show).then(Action.audience { it.sendAnnouncement(result.error) })
            is ImperiumResult.Success -> {
                try {
                    val file = window.state[SAVE_FILE]!!
                    file.moveTo(result.value)
                    Action.compose(
                        Action.with(SAVE_PAGE, SavePage.LIST),
                        Action.back(),
                        Action.audience { it.sendAnnouncement(text("Save renamed to $input.", ComponentColor.ACCENT)) },
                    )
                } catch (exception: IOException) {
                    Action.compose(
                        Action.with(SAVE_PAGE, SavePage.LIST),
                        Action.back(),
                        Action.audience {
                            it.sendAnnouncement(
                                text(
                                    "An error occurred while renaming the save: ${Strings.neatError(exception)}",
                                    SCARLET,
                                )
                            )
                        },
                    )
                }
            }
        }

    private fun onCreateSaveAction(window: Window, input: String) =
        when (val result = validateSave(input)) {
            is ImperiumResult.Failure ->
                Action(Window::show).then(Action.audience { it.sendAnnouncement(result.error) })
            is ImperiumResult.Success -> {
                try {
                    SaveIO.save(Fi(result.value.toFile()))
                    Action.compose(
                        Action.with(SAVE_PAGE, SavePage.VIEW),
                        Action.with(SAVE_FILE, result.value),
                        Action.back(),
                        Action.audience {
                            it.sendAnnouncement(
                                text("Current game saved to ${result.value.fileName}.", ComponentColor.ACCENT)
                            )
                        },
                    )
                } catch (exception: IOException) {
                    Action.compose(
                        Action.with(SAVE_PAGE, SavePage.LIST),
                        Action.back(),
                        Action.audience {
                            it.sendAnnouncement(
                                text(
                                    "An error occurred while creating the save: ${Strings.neatError(exception)}",
                                    SCARLET,
                                )
                            )
                        },
                    )
                }
            }
        }

    private fun validateSave(name: String): ImperiumResult<Path, Component> {
        if (!SAVE_VALIDATE_REGEX.matches(name)) {
            return ImperiumResult.failure(text("Invalid name.", SCARLET))
        }
        val path = Vars.saveDirectory.child("${name.replace("\\s".toRegex(), "_")}.$SAVE_EXTENSION").file().toPath()
        if (path.exists()) {
            return ImperiumResult.failure(text("A save file with this name already exists.", SCARLET))
        }
        return ImperiumResult.success(path)
    }

    private fun renderSaveName(path: Path): Component {
        val result = SAVE_NAME_REGEX.find(path.nameWithoutExtension) ?: return text(path.nameWithoutExtension)
        val name =
            try {
                SaveIO.getMeta(Fi(path.toFile())).map.name()
            } catch (exception: Exception) {
                result.groups["name"]!!.value
            }
        return components(
            text(name, ComponentColor.ACCENT),
            newline(),
            text(result.groups["date"]!!.value, LIGHT_GRAY),
            space(),
            text(result.groups["time"]!!.value, LIGHT_GRAY),
        )
    }

    private enum class SavePage {
        LIST,
        VIEW,
        RENAME,
        CREATE,
    }

    companion object {
        private const val SAVE_EXTENSION = "msav"
        private const val SAVE_BACKUP_EXTENSION = "msav-backup.msav"
        private val SAVE_PAGE = key<SavePage>("save_page")
        private val SAVE_FILE = key<Path>("choice")
        private val SAVE_NAME_REGEX =
            Regex("^auto_(?<name>.+)_(?<date>\\d{2}-\\d{2}-\\d{4})_(?<time>\\d{2}-\\d{2}-\\d{2})$")
        private val SAVE_VALIDATE_REGEX = Regex("^[a-zA-Z0-9_\\-\\s]+$")
    }
}
