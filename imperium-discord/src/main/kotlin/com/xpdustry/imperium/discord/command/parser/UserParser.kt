/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.discord.command.parser

import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import org.incendo.cloud.caption.Caption
import org.incendo.cloud.caption.CaptionVariable
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.exception.parsing.ParserException
import org.incendo.cloud.kotlin.coroutines.SuspendingArgumentParser
import org.incendo.cloud.parser.ArgumentParseResult

class UserParser<C : Any>(private val users: UserManager) : SuspendingArgumentParser<C, User> {

    override suspend fun invoke(
        commandContext: CommandContext<C>,
        commandInput: CommandInput
    ): ArgumentParseResult<User> {
        val input: String
        val user =
            if (commandInput.isValidLong(0, Long.MAX_VALUE)) {
                val snowflake = commandInput.readLong()
                input = snowflake.toString()
                users.findBySnowflake(snowflake)
            } else {
                input = commandInput.readString()
                users.findByUuid(input)
            }
        return if (user == null) {
            ArgumentParseResult.failure(UserParserException(commandContext, input))
        } else {
            ArgumentParseResult.success(user)
        }
    }

    private class UserParserException(ctx: CommandContext<*>, val input: String) :
        ParserException(UserParser::class.java, ctx, CAPTION, CaptionVariable.of("input", input)) {
        companion object {
            private val CAPTION = Caption.of("argument.parse.failure.imperium_user")
        }
    }
}
