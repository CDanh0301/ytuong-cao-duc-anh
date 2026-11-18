package com.example.test2.database

object DefaultCategories {
    const val EVENTS = "cat_events"
    const val TO_BUY = "cat_tobuy"
    const val READING = "cat_reading"
    const val NATURE = "cat_nature"
    const val PERSONAL = "cat_personal"

    val ALL_IDS = setOf(EVENTS, TO_BUY, READING, NATURE, PERSONAL)
}
