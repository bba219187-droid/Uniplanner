package com.uniplanner.app.online

import android.app.Application
import android.content.Context
import android.net.Uri
import com.uniplanner.app.R
import com.uniplanner.app.calls.CallManager
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
enum class OnlineNotice {
    FRIEND_REQUEST_SENT, CODE_NOT_FOUND, JOINED_GROUP, RESET_EMAIL_SENT, PROFILE_SAVED,
    FILE_TOO_BIG, NO_LOCATION, NO_APP_TO_OPEN, NOT_DOWNLOADED, NOT_READABLE, SIGN_OUT_OFFLINE,
}

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

    init {
        // Loads this account's encryption keys once signed in, and drops them on sign-out.
        viewModelScope.launch {
            uid.collect { id ->
                if (id == null) ChatCrypto.forget() else runCatching { ChatCrypto.prepare(getApplication(), id) }
            }
        }
        // Rings when a friend or a group calls, while the app is running.
        viewModelScope.launch {
            combine(friendships, groups) { fs, gs ->
                fs.filter { it.accepted }.associate { (ChatKind.FRIEND to it.id) to it.otherName } +
                    gs.associate { (ChatKind.GROUP to it.id) to it.name }
            }.collect { chats ->
                if (uid.value == null) CallManager.stopWatching() else CallManager.watch(getApplication(), chats)
            }
        }
    }

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

    // Messages deleted only on this phone.
    private val hiddenPrefs = app.getSharedPreferences("chat_hidden", Context.MODE_PRIVATE)
    private val hidden = MutableStateFlow(hiddenPrefs.getStringSet("ids", null).orEmpty().toSet())

    fun messages(kind: ChatKind, id: String): Flow<List<ChatMessage>> =
        (repo?.messages(kind, id) ?: emptyFlow()).combine(hidden) { list, gone -> list.filter { it.id !in gone } }

    fun timer(kind: ChatKind, id: String): Flow<Int> = repo?.timer(kind, id) ?: flowOf(0)

    fun setTimer(kind: ChatKind, id: String, hours: Int) = act { it.setTimer(kind, id, hours) }

    fun profileOf(uid: String): Flow<Profile?> = repo?.profile(uid) ?: flowOf(null)

    fun sendMessage(kind: ChatKind, id: String, text: String) = withProfile { r, me -> r.sendMessage(me, kind, id, text) }

    private val _sending = MutableStateFlow(0)
    /** Photos, files or a place being prepared to send. */
    val sending: StateFlow<Int> = _sending.asStateFlow()

    private fun text(res: Int, vararg args: Any): String = getApplication<Application>().getString(res, *args)

    /** Photos from the gallery or the camera, one message each. */
    fun sendPhotos(kind: ChatKind, id: String, uris: List<Uri>, fromCamera: Boolean = false) = sendEach(kind, id, uris) { uri ->
        try {
            Attachments.photo(getApplication(), uri)
        } finally {
            // The camera's copy is only needed until it has been read.
            if (fromCamera) Attachments.dropCameraPhoto(getApplication(), uri)
        }
    }

    fun sendFile(kind: ChatKind, id: String, uri: Uri, course: String = "") =
        sendEach(kind, id, listOf(uri), course) { Attachments.file(getApplication(), it) }

    fun sendNote(kind: ChatKind, id: String, course: String, text: String) = withProfile { r, me -> r.sendNote(me, kind, id, course, text) }

    private fun failed(e: Exception) {
        _error.value = e.localizedMessage ?: e.javaClass.simpleName
    }

    /**
     * Each item is handed to Firestore as soon as it is ready, without waiting for the server:
     * Firestore keeps it on the phone and sends it when there is internet, even after the app closes.
     */
    private fun sendEach(kind: ChatKind, id: String, uris: List<Uri>, course: String = "", read: suspend (Uri) -> Outgoing?) = viewModelScope.launch {
        val r = repo ?: return@launch
        _sending.value += 1
        try {
            val me = try {
                profile.value ?: r.loadProfile(r.uid ?: return@launch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
                return@launch
            }
            // One item that cannot be read does not stop the others.
            for (uri in uris) {
                try {
                    val out = read(uri)
                    if (out == null) {
                        _notice.value = OnlineNotice.NOT_READABLE
                        continue
                    }
                    val blobId = r.newBlobId(kind, id)
                    Attachments.remember(getApplication(), Attachments.cacheKey(kind, id, blobId), out.bytes)
                    val preview = if (out.type == MessageType.IMAGE) text(R.string.chat_preview_photo) else text(R.string.chat_preview_file, out.fileName)
                    val shown = if (course.isNotBlank()) "📝 $course: $preview" else preview
                    r.sendAttachment(me, kind, id, blobId, out, shown, course).addOnFailureListener(::failed)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: TooBigException) {
                    _notice.value = OnlineNotice.FILE_TOO_BIG
                } catch (e: Exception) {
                    failed(e)
                }
            }
        } finally {
            _sending.value -= 1
        }
    }

    /** Sends where the phone is now. The screen asks for the permission first. */
    fun sendLocation(kind: ChatKind, id: String) = viewModelScope.launch {
        val r = repo ?: return@launch
        _sending.value += 1
        try {
            val here = LocationShare.here(getApplication())
            if (here == null) {
                _notice.value = OnlineNotice.NO_LOCATION
                return@launch
            }
            val (loc, name) = here
            val me = profile.value ?: r.loadProfile(r.uid ?: return@launch)
            r.sendPlace(me, kind, id, SharedPlace(loc.latitude, loc.longitude, name), text(R.string.chat_preview_location))
                .addOnFailureListener(::failed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(e)
        } finally {
            _sending.value -= 1
        }
    }

    /** The photo or file on this phone, downloading it the first time. Null when it could not be downloaded. */
    suspend fun attachmentFile(kind: ChatKind, id: String, a: Attachment): File? {
        val r = repo ?: return null
        return try {
            Attachments.local(getApplication(), a.cacheKey) { r.download(kind, id, a) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Opens the photo or file in another app, or shares it. Waits while it downloads. */
    suspend fun openAttachment(kind: ChatKind, id: String, a: Attachment, share: Boolean = false) {
        val file = attachmentFile(kind, id, a)
        if (file == null) {
            _notice.value = OnlineNotice.NOT_DOWNLOADED
            return
        }
        try {
            val uri = Attachments.shareable(getApplication(), file, a)
            if (share) {
                Attachments.share(getApplication(), uri, a)
            } else if (!Attachments.open(getApplication(), uri, a)) {
                _notice.value = OnlineNotice.NO_APP_TO_OPEN
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(e)
        }
    }

    fun noAppToOpen() {
        _notice.value = OnlineNotice.NO_APP_TO_OPEN
    }

    /** Hides a message on this phone only. */
    fun deleteForMe(m: ChatMessage) {
        val next = hidden.value + m.id
        hiddenPrefs.edit().putStringSet("ids", next).apply()
        hidden.value = next
        m.attachment?.let { Attachments.forget(getApplication(), it.cacheKey) }
    }

    /** Deletes one of my messages for everyone in the conversation. */
    fun deleteForEveryone(kind: ChatKind, id: String, m: ChatMessage) = withProfile { r, me ->
        val last = conversations.value.firstOrNull { it.kind == kind && it.id == id }?.last
        val wasNewest = last != null && last.byUid == me.uid && last.at == m.createdAt
        r.deleteForEveryone(me, kind, id, m, wasNewest, text(R.string.chat_preview_deleted))
        m.attachment?.let { Attachments.forget(getApplication(), it.cacheKey) }
    }

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
        // Null when the student closed the account picker.
        Online.googleIdToken(activityContext)?.let { r.signInWithGoogle(it) }
    }

    /** Removes what this account shared about its location, then signs out. */
    fun signOut() {
        val r = repo ?: return
        viewModelScope.launch {
            // What was shared about the location has to be removed first, which needs internet.
            if (!LocationShare.stopSharing(getApplication())) {
                _notice.value = OnlineNotice.SIGN_OUT_OFFLINE
                return@launch
            }
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
                // A photo chosen before signing in goes on the profile now.
                com.uniplanner.app.settings.ProfilePhoto.syncAfterSignIn(getApplication())
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }
}
