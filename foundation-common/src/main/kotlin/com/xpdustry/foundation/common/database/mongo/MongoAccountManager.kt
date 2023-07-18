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
package com.xpdustry.foundation.common.database.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.foundation.common.database.model.Account
import com.xpdustry.foundation.common.database.model.AccountManager
import com.xpdustry.foundation.common.database.model.MindustryUUID
import com.xpdustry.foundation.common.misc.then
import com.xpdustry.foundation.common.misc.toValueMono
import org.bson.types.ObjectId
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MongoAccountManager(collection: MongoCollection<Account>) : MongoEntityManager<Account, ObjectId>(collection), AccountManager {

    override fun findByUuid(uuid: MindustryUUID): Mono<Account> =
        collection.find(Filters.`in`("uuids", uuid)).toValueMono()

    override fun findByHashedUsername(hashedUsername: String): Mono<Account> =
        collection.find(Filters.eq("hashed_username", hashedUsername)).toValueMono()

    override fun deleteById(id: ObjectId): Mono<Void> = super.deleteById(id)
        .then { deleteFriendFromAll(id) }

    override fun deleteAll(entities: Iterable<Account>): Mono<Void> = super.deleteAll(entities)
        .then { Flux.fromIterable(entities).map(Account::id).flatMap(::deleteFriendFromAll).then() }

    private fun deleteFriendFromAll(id: ObjectId): Mono<Void> =
        collection.updateMany(Filters.`in`("friends", id), Updates.pull("friends", id)).toValueMono().then()
}
