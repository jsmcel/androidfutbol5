package com.pcfutbol.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Noticia del juego (equivalente a ACTLIGA\noticias.act).
 * Generada por eventos: fichajes, resultados, lesiones, ofertas de manager.
 */
@Entity(tableName = "news")
data class NewsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,               // ISO-8601
    val matchday: Int,
    val category: String,           // "RESULT" | "TRANSFER" | "INJURY" | "OFFER" | "BOARD"
    val titleEs: String,
    val bodyEs: String,
    val teamId: Int = -1,           // equipo relacionado (-1 = global)
    val read: Boolean = false,
)
