package com.uniplanner.app.online

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.uniplanner.app.location.LocationShare

/** One row of the chat list: a friend or a group. */
data class Conversation(
    val kind: ChatKind,
    val id: String,
    val title: String,
    val last: LastMessage?,
    val unread: Boolean,
    val memberCount: Int,
)

/** What to tell the student after an online action. */
enum class OnlineNotice { FRIEND_REQUEST_SENT, CODE_NOT_FOUND, JOINED_GROUP, RESET_EMAIL_SENT, PROFILE_SAVED }

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    val configured: Boolean = Online.isConfigured(app)
    private val repo: OnlineRepository? = if (configured) OnlineRepository() else null

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _notice = MutableStateFlow<OnlineNotice?>(null)
    val notice: StateFlow<OnlineNotice?> = _notice.asStateFlow()

    val uid: StateFlow<String?> =
        (repo?.authState() ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.Eagerly, repo?.uid)

    // Eager: actions such as posting read it even when no screen shows the profile.
    val profile: StateFlow<Profile?> = uid.flatMapLatest { id -> if (id == null) flowOf(null) else repo!!.profile(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val friendships: StateFlow<List<Friendship>> = uid.flatMapLatest { id -> listFlow(id) { repo!!.friendships(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<Group>> = uid.flatMapLatest { id -> listFlow(id) { repo!!.groups(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val readPrefs = app.getSharedPreferences("chat_read", Context.MODE_PRIVATE)
    private val readAt = MutableStateFlow(readPrefs.all.mapValues { (it.value as? Long) ?: 0L })

    /** Friends and groups, newest conversation first, as in a messaging app. */
    val conversations: StateFlow<List<Conversation>> =
        combine(friendships, groups, readAt, uid) { fs, gs, read, me ->
            val friends = fs.filter { it.accepted }.map { f ->
                Conversation(ChatKind.FRIEND, f.id, f.otherName, f.last, isUnread(f.last, read["f_${f.id}"], me), 2)
            }
            val groupRows = gs.map { g ->
                Conversation(ChatKind.GROUP, g.id, g.name, g.last, isUnread(g.last, read["g_${g.id}"], me), g.memberCount)
            }
            (friends + groupRows).sortedWith(compareByDescending<Conversation> { it.last?.at ?: 0L }.thenBy { it.title.lowercase() })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun isUnread(last: LastMessage?, read: Long?, me: String?) =
        last != null && last.byUid != me && last.at > (read ?: 0L)

    /**
     * Remembers the newest message seen, by the server's clock like the messages themselves, so a
     * phone whose clock is a little off does not show read chats as unread or the other way round.
     */
    fun markRead(kind: ChatKind, id: String, newestSeen: Long?) {
        val key = (if (kind == ChatKind.FRIEND) "f_" else "g_") + id
        val last = conversations.value.firstOrNull { it.kind == kind && it.id == id }?.last?.at
        val seen = maxOf(readAt.value[key] ?: 0L, last ?: 0L, newestSeen ?: 0L)
        readPrefs.edit().putLong(key, seen).apply()
        readAt.value = readAt.value + (key to seen)
    }

    fun messages(kind: ChatKind, id: String): Flow<List<ChatMessage>> = repo?.messages(kind, id) ?: emptyFlow()

    fun sendMessage(kind: ChatKind, id: String, text: String) = withProfile { r, me -> r.sendMessage(me, kind, id, text) }

    private fun <T> listFlow(id: String?, source: (String) -> Flow<List<T>>): Flow<List<T>> =
        if (id == null) flowOf(emptyList()) else source(id)

    fun posts(groupId: String): Flow<List<Post>> = repo?.posts(groupId) ?: emptyFlow()

    fun clearMessages() {
        _error.value = null
        _notice.value = null
    }

    fun signIn(email: String, password: String) = act { it.signIn(email, password) }

    fun register(email: String, password: String, name: String) = act { it.register(email, password, name) }

    fun resetPassword(email: String) = act {
        it.sendPasswordReset(email)
        _notice.value = OnlineNotice.RESET_EMAIL_SENT
    }

    fun signInWithGoogle(activityContext: Context) = act { r ->
        val clientId = Online.googleClientId(getApplication()) ?: throw IllegalStateException("Google sign-in is not set up")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(clientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build(),
            )
            .build()
        try {
            val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            r.signInWithGoogle(token)
        } catch (_: GetCredentialCancellationException) {
            // The student closed the account picker.
        }
    }

    /** Removes what this account shared about its location, then signs out. */
    fun signOut() {
        val r = repo ?: return
        viewModelScope.launch {
            LocationShare.stopSharing(getApplication())
            r.signOut()
        }
    }

    fun saveProfile(name: String, university: String, course: String, level: String) = act { r ->
        val id = r.uid ?: return@act
        r.saveProfile(id, name, university, course, level)
        _notice.value = OnlineNotice.PROFILE_SAVED
    }

    fun addFriend(code: String) = withProfile { r, me ->
        _notice.value = if (r.sendFriendRequest(me, code)) OnlineNotice.FRIEND_REQUEST_SENT else OnlineNotice.CODE_NOT_FOUND
    }

    fun acceptFriend(f: Friendship) = act { it.acceptFriend(f.id) }

    fun removeFriend(f: Friendship) = act { it.removeFriend(f.id) }

    fun createGroup(name: String, description: String) = withProfile { r, me -> r.createGroup(me, name, description) }

    fun joinGroup(code: String) = withProfile { r, me ->
        _notice.value = if (r.joinGroup(me, code)) OnlineNotice.JOINED_GROUP else OnlineNotice.CODE_NOT_FOUND
    }

    fun leaveGroup(groupId: String) = withProfile { r, me -> r.leaveGroup(me, groupId) }

    fun addPost(groupId: String, text: String, link: String) = withProfile { r, me -> r.addPost(me, groupId, text, link) }

    private fun withProfile(block: suspend (OnlineRepository, Profile) -> Unit) = act { r ->
        val me = profile.value ?: r.loadProfile(r.uid ?: return@act)
        block(r, me)
    }

    private fun act(block: suspend (OnlineRepository) -> Unit) {
        val r = repo ?: return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                block(r)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }
}
