package com.example.lifetrack.domain.repository

interface DatabaseMaintenanceRepository {
    suspend fun databaseSizeBytes(): Long
    suspend fun compactDatabase(): Long
}
