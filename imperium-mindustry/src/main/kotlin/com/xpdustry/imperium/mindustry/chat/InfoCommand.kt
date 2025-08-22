package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor.ACCENT
import com.xpdustry.distributor.api.component.style.ComponentColor.CYAN
import com.xpdustry.distributor.api.component.style.ComponentColor.SCARLET
import com.xpdustry.distributor.api.component.style.ComponentColor.WHITE
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.asAudience

class InfoCommand : ImperiumApplication.Listener {

    // Dont require a page for 1 page
    @ImperiumCommand(["info|i"])
    @ClientSide
    fun onInfoCommandSingle(sender: CommandSender) {
        onInfoCommand(sender, 1)
    }

    @ImperiumCommand(["info|i"])
    @ClientSide
    fun onInfoCommand(sender: CommandSender, page: Int = 1) {
        val list = pages[page] ?: pages[0]
        sender.player.asAudience.sendMessage(compileText(list as List<TranslatableComponent>))
    }

    fun compileText(list: List<TranslatableComponent>): Component {
        var component: Component? = null
        if (list.size != 1) {
            component = components(
            *list.flatMapIndexed { index, it ->
                    if (index == 0) {
                        listOf(
                            it,
                            newline(),
                            translatable("imperium.seperator", ACCENT),
                            newline()
                        )
                    } else {
                        listOf(
                            text(">> ", CYAN),
                            text("", WHITE),
                            it,
                            newline()
                        )
                    }
                }.toTypedArray()
            )
        } else component = components(list.first()) // always the error page
        return component as Component // remove the Nullable? type
    }

    // Colors will be directly in the text as dealing with this is nightmarish
    private val pages = mapOf(
        // Error page, used when page is not found
        0 to listOf(
            translatable("imperium.info.page0", SCARLET) // Has bundle
        ),
        // Table of Contents
        1 to listOf(
            translatable("imperium.info.page1.title"),
            translatable("imperium.info.page1.line1"),
            translatable("imperium.info.page1.line2"),
            translatable("imperium.info.page1.line3"),
            translatable("imperium.info.page1.line4"),
        ),
        // Staff Info
        2 to listOf(
            translatable("imperium.info.page2.title", ACCENT),
            translatable("imperium.info.page2.line1"),
            translatable("imperium.info.page2.line2"),
            translatable("imperium.info.page2.line3"),
            translatable("imperium.info.page2.line4"),
            translatable("imperium.info.page2.line5"),
            translatable("imperium.info.page2.line6"),
            translatable("imperium.info.page2.line7"),
        ),
        // Excavate Command
        3 to listOf(
            translatable("imperium.info.page3.title", ACCENT),
            translatable("imperium.info.page3.line1"),
            translatable("imperium.info.page3.line2"),
            translatable("imperium.info.page3.line3"),
            translatable("imperium.info.page3.line4"),
            translatable("imperium.info.page3.line5"),
            translatable("imperium.info.page3.line6"),
            translatable("imperium.info.page3.line7"),
            translatable("imperium.info.page3.line8"),
        ),
        // Formation
        4 to listOf(
            translatable("imperium.info.page4.title", ACCENT),
            translatable("imperium.info.page4.line1"),
            translatable("imperium.info.page4.line2"),
            translatable("imperium.info.page4.line3"),
            translatable("imperium.info.page4.line4"),
            translatable("imperium.info.page4.line5"),
            translatable("imperium.info.page4.line6"),
        )
    )
}