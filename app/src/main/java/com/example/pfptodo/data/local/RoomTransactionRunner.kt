package com.example.pfptodo.data.local

import androidx.room.withTransaction
import com.example.pfptodo.domain.repository.TransactionRunner

/** Room-backed transaction runner: every statement inside the block is atomic. */
class RoomTransactionRunner(private val database: PishiDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }
}
