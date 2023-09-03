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
import com.xpdustry.imperium.common.database.MongoEntityCollection
import com.xpdustry.imperium.common.database.MongoProvider
import kotlinx.coroutines.flow.Flow
import org.bson.types.ObjectId
import java.net.InetAddress

internal class MongoPunishmentManager(private val mongo: MongoProvider) : PunishmentManager, ImperiumApplication.Listener {

    private lateinit var punishments: MongoEntityCollection<Punishment, ObjectId>

    override fun onImperiumInit() {
        punishments = mongo.getCollection("punishments", Punishment::class)
    }

    override suspend fun findAllByTargetAddress(target: InetAddress): Flow<Punishment> =
        punishments.find(Filters.eq("targetAddress", target))

    override suspend fun findAllByTargetUuid(target: MindustryUUID): Flow<Punishment> =
        punishments.find(Filters.eq("targetUuid", target))
}
