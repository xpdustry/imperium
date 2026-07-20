// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command

import com.xpdustry.distributor.api.command.CommandElement
import com.xpdustry.distributor.api.command.CommandFacade
import com.xpdustry.distributor.api.command.CommandHelp
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.component.style.ComponentColor.ACCENT
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.menu.ListTransformer
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.key.Key
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.NavigateAction
import com.xpdustry.imperium.mindustry.misc.NavigationTransformer
import com.xpdustry.imperium.mindustry.misc.asList
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.component3
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.translation.LIGHT_GRAY
import com.xpdustry.imperium.mindustry.translation.gui_back
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Iconc

@Inject
class HelpCommand(plugin: MindustryPlugin) : ImperiumApplication.Listener {

    private val menu = MenuManager.create(plugin)

    init {
        menu.addTransformer(
            NavigationTransformer(
                HELP_PAGE,
                HelpPage.LIST,
                ListTransformer<HelpEntry>()
                    .setProvider { it.state[HELP_ENTRIES]!! }
                    .setRenderer { context, entry -> renderCommandButton(CommandSender.player(context.viewer), entry) }
                    .setHeight(COMMANDS_PER_PAGE)
                    .setFillEmptyHeight(true)
                    .setPageKey(HELP_LIST_PAGE)
                    .setChoiceAction(
                        BiAction.with(HELP_ENTRY).then(BiAction.from(NavigateAction(HELP_PAGE, HelpPage.DETAIL)))
                    ),
            )
        )
        menu.addTransformer(
            NavigationTransformer(HELP_PAGE, HelpPage.LIST) { (pane, state) ->
                val entries = state[HELP_ENTRIES]!!
                val pageCount = maxOf(1, (entries.size + COMMANDS_PER_PAGE - 1) / COMMANDS_PER_PAGE)
                val page = state.get(HELP_LIST_PAGE)?.coerceIn(0, pageCount - 1) ?: 0

                pane.title = text("Commands (${page + 1}/$pageCount)")
                pane.content =
                    components(newline(), text("Select a command to view its details.", LIGHT_GRAY), newline())
                pane.grid.addRow(
                    MenuOption.of(components(text(Iconc.left, ACCENT), space(), gui_back()), Action.back())
                )
                pane.exitAction = Action.back()
            }
        )
        menu.addTransformer(
            NavigationTransformer(HELP_PAGE, HelpPage.DETAIL) { (pane, state, viewer) ->
                val entry = state[HELP_ENTRY]!!
                val sender = CommandSender.player(viewer)
                pane.title = text(entry.path)
                pane.content = renderHelp(sender, entry.help)

                pane.grid.addRow(
                    buildList {
                        add(
                            MenuOption.of(
                                components(text(Iconc.left), space(), gui_back()),
                                NavigateAction(HELP_PAGE, HelpPage.LIST),
                            )
                        )
                        add(
                            MenuOption.of(
                                components(text(Iconc.copy), space(), text("Copy")),
                                Action.show().then { Call.copyToClipboard(it.viewer.con, entry.path) },
                            )
                        )
                        if (!entry.help.acceptsParameters()) {
                            add(
                                MenuOption.of(
                                    components(ComponentColor.GREEN, text(Iconc.play), space(), text("Run")),
                                    Action.hideAll().then(commandAction(entry.path)),
                                )
                            )
                        }
                    }
                )
                pane.exitAction = NavigateAction(HELP_PAGE, HelpPage.LIST)
            }
        )
    }

    @ImperiumCommand(["help"])
    @ClientSide
    fun onHelpCommand(sender: CommandSender) {
        menu
            .create(sender.player)
            .apply {
                state[HELP_PAGE] = HelpPage.LIST
                state[HELP_LIST_PAGE] = 0
                state[HELP_ENTRIES] = getHelpEntries(sender)
            }
            .show()
    }

    private fun renderCommandButton(sender: CommandSender, entry: HelpEntry): Component {
        if (entry.help.description.isEmpty(sender)) return text(entry.path)
        return components(
            text(entry.path),
            text(" >>> ", ACCENT),
            components(LIGHT_GRAY, entry.help.description.getComponent(sender)),
        )
    }

    private fun getHelpEntries(sender: CommandSender): List<HelpEntry> =
        Vars.netServer.clientCommands.commandList
            .asList()
            .asSequence()
            .map(CommandFacade::from)
            .filter { !it.isAlias && it.isVisible(sender) }
            .flatMap { collectHelpEntries(it, sender) }
            .distinctBy(HelpEntry::path)
            .sortedBy(HelpEntry::path)
            .toList()

    private fun collectHelpEntries(
        command: CommandFacade,
        sender: CommandSender,
        query: String = "",
        visited: MutableSet<String> = mutableSetOf(),
    ): List<HelpEntry> {
        if (!visited.add(query)) return emptyList()
        return when (val help = command.getHelp(sender, query)) {
            is CommandHelp.Empty -> emptyList()
            is CommandHelp.Entry -> listOf(HelpEntry(help.commandPath(), help))
            is CommandHelp.Suggestion ->
                help.childSuggestions.flatMap { suggestion ->
                    collectHelpEntries(command, sender, suggestion.removePrefix(command.realName).trim(), visited)
                }
        }
    }

    private fun renderHelp(sender: CommandSender, help: CommandHelp.Entry): Component {
        val arguments = help.arguments.filter { it.kind != CommandElement.Argument.Kind.LITERAL }
        val lines = mutableListOf<Component>(newline(), newline())
        if (!help.description.isEmpty(sender)) {
            lines += help.description.getComponent(sender)
            lines += listOf(newline(), newline())
        }
        if (arguments.isNotEmpty()) {
            lines += text("━━━━━━━━━━━━", ACCENT)
            lines += newline()
        }
        arguments.forEach {
            val name =
                when (it.kind) {
                    CommandElement.Argument.Kind.REQUIRED -> "<${it.name}>"
                    CommandElement.Argument.Kind.OPTIONAL -> "[${it.name}]"
                    CommandElement.Argument.Kind.LITERAL -> error("Literal arguments were filtered out")
                }
            lines += renderParameter(name, it.description.getComponent(sender))
        }
        help.flags.forEach { lines += renderParameter("[--${it.name}]", it.description.getComponent(sender)) }
        return components(lines)
    }

    private fun renderParameter(name: String, description: Component): Component =
        components(text("$name ", ACCENT), components(LIGHT_GRAY, description), newline(), newline())

    private fun CommandHelp.Entry.acceptsParameters(): Boolean =
        arguments.any { it.kind != CommandElement.Argument.Kind.LITERAL } || flags.isNotEmpty()

    private fun commandAction(path: String): Action {
        val parts = path.removePrefix("/").split(" ")
        return Action.command(parts.first(), *parts.drop(1).toTypedArray())
    }

    private fun CommandHelp.Entry.commandPath(): String = "/" + syntax.substringBefore(" <").substringBefore(" [")

    private data class HelpEntry(val path: String, val help: CommandHelp.Entry)

    private enum class HelpPage {
        LIST,
        DETAIL,
    }

    companion object {
        private const val COMMANDS_PER_PAGE = 6
        private val HELP_PAGE = Key.generated(HelpPage::class.java)
        private val HELP_ENTRY = Key.generated(HelpEntry::class.java)
        private val HELP_ENTRIES = key<List<HelpEntry>>("help-command-entries")
        private val HELP_LIST_PAGE = Key.generated(Int::class.javaObjectType)
    }
}
