package com.example.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

sealed interface DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome
    data class NeedsConsent(val intentSender: IntentSender) : DriveAuthOutcome
    data class Failed(val message: String) : DriveAuthOutcome
}

object GoogleDriveAuthHelper {

    suspend fun authorizeDriveAccess(activity: Activity): DriveAuthOutcome {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveBackupContract.DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(activity).authorize(request).await()
            val pendingIntent = result.pendingIntent
            if (result.hasResolution() && pendingIntent != null) {
                DriveAuthOutcome.NeedsConsent(pendingIntent.intentSender)
            } else {
                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    DriveAuthOutcome.Failed("Google Drive access token was not returned.")
                } else {
                    DriveAuthOutcome.Authorized(token)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DriveAuthOutcome.Failed(
                e.localizedMessage ?: "Unable to authorize Google Drive access."
            )
        }
    }

    fun extractTokenFromActivityResult(context: Context, data: Intent?): Result<String> {
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (token.isNullOrBlank()) {
                Result.failure(IllegalStateException("Google Drive permission was not granted."))
            } else {
                Result.success(token)
            }
        } catch (e: ApiException) {
            Result.failure(
                IllegalStateException("Google Drive consent cancelled or failed (${e.statusCode}).", e)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
