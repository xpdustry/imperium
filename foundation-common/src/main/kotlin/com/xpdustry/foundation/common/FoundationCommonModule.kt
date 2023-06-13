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
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.config.FoundationConfigProvider
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.database.mongo.MongoDatabase
import com.xpdustry.foundation.common.message.Messenger
import com.xpdustry.foundation.common.message.RabbitmqMessenger
import com.xpdustry.foundation.common.network.AddressInfoProvider
import com.xpdustry.foundation.common.network.Discovery
import com.xpdustry.foundation.common.network.IPHubAddressInfoProvider
import com.xpdustry.foundation.common.network.SimpleDiscovery
import com.xpdustry.foundation.common.translator.DeeplTranslator
import com.xpdustry.foundation.common.translator.Translator

class FoundationCommonModule : KotlinAbstractModule() {
    override fun configure() {
        // Database
        bind(Database::class)
            .implementation(MongoDatabase::class)
            .singleton()

        // Translation
        bind(Translator::class)
            .implementation(DeeplTranslator::class)
            .singleton()

        // Networking
        bind(Discovery::class)
            .implementation(SimpleDiscovery::class)
            .singleton()

        bind(AddressInfoProvider::class)
            .implementation(IPHubAddressInfoProvider::class)
            .singleton()

        // Messaging
        bind(Messenger::class)
            .implementation(RabbitmqMessenger::class)
            .singleton()

        // Config
        bind(FoundationConfig::class)
            .provider(FoundationConfigProvider::class)
            .singleton()
    }
}
