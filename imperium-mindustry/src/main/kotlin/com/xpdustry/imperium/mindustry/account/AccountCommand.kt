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
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.database.AccountManager
import com.xpdustry.imperium.common.database.AccountOperationResult
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import fr.xpdustry.distributor.api.plugin.MindustryPlugin
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import mindustry.gen.Player
import kotlin.coroutines.CoroutineContext

// TODO
//  - Replace sequential interfaces with a proper form interface
//  - Add rate limit warning BEFORE running the command
private val logger = logger<AccountCommand>()

private val USERNAME = stateKey<String>("username")
private val PASSWORD = stateKey<String>("password")
private val OLD_USERNAME = stateKey<String>("old_username")
private val OLD_PASSWORD = stateKey<String>("old_password")

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val plugin: MindustryPlugin = instances.get()
    private val manager: AccountManager = instances.get()
    private val clientCommandManager: ImperiumPluginCommandManager = instances.get("client")

    override fun onImperiumInit() {
        val loginInterface = createLoginInterface(plugin, manager)
        clientCommandManager.buildAndRegister("login") {
            commandDescription("Login to your account")
            argument(StringArgument.optional("username", StringArgument.StringMode.GREEDY))
            handler { ctx ->
                ImperiumScope.MAIN.launch(PlayerCoroutineExceptionHandler(ctx.sender.player)) {
                    val account = manager.findAccountByIdentity(ctx.sender.player.identity)
                    if (account == null) {
                        runMindustryThread {
                            loginInterface.open(ctx.sender.player)
                        }
                    } else {
                        ctx.sender.player.showInfoMessage("You are already logged in!")
                    }
                }
            }
        }

        val registerInterface = createRegisterInterface(plugin, manager)
        clientCommandManager.buildAndRegister("register") {
            commandDescription("Register your account")
            handler { ctx -> registerInterface.open(ctx.sender.player) }
        }

        val migrateInterface = createMigrateInterface(plugin, manager)
        clientCommandManager.buildAndRegister("migrate") {
            commandDescription("Migrate your CN account")
            handler { ctx -> migrateInterface.open(ctx.sender.player) }
        }

        clientCommandManager.buildAndRegister("logout") {
            commandDescription("Logout from your account")
            handler { ctx ->
                ImperiumScope.MAIN.launch(PlayerCoroutineExceptionHandler(ctx.sender.player)) {
                    if (manager.findAccountByIdentity(ctx.sender.player.identity) == null) {
                        ctx.sender.sendMessage("You are not logged in!")
                    } else {
                        manager.logout(ctx.sender.player.identity)
                        ctx.sender.sendMessage("You have been logged out!")
                    }
                }
            }
        }

        val changePasswordInterface = createPasswordChangeInterface(plugin, manager)
        clientCommandManager.buildAndRegister("change-password") {
            commandDescription("Change your password")
            handler { ctx -> changePasswordInterface.open(ctx.sender.player) }
        }
    }
}

private class PlayerCoroutineExceptionHandler(private val player: Player, private val view: View? = null) : CoroutineExceptionHandler {
    constructor(view: View) : this(view.viewer, view)
    override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        logger.error("An error occurred in a account interface", exception)
        view?.closeAll()
        player.showInfoMessage("[red]A critical error occurred in the server, please report this to the server owners.")
    }
}

private fun createLoginInterface(plugin: MindustryPlugin, manager: AccountManager): Interface {
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
            ImperiumScope.MAIN.launch(PlayerCoroutineExceptionHandler(view)) {
                when (val result = manager.login(view.state[USERNAME]!!, value.toCharArray(), view.viewer.identity)) {
                    is AccountOperationResult.Success -> {
                        view.viewer.sendMessage("You have been logged in!")
                    }
                    is AccountOperationResult.WrongPassword, AccountOperationResult.NotRegistered -> {
                        view.open()
                        view.viewer.showInfoMessage("The username or password is incorrect!")
                    }
                    else -> {
                        handleAccountResult(result, view)
                    }
                }
            }
        }
    }

    return usernameInterface
}

private fun createRegisterInterface(plugin: MindustryPlugin, manager: AccountManager): Interface {
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
            ImperiumScope.MAIN.launch(PlayerCoroutineExceptionHandler(view)) {
                when (val result = manager.register(view.state[USERNAME]!!, value.toCharArray(), view.viewer.identity)) {
                    is AccountOperationResult.Success -> {
                        view.viewer.sendMessage("Your account have been created! You can do /login now.")
                    }
                    else -> {
                        handleAccountResult(result, view)
                    }
                }
            }
        }
    }

    return usernameInterface
}

fun createMigrateInterface(plugin: MindustryPlugin, manager: AccountManager): Interface {
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
            ImperiumScope.MAIN.launch(PlayerCoroutineExceptionHandler(view)) {
                when (val result = manager.migrate(view.state[OLD_USERNAME]!!, value, view.state[PASSWORD]!!.toCharArray(), view.viewer.identity)) {
                    is AccountOperationResult.Success -> {
                        view.viewer.sendMessage("Your account have been migrated! You can do /login now.")
                    }
                    else -> {
                        handleAccountResult(result, view)
                    }
                }
            }
        }
    }

    return oldUsernameInterface
}

private fun createPasswordChangeInterface(plugin: MindustryPlugin, manager: AccountManager): Interface {
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
            ImperiumScope.MAIN.launch(PlayerCoroutineExceptionHandler(view)) {
                when (val result = manager.changePassword(view.state[OLD_PASSWORD]!!.toCharArray(), value.toCharArray(), view.viewer.identity)) {
                    is AccountOperationResult.Success -> {
                        view.viewer.sendMessage("Your password have been changed!")
                    }
                    else -> {
                        handleAccountResult(result, view)
                    }
                }
            }
        }
    }

    return oldPasswordInterface
}

private suspend fun handleAccountResult(result: AccountOperationResult, view: View) = runMindustryThread {
    val message = when (result) {
        is AccountOperationResult.Success -> "Success!"
        is AccountOperationResult.AlreadyRegistered -> "This account is already registered!"
        is AccountOperationResult.NotRegistered -> "You are not registered!"
        is AccountOperationResult.NotLogged -> "You are not logged in! Use /login to login."
        is AccountOperationResult.WrongPassword -> "Wrong password!"
        is AccountOperationResult.InvalidPassword ->
            "The password does not meet the requirements:\n - ${result.missing.joinToString("\n - ")}"
        is AccountOperationResult.InvalidUsername ->
            "The username does not meet the requirements:\n - ${result.missing.joinToString("\n - ")}"
        is AccountOperationResult.RateLimit ->
            "You have made too many attempts, please try again later."
    }
    view.open()
    view.viewer.showInfoMessage("[red]$message")
}
