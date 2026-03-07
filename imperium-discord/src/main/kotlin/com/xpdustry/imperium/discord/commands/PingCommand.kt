// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class PingCommand : ImperiumApplication.Listener {
    @ImperiumCommand(["ping"])
    suspend fun onPingCommand(interaction: SlashCommandInteraction) =
        interaction.reply("pong with **${interaction.jda.restPing.await()}** milliseconds of latency!").await()
}
