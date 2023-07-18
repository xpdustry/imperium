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
package com.xpdustry.foundation.common.database.model

import com.xpdustry.foundation.common.database.Entity
import com.xpdustry.foundation.common.hash.Hash
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.bson.types.ObjectId
import java.time.Instant

typealias HashedSessionToken = String

data class Account(
    override val id: ObjectId = ObjectId(),
    val uuids: MutableSet<MindustryUUID> = mutableSetOf(),
    var password: Hash,
    val hashedUsername: String? = null,
    var rank: Rank = Rank.NEWBIE,
    var steam: Long? = null,
    var discord: Long? = null,
    val sessions: MutableMap<HashedSessionToken, Instant> = mutableMapOf(),
    val friends: MutableMap<String, FriendData> = mutableMapOf(),
) : Entity<ObjectId> {
    constructor(uuid: MindustryUUID, password: Hash) : this(uuids = mutableSetOf(uuid), password = password)

    @get:BsonIgnore
    val legacy: Boolean get() = hashedUsername != null

    @get:BsonIgnore
    val verified: Boolean get() = steam != null || discord != null

    enum class Rank {
        NEWBIE,
        ACTIVE,
        HYPER_ACTIVE,
        OVERSEER,
        MODERATOR,
        ADMINISTRATOR,
        OWNER,
    }
}

data class FriendData(val status: Status) {
    enum class Status {
        REQUEST,
        ACCEPTED,
    }
}
