package com.example.test2.ai

enum class ScreenshotIntent {
    EVENT,
    SHOPPING,
    READING,
    PERSONAL,
    OTHER;

    companion object {
        fun fromString(value: String?): ScreenshotIntent =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}
