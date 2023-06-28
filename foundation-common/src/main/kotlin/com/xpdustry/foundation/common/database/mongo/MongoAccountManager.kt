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

import com.mongodb.reactivestreams.client.MongoCollection
import com.xpdustry.foundation.common.database.model.Account
import com.xpdustry.foundation.common.database.model.AccountManager
import com.xpdustry.foundation.common.misc.toValueMono
import org.bson.types.ObjectId
import org.litote.kmongo.contains
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.findOne
import reactor.core.publisher.Mono

class MongoAccountManager(collection: MongoCollection<Account>) : MongoEntityManager<Account, ObjectId>(collection), AccountManager {

    override fun findByUuid(uuid: String): Mono<Account> =
        collection.findOne(Account::uuids contains uuid).toValueMono()

    override fun findByHashedUsername(hashedUsername: String): Mono<Account> =
        collection.findOne(Account::hashedUsername eq hashedUsername).toValueMono()
}
