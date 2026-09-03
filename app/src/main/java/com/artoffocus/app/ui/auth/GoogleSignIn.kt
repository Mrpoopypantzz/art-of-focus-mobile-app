package com.artoffocus.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.artoffocus.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

data class GoogleSignInResult(val idToken: String, val rawNonce: String)

/**
 * Native "Sign in with Google" via Credential Manager's system account picker — no browser,
 * no deep link. Google is given a SHA-256 hash of a fresh nonce; Supabase is given the RAW
 * nonce back and hashes it itself to verify the ID token's nonce claim matches, so the two
 * must never be swapped.
 */
suspend fun requestGoogleSignIn(context: Context): GoogleSignInResult {
    val rawNonce = UUID.randomUUID().toString()
    val hashedNonce = MessageDigest.getInstance("SHA-256")
        .digest(rawNonce.toByteArray())
        .joinToString("") { "%02x".format(it) }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setNonce(hashedNonce)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val response = CredentialManager.create(context).getCredential(context, request)
    val credential = response.credential

    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Unexpected credential type from Credential Manager: ${credential.type}"
    }
    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
    return GoogleSignInResult(idToken = googleIdTokenCredential.idToken, rawNonce = rawNonce)
}
