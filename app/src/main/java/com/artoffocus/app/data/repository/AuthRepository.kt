package com.artoffocus.app.data.repository

import com.artoffocus.app.domain.account.AccountStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus

/**
 * Native "Sign in with Google" only — no email/password, no magic link. Wraps the Supabase
 * Auth plugin and mirrors the result into [AccountRepository]'s local DataStore state, which
 * is what the rest of the app (Profile settings) actually reads.
 */
class AuthRepository(
    private val supabase: SupabaseClient,
    private val accountRepository: AccountRepository,
) {
    val sessionStatus = supabase.auth.sessionStatus

    /**
     * [idToken] and [rawNonce] come from Credential Manager's Google ID token flow
     * (see ui/auth/GoogleSignIn.kt). Supabase verifies the token's nonce claim against a
     * hash of [rawNonce], so the RAW nonce must be passed here, not the hashed one sent to
     * Google.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
        accountRepository.setStatus(AccountStatus.SIGNED_IN)
        supabase.auth.currentUserOrNull()?.email?.let { accountRepository.setEmail(it) }
    }

    suspend fun signOut() {
        runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
        accountRepository.clearAccountState()
    }

    fun isSignedIn(): Boolean = sessionStatus.value is SessionStatus.Authenticated

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id
}
