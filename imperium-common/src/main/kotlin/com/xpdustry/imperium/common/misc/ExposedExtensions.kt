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
package com.xpdustry.imperium.common.misc

import org.jetbrains.exposed.sql.BlobColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

fun FieldSet.exists(where: SqlExpressionBuilder.() -> Op<Boolean>): Boolean = !selectAll().where(where).empty()

fun Table.mediumblob(name: String): Column<ExposedBlob> = registerColumn(name, MediumBlobColumnType())

private class MediumBlobColumnType : IColumnType<ExposedBlob> by BlobColumnType() {
    override fun sqlType(): String = "MEDIUMBLOB"
}
