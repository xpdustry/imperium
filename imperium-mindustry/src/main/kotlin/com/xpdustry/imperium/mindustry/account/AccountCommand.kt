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

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.buildCache
import com.xpdustry.imperium.common.security.VerificationMessage
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.misc.showInfoMessage
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val accounts = instances.get<AccountManager>()
    private val users = instances.get<UserManager>()
    private val messenger = instances.get<Messenger>()
    private val login = LoginWindow(instances.get(), accounts)
    private val register = RegisterWindow(instances.get(), accounts)
    private val changePassword = ChangePasswordWindow(instances.get(), accounts)
    private val verifications = buildCache<Int, Int> { expireAfterWrite(10.minutes.toJavaDuration()) }

    @ImperiumCommand(["login"])
    @ClientSide
    suspend fun onLoginCommand(sender: CommandSender) {
        val account = accounts.selectBySession(sender.player.sessionKey)
        val remember = users.getSetting(sender.player.uuid(), User.Setting.REMEMBER_LOGIN)
        runMindustryThread {
            if (account == null) {
                val window = login.create(sender.player)
                window.state[REMEMBER_LOGIN_WARNING] = !remember
                window.show()
            } else {
                handleAccountResult(AccountResult.AlreadyLogged, sender.player)
            }
        }
    }

    @ImperiumCommand(["register"])
    @ClientSide
    fun onRegisterCommand(sender: CommandSender) {
        register.create(sender.player).show()
    }

    @ImperiumCommand(["logout"])
    @ClientSide
    suspend fun onLogoutCommand(sender: CommandSender) {
        if (accounts.selectBySession(sender.player.sessionKey) == null) {
            sender.player.sendMessage("You are not logged in!")
        } else {
            accounts.logout(sender.player.sessionKey)
            sender.player.admin = false
            sender.player.sendMessage("You have been logged out!")
            runMindustryThread { Distributor.get().eventBus.post(PlayerLogoutEvent(sender.player)) }
        }
    }

    @ImperiumCommand(["change-password"])
    @ClientSide
    fun onChangePasswordCommand(sender: CommandSender) {
        changePassword.create(sender.player).show()
    }

    @ImperiumCommand(["verify"])
    @ClientSide
    suspend fun onVerifyCommand(sender: CommandSender) {
        val account = accounts.selectBySession(sender.player.sessionKey)
        if (account == null) {
            sender.error("You are not logged in!")
            return
        } else if (account.discord != null) {
            sender.error("Your account is already discord verified.")
            return
        }

        var code: Int? = verifications.getIfPresent(account.id)
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
            }
        }
    }
}
