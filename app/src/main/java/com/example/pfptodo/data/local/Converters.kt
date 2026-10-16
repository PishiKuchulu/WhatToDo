package com.example.pfptodo.data.local

import androidx.room.TypeConverter
import com.example.pfptodo.domain.model.AttachmentType
import com.example.pfptodo.domain.model.Priority
import com.example.pfptodo.domain.model.RecurrenceRule
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long?): Instant? = millis?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromRecurrenceRule(rule: RecurrenceRule): String = rule.toString()

    @TypeConverter
    fun toRecurrenceRule(rule: String): RecurrenceRule = RecurrenceRule.fromString(rule)

    @TypeConverter
    fun fromPriority(priority: Priority): String = priority.name

    @TypeConverter
    fun toPriority(priority: String): Priority =
        // Never throw on unknown/corrupt DB values — fall back to the neutral default.
        runCatching { enumValueOf<Priority>(priority) }.getOrDefault(Priority.MEDIUM)

    @TypeConverter
    fun fromAttachmentType(type: AttachmentType): String = type.name

    @TypeConverter
    fun toAttachmentType(type: String): AttachmentType =
        // Never throw on unknown/corrupt DB values — OTHER is the catch-all bucket.
        runCatching { enumValueOf<AttachmentType>(type) }.getOrDefault(AttachmentType.OTHER)
}
