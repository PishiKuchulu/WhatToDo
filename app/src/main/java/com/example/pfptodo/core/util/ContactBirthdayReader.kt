package com.example.pfptodo.core.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract

/**
 * Reads a single contact's name and birthday from a URI returned by the system
 * contact picker.
 *
 * This intentionally does NOT hold `READ_CONTACTS`: the picker grants read access
 * to exactly the one contact the user chose, so the app never needs blanket access
 * to the address book.
 */
object ContactBirthdayReader {

    /** A parsed contact — [month]/[day] are null when the contact has no birthday. */
    data class ContactData(
        val name: String?,
        val month: Int?,
        val day: Int?,
        val year: Int?,
        val contactId: String?
    )

    fun read(resolver: ContentResolver, contactUri: Uri): ContactData? = try {
        val contactId = readContactId(resolver, contactUri) ?: return null
        val name = readName(resolver, contactId)
        val event = readBirthdayEvent(resolver, contactId)
        ContactData(
            name = name,
            month = event?.first,
            day = event?.second,
            year = event?.third,
            contactId = contactId
        )
    } catch (e: Exception) {
        // Any provider/security failure must degrade to "nothing to import", never crash.
        null
    }

    private fun readContactId(resolver: ContentResolver, contactUri: Uri): String? {
        resolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private fun readName(resolver: ContentResolver, contactId: String): String? {
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
            "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                contactId,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            ),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    /** @return (month, day, year) where year may be null or absent. */
    private fun readBirthdayEvent(
        resolver: ContentResolver,
        contactId: String
    ): Triple<Int, Int, Int?>? {
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE
            ),
            "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                contactId,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
            ),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getInt(1)
                if (type != ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) continue
                val raw = cursor.getString(0) ?: continue
                parseEventDate(raw)?.let { return it }
            }
        }
        return null
    }

    /**
     * Contact birthdays are stored as `YYYY-MM-DD` when the year is known and
     * `--MM-DD` when it is not.
     */
    internal fun parseEventDate(raw: String): Triple<Int, Int, Int?>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            if (trimmed.startsWith("--")) {
                val parts = trimmed.removePrefix("--").split("-")
                if (parts.size < 2) return null
                Triple(parts[0].toInt(), parts[1].toInt(), null)
            } else {
                val parts = trimmed.split("-")
                if (parts.size < 3) return null
                Triple(parts[1].toInt(), parts[2].toInt(), parts[0].toInt())
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
