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
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import fr.xpdustry.distributor.api.util.Priority

class ChatInvisibleCharactersListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val pipeline = instances.get<ChatMessagePipeline>()
    override fun onImperiumInit() {
        // I don't know why but Foo's client appends invisible characters to the end of messages,
        // this is very annoying for the discord bridge.
        pipeline.register("anti-foo-sign", Priority.HIGHEST) { context ->
            val msg = context.message
            // https://github.com/mindustry-antigrief/mindustry-client/blob/23025185c20d102f3fbb9d9a4c20196cc871d94b/core/src/mindustry/client/communication/InvisibleCharCoder.kt#L14
            if (msg.takeLast(2).all { (0xF80 until 0x107F).contains(it.code) }) msg.dropLast(2) else msg
        }
    }
}
