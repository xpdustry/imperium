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
package com.xpdustry.foundation.common

import com.xpdustry.foundation.common.application.KotlinAbstractModule
import com.xpdustry.foundation.common.database.model.PunishmentManager
import com.xpdustry.foundation.common.database.model.UserManager
import com.xpdustry.foundation.common.database.mongo.MongoProvider
import com.xpdustry.foundation.common.database.mongo.MongoPunishmentManager
import com.xpdustry.foundation.common.database.mongo.MongoUserManager
import com.xpdustry.foundation.common.message.Messenger
import com.xpdustry.foundation.common.message.MongoMessenger
import com.xpdustry.foundation.common.network.AddressInfoProvider
import com.xpdustry.foundation.common.network.Discovery
import com.xpdustry.foundation.common.network.IPHubAddressInfoProvider
import com.xpdustry.foundation.common.network.SimpleDiscovery
import com.xpdustry.foundation.common.translator.DeeplTranslator
import com.xpdustry.foundation.common.translator.Translator

object FoundationCommonModule : KotlinAbstractModule() {
    override fun configure() {
        // Mongo
        bind(MongoProvider::class).toClass(MongoProvider::class).singleton()
        bind(UserManager::class).toClass(MongoUserManager::class).singleton()
        bind(PunishmentManager::class).toClass(MongoPunishmentManager::class).singleton()

        // Translation
        bind(Translator::class).toClass(DeeplTranslator::class).singleton()

        // Networking
        bind(Discovery::class).toClass(SimpleDiscovery::class).singleton()
        bind(AddressInfoProvider::class).toClass(IPHubAddressInfoProvider::class).singleton()

        // Messaging
        bind(Messenger::class).toClass(MongoMessenger::class).singleton()
    }
}
