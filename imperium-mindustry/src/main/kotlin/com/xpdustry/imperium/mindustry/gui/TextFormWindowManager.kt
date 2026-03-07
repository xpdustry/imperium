// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.gui

import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.NumberComponent.number
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.gui.input.TextInputManager
import com.xpdustry.distributor.api.gui.input.TextInputPane
import com.xpdustry.distributor.api.gui.transform.Transformer
import com.xpdustry.distributor.api.key.Key
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.mindustry.translation.GRAY
import kotlin.enums.enumEntries

// TODO: Actually, that shit can be a Transformer :^)
@Suppress("FunctionName")
inline fun <reified P : Enum<P>> TextFormWindowManager(
    plugin: MindustryPlugin,
    name: String,
    footer: Boolean = false,
    extra: Transformer<TextInputPane> = Transformer { _ -> },
    submit: BiAction<Map<P, String>>,
): WindowManager {
    val input = TextInputManager.create(plugin)
    val pageKey = Key.generated(P::class.java)
    val entries = enumEntries<P>()

    input.addTransformer { ctx ->
        val page = ctx.state[pageKey] ?: entries.first()

        ctx.pane.title =
            components(
                translatable("imperium.gui.$name.title"),
                space(),
                text('('),
                number(page.ordinal + 1),
                text('/'),
                number(entries.size),
                text(')'),
            )

        ctx.pane.maxLength = 64
        ctx.pane.description =
            components()
                .append(translatable("imperium.gui.$name.page.${page.name.lowercase()}.description"))
                .apply { if (footer) append(newline(), newline(), translatable("imperium.gui.$name.footer", GRAY)) }
                .build()

        var action = BiAction.with(Key.of("imperium", page.name, String::class.java))
        action =
            if (page.ordinal + 1 < entries.size) {
                action.then(Action.with(pageKey, entries[page.ordinal + 1])).then(Window::show)
            } else {
                action.then { window ->
                    submit.act(
                        window,
                        entries.associateWith { window.state[Key.of("imperium", it.name, String::class.java)] ?: "" },
                    )
                }
            }

        ctx.pane.inputAction = BiAction.from<String>(Window::hide).then(action)

        ctx.pane.exitAction =
            if (page.ordinal > 0) {
                Action.with(pageKey, entries[page.ordinal - 1]).then(Window::show)
            } else {
                Action.back()
            }
    }

    input.addTransformer(extra)

    return input
}
