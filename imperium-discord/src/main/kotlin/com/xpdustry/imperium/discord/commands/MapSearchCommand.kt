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
package com.xpdustry.imperium.discord.commands

import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.Scheduler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.discord.command.MenuCommand
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.misc.disableComponents
import com.xpdustry.imperium.discord.service.DiscordService
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ComponentInteraction
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.utils.messages.MessageEditData

class MapSearchCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val maps = instances.get<MindustryMapManager>()
    private val codec = instances.get<IdentifierCodec>()
    private val states =
        buildCache<Long, MapSearchState> {
            expireAfterWrite(1.minutes.toJavaDuration())
            expireAfterAccess(1.minutes.toJavaDuration())
            scheduler(Scheduler.systemScheduler())
            removalListener<Long, MapSearchState> { key, value, cause ->
                if (key == null || value == null || !(cause == RemovalCause.EXPLICIT || cause == RemovalCause.EXPIRED))
                    return@removalListener
                ImperiumScope.MAIN.launch { disableMessageComponents(key, value.channel) }
            }
        }

    override fun onImperiumExit() {
        states.invalidateAll()
    }

    @ImperiumCommand(["map", "search"])
    suspend fun onMapSearchCommand(interaction: SlashCommandInteraction, name: String? = null) {
        val state = MapSearchState(name, 0, emptySet(), interaction.user.idLong, interaction.channel.idLong)
        val result = getResultFromState(state)
        val message = interaction.deferReply().await().sendMessage(createMessage(result, state)).await()
        states.put(message.idLong, state)
    }

    @MenuCommand(MAP_SEARCH_PREVIOUS_BUTTON)
    suspend fun onPreviousButton(interaction: ButtonInteraction) {
        onMapSearchMessageUpdate(interaction) { it.copy(page = it.page - 1) }
    }

    @MenuCommand(MAP_SEARCH_NEXT_BUTTON)
    suspend fun onNextButton(interaction: ButtonInteraction) {
        onMapSearchMessageUpdate(interaction) { it.copy(page = it.page + 1) }
    }

    @MenuCommand(MAP_SEARCH_FIRST_BUTTON)
    suspend fun onFirstButton(interaction: ButtonInteraction) {
        onMapSearchMessageUpdate(interaction) { it.copy(page = 0) }
    }

    @MenuCommand(MAP_SEARCH_LAST_BUTTON)
    suspend fun onLastButton(interaction: ButtonInteraction) {
        onMapSearchMessageUpdate(interaction) { it.copy(page = Int.MAX_VALUE) }
    }

    @MenuCommand(MAP_SEARCH_GAMEMODE_SELECT)
    suspend fun onGamemodeSelect(interaction: StringSelectInteraction) {
        val gamemodes =
            interaction.values.map { if (it == NONE_GAMEMODE) null else MindustryGamemode.valueOf(it) }.toSet()
        onMapSearchMessageUpdate(interaction) { it.copy(gamemodes = gamemodes) }
    }

    private suspend fun onMapSearchMessageUpdate(
        interaction: ComponentInteraction,
        update: (MapSearchState) -> MapSearchState,
    ) {
        var state = states.getIfPresent(interaction.message.idLong)
        if (state == null) {
            interaction.deferReply(true).await().sendMessage("This button has has expired").await()
            return
        }
        if (state.owner != interaction.user.idLong) {
            interaction.deferReply(true).await().sendMessage("This button is not for you").await()
            // Ensures selects are reset
            val result = getResultFromState(state)
            interaction.message.editMessage(MessageEditData.fromCreateData(createMessage(result, state))).await()
            return
        }
        val edit = interaction.deferEdit().await()
        state = update(state)
        val result = getResultFromState(state)
        state = state.copy(page = state.page.coerceAtMost(result.pages))
        states.put(interaction.message.idLong, state)
        edit.editOriginal(MessageEditData.fromCreateData(createMessage(result, state))).await()
    }

    private fun createMessage(result: List<MindustryMap>, state: MapSearchState) = MessageCreate {
        val pages = result.pages
        val listing = result.drop(state.page * PAGE_SIZE).take(PAGE_SIZE)

        embeds += Embed {
            color = MINDUSTRY_ACCENT_COLOR.rgb
            description =
                if (listing.isEmpty()) {
                    "No maps found"
                } else {
                    listing.joinToString("\n") { "- ${it.name} / ${codec.encode(it.id)}" }
                }
        }

        components +=
            ActionRow.of(
                Button.primary(MAP_SEARCH_PREVIOUS_BUTTON, "Previous").withDisabled(state.page == 0),
                Button.secondary("unused", "${state.page + 1} / ${pages + 1}").withDisabled(true),
                Button.primary(MAP_SEARCH_NEXT_BUTTON, "Next").withDisabled(state.page == pages),
                Button.success(MAP_SEARCH_FIRST_BUTTON, "First").withDisabled(state.page == 0),
                Button.success(MAP_SEARCH_LAST_BUTTON, "Last").withDisabled(state.page == pages),
            )

        val entries = (MindustryGamemode.entries + null)
        components +=
            ActionRow.of(
                StringSelectMenu.create(MAP_SEARCH_GAMEMODE_SELECT)
                    .setPlaceholder("Gamemode")
                    .setMinValues(0)
                    .setMaxValues(entries.size)
                    .setDefaultValues(state.gamemodes.map { it?.name ?: NONE_GAMEMODE })
                    .apply {
                        val options =
                            entries.associateWith {
                                val name = it?.name ?: NONE_GAMEMODE
                                SelectOption.of(name.lowercase().replace("_", " "), name)
                            }
                        addOptions(options.values)
                        setDefaultOptions(state.gamemodes.map { options[it]!! })
                    }
                    .build()
            )
    }

    private suspend fun getResultFromState(state: MapSearchState) =
        (if (state.query == null) maps.findAllMaps() else maps.searchMapByName(state.query)).filter {
            state.gamemodes.isEmpty() ||
                it.gamemodes.intersect(state.gamemodes).isNotEmpty() ||
                (null in state.gamemodes && it.gamemodes.isEmpty())
        }

    private suspend fun disableMessageComponents(messageId: Long, channelId: Long) {
        discord.jda.getTextChannelById(channelId)?.retrieveMessageById(messageId)?.await()?.disableComponents()
    }

    private val List<MindustryMap>.pages: Int
        get() = (ceil(size.toFloat() / PAGE_SIZE).toInt() - 1).coerceAtLeast(0)

    data class MapSearchState(
        val query: String?,
        val page: Int,
        val gamemodes: Set<MindustryGamemode?>,
        val owner: Long,
        val channel: Long,
    )

    companion object {
        private const val MAP_SEARCH_PREVIOUS_BUTTON = "map-search-previous:1"
        private const val MAP_SEARCH_NEXT_BUTTON = "map-search-next:1"
        private const val MAP_SEARCH_FIRST_BUTTON = "map-search-first:1"
        private const val MAP_SEARCH_LAST_BUTTON = "map-search-last:1"
        private const val MAP_SEARCH_GAMEMODE_SELECT = "map-search-gamemode-select:1"
        private const val NONE_GAMEMODE = "none"
        private const val PAGE_SIZE = 15
    }
}
