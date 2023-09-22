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
package com.xpdustry.imperium.common.security

import com.mongodb.client.model.Filters
import com.xpdustry.imperium.common.account.MindustryUUID
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.ImperiumSnowflake
import com.xpdustry.imperium.common.misc.ImperiumSnowflakeGenerator
import com.xpdustry.imperium.common.mongo.MongoEntityCollection
import com.xpdustry.imperium.common.mongo.MongoProvider
import kotlinx.coroutines.flow.Flow
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

internal class MongoBanManager(private val generator: ImperiumSnowflakeGenerator, private val mongo: MongoProvider, private val messenger: Messenger) : PunishmentManager, ImperiumApplication.Listener {

    private lateinit var bans: MongoEntityCollection<Punishment, ImperiumSnowflake>

    override fun onImperiumInit() {
        bans = mongo.getCollection("punishments", Punishment::class)
    }

    override suspend fun punish(author: Identity?, target: Punishment.Target, reason: String, type: Punishment.Type, duration: Duration?) {
        val punishment = Punishment(generator.generate(), target, reason, type, duration)
        bans.save(punishment)
        messenger.publish(PunishmentMessage(author, PunishmentMessage.Type.CREATE, punishment._id))
    }

    override suspend fun pardon(author: Identity?, id: ImperiumSnowflake, reason: String) {
        val punishment = findById(id) ?: return
        punishment.pardon = Punishment.Pardon(Instant.now(), reason)
        bans.save(punishment)
        messenger.publish(PunishmentMessage(author, PunishmentMessage.Type.PARDON, punishment._id))
    }

    override suspend fun findById(id: ImperiumSnowflake): Punishment? =
        bans.findById(id)

    override suspend fun findAllByAddress(target: InetAddress): Flow<Punishment> =
        bans.find(Filters.eq("target.address", target))

    override suspend fun findAllByUuid(uuid: MindustryUUID): Flow<Punishment> =
        bans.find(Filters.eq("target.uuid", uuid))
}
