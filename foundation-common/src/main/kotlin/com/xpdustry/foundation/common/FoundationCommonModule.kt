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

import com.xpdustry.foundation.common.application.FoundationMetadata
import com.xpdustry.foundation.common.config.FoundationConfigFactory
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.database.model.AccountService
import com.xpdustry.foundation.common.database.model.SimpleAccountService
import com.xpdustry.foundation.common.database.mongo.MongoDatabase
import com.xpdustry.foundation.common.inject.get
import com.xpdustry.foundation.common.inject.module
import com.xpdustry.foundation.common.inject.single
import com.xpdustry.foundation.common.message.Messenger
import com.xpdustry.foundation.common.message.RabbitmqMessenger
import com.xpdustry.foundation.common.network.Discovery
import com.xpdustry.foundation.common.network.IpHubVpnAddressDetector
import com.xpdustry.foundation.common.network.SimpleDiscovery
import com.xpdustry.foundation.common.network.VpnAddressDetector
import com.xpdustry.foundation.common.translator.DeeplTranslator
import com.xpdustry.foundation.common.translator.Translator

fun commonModule() = module("common") {
    single(FoundationConfigFactory())

    single<Database> {
        MongoDatabase(get(), get())
    }

    single<AccountService> {
        SimpleAccountService(get())
    }

    single<Translator> {
        DeeplTranslator(get(), get())
    }

    single<Discovery> {
        SimpleDiscovery(get(), get(), get())
    }

    single<VpnAddressDetector> {
        IpHubVpnAddressDetector(get())
    }

    single<Messenger> {
        RabbitmqMessenger(get(), get())
    }

    single {
        FoundationMetadata()
    }
}
