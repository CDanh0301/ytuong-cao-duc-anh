package com.example.test2.shared.models

/**
 * Canonical default AI category IDs shared across Domain, Data, AI, and Presentation layers.
 */
object DefaultCategories {
    const val EVENTS = "cat_events"
    const val TO_BUY = "cat_tobuy"
    const val READING = "cat_reading"
    const val NATURE = "cat_nature"
    const val PERSONAL = "cat_personal"

    val ALL_IDS: Set<String> = setOf(EVENTS, TO_BUY, READING, NATURE, PERSONAL)
}
