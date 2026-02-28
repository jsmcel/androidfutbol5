package com.pcfutbol.economy

/**
 * Reglas del calendario de mercado de fichajes para temporada 2025/26.
 * Basado en las reglas de LaLiga y el comportamiento del original.
 */
object TransferMarketCalendar {

    data class Window(
        val name: String,
        val openMatchday: Int,
        val closeMatchday: Int,
    )

    val SUMMER = Window("Mercado de Verano", openMatchday = 0, closeMatchday = 3)
    val WINTER = Window("Mercado de Invierno", openMatchday = 19, closeMatchday = 21)

    fun isOpen(currentMatchday: Int): Boolean =
        currentMatchday in SUMMER.openMatchday..SUMMER.closeMatchday ||
        currentMatchday in WINTER.openMatchday..WINTER.closeMatchday

    fun currentWindow(currentMatchday: Int): Window? = when {
        currentMatchday in SUMMER.openMatchday..SUMMER.closeMatchday -> SUMMER
        currentMatchday in WINTER.openMatchday..WINTER.closeMatchday -> WINTER
        else -> null
    }

    fun daysUntilNextWindow(currentMatchday: Int): Int = when {
        currentMatchday < SUMMER.openMatchday -> SUMMER.openMatchday - currentMatchday
        currentMatchday < WINTER.openMatchday -> WINTER.openMatchday - currentMatchday
        else -> 38 - currentMatchday + SUMMER.openMatchday // próxima temporada
    }
}
