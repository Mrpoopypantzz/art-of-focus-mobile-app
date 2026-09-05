package com.artoffocus.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artoffocus.app.data.remote.isBackendConfigured
import com.artoffocus.app.data.repository.AccountRepository
import com.artoffocus.app.data.repository.AuthRepository
import com.artoffocus.app.data.repository.FriendsRepository
import com.artoffocus.app.data.repository.GroupsRepository
import com.artoffocus.app.domain.account.AccountStatus
import com.artoffocus.app.domain.friends.Friend
import com.artoffocus.app.domain.friends.Group
import com.artoffocus.app.domain.friends.PendingRequest
import com.artoffocus.app.domain.friends.SortBy
import com.artoffocus.app.domain.friends.describeGroupNameProblem
import com.artoffocus.app.domain.friends.formatHandle
import com.artoffocus.app.domain.friends.groupNameProblem
import com.artoffocus.app.domain.friends.isValidInviteCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class FriendsScope { FRIENDS, GROUPS }

data class FriendsUiState(
    val configured: Boolean = isBackendConfigured(),
    val signedIn: Boolean = false,
    val scope: FriendsScope = FriendsScope.FRIENDS,
    val sortBy: SortBy = SortBy.HOURS,

    val friends: List<Friend> = emptyList(),
    val pending: List<PendingRequest> = emptyList(),
    val query: String = "",
    val match: FriendsRepository.HandleMatch? = null,
    val searched: Boolean = false,

    val groups: List<Group> = emptyList(),
    val openGroupId: String? = null,
    val standings: List<Friend> = emptyList(),
    val groupNameInput: String = "",
    val joinCodeInput: String = "",

    val notice: String? = null,
    val busy: Boolean = false,
)

/** D1 — a port of the web app's FriendsScreen.tsx state and handlers, against the same backend. */
class FriendsViewModel(
    private val friendsRepository: FriendsRepository,
    private val groupsRepository: GroupsRepository,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state

    init {
        viewModelScope.launch {
            accountRepository.observe().collect { account ->
                val nowSignedIn = account.status == AccountStatus.SIGNED_IN
                val wasSignedIn = _state.value.signedIn
                _state.value = _state.value.copy(signedIn = nowSignedIn)
                if (nowSignedIn && !wasSignedIn) refresh()
            }
        }
    }

    fun setScope(scope: FriendsScope) {
        _state.value = _state.value.copy(scope = scope, notice = null)
    }

    fun setSortBy(sortBy: SortBy) {
        _state.value = _state.value.copy(sortBy = sortBy)
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query, match = null, searched = false)
    }

    fun setGroupNameInput(name: String) {
        _state.value = _state.value.copy(groupNameInput = name)
    }

    fun setJoinCodeInput(code: String) {
        _state.value = _state.value.copy(joinCodeInput = code)
    }

    fun search() {
        val query = _state.value.query
        if (query.isBlank()) return
        viewModelScope.launch {
            val (match, error) = friendsRepository.searchHandle(query)
            _state.value = _state.value.copy(match = match, searched = true, notice = error)
        }
    }

    fun addFriend(match: FriendsRepository.HandleMatch) {
        val meId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            val error = friendsRepository.sendRequest(meId, match.id)
            _state.value = _state.value.copy(
                notice = error ?: "Request sent to ${formatHandle(match.handle)}.",
                match = null,
                query = "",
                searched = false,
            )
            refresh()
        }
    }

    fun decline(them: PendingRequest) {
        val meId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            friendsRepository.removeFriendship(meId, them.id)
            refresh()
        }
    }

    fun accept(them: PendingRequest) {
        val meId = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            val error = friendsRepository.acceptRequest(meId, them.id)
            _state.value = _state.value.copy(notice = error)
            refresh()
        }
    }

    fun createGroup() {
        val name = _state.value.groupNameInput
        val problem = groupNameProblem(name)
        if (problem != null) {
            _state.value = _state.value.copy(notice = describeGroupNameProblem(problem))
            return
        }
        viewModelScope.launch {
            val error = groupsRepository.createGroup(name)
            _state.value = _state.value.copy(
                notice = error ?: "Created ${name.trim()}.",
                groupNameInput = if (error == null) "" else name,
            )
            refresh()
        }
    }

    fun joinGroup() {
        val code = _state.value.joinCodeInput
        if (!isValidInviteCode(code)) {
            _state.value = _state.value.copy(notice = "That does not look like an invite code.")
            return
        }
        viewModelScope.launch {
            val error = groupsRepository.joinGroup(code)
            _state.value = _state.value.copy(
                notice = error ?: "Joined.",
                joinCodeInput = if (error == null) "" else code,
            )
            refresh()
        }
    }

    fun leaveGroup(group: Group) {
        viewModelScope.launch {
            val error = groupsRepository.leaveGroup(group.id)
            val collapsing = _state.value.openGroupId == group.id
            _state.value = _state.value.copy(
                notice = error ?: "Left ${group.name}.",
                openGroupId = if (collapsing) null else _state.value.openGroupId,
                standings = if (collapsing) emptyList() else _state.value.standings,
            )
            refresh()
        }
    }

    fun openStandings(groupId: String) {
        if (_state.value.openGroupId == groupId) {
            _state.value = _state.value.copy(openGroupId = null, standings = emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(openGroupId = groupId)
            val standings = groupsRepository.loadStandings(groupId)
            if (_state.value.openGroupId == groupId) {
                _state.value = _state.value.copy(standings = standings)
            }
        }
    }

    private suspend fun refresh() {
        val friends = friendsRepository.loadFriends()
        val pending = friendsRepository.loadPendingRequests()
        val groups = groupsRepository.loadGroups()
        _state.value = _state.value.copy(friends = friends, pending = pending, groups = groups)
    }

    class Factory(
        private val friendsRepository: FriendsRepository,
        private val groupsRepository: GroupsRepository,
        private val accountRepository: AccountRepository,
        private val authRepository: AuthRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FriendsViewModel(friendsRepository, groupsRepository, accountRepository, authRepository) as T
        }
    }
}
