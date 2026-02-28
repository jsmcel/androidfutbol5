package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Perfil del manager.
 * Equivalente a TACTICS\MANAGER y TACTICS\PROMANAG del original.
 */
@Entity(tableName = "manager_profiles")
data class ManagerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val passwordHash: String,       // BCrypt o SHA-256 del PIN de 4 dígitos
    val prestige: Int = 1,          // 1..10
    val currentTeamId: Int = -1,
    val totalSeasons: Int = 0,
    val titlesWon: Int = 0,
    val promotionsAchieved: Int = 0,
    val relegationsSuffered: Int = 0,
    val careerHistoryJson: String = "[]", // JSON array de ManagerSeasonRecord
    val isActive: Boolean = true,
)
