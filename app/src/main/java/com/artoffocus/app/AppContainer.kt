package com.artoffocus.app

import android.content.Context
import com.artoffocus.app.data.local.AppDatabase
import com.artoffocus.app.data.local.CommunityArtCache
import com.artoffocus.app.data.local.PhotoStore
import com.artoffocus.app.data.remote.CommunityArtApi
import com.artoffocus.app.data.remote.createAppSupabaseClient
import com.artoffocus.app.data.repository.CommunityArtRepository
import com.artoffocus.app.data.repository.AccountRepository
import com.artoffocus.app.data.repository.ArtworkRepository
import com.artoffocus.app.data.repository.AuthRepository
import com.artoffocus.app.data.repository.FocusSetupRepository
import com.artoffocus.app.data.repository.FriendsRepository
import com.artoffocus.app.data.repository.GroupsRepository
import com.artoffocus.app.data.repository.MuseumRepository
import com.artoffocus.app.data.repository.NotificationPrefsRepository
import com.artoffocus.app.data.repository.OnboardingRepository
import com.artoffocus.app.data.repository.ProfileRepository
import com.artoffocus.app.data.repository.SessionRepository
import com.artoffocus.app.data.repository.SyncRepository

/** Simple hand-rolled DI container — no framework needed for this app's size. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val focusSetupRepository by lazy { FocusSetupRepository(appContext) }
    val sessionRepository by lazy { SessionRepository(appContext) }
    val artworkRepository by lazy { ArtworkRepository(appContext) }
    val museumRepository by lazy { MuseumRepository(AppDatabase.get(appContext).paintingDao()) }
    val profileRepository by lazy { ProfileRepository(appContext) }
    val onboardingRepository by lazy { OnboardingRepository(appContext) }
    val notificationPrefsRepository by lazy { NotificationPrefsRepository(appContext) }
    val accountRepository by lazy { AccountRepository(appContext) }
    val photoStore by lazy { PhotoStore(appContext) }
    val communityArtRepository by lazy {
        CommunityArtRepository(CommunityArtApi(), CommunityArtCache(appContext))
    }

    private val supabaseClient by lazy { createAppSupabaseClient() }
    val authRepository by lazy { AuthRepository(supabaseClient, accountRepository) }
    val syncRepository by lazy { SyncRepository(supabaseClient, museumRepository, profileRepository, accountRepository) }
    val friendsRepository by lazy { FriendsRepository(supabaseClient) }
    val groupsRepository by lazy { GroupsRepository(supabaseClient) }
}
