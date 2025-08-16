package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.asAudience

class InfoCommand : ImperiumApplication.Listener {

    private var pageNumber: Int = -1

    // Dont require a page for 1 page
    @ImperiumCommand(["info|i"])
    @ClientSide
    fun onInfoCommandSingle(sender: CommandSender) {
        onInfoCommand(sender, 1)
    }

    @ImperiumCommand(["info|i"])
    @ClientSide
    fun onInfoCommand(sender: CommandSender, page: Int = 1) {
        pageNumber = page
        val info = pages[page] ?: pages[0]
        val compiled = info!!.listIterator() // Convert it to a string
        sender.player.sendMessage(compiled)
    }

    // Colors will be directly in the text as dealing with this is nightmarish
    private val pages = mapOf(
        0 to listOf(
            translatable("imperium.info.page0", TranslationArguments.array(pageNumber)) // Has bundle
        ),
        1 to listOf(
            translatable("imperium.info.page1.title"),
            translatable("imperium.info.page1.line1"),
            translatable("imperium.info.page1.line2"),
            translatable("imperium.info.page1.line3"),
            translatable("imperium.info.page1.line4"),
            translatable("imperium.info.page1.line5"),
        )
    )
}