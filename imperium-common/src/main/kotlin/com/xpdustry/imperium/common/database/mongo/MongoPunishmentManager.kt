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
package com.xpdustry.imperium.common.database.mongo

import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.imperium.common.database.MindustryUUID
import com.xpdustry.imperium.common.database.Punishment
import com.xpdustry.imperium.common.database.PunishmentManager
import com.xpdustry.imperium.common.misc.toValueFlux
import org.bson.types.ObjectId
import reactor.core.publisher.Flux
import java.net.InetAddress

class MongoPunishmentManager(collection: MongoCollection<Punishment>) : MongoEntityManager<Punishment, ObjectId>(collection), PunishmentManager {
    override fun findAllByTargetAddress(target: InetAddress): Flux<Punishment> =
        collection.find(Filters.eq("target_address", target)).toValueFlux()
    override fun findAllByTargetUuid(target: MindustryUUID): Flux<Punishment> =
        collection.find(Filters.eq("target_uuid", target)).toValueFlux()
}
