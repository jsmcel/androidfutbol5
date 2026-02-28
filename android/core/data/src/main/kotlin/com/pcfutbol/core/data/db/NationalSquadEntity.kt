package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "national_squad",
    foreignKeys = [
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["season", "playerId"], unique = true),
        Index("season"),
        Index("playerId"),
    ],
)
data class NationalSquadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val season: String,
    val playerId: Int,
    val score: Double,
    val updatedAtMatchday: Int,
)

