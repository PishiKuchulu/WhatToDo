package com.example.pfptodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.Tag

/**
 * Safely parse a hex color string (e.g. "#E57373" or "E57373"), falling back to defaultColor.
 */
fun parseTagColor(hex: String?, defaultColor: Color = Color(0xFF6750A4)): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6 -> (0xFF000000 or cleanHex.toLong(16)).toInt()
            8 -> cleanHex.toLong(16).toInt()
            else -> return defaultColor
        }
        Color(colorInt)
    } catch (e: Exception) {
        defaultColor
    }
}

@Composable
fun TagChip(
    tag: Tag,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val baseColor = parseTagColor(tag.color, MaterialTheme.colorScheme.primary)
    val backgroundColor = if (isSelected) {
        baseColor.copy(alpha = 0.25f)
    } else {
        baseColor.copy(alpha = 0.12f)
    }
    val contentColor = baseColor
    val borderColor = if (isSelected) baseColor else baseColor.copy(alpha = 0.35f)
    val cdTagText = stringResource(R.string.cd_tag, tag.name)

    val shape = RoundedCornerShape(8.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = cdTagText }
    ) {
        Text(
            text = "#${tag.name}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )

        if (onRemove != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_delete_tag, tag.name),
                    modifier = Modifier.size(12.dp),
                    tint = contentColor
                )
            }
        }
    }
}
