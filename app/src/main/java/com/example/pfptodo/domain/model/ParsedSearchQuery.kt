package com.example.pfptodo.domain.model

/**
 * Represents the structured result of parsing a natural language search query.
 */
data class ParsedSearchQuery(
    val rawQuery: String = "",
    val keyword: String = "",
    val filter: TaskFilter = TaskFilter(),
    val chips: List<SearchFilterChip> = emptyList()
)

/**
 * Represents an active, dismissible filter chip derived from parsed natural language intent.
 */
data class SearchFilterChip(
    val id: String,
    val labelRes: Int,
    val customLabel: String? = null,
    val type: SearchChipType
)

enum class SearchChipType {
    PRIORITY,
    STATUS,
    DATE,
    RECURRING,
    KEYWORD
}
