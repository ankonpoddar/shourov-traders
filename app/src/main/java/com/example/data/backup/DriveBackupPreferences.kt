package com.example.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.driveBackupDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shourav_drive_backup_prefs"
)

data class StoredDriveBackupInfo(
    val lastBackupAtMillis: Long? = null,
    val lastRestoreAtMillis: Long? = null,
    val lastRemoteFileId: String? = null,
    val lastRemoteFileName: String? = null,
)

class DriveBackupPreferences(private val context: Context) {

    private fun lastBackupKey(userId: String) = longPreferencesKey("last_backup_at_$userId")
    private fun lastRestoreKey(userId: String) = longPreferencesKey("last_restore_at_$userId")
    private fun remoteFileIdKey(userId: String) = stringPreferencesKey("remote_file_id_$userId")
    private fun remoteFileNameKey(userId: String) = stringPreferencesKey("remote_file_name_$userId")

    fun observeBackupInfo(userId: String): Flow<StoredDriveBackupInfo> =
        context.driveBackupDataStore.data.map { prefs ->
            StoredDriveBackupInfo(
                lastBackupAtMillis = prefs[lastBackupKey(userId)],
                lastRestoreAtMillis = prefs[lastRestoreKey(userId)],
                lastRemoteFileId = prefs[remoteFileIdKey(userId)],
                lastRemoteFileName = prefs[remoteFileNameKey(userId)],
            )
        }

    suspend fun recordBackupSuccess(
        userId: String,
        timestampMillis: Long,
        fileId: String,
        fileName: String,
    ) {
        context.driveBackupDataStore.edit { prefs ->
            prefs[lastBackupKey(userId)] = timestampMillis
            prefs[remoteFileIdKey(userId)] = fileId
            prefs[remoteFileNameKey(userId)] = fileName
        }
    }

    suspend fun recordRestoreSuccess(
        userId: String,
        timestampMillis: Long,
        fileId: String?,
        fileName: String?,
    ) {
        context.driveBackupDataStore.edit { prefs ->
            prefs[lastRestoreKey(userId)] = timestampMillis
            if (!fileId.isNullOrBlank()) prefs[remoteFileIdKey(userId)] = fileId
            if (!fileName.isNullOrBlank()) prefs[remoteFileNameKey(userId)] = fileName
        }
    }
}
