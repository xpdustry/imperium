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
import com.xpdustry.foundation.common.database.model.AccountOperationResult
import com.xpdustry.foundation.common.database.model.AccountService
import com.xpdustry.foundation.common.database.model.SessionToken
import com.xpdustry.foundation.common.misc.switchIfEmpty
import com.xpdustry.foundation.mindustry.command.FoundationPluginCommandManager
import com.xpdustry.foundation.mindustry.misc.MindustryScheduler
import com.xpdustry.foundation.mindustry.ui.Interface
import com.xpdustry.foundation.mindustry.ui.View
import com.xpdustry.foundation.mindustry.ui.action.BiAction
import com.xpdustry.foundation.mindustry.ui.input.TextInputInterface
import com.xpdustry.foundation.mindustry.ui.state.stateKey
import com.xpdustry.foundation.mindustry.verification.VerificationPipeline
import com.xpdustry.foundation.mindustry.verification.VerificationResult
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import fr.xpdustry.distributor.api.util.Priority
import jakarta.inject.Named
import mindustry.gen.Player
import reactor.core.publisher.Mono

// TODO
//  - Add error handling to the monos
//  - Add rate limiting
//  - Add password reset and update
class AccountListener @Inject constructor(
    private val plugin: MindustryPlugin,
    private val service: AccountService,
    private val verificationPipeline: VerificationPipeline,
    @param:Named("client") private val clientCommandManager: FoundationPluginCommandManager,
) : FoundationListener {
    override fun onFoundationInit() {
        // NOTE:
        //   Small hack to make sure a player session is refreshed when it joins the server,
        //   instead of blocking the process in a PlayerConnectionConfirmed event listener
        verificationPipeline.register("account", Priority.LOWEST) {
            service.refresh(SessionToken(it.uuid, it.usid)).thenReturn(VerificationResult.Success)
        }

        val loginInterface = createLoginInterface(plugin, service)

        // TODO: Implement RateLimiter
        clientCommandManager.buildAndRegister("login") {
            commandDescription("Login to your account")
            argument(StringArgument.optional("username", StringArgument.StringMode.GREEDY))
            handler { ctx ->
                service.findAccountBySession(ctx.sender.player.token)
                    .publishOn(MindustryScheduler)
                    .doOnNext {
                        ctx.sender.sendMessage("You are already logged in!")
                    }
                    .switchIfEmpty {
                        Mono.fromRunnable {
                            loginInterface.open(ctx.sender.player) { state ->
                                if (ctx.contains("username")) {
                                    state[USERNAME] = ctx.get("username")
                                }
                            }
                        }
                    }
                    .subscribe()
            }
        }

        val registerInterface = createRegisterInterface(plugin, service)

        clientCommandManager.buildAndRegister("register") {
            commandDescription("Register your account")
            handler { ctx ->
                service.findAccountBySession(ctx.sender.player.token)
                    .publishOn(MindustryScheduler)
                    .doOnNext {
                        ctx.sender.sendMessage("You are already registered in!")
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
                service.logout(ctx.sender.player.token)
                    .publishOn(MindustryScheduler)
                    .subscribe { result ->
                        if (result) {
                            ctx.sender.sendMessage("You have been logged out!")
                        } else {
                            ctx.sender.sendMessage("You are not logged in!")
                        }
                    }
            }
        }
    }
}

private val ERROR_MESSAGE = stateKey<String>("error_message")
private val USERNAME = stateKey<String>("username")

fun createLoginInterface(plugin: MindustryPlugin, service: AccountService): Interface {
    val inter = TextInputInterface.create(plugin)
    inter.addTransformer { view, pane ->
        pane.title = "Password"
        pane.description = "Your password"
        if (ERROR_MESSAGE in view.state) {
            pane.description += "\n[red]${view.state[ERROR_MESSAGE]}"
        }
        pane.inputAction = BiAction { _, value ->
            // We will do some async stuff, so we close the interface for now
            view.close()
            val login = if (USERNAME in view.state) {
                service.login(view.viewer.token, view.state[USERNAME]!!, value.trim().toCharArray())
            } else {
                service.login(view.viewer.token, value.trim().toCharArray())
            }
            login.publishOn(MindustryScheduler).subscribe { result ->
                handleAccountResult(view, result) {
                    view.viewer.sendMessage("You have been logged in!")
                }
            }
        }
    }
    return inter
}

fun createRegisterInterface(plugin: MindustryPlugin, service: AccountService): Interface {
    val inter = TextInputInterface.create(plugin)
    inter.addTransformer { view, pane ->
        pane.title = "Password"
        pane.description = "Your password"
        if (ERROR_MESSAGE in view.state) {
            pane.description += "\n[red]${view.state[ERROR_MESSAGE]}"
        }
        pane.inputAction = BiAction { _, value ->
            // We will do some async stuff, so we close the interface for now
            view.close()
            service.register(view.viewer.token, value.trim().toCharArray())
                .publishOn(MindustryScheduler)
                .subscribe { result ->
                    handleAccountResult(view, result) {
                        view.viewer.sendMessage("You have been registered!")
                    }
                }
        }
    }
    return inter
}

fun handleAccountResult(view: View, result: AccountOperationResult, success: Runnable) {
    if (result == AccountOperationResult.Success) {
        success.run()
        view.close()
        return
    }

    view.state[ERROR_MESSAGE] = when (result) {
        is AccountOperationResult.AlreadyRegistered ->
            "You are already registered! Use /login to login."
        is AccountOperationResult.NotRegistered ->
            "You are not registered! Use /register to register."
        is AccountOperationResult.NotLogged ->
            "You are not logged in! Use /login to login."
        is AccountOperationResult.WrongPassword ->
            "Wrong password! Try again."
        is AccountOperationResult.InvalidPassword ->
            "The password does not meet the requirements:\n - ${result.missing.joinToString("\n - ")}"
        else ->
            "Unknown error!"
    }

    view.open()
}

val Player.token: SessionToken get() = SessionToken(uuid(), usid())
