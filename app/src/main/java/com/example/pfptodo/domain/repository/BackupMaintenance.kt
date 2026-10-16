package com.example.pfptodo.domain.repository

/**
 * Post-import database maintenance. Runs inside the import transaction so the
 * rollback guarantee covers the sequence fix as well.
 */
interface BackupMaintenance {
    /**
     * Resets SQLite AUTOINCREMENT sequences so future inserts never collide
     * with imported ids.
     * @param tables table names whose sequence must be updated
     */
    suspend fun resetSequences(tables: List<String>)
}