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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.Database
import com.xpdustry.imperium.common.database.LegacyAccount
import com.xpdustry.imperium.common.database.LegacyAccountManager
import com.xpdustry.imperium.common.database.MindustryMap
import com.xpdustry.imperium.common.database.MindustryMapManager
import com.xpdustry.imperium.common.database.MindustryMapRatingManager
import com.xpdustry.imperium.common.database.Punishment
import com.xpdustry.imperium.common.database.PunishmentManager
import com.xpdustry.imperium.common.database.Rating
import com.xpdustry.imperium.common.database.User
import com.xpdustry.imperium.common.database.UserManager

internal class MongoDatabase(private val provider: MongoProvider) : Database, ImperiumApplication.Listener {

    override lateinit var users: UserManager
    override lateinit var punishments: PunishmentManager
    override lateinit var legacyAccounts: LegacyAccountManager
    override lateinit var maps: MindustryMapManager
    override lateinit var mapRatings: MindustryMapRatingManager

    override fun onImperiumInit() {
        users = MongoUserManager(provider.getCollectionLegacy("users", User::class))
        punishments = MongoPunishmentManager(provider.getCollectionLegacy("punishments", Punishment::class))
        legacyAccounts = MongoLegacyAccountManager(provider.getCollectionLegacy("accounts_legacy", LegacyAccount::class))
        maps = MongoMindustryMapManager(provider.getCollectionLegacy("maps", MindustryMap::class))
        mapRatings = MongoMindustryMapRatingManager(provider.getCollectionLegacy("map_ratings", Rating::class))
    }
}
