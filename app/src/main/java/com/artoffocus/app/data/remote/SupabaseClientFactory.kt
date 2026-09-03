package com.artoffocus.app.data.remote

import com.artoffocus.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * I7 — a backend never sits in the timer's path; this client is only ever touched from
 * account/friends flows, never from Focus session code. URL/key come from BuildConfig
 * (populated from local.properties, gitignored) so no secret is committed.
 */
fun createAppSupabaseClient(): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Auth)
    install(Postgrest)
}
