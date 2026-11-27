package com.example.test2.shared.models

/**
 * Canonical chronological comparators shared across Domain UseCases, Repository filtering,
 * and MediaStore synchronization.
 */
object PhotoComparators {
    private fun normalizedTitleKey(photo: Photo): String =
        photo.title.trim().lowercase()

    private fun normalizedFileNameKey(photo: Photo): String =
        photo.fileName.removeSuffix(".enc").trim().lowercase()

    val chronologicalDescComparator: Comparator<Photo> = Comparator { a, b ->
        val dateCmp = b.dateAdded.compareTo(a.dateAdded)
        if (dateCmp != 0) return@Comparator dateCmp
        val titleCmp = normalizedTitleKey(b).compareTo(normalizedTitleKey(a))
        if (titleCmp != 0) return@Comparator titleCmp
        val fileCmp = normalizedFileNameKey(b).compareTo(normalizedFileNameKey(a))
        if (fileCmp != 0) return@Comparator fileCmp
        b.id.compareTo(a.id)
    }

    val chronologicalAscComparator: Comparator<Photo> = Comparator { a, b ->
        chronologicalDescComparator.compare(b, a)
    }
}
