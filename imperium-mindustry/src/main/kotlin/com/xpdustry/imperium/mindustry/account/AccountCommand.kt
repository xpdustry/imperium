/*
 * Imperium, the software collection powering the Xpdustry network.
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
package com.xpdustry.imperium.mindustry.account

import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.kotlin.extension.buildAndRegister
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.doOnEmpty
import com.xpdustry.imperium.common.misc.toErrorMono
import com.xpdustry.imperium.common.service.AccountException
import com.xpdustry.imperium.common.service.AccountService
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.misc.MindustryScheduler
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import mindustry.gen.Call
import mindustry.gen.Player
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

// TODO Replace sequential interfaces with a proper form interface

private val logger = LoggerFactory.getLogger(AccountCommand::class.java)

private val USERNAME = stateKey<String>("username")
private val PASSWORD = stateKey<String>("password")
private val OLD_USERNAME = stateKey<String>("old_username")
private val OLD_PASSWORD = stateKey<String>("old_password")

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val plugin: MindustryPlugin = instances.get()
    private val service: AccountService = instances.get()
    private val clientCommandManager: ImperiumPluginCommandManager = instances.get("client")

    override fun onImperiumInit() {
        val loginInterface = createLoginInterface(plugin, service)
        clientCommandManager.buildAndRegister("login") {
            commandDescription("Login to your account")
            argument(StringArgument.optional("username", StringArgument.StringMode.GREEDY))
            handler { ctx ->
                service.findAccountByIdentity(ctx.sender.player.identity)
                    .publishOn(MindustryScheduler)
                    .doOnEmpty { loginInterface.open(ctx.sender.player) }
                    .onAccountErrorResume(ctx.sender.player)
                    .subscribe { ctx.sender.player.showInfoMessage("You are already logged in!") }
            }
        }

        val registerInterface = createRegisterInterface(plugin, service)
        clientCommandManager.buildAndRegister("register") {
            commandDescription("Register your account")
            handler { ctx -> registerInterface.open(ctx.sender.player) }
        }

        val migrateInterface = createMigrateInterface(plugin, service)
        clientCommandManager.buildAndRegister("migrate") {
            commandDescription("Migrate your CN account")
            handler { ctx -> migrateInterface.open(ctx.sender.player) }
        }

        clientCommandManager.buildAndRegister("logout") {
            commandDescription("Logout from your account")
            handler { ctx ->
                service.logout(ctx.sender.player.identity)
                    .publishOn(MindustryScheduler)
                    .onAccountErrorResume(ctx.sender.player)
                    .subscribe { logged ->
                        if (logged) {
                            ctx.sender.sendMessage("You have been logged out!")
                        } else {
                            ctx.sender.sendMessage("You are not logged in!")
                        }
                    }
            }
        }

        val changePasswordInterface = createPasswordChangeInterface(plugin, service)
        clientCommandManager.buildAndRegister("change-password") {
            commandDescription("Change your password")
            handler { ctx -> changePasswordInterface.open(ctx.sender.player) }
        }
    }
}

private fun createLoginInterface(plugin: MindustryPlugin, service: AccountService): Interface {
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
                .onErrorResume { error ->
                    // According to log, it's better to not let the client know if the username is incorrect
                    if (error is AccountException.WrongPassword || error is AccountException.NotRegistered) {
                        view.open()
                        view.viewer.showInfoMessage("The username or password is incorrect!")
                        return@onErrorResume Mono.empty()
                    }
                    error.toErrorMono()
                }
                .onAccountErrorResume(view)
                .subscribe()
        }
    }

    return usernameInterface
}

private fun createRegisterInterface(plugin: MindustryPlugin, service: AccountService): Interface {
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
                view.back()
                view.viewer.showInfoMessage("[red]Passwords do not match")
                return@BiAction
            }
            service.register(view.state[USERNAME]!!, value.toCharArray(), view.viewer.identity)
                .publishOn(MindustryScheduler)
                .doOnSuccess { view.viewer.sendMessage("Your account have been created! You can do /login now.") }
                .onAccountErrorResume(view)
                .subscribe()
        }
    }

    return usernameInterface
}

fun createMigrateInterface(plugin: MindustryPlugin, service: AccountService): Interface {
    val oldUsernameInterface = TextInputInterface.create(plugin)
    val oldPasswordInterface = TextInputInterface.create(plugin)
    val newUsernameInterface = TextInputInterface.create(plugin)

    oldUsernameInterface.addTransformer { view, pane ->
        pane.title = "Migrate (1/3)"
        pane.description = "Enter your old username"
        pane.placeholder = view.state[OLD_USERNAME] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[OLD_USERNAME] = value
            oldPasswordInterface.open(view)
        }
    }

    oldPasswordInterface.addTransformer { view, pane ->
        pane.title = "Migrate (2/3)"
        pane.description = "Enter your old password"
        pane.placeholder = view.state[PASSWORD] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[PASSWORD] = value
            newUsernameInterface.open(view)
        }
    }

    newUsernameInterface.addTransformer { view, pane ->
        pane.title = "Migrate (3/3)"
        pane.description = "Enter your new username"
        pane.placeholder = view.state[USERNAME] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[USERNAME] = value
            service.migrate(view.state[OLD_USERNAME]!!, value, view.state[PASSWORD]!!.toCharArray(), view.viewer.identity)
                .publishOn(MindustryScheduler)
                .doOnSuccess { view.viewer.sendMessage("Your account have been migrated! You can do /login now.") }
                .onAccountErrorResume(view)
                .subscribe()
        }
    }

    return oldUsernameInterface
}

private fun createPasswordChangeInterface(plugin: MindustryPlugin, service: AccountService): Interface {
    val oldPasswordInterface = TextInputInterface.create(plugin)
    val newPasswordInterface = TextInputInterface.create(plugin)
    val confirmPasswordInterface = TextInputInterface.create(plugin)

    oldPasswordInterface.addTransformer { view, pane ->
        pane.title = "Change Password (1/3)"
        pane.description = "Enter your old password"
        pane.placeholder = view.state[OLD_PASSWORD] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[OLD_PASSWORD] = value
            newPasswordInterface.open(view)
        }
    }

    newPasswordInterface.addTransformer { view, pane ->
        pane.title = "Change Password (2/3)"
        pane.description = "Enter your new password"
        pane.placeholder = view.state[PASSWORD] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[PASSWORD] = value
            confirmPasswordInterface.open(view)
        }
    }

    confirmPasswordInterface.addTransformer { view, pane ->
        pane.title = "Change Password (3/3)"
        pane.description = "Confirm your new password"
        pane.inputAction = BiAction { _, value ->
            view.close()
            if (value != view.state[PASSWORD]) {
                view.back()
                view.viewer.showInfoMessage("[red]Passwords do not match")
                return@BiAction
            }
            service.changePassword(view.state[OLD_PASSWORD]!!.toCharArray(), value.toCharArray(), view.viewer.identity)
                .publishOn(MindustryScheduler)
                .doOnSuccess { view.viewer.showInfoMessage("Your password have been changed!") }
                .onAccountErrorResume(view)
                .subscribe()
        }
    }

    return oldPasswordInterface
}

private fun <T> Mono<T>.onAccountErrorResume(player: Player) = onAccountErrorResume0(player, null)
private fun <T> Mono<T>.onAccountErrorResume(view: View) = onAccountErrorResume0(view.viewer, view)

private fun <T> Mono<T>.onAccountErrorResume0(player: Player, view: View?) = onErrorResume { error ->
    if (error !is AccountException) {
        logger.error("An error occurred in a account interface", error)
        return@onErrorResume Mono.fromRunnable {
            view?.closeAll()
            player.showInfoMessage(
                "[red]A critical error occurred in the server, please report this to the server owners.",
            )
        }
    }
    val message = when (error) {
        is AccountException.AlreadyRegistered -> "This account is already registered!"
        is AccountException.NotRegistered -> "You are not registered!"
        is AccountException.NotLogged -> "You are not logged in! Use /login to login."
        is AccountException.WrongPassword -> "Wrong password!"
        is AccountException.InvalidPassword ->
            "The password does not meet the requirements:\n - ${error.missing.joinToString("\n - ")}"
        is AccountException.InvalidUsername ->
            "The username does not meet the requirements:\n - ${error.missing.joinToString("\n - ")}"
        is AccountException.RateLimit ->
            "You have made too many attempts, please try again later."
    }

    Mono.fromRunnable<T> {
        view?.open()
        player.showInfoMessage("[red]$message")
    }
        .subscribeOn(MindustryScheduler)
}

private fun Player.showInfoMessage(message: String) = Call.infoMessage(con, message)
