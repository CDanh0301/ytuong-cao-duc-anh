package com.example.test2.database

enum class QueueStatus {
    PENDING,
    PROCESSING,
    OCR_DONE,
    CLOUD_DONE,
    EMBEDDED,
    COMPLETED;

    companion object {
        fun fromString(value: String?): QueueStatus? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
