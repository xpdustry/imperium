// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.commands

import com.github.benmanes.caffeine.cache.Scheduler
import com.xpdustry.imperium.backend.command.MenuCommand
import com.xpdustry.imperium.backend.command.annotation.Range
import com.xpdustry.imperium.backend.misc.Embed
import com.xpdustry.imperium.backend.misc.MessageCreate
import com.xpdustry.imperium.backend.misc.await
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.common.security.MAX_WHITELIST_REASON_LENGTH
import com.xpdustry.imperium.common.security.Whitelist
import com.xpdustry.imperium.common.security.WhitelistEntry
import com.xpdustry.imperium.common.security.WhitelistTarget
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.components.ComponentInteraction
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.utils.messages.MessageEditData

@Inject
class WhitelistCommand(private val whitelist: Whitelist) : ImperiumApplication.Listener {

    private val states =
        buildCache<Long, WhitelistState> {
            expireAfterWrite(1.minutes.toJavaDuration())
            expireAfterAccess(5.minutes.toJavaDuration())
            scheduler(Scheduler.systemScheduler())
        }

    override fun onImperiumExit() {
        states.invalidateAll()
    }

    @ImperiumCommand(["whitelist", "add"], Rank.ADMIN)
    suspend fun onWhitelistAddCommand(
        interaction: SlashCommandInteraction,
        target: String,
        @Range(min = "1", max = "$MAX_WHITELIST_REASON_LENGTH") reason: String,
    ) {
        val reply = interaction.deferReply(true).await()
        val parsed = WhitelistTarget.parse(target)
        if (parsed == null) {
            reply.sendMessage(INVALID_TARGET_MESSAGE).await()
            return
        }
        whitelist.add(parsed, reason)
        reply.sendMessage("Added `$parsed` to the whitelist.").await()
    }

    @ImperiumCommand(["whitelist", "remove"], Rank.ADMIN)
    suspend fun onWhitelistRemoveCommand(interaction: SlashCommandInteraction, target: String) {
        val reply = interaction.deferReply(true).await()
        val parsed = WhitelistTarget.parse(target)
        if (parsed == null) {
            reply.sendMessage(INVALID_TARGET_MESSAGE).await()
            return
        }
        if (whitelist.remove(parsed)) {
            reply.sendMessage("Removed `$parsed` from the whitelist.").await()
        } else {
            reply.sendMessage("The whitelist does not contain `$parsed`.").await()
        }
    }

    @ImperiumCommand(["whitelist", "list"], Rank.ADMIN)
    suspend fun onWhitelistListCommand(interaction: SlashCommandInteraction) {
        val state = WhitelistState(0, interaction.user.idLong)
        val result = whitelist.list()
        val message = interaction.deferReply(true).await().sendMessage(createMessage(result, state)).await()

        states.put(message.idLong, state)
    }

    @MenuCommand(WHITELIST_PREVIOUS_BUTTON)
    suspend fun onPreviousButton(interaction: ButtonInteraction) {
        onWhitelistMessageUpdate(interaction) { it.copy(page = it.page - 1) }
    }

    @MenuCommand(WHITELIST_NEXT_BUTTON)
    suspend fun onNextButton(interaction: ButtonInteraction) {
        onWhitelistMessageUpdate(interaction) { it.copy(page = it.page + 1) }
    }

    @MenuCommand(WHITELIST_FIRST_BUTTON)
    suspend fun onFirstButton(interaction: ButtonInteraction) {
        onWhitelistMessageUpdate(interaction) { it.copy(page = 0) }
    }

    @MenuCommand(WHITELIST_LAST_BUTTON)
    suspend fun onLastButton(interaction: ButtonInteraction) {
        onWhitelistMessageUpdate(interaction) { it.copy(page = Int.MAX_VALUE) }
    }

    private suspend fun onWhitelistMessageUpdate(
        interaction: ComponentInteraction,
        update: (WhitelistState) -> WhitelistState,
    ) {
        var state = states.getIfPresent(interaction.message.idLong)
        if (state == null) {
            interaction.deferReply(true).await().sendMessage("This button has expired.").await()
            return
        }

        if (state.owner != interaction.user.idLong) {
            interaction.deferReply(true).await().sendMessage("This button is not for you.").await()
            return
        }

        val edit = interaction.deferEdit().await()
        state = update(state)

        val result = whitelist.list()
        state = state.copy(page = state.page.coerceAtMost(result.pages))

        states.put(interaction.message.idLong, state)

        edit.editOriginal(MessageEditData.fromCreateData(createMessage(result, state))).await()
    }

    private fun createMessage(
        result: List<WhitelistEntry>,
        state: WhitelistState,
    ) = MessageCreate {
        val pages = result.pages
        val listing = result.drop(state.page * PAGE_SIZE).take(PAGE_SIZE)

        embeds += Embed {
            description =
                if (listing.isEmpty()) {
                    "The whitelist is empty."
                } else {
                    listing.joinToString("\n") { (target, reason) ->
                        "- `$target` / $reason"
                    }
                }

            components +=
                ActionRow.of(
                    Button.primary(WHITELIST_PREVIOUS_BUTTON, "Previous").withDisabled(state.page == 0),
                    Button.secondary("unused", "${state.page + 1} / ${pages + 1}").withDisabled(true),
                    Button.primary(WHITELIST_NEXT_BUTTON, "Next").withDisabled(state.page == pages),
                    Button.success(WHITELIST_FIRST_BUTTON, "First").withDisabled(state.page == 0),
                    Button.success(WHITELIST_LAST_BUTTON, "Last").withDisabled(state.page == pages),
                )
        }
    }

    private val List<WhitelistEntry>.pages: Int
        get() = (ceil(size.toFloat() / PAGE_SIZE).toInt() - 1).coerceAtLeast(0)

    data class WhitelistState(
        val page: Int,
        val owner: Long,
    )

    companion object {
        private const val WHITELIST_PREVIOUS_BUTTON = "whitelist-previous:1"
        private const val WHITELIST_NEXT_BUTTON = "whitelist-next:1"
        private const val WHITELIST_FIRST_BUTTON = "whitelist-first:1"
        private const val WHITELIST_LAST_BUTTON = "whitelist-last:1"

        // Keeps the worst case page (uuid + max length reason per entry) below the 4096 characters embed limit
        private const val PAGE_SIZE = 10

        private const val INVALID_TARGET_MESSAGE = "The target is neither a valid uuid nor a valid ip address."
    }
}
