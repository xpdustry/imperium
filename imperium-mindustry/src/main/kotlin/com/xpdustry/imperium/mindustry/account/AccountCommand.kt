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
package com.xpdustry.imperium.mindustry.account

import com.google.common.cache.CacheBuilder
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.security.PasswordRequirement
import com.xpdustry.imperium.common.security.UsernameRequirement
import com.xpdustry.imperium.common.security.VerificationMessage
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import com.xpdustry.imperium.mindustry.misc.tryGrantAdmin
import com.xpdustry.imperium.mindustry.ui.Interface
import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.action.BiAction
import com.xpdustry.imperium.mindustry.ui.input.TextInputInterface
import com.xpdustry.imperium.mindustry.ui.state.stateKey
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mindustry.gen.Player

// TODO
//  - Replace sequential interfaces with a proper form interface
private val logger = logger<AccountCommand>()

private val USERNAME = stateKey<String>("username")
private val PASSWORD = stateKey<String>("password")
private val OLD_USERNAME = stateKey<String>("old_username")
private val OLD_PASSWORD = stateKey<String>("old_password")

private val ACCOUNT_LOGIN_WARNING =
    """
    [red]CN HAS BEEN MIGRATED TO A COMPLETELY NEW INFRASTRUCTURE.[]
    IF YOU WANT TO LOGIN TO YOUR OLD ACCOUNT,
    MIGRATE IT FIRST USING THE [accent]/migrate[] COMMAND.
    [lightgray]THANK YOU FOR YOUR UNDERSTANDING.
    """
        .trimIndent()

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val manager = instances.get<AccountManager>()
    private val messenger = instances.get<Messenger>()
    private val loginInterface = createLoginInterface(instances.get(), manager)
    private val registerInterface = createRegisterInterface(instances.get(), manager)
    private val migrateInterface = createMigrateInterface(instances.get(), manager)
    private val changePasswordInterface = createPasswordChangeInterface(instances.get(), manager)
    private val verifications =
        CacheBuilder.newBuilder().expireAfterWrite(10.minutes.toJavaDuration()).build<Int, Int>()

    @ImperiumCommand(["login"])
    @ClientSide
    suspend fun onLoginCommand(sender: CommandSender) =
        withContext(PlayerCoroutineExceptionHandler(sender.player)) {
            val account = manager.selectBySession(sender.player.sessionKey)
            if (account == null) {
                runMindustryThread { loginInterface.open(sender.player) }
            } else {
                sender.player.showInfoMessage("You are already logged in!")
            }
        }

    @ImperiumCommand(["register"])
    @ClientSide
    fun onRegisterCommand(sender: CommandSender) {
        registerInterface.open(sender.player)
    }

    @ImperiumCommand(["migrate"])
    @ClientSide
    fun onMigrateCommand(sender: CommandSender) {
        migrateInterface.open(sender.player)
    }

    @ImperiumCommand(["logout"])
    @ClientSide
    suspend fun onLogoutCommand(sender: CommandSender) =
        withContext(PlayerCoroutineExceptionHandler(sender.player)) {
            if (manager.selectBySession(sender.player.sessionKey) == null) {
                sender.player.sendMessage("You are not logged in!")
            } else {
                manager.logout(sender.player.sessionKey)
                sender.player.admin = false
                sender.player.sendMessage("You have been logged out!")
            }
        }

    @ImperiumCommand(["change-password"])
    @ClientSide
    fun onChangePasswordCommand(sender: CommandSender) {
        changePasswordInterface.open(sender.player)
    }

    @ImperiumCommand(["verify"])
    @ClientSide
    suspend fun onVerifyCommand(sender: CommandSender) {
        val account = manager.selectBySession(sender.player.sessionKey)
        if (account == null) {
            sender.error("You are not logged in!")
            return
        } else if (account.discord != null) {
            sender.error("Your account is already discord verified.")
            return
        }

        var code: Int? = verifications.getIfPresent(sender.player.uuid())
        if (code != null) {
            sender.error(
                """
                You already have a pending verification.
                [lightgray]Remember:
                Join our discord server with the [cyan]/discord[] command.
                And run the [cyan]/verify[] command in the [accent]#bot[] channel with the code [accent]$code[].
                """
                    .trimIndent()
            )
            return
        }

        code = Random.nextInt(1000..9999)
        messenger.publish(VerificationMessage(account.id, sender.player.uuid(), sender.player.usid(), code))
        verifications.put(account.id, code)

        sender.reply(
            """
            To go forward with the verification process, join our discord server using the [cyan]/discord[].
            Then use the [cyan]/verify[] command in the [accent]#bot[] channel with the code [accent]$code[].
            The code will expire in 10 minutes.
            """
                .trimIndent()
        )
    }

    override fun onImperiumInit() {
        messenger.consumer<VerificationMessage> { message ->
            if (message.response && verifications.getIfPresent(message.account) == message.code) {
                verifications.invalidate(message.account)
                val player =
                    Entities.getPlayersAsync().find { it.uuid() == message.uuid && it.usid() == message.usid }
                        ?: return@consumer
                player.showInfoMessage("You have been verified!")
                player.tryGrantAdmin(manager)
            }
        }
    }
}

