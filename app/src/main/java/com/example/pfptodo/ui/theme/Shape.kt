package com.example.pfptodo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape Lock Scale (Appllama Design Standard):
 * - Extra Small (6dp): Micro badges, category dots
 * - Small (10dp): Tags, filter chips, compact input buttons
 * - Medium (20dp): Cards, list rows, search bar, carousel cards
 * - Large (24dp): Dialogs, modal bottom sheets top corners
 * - Extra Large (32dp): Full-screen modals, floating pill buttons
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
