package com.ziiqiio.simplejournalingapp.data.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.tasks.await

/**
 * Wraps Google Sign-In + builds an OAuth credential we can pass to the
 * Docs and Drive API clients. The Activity is responsible for launching
 * the sign-in intent and feeding the result back via [handleSignInResult].
 */
class GoogleAuthManager(private val context: Context) {

    private val scopes = listOf(
        DocsScopes.DOCUMENTS,
        DriveScopes.DRIVE_FILE,
        DriveScopes.DRIVE_METADATA_READONLY
    )

    private val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .apply { scopes.forEach { requestScopes(Scope(it)) } }
            .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun signInIntent(): Intent = signInClient.signInIntent

    fun currentAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf(::hasRequiredPermissions)

    fun hasRequiredPermissions(account: GoogleSignInAccount): Boolean =
        scopes.all { GoogleSignIn.hasPermissions(account, Scope(it)) }

    fun credential(account: GoogleSignInAccount): GoogleAccountCredential {
        val email = account.email ?: error("Google account is missing an email")
        return GoogleAccountCredential.usingOAuth2(context, scopes).apply {
            selectedAccount = account.account ?: Account(email, "com.google")
        }
    }

    suspend fun signOut() {
        signInClient.signOut().await()
    }
}
