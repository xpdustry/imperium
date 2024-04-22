/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.mindustry.security

// TODO Should be a bare bone enum class, but I'm too lazy to do it right now.
enum class MindustryRules(val title: String, val description: String, val example: String) {
    GRIEFING(
        "Griefing (sabotaging)",
        "Sabotaging your team is annoying, childish and makes the game less fun for other people.",
        "Deconstructing everything near core."),
    VOTEKICK_ABUSE(
        "Vote kick abuse",
        "The vote kick system is only for removing a griefer from the game, not removing noobs or people you don't like.",
        "Vote kicking someone you dislike."),
    ADVERTISING(
        "Advertising",
        "Everyone finds advertising annoying.",
        "Advertising some generic server someone is hosting."),
    NSFW(
        "NSFW", "No one wants to see that here, specially Anuke.", "Explicit logic display image."),
    AUTOMATION(
        "Automation",
        "Anything that makes the player (you) do anything automatically.",
        "Foo client auto-item, player assist, etc.")
}
