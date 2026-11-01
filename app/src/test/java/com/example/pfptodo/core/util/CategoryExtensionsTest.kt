package com.example.pfptodo.core.util

import android.content.Context
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---- M-25: canonical default-category identity across locales ----

    @Test
    fun `default category aliases resolve to their default icon in any locale`() {
        assertEquals("person", defaultCategoryIconForName("Personal"))
        assertEquals("person", defaultCategoryIconForName("شخصی"))
        assertEquals("person", defaultCategoryIconForName("Privat"))
        assertEquals("work", defaultCategoryIconForName("Work"))
        assertEquals("work", defaultCategoryIconForName("کاری"))
        assertEquals("work", defaultCategoryIconForName("Travail"))
        assertEquals("shopping_cart", defaultCategoryIconForName("Shopping"))
        assertEquals("shopping_cart", defaultCategoryIconForName("خرید"))
        assertEquals("shopping_cart", defaultCategoryIconForName("Einkaufen"))
        assertEquals("school", defaultCategoryIconForName("Study"))
        assertEquals("school", defaultCategoryIconForName("مطالعه"))
        assertEquals("school", defaultCategoryIconForName("学習"))
    }

    @Test
    fun `alias lookup is case-insensitive and trims whitespace`() {
        assertEquals("work", defaultCategoryIconForName("  WORK "))
        assertEquals("person", defaultCategoryIconForName("PERSONAL"))
    }

    @Test
    fun `custom names are not default aliases`() {
        assertNull(defaultCategoryIconForName("Gaming Projects"))
        assertFalse(isDefaultCategoryAlias("Gaming Projects", "person"))
    }

    @Test
    fun `alias must belong to the given icon`() {
        assertTrue(isDefaultCategoryAlias("شخصی", "person"))
        assertTrue(isDefaultCategoryAlias("Personal", "person"))
        // Same name, wrong default -> not a match.
        assertFalse(isDefaultCategoryAlias("شخصی", "work"))
        assertFalse(isDefaultCategoryAlias("Personal", "school"))
    }
}
