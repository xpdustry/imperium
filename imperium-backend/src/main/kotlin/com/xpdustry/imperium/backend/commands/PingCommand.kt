// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.commands

import com.xpdustry.imperium.backend.misc.await
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

@Inject
class PingCommand constructor() : ImperiumApplication.Listener {
    @ImperiumCommand(["ping"])
    suspend fun onPingCommand(interaction: SlashCommandInteraction) =
        interaction.reply("pong with **${interaction.jda.restPing.await()}** milliseconds of latency!").await()
}
