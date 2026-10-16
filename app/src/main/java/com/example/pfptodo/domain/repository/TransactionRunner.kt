package com.example.pfptodo.domain.repository

/**
 * Executes a suspend block atomically inside a single database transaction.
 * Implementations must roll back the entire block if any operation throws.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
