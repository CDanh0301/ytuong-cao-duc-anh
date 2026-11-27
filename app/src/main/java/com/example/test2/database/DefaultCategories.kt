package com.example.test2.database

import com.example.test2.shared.models.DefaultCategories as SharedDefaultCategories

@Deprecated(
    message = "Use com.example.test2.shared.models.DefaultCategories",
    replaceWith = ReplaceWith("DefaultCategories", "com.example.test2.shared.models.DefaultCategories")
)
object DefaultCategories {
    const val EVENTS = SharedDefaultCategories.EVENTS
    const val TO_BUY = SharedDefaultCategories.TO_BUY
    const val READING = SharedDefaultCategories.READING
    const val NATURE = SharedDefaultCategories.NATURE
    const val PERSONAL = SharedDefaultCategories.PERSONAL

    val ALL_IDS: Set<String> = SharedDefaultCategories.ALL_IDS
}
