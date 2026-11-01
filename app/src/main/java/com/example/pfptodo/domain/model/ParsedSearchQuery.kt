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
    val type: SearchChipType,
    /**
     * The exact phrase in the raw query that produced this chip. Lets the UI remove
     * just this chip's contribution from the query instead of clearing the whole search.
     */
    val matchedPhrase: String? = null
)

enum class SearchChipType {
    PRIORITY,
    STATUS,
    DATE,
    RECURRING,
    KEYWORD
}
