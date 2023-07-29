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
package com.xpdustry.imperium.common.message

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ImmutableSerializer
import java.net.Inet4Address
import java.net.InetAddress

object InetAddressSerializer : ImmutableSerializer<InetAddress>() {
    override fun write(kryo: Kryo, output: Output, address: InetAddress) {
        val bytes = address.address
        output.writeBoolean(address is Inet4Address)
        output.writeBytes(bytes)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out InetAddress>): InetAddress {
        val isIpv4 = input.readBoolean()
        val bytes = ByteArray(if (isIpv4) 4 else 16)
        input.readBytes(bytes)
        return InetAddress.getByAddress(bytes)
    }
}
