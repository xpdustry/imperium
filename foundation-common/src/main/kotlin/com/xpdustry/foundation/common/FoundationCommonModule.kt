package com.xpdustry.foundation.common

import com.xpdustry.foundation.common.database.model.PunishmentManager
import com.xpdustry.foundation.common.database.model.UserManager
import com.xpdustry.foundation.common.database.mongo.MongoProvider
import com.xpdustry.foundation.common.database.mongo.MongoPunishmentManager
import com.xpdustry.foundation.common.database.mongo.MongoUserManager
import com.xpdustry.foundation.common.inject.KotlinAbstractModule
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
        bind(MongoProvider::class).to(MongoProvider::class).singleton()
        bind(UserManager::class).to(MongoUserManager::class).singleton()
        bind(PunishmentManager::class).to(MongoPunishmentManager::class).singleton()

        // Translation
        bind(Translator::class).to(DeeplTranslator::class).singleton()

        // Networking
        bind(Discovery::class).to(SimpleDiscovery::class).singleton()
        bind(AddressInfoProvider::class).to(IPHubAddressInfoProvider::class).singleton()

        // Messaging
        bind(Messenger::class).to(MongoMessenger::class).singleton()
    }
}