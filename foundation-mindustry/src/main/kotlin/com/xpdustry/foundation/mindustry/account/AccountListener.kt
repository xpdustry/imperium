/*
 * Foundation, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.foundation.mindustry.account

import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.kotlin.extension.buildAndRegister
import com.google.inject.Inject
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.model.AccountException
import com.xpdustry.foundation.common.database.model.AccountService
import com.xpdustry.foundation.common.database.model.PlayerIdentity
import com.xpdustry.foundation.common.misc.switchIfEmpty
import com.xpdustry.foundation.mindustry.command.FoundationPluginCommandManager
import com.xpdustry.foundation.mindustry.misc.MindustryScheduler
import com.xpdustry.foundation.mindustry.ui.Interface
import com.xpdustry.foundation.mindustry.ui.View
import com.xpdustry.foundation.mindustry.ui.action.Action
import com.xpdustry.foundation.mindustry.ui.action.BiAction
import com.xpdustry.foundation.mindustry.ui.input.TextInputInterface
import com.xpdustry.foundation.mindustry.ui.state.stateKey
import com.xpdustry.foundation.mindustry.verification.VerificationPipeline
import com.xpdustry.foundation.mindustry.verification.VerificationResult
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.Priority
import jakarta.inject.Named
import mindustry.gen.Call
import mindustry.gen.Player
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

private val logger = LoggerFactory.getLogger(AccountListener::class.java)

// TODO
//  - Add rate limiting
//  - Add password reset and update
//  - Add migrate command
class AccountListener @Inject constructor(
    private val plugin: MindustryPlugin,
    private val service: AccountService,
    private val verificationPipeline: VerificationPipeline,
    @param:Named("client") private val clientCommandManager: FoundationPluginCommandManager,
) : FoundationListener {
    override fun onFoundationInit() {
        // Small hack to make sure a player session is refreshed when it joins the server,
        // instead of blocking the process in a PlayerConnectionConfirmed event listener
        verificationPipeline.register("account", Priority.LOWEST) {
            service.refresh(PlayerIdentity(it.uuid, it.usid)).thenReturn(VerificationResult.Success)
        }

        val loginInterface = createLoginInterface(plugin, service)

        clientCommandManager.buildAndRegister("login") {
            commandDescription("Login to your account")
            argument(StringArgument.optional("username", StringArgument.StringMode.GREEDY))
            handler { ctx ->
                service.findAccountByIdentity(ctx.sender.player.identity)
                    .publishOn(MindustryScheduler)
                    .doOnNext {
                        ctx.sender.sendMessage("You are already logged in!")
                    }
                    .switchIfEmpty {
                        Mono.fromRunnable {
                            loginInterface.open(ctx.sender.player)
                        }
                    }
                    .subscribe()
            }
        }

        val registerInterface = createRegisterInterface(plugin, service)

        clientCommandManager.buildAndRegister("register") {
            commandDescription("Register your account")
            handler { ctx ->
                service.findAccountByIdentity(ctx.sender.player.identity)
                    .publishOn(MindustryScheduler)
                    .doOnNext {
                        ctx.sender.sendMessage("You are already logged in!")
                    }
                    .switchIfEmpty {
                        Mono.fromRunnable {
                            registerInterface.open(ctx.sender.player)
                        }
                    }
                    .subscribe()
            }
        }

        clientCommandManager.buildAndRegister("logout") {
            commandDescription("Logout from your account")
            handler { ctx ->
                service.logout(ctx.sender.player.identity)
                    .publishOn(MindustryScheduler)
                    .doOnError { error ->
                        logger.error("Error while logging out", error)
                        ctx.sender.sendMessage(
                            "[red]A critical error occurred in the server, please report this to the server owners.",
                        )
                    }
                    .subscribe { logged ->
                        if (logged) {
                            ctx.sender.sendMessage("You have been logged out!")
                        } else {
                            ctx.sender.sendMessage("You are not logged in!")
                        }
                    }
            }
        }
    }
}

private val USERNAME = stateKey<String>("username")
private val PASSWORD = stateKey<String>("password")

fun createLoginInterface(plugin: MindustryPlugin, service: AccountService): Interface {
    val usernameInterface = TextInputInterface.create(plugin)
    val passwordInterface = TextInputInterface.create(plugin)

    usernameInterface.addTransformer { view, pane ->
        pane.title = "Login (1/2)"
        pane.description = "Enter your username"
        pane.placeholder = view.state[USERNAME] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[USERNAME] = value
            passwordInterface.open(view)
        }
    }

    passwordInterface.addTransformer { view, pane ->
        pane.title = "Login (2/2)"
        pane.description = "Enter your password"
        pane.placeholder = view.state[PASSWORD] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[PASSWORD] = value
            service.login(view.state[USERNAME]!!, value.toCharArray(), view.viewer.identity)
                .publishOn(MindustryScheduler)
                .doOnSuccess { view.viewer.sendMessage("You have been logged in!") }
                .onAccountErrorResume(view)
                .subscribe()
        }
    }

    return usernameInterface
}

fun createRegisterInterface(plugin: MindustryPlugin, service: AccountService): Interface {
    val usernameInterface = TextInputInterface.create(plugin)
    val initialPasswordInterface = TextInputInterface.create(plugin)
    val confirmPasswordInterface = TextInputInterface.create(plugin)

    usernameInterface.addTransformer { view, pane ->
        pane.title = "Register (1/3)"
        pane.description = "Enter your username"
        pane.placeholder = view.state[USERNAME] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[USERNAME] = value
            initialPasswordInterface.open(view)
        }
    }

    initialPasswordInterface.addTransformer { view, pane ->
        pane.title = "Register (2/3)"
        pane.description = "Enter your password"
        pane.placeholder = view.state[PASSWORD] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[PASSWORD] = value
            confirmPasswordInterface.open(view)
        }
    }

    confirmPasswordInterface.addTransformer { view, pane ->
        pane.title = "Register (3/3)"
        pane.description = "Confirm your password"
        pane.inputAction = BiAction { _, value ->
            view.close()
            if (value != view.state[PASSWORD]) {
                Action.back().accept(view)
                Call.infoMessage(view.viewer.con, "[red]Passwords do not match")
                return@BiAction
            }
            service.register(view.state[USERNAME]!!, value.toCharArray())
                .publishOn(MindustryScheduler)
                .doOnSuccess { view.viewer.sendMessage("Your have been created! You can do /login now") }
                .onAccountErrorResume(view)
                .subscribe()
        }
    }

    return usernameInterface
}

private fun Mono<Void>.onAccountErrorResume(view: View) = onErrorResume { error ->
    if (error !is AccountException) {
        logger.error("An error occurred in a account interface", error)
        return@onErrorResume Mono.fromRunnable {
            Call.infoMessage(
                view.viewer.con,
                "[red]A critical error occurred in the server, please report this to the server owners.",
            )
        }
    }
    val message = when (error) {
        is AccountException.AlreadyRegistered -> "You are already registered! Use /login to login."
        is AccountException.NotRegistered -> "You are not registered! Use /register to register."
        is AccountException.NotLogged -> "You are not logged in! Use /login to login."
        is AccountException.WrongPassword -> "Wrong password! Try again."
        is AccountException.InvalidPassword ->
            "The password does not meet the requirements:\n - ${error.missing.joinToString("\n - ")}"
        is AccountException.InvalidUsername ->
            "The username does not meet the requirements:\n - ${error.missing.joinToString("\n - ")}"
    }

    Mono.fromRunnable<Void> {
        view.open()
        view.viewer.showInfoMessage("[red]$message")
    }
        .subscribeOn(MindustryScheduler)
}

private val Player.identity: PlayerIdentity get() = PlayerIdentity(uuid(), usid())
private fun Player.showInfoMessage(message: String) = Call.infoMessage(con, message)
