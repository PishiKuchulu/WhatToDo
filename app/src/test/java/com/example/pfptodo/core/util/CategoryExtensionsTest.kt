package com.example.pfptodo.core.util

import android.content.Context
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CategoryExtensionsTest {

    private val context: Context = mock()

    @Test
    fun `default category icons resolve to localized resource strings`() {
        whenever(context.getString(R.string.category_personal)).thenReturn("Personal")
        whenever(context.getString(R.string.category_work)).thenReturn("Work")
        whenever(context.getString(R.string.category_shopping)).thenReturn("Shopping")
        whenever(context.getString(R.string.category_study)).thenReturn("Study")

        val personalCat = Category(id = 1, name = "شخصی", color = "#FF6200EE", iconName = "person")
        val workCat = Category(id = 2, name = "کاری", color = "#FF03DAC5", iconName = "work")
        val shoppingCat = Category(id = 3, name = "خرید", color = "#FFFFAB00", iconName = "shopping_cart")
        val studyCat = Category(id = 4, name = "مطالعه", color = "#FF64DD17", iconName = "school")

        assertEquals("Personal", personalCat.getDisplayName(context))
        assertEquals("Work", workCat.getDisplayName(context))
        assertEquals("Shopping", shoppingCat.getDisplayName(context))
        assertEquals("Study", studyCat.getDisplayName(context))
    }

    @Test
    fun `custom user categories preserve their exact custom name`() {
        val customCat = Category(id = 5, name = "Gaming Projects", color = "#FF123456", iconName = "custom_icon")
        assertEquals("Gaming Projects", customCat.getDisplayName(context))
    }

    @Test
    fun `resolveCategoryDisplayName resolves default multilingual category names`() {
        whenever(context.getString(R.string.category_personal)).thenReturn("Personal (Localized)")
        whenever(context.getString(R.string.category_work)).thenReturn("Work (Localized)")

        assertEquals("Personal (Localized)", resolveCategoryDisplayName("Personal", context))
        assertEquals("Personal (Localized)", resolveCategoryDisplayName("شخصی", context))
        assertEquals("Personal (Localized)", resolveCategoryDisplayName("Privat", context))
        assertEquals("Personal (Localized)", resolveCategoryDisplayName("个人", context))

        assertEquals("Work (Localized)", resolveCategoryDisplayName("Work", context))
        assertEquals("Work (Localized)", resolveCategoryDisplayName("کاری", context))
        assertEquals("Work (Localized)", resolveCategoryDisplayName("Arbeit", context))

        assertEquals("Custom Category Name", resolveCategoryDisplayName("Custom Category Name", context))
    }
}