private class PlayerCoroutineExceptionHandler(private val player: Player, private val view: View? = null) :
    CoroutineExceptionHandler {
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
        pane.description = "Enter your username\n\n$ACCOUNT_LOGIN_WARNING"
        pane.placeholder = view.state[USERNAME] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[USERNAME] = value
            passwordInterface.open(view)
        }
    }

    passwordInterface.addTransformer { view, pane ->
        pane.title = "Login (2/2)"
        pane.description = "Enter your password\n\n$ACCOUNT_LOGIN_WARNING"
        pane.placeholder = view.state[PASSWORD] ?: ""
        pane.inputAction = BiAction { _, value ->
            view.close()
            view.state[PASSWORD] = value
            ImperiumScope.MAIN.launch(PlayerCoroutineExceptionHandler(view)) {
                when (val result = manager.login(view.viewer.sessionKey, view.state[USERNAME]!!, value.toCharArray())) {
                    is AccountResult.Success -> {
                        view.viewer.sendMessage("You have been logged in!")
                        view.viewer.tryGrantAdmin(manager)
                        val account = manager.selectBySession(view.viewer.sessionKey)!!
                        runMindustryThread { Distributor.get().eventBus.post(PlayerLoginEvent(view.viewer, account)) }
                    }
                    is AccountResult.WrongPassword,
                    AccountResult.NotFound -> {
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
                when (val result = manager.register(view.state[USERNAME]!!, value.toCharArray())) {
                    is AccountResult.Success -> {
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
                when (
                    val result =
                        manager.migrate(view.state[OLD_USERNAME]!!, value, view.state[PASSWORD]!!.toCharArray())
                ) {
                    is AccountResult.Success -> {
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
                val account = manager.selectBySession(view.viewer.sessionKey)!!
                when (
                    val result =
                        manager.updatePassword(
                            account.id,
                            view.state[OLD_PASSWORD]!!.toCharArray(),
                            value.toCharArray(),
                        )
                ) {
                    is AccountResult.Success -> {
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

private suspend fun handleAccountResult(result: AccountResult, view: View) = runMindustryThread {
    val message =
        when (result) {
            is AccountResult.Success -> "Success!"
            is AccountResult.AlreadyRegistered -> "This account is already registered!"
            is AccountResult.NotFound -> "You are not registered!"
            is AccountResult.WrongPassword -> "Wrong password!"
            is AccountResult.InvalidPassword ->
                "The password does not meet the requirements:\n ${result.missing.joinToString("\n- ", transform = ::getErrorMessage)}"
            is AccountResult.InvalidUsername ->
                "The username does not meet the requirements:\n ${result.missing.joinToString("\n- ", transform = ::getErrorMessage)}"
            is AccountResult.AlreadyLogged -> "You are already logged in."
        }
    view.open()
    view.viewer.showInfoMessage("[red]$message")
}

private fun getErrorMessage(requirement: PasswordRequirement) =
    when (requirement) {
        is PasswordRequirement.LowercaseLetter -> "It needs at least a lowercase letter."
        is PasswordRequirement.UppercaseLetter -> "It needs at least a uppercase letter."
        is PasswordRequirement.Number -> "It needs at least a number."
        is PasswordRequirement.Symbol -> "It needs at least a symbol."
        is PasswordRequirement.Length ->
            "It needs to be between ${requirement.min} and ${requirement.max} characters long."
    }

private fun getErrorMessage(requirement: UsernameRequirement) =
    when (requirement) {
        is UsernameRequirement.InvalidSymbol ->
            "It can only contain letters, numbers and ${requirement.allowed.joinToString()}."
        is UsernameRequirement.Length ->
            "It needs to be between ${requirement.min} and ${requirement.max} characters long."
        is UsernameRequirement.Reserved -> "This username is reserved or already taken."
        is UsernameRequirement.AllLowercase -> "Uppercase letters aren't allowed in the username."
    }
