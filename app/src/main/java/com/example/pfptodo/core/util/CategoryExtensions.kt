package com.example.pfptodo.core.util

import android.content.Context
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Category

/**
 * Returns the localized display name for default/seeded categories,
 * or the custom name for user-created categories.
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
    return when (name.trim().lowercase()) {
        "personal", "شخصی", "privat", "personale", "pessoal", "личное", "شخصي", "kişisel", "个人", "プライベート" ->
            context.getString(R.string.category_personal)
        "work", "کاری", "arbeit", "lavoro", "trabalho", "работа", "عمل", "iş", "工作", "仕事", "travail" ->
            context.getString(R.string.category_work)
        "shopping", "خرید", "compras", "achats", "einkaufen", "acquisti", "покупки", "تسوق", "alışveriş", "购物", "買い物" ->
            context.getString(R.string.category_shopping)
        "study", "مطالعه", "estudio", "études", "lernen", "studio", "estudos", "учёба", "دراسة", "eğitim", "学习", "学習" ->
            context.getString(R.string.category_study)
        else -> name
    }
}
