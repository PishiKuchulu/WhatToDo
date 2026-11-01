package com.example.pfptodo.core.util

import android.content.Context
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Category

/**
 * Known names of the four seeded default categories in every supported locale,
 * keyed by the default category's `iconName`. The seeded names come from string
 * resources (so they differ per install locale) while the icons are stable —
 * which makes this table the canonical cross-locale identity of a default
 * category: display localization resolves through it, and backup import uses it
 * to recognize a default category that was backed up under another locale.
 */
val DEFAULT_CATEGORY_ALIASES_BY_ICON: Map<String, Set<String>> = linkedMapOf(
    "person" to setOf(
        "personal", "شخصی", "privat", "personale", "pessoal", "личное", "شخصي", "kişisel", "个人", "プライベート"
    ),
    "work" to setOf(
        "work", "کاری", "arbeit", "lavoro", "trabalho", "работа", "عمل", "iş", "工作", "仕事", "travail"
    ),
    "shopping_cart" to setOf(
        "shopping", "خرید", "compras", "achats", "einkaufen", "acquisti", "покупки", "تسوق", "alışveriş", "购物", "買い物"
    ),
    "school" to setOf(
        "study", "مطالعه", "estudio", "études", "lernen", "studio", "estudos", "учёба", "دراسة", "eğitim", "学习", "学習"
    )
)

private val DEFAULT_CATEGORY_STRING_RES: Map<String, Int> = mapOf(
    "person" to R.string.category_personal,
    "work" to R.string.category_work,
    "shopping_cart" to R.string.category_shopping,
    "school" to R.string.category_study
)

/**
 * The `iconName` of the seeded default category that [name] is a known alias of
 * (in any supported locale), or null when [name] is not a known default name.
 */
fun defaultCategoryIconForName(name: String): String? {
    val normalized = name.trim().lowercase()
    return DEFAULT_CATEGORY_ALIASES_BY_ICON.entries.firstOrNull { normalized in it.value }?.key
}

/** True when [name] is a known alias (any locale) of the default category identified by [iconName]. */
fun isDefaultCategoryAlias(name: String, iconName: String): Boolean =
    defaultCategoryIconForName(name) == iconName

/**
 * Returns the localized display name for default/seeded categories,
 * or the custom name for user-created categories.
 *
 * Identity note: a category's identity is its RAW stored [Category.name]; this
 * localization is a display-only, name-based mapping. Editors must seed and
 * save the raw name, never this display string.
 */
fun Category.getDisplayName(context: Context): String {
    // Resolved by name (not by icon) so a renamed default category keeps the name the user chose,
    // while untouched seeded categories still follow the app language.
    return resolveCategoryDisplayName(name, context)
}

/**
 * Resolves a category name string into a localized string if it matches
 * any known default seeded category name in supported languages.
 */
fun resolveCategoryDisplayName(name: String, context: Context): String {
    val res = defaultCategoryIconForName(name)?.let { DEFAULT_CATEGORY_STRING_RES[it] }
    return if (res != null) context.getString(res) else name
}
