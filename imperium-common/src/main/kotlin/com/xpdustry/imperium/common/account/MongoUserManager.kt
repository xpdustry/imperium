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
package com.xpdustry.imperium.common.account

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.mongo.MongoEntityCollection
import com.xpdustry.imperium.common.database.mongo.MongoProvider
import com.xpdustry.imperium.common.misc.MindustryUUID
import java.net.InetAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId

internal class MongoUserManager(private val mongo: MongoProvider) :
    UserManager, ImperiumApplication.Listener {

    private lateinit var users: MongoEntityCollection<User, ObjectId>

    override fun onImperiumInit() {
        users = mongo.getCollection("users", User::class)
        runBlocking {
            users.index(Indexes.text(User::names.name)) { name("names_text_index") }
            users.index(Indexes.hashed(User::uuid.name)) { name("uuid_index") }
        }
    }

    override suspend fun findById(id: ObjectId): User? = users.findById(id)

    override suspend fun findByUuidOrCreate(uuid: MindustryUUID): User =
        findByUuid(uuid) ?: User(uuid)

    override suspend fun findByUuid(uuid: MindustryUUID): User? =
        users.find(Filters.eq(User::uuid.name, uuid)).firstOrNull()

    override suspend fun findByLastAddress(address: InetAddress): User? =
        users.find(Filters.eq(User::lastAddress.name, address)).firstOrNull()

    override suspend fun searchUser(query: String): Flow<User> =
        users.find(Filters.text(query)).sort(Sorts.ascending(User::lastName.name))

    override suspend fun updateOrCreateByUuid(
        uuid: MindustryUUID,
        updater: suspend (User) -> Unit
    ) {
        val user = findByUuidOrCreate(uuid)
        updater(user)
        users.save(user)
    }
}
