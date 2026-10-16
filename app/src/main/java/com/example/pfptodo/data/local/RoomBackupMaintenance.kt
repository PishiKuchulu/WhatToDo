package com.example.pfptodo.data.local

import com.example.pfptodo.domain.repository.BackupMaintenance

/**
 * Updates sqlite_sequence so the AUTOINCREMENT counter is at least the current
 * max id of each table — imported rows never get overwritten by fresh inserts.
 */
class RoomBackupMaintenance(private val database: PishiDatabase) : BackupMaintenance {
    override suspend fun resetSequences(tables: List<String>) {
        // Never close this connection: openHelper.writableDatabase returns Room's shared
        // cached connection, and closing it (e.g. via .use { }) would break every other
        // database access in the process. This is called from BackupUseCases.importParsed
        // inside transactionRunner.inTransaction — plain execSQL statements on the same
        // connection participate in that transaction normally, keeping the import atomic.
        val db = database.openHelper.writableDatabase
        for (table in tables) {
            db.execSQL(
                "UPDATE sqlite_sequence SET seq = (SELECT MAX(id) FROM `$table`) WHERE name = '$table'"
            )
        }
    }
}
