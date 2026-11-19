package com.example.lifetrack.domain.repository

import com.example.lifetrack.core.backup.BackupPayload

interface BackupRepository {
    suspend fun readBackup(): BackupPayload
    suspend fun restoreBackup(payload: BackupPayload)
    suspend fun countAllRecords(): Int
}
