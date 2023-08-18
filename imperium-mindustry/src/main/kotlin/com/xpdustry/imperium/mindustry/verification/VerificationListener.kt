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
package com.xpdustry.imperium.mindustry.verification

import arc.Events
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import arc.util.io.Writes
import com.google.common.net.InetAddresses
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.database.Database
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.network.CoroutineHttpClient
import com.xpdustry.imperium.common.network.VpnAddressDetector
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.util.Priority
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.core.Version
import mindustry.game.EventType.ConnectPacketEvent
import mindustry.game.EventType.PlayerConnect
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.net.Administration.PlayerInfo
import mindustry.net.NetConnection
import mindustry.net.Packets
import mindustry.net.Packets.KickReason
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class VerificationListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val pipeline: VerificationPipeline = instances.get()
    private val database: Database = instances.get()
    private val provider: VpnAddressDetector = instances.get()
    private val http = instances.get<CoroutineHttpClient>()

    override fun onImperiumInit() {
        pipeline.register("ddos", Priority.HIGH, DdosVerification(http))
        pipeline.register("cracked-client", Priority.NORMAL, CrackedClientVerification())
        pipeline.register("punishment", Priority.NORMAL, PunishmentVerification(database))
        pipeline.register("vpn", Priority.LOW, VpnVerification(provider))

        Vars.net.handleServer(Packets.ConnectPacket::class.java) { con, packet ->
            interceptPlayerConnection(con, packet, pipeline)
        }
    }
}

