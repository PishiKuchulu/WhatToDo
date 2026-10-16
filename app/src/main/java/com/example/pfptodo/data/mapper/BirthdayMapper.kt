package com.example.pfptodo.data.mapper

import com.example.pfptodo.data.local.entity.BirthdayEntity
import com.example.pfptodo.domain.model.Birthday

fun BirthdayEntity.toDomain(): Birthday = Birthday(
    id = id,
    name = name,
    month = month,
    day = day,
    birthYear = birthYear,
    notes = notes,
    reminderMinutesOfDay = reminderMinutesOfDay,
    notifyOnDayBefore = notifyOnDayBefore,
    contactId = contactId,
    createdAt = createdAt
)

fun Birthday.toEntity(): BirthdayEntity = BirthdayEntity(
    id = id,
    name = name,
    month = month,
    day = day,
    birthYear = birthYear,
    notes = notes,
    reminderMinutesOfDay = reminderMinutesOfDay,
    notifyOnDayBefore = notifyOnDayBefore,
    contactId = contactId,
    createdAt = createdAt
)
