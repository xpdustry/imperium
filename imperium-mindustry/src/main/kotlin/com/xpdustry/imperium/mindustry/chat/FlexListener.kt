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
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.flex.FlexAPI
import com.xpdustry.flex.message.MessageContext
import com.xpdustry.flex.message.TranslationProcessor
import com.xpdustry.flex.placeholder.template.Template
import com.xpdustry.flex.placeholder.template.TemplateFilter
import com.xpdustry.flex.placeholder.template.TemplateManager
import com.xpdustry.flex.placeholder.template.TemplateStep
import com.xpdustry.flex.translator.Translator
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.BLURPLE
import com.xpdustry.imperium.common.misc.containsLink
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.translation.SCARLET
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import mindustry.gen.Iconc

class FlexListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val plugin = instances.get<MindustryPlugin>()
    private val accounts = instances.get<AccountManager>()
    private val users = instances.get<UserManager>()

    override fun onImperiumInit() {
        FlexAPI.get().placeholders.register("imperium", ImperiumPlaceholderProcessor(plugin, accounts))

        FlexAPI.get()
            .templates
            .setDefaultTemplate(
                TemplateManager.NAME_TEMPLATE_NAME,
                Template(
                    listOf(
                        TemplateStep(
                            "[%imperium:rank_color%]<[white]%imperium:hours%[%imperium:rank_color%]> [%audience:color%]%audience:name_colored%"
                        )
                    )
                ),
            )

        FlexAPI.get()
            .templates
            .setDefaultTemplate(
                TemplateManager.CHAT_TEMPLATE_NAME,
                Template(
                    listOf(
                        TemplateStep(
                            filter = TemplateFilter.placeholder("imperium:is_discord"),
                            text = "[${BLURPLE.toHexString()}]<${Iconc.discord}> ",
                        ),
                        TemplateStep(
                            "%template:${TemplateManager.NAME_TEMPLATE_NAME}% [accent]>[white] %argument:flex:message%"
                        ),
                    )
                ),
            )

        FlexAPI.get()
            .templates
            .setDefaultTemplate(
                "mindustry_chat_team",
                Template(
                    listOf(TemplateStep("[%audience:team_color%]<T> %template:${TemplateManager.CHAT_TEMPLATE_NAME}%"))
                ),
            )

        FlexAPI.get()
            .templates
            .setDefaultTemplate(
                "mindustry_chat_say",
                Template(
                    listOf(
                        TemplateStep(
                            "[${config.mindustry.color.toHexString()}]<${Iconc.infoCircle}> ${config.server.name} [accent]>[white] %argument:flex_message%"
                        )
                    )
                ),
            )

        FlexAPI.get()
            .templates
            .setDefaultTemplate(
                "mindustry_chat_whisper",
                Template(listOf(TemplateStep("[gray]<T> %template:${TemplateManager.CHAT_TEMPLATE_NAME}%"))),
            )

        // I don't know why but Foo client appends invisible characters to the end of messages,
        // this is very annoying for the discord bridge.
        FlexAPI.get().messages.register("anti-foo-sign", Priority.HIGHEST) { context ->
            val msg = context.message
            // https://github.com/mindustry-antigrief/mindustry-client/blob/23025185c20d102f3fbb9d9a4c20196cc871d94b/core/src/mindustry/client/communication/InvisibleCharCoder.kt#L14
            CompletableFuture.completedFuture(
                if (msg.takeLast(2).all { (0xF80 until 0x107F).contains(it.code) }) msg.dropLast(2) else msg
            )
        }

        FlexAPI.get().messages.register("anti-links", Priority.NORMAL) { ctx ->
            if (ctx.filter && ctx.sender != Distributor.get().audienceProvider.server && ctx.message.containsLink()) {
                ctx.sender.sendMessage(text("You can't send discord invitations or links in the chat.", SCARLET))
                CompletableFuture.completedFuture("")
            } else {
                CompletableFuture.completedFuture(ctx.message)
            }
        }

        FlexAPI.get()
            .messages
            .register(
                "translator",
                Priority.LOW,
                object : TranslationProcessor() {
                    override fun process(context: MessageContext) =
                        ImperiumScope.MAIN.future {
                            val muuid = context.sender.metadata[StandardKeys.MUUID]
                            var sourceLocale = context.sender.metadata[StandardKeys.LOCALE] ?: Locale.getDefault()
                            val targetLocale = context.target.metadata[StandardKeys.LOCALE] ?: Locale.getDefault()
                            if (
                                sourceLocale.language != targetLocale.language &&
                                    muuid != null &&
                                    users.getSetting(muuid.uuid, User.Setting.AUTOMATIC_LANGUAGE_DETECTION)
                            ) {
                                sourceLocale = Translator.AUTO_DETECT
                            }
                            process(context, sourceLocale, targetLocale).await()
                        }
                },
            )
    }
}