private fun interceptPlayerConnection(con: NetConnection, packet: Packets.ConnectPacket, pipeline: VerificationPipeline) {
    if (con.kicked) return

    // TODO Add steam support
    if (con.address.startsWith("steam:")) {
        packet.uuid = con.address.substring("steam:".length)
    }

    Events.fire(ConnectPacketEvent(con, packet))

    con.connectTime = Time.millis()

    if (Vars.netServer.admins.isIPBanned(con.address) || Vars.netServer.admins.isSubnetBanned(con.address) || con.kicked || !con.isConnected) return

    if (con.hasBegunConnecting) {
        con.kick(KickReason.idInUse)
        return
    }

    // We do not want to save the data of DDOSers, so we postpone the saving of the player info
    val info = Vars.netServer.admins.getInfoOptional(packet.uuid)
        ?: PlayerInfo().apply { id = packet.uuid }

    con.hasBegunConnecting = true
    con.mobile = packet.mobile

    if (packet.uuid == null || packet.usid == null) {
        con.kick(KickReason.idInUse)
        return
    }

    if (Vars.netServer.admins.isIDBanned(packet.uuid)) {
        con.kick(KickReason.banned)
        return
    }

    if (Time.millis() < Vars.netServer.admins.getKickTime(packet.uuid, con.address)) {
        con.kick(KickReason.recentKick)
        return
    }

    // CHECK: Player limit
    if (
        Vars.netServer.admins.playerLimit > 0 &&
        Groups.player.size() >= Vars.netServer.admins.playerLimit &&
        !Vars.netServer.admins.isAdmin(packet.uuid, packet.usid)
    ) {
        con.kick(KickReason.playerLimit)
        return
    }

    // CHECK: Mods
    val mods = packet.mods.copy()
    val missing = Vars.mods.getIncompatibility(mods)
    if (!mods.isEmpty || !missing.isEmpty) {
        // TODO Localize this message
        // can't easily be localized since kick reasons can't have formatted text with them
        val result = StringBuilder("[accent]Incompatible mods![]\n\n")
        if (!missing.isEmpty) {
            result.append("Missing:[lightgray]\n").append("> ").append(missing.toString("\n> "))
            result.append("[]\n")
        }
        if (!mods.isEmpty) {
            result.append("Unnecessary mods:[lightgray]\n").append("> ").append(mods.toString("\n> "))
        }
        con.kick(result.toString(), 0)
        return
    }

    // CHECK: Whitelist
    if (!Vars.netServer.admins.isWhitelisted(packet.uuid, packet.usid)) {
        info.adminUsid = packet.usid
        info.lastName = packet.name
        info.id = packet.uuid
        Vars.netServer.admins.save()
        Call.infoMessage(con, "You are not whitelisted here.")
        Log.info("&lcDo &lywhitelist-add @&lc to whitelist the player &lb'@'", packet.uuid, packet.name)
        con.kick(KickReason.whitelist)
        return
    }

    // CHECK: Custom client
    if (packet.versionType == null || (packet.version == -1 || packet.versionType != Version.type) && Version.build != -1 && Vars.netServer.admins.allowsCustomClients().not()) {
        con.kick(if (Version.type != packet.versionType) KickReason.typeMismatch else KickReason.customClient)
        return
    }

    // CHECK: Duplicate names
    if (Groups.player.contains { player -> Strings.stripColors(player.name).trim().equals(Strings.stripColors(packet.name).trim(), ignoreCase = true) }) {
        con.kick(KickReason.nameInUse)
        return
    }

    // CHECK: Duplicate ids
    if (Groups.player.contains { player -> player.uuid() == packet.uuid || player.usid() == packet.usid }) {
        con.uuid = packet.uuid
        con.kick(KickReason.idInUse)
        return
    }

    // CHECK: Duplicate connections
    for (otherCon in Vars.net.connections) {
        if (otherCon !== con && packet.uuid == otherCon.uuid) {
            con.uuid = packet.uuid
            con.kick(KickReason.idInUse)
            return
        }
    }

    packet.name = Vars.netServer.fixName(packet.name)

    // CHECK: Empty name
    if (Strings.stripColors(packet.name.trim()).isBlank()) {
        con.kick(KickReason.nameEmpty)
        return
    }

    // CHECK: Locale
    if (packet.locale == null) {
        packet.locale = "en"
    }

    // CHECK: Version
    if (packet.version != Version.build && Version.build != -1 && packet.version != -1) {
        con.kick(if (packet.version > Version.build) KickReason.serverOutdated else KickReason.clientOutdated)
        return
    }

    if (packet.version == -1) {
        con.modclient = true
    }

    // To not spam the clients, we do our own verification through the pipeline, then we can safely create the player
    ImperiumScope.MAIN.launch {
        val result = pipeline.pump(VerificationContext(packet.name, packet.uuid, packet.usid, InetAddresses.forString(con.address)))
        runMindustryThread {
            if (result is VerificationResult.Failure) {
                con.kick(result.reason, result.time.toMillis())
                return@runMindustryThread
            }

            // The postponed info is now saved
            Vars.netServer.admins.playerInfo.put(info.id, info)
            Vars.netServer.admins.updatePlayerJoined(packet.uuid, con.address, packet.name)

            val player = Player.create()
            player.admin = Vars.netServer.admins.isAdmin(packet.uuid, packet.usid)
            player.con = con
            player.con.usid = packet.usid
            player.con.uuid = packet.uuid
            player.con.mobile = packet.mobile
            player.name = packet.name
            player.locale = packet.locale
            player.color.set(packet.color).a(1f)

            // Save admin ID but don't overwrite it
            if (!player.admin && !info.admin) {
                info.adminUsid = packet.usid
            }

            // CHECK: Data validity or something...
            try {
                val output = Writes(DataOutputStream(ByteArrayOutputStream(127)))
                player.write(output)
            } catch (error: Throwable) {
                con.kick(KickReason.nameEmpty)
                Log.err(error)
                return@runMindustryThread
            }

            con.player = player

            // Playing in pvp mode automatically assigns players to teams
            player.team(Vars.netServer.assignTeam(player))

            Vars.netServer.sendWorldData(player)

            Events.fire(PlayerConnect(player))
        }
    }
}
