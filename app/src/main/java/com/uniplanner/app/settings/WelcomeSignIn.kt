package com.uniplanner.app.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.ui.screens.AreaCards
import com.uniplanner.app.ui.theme.Bricolage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.uniplanner.app.R
import com.uniplanner.app.online.CloudSettings
import com.uniplanner.app.online.Online
import com.uniplanner.app.online.OnlineRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The sign-in offered before the welcome questions. A student who signs in to an account that
 * already has a profile gets it back and only answers the location question again.
 */
object Welcome {
    private const val PREFS = "welcome"
    private val needed = MutableStateFlow<Boolean?>(null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True while the sign-in screen should be shown, before the welcome questions. */
    fun flow(ctx: Context): StateFlow<Boolean?> {
        if (needed.value == null) {
            needed.value = Online.isConfigured(ctx) &&
                Firebase.auth.currentUser == null &&
                !prefs(ctx).getBoolean("asked", false) &&
                !PersonalSettings.get(ctx).done
        }
        return needed
    }

    fun finish(ctx: Context) {
        prefs(ctx).edit().putBoolean("asked", true).apply()
        needed.value = false
    }

    /** The profile came back from the account: the questions open straight at the location. */
    fun isReturning(ctx: Context) = prefs(ctx).getBoolean("returning", false)

    fun setReturning(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean("returning", value).apply()
}

@Composable
fun WelcomeSignIn() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun signIn() {
        busy = true
        failed = false
        scope.launch {
            val ok = try {
                val token = Online.googleIdToken(ctx)
                if (token != null) {
                    OnlineRepository().signInWithGoogle(token)
                    Firebase.auth.currentUser?.let { user ->
                        runCatching { CloudSettings.pull(user.uid) }
                        val p = PersonalSettings.get(ctx)
                        if (p.done) {
                            // Profile and timetable are back; only the location and permissions are asked again.
                            Welcome.setReturning(ctx, true)
                            PersonalSettings.save(ctx, p.copy(done = false))
                        } else if (p.name.isBlank() && !user.displayName.isNullOrBlank()) {
                            PersonalSettings.save(ctx, p.copy(name = user.displayName.orEmpty()))
                        }
                    }
                    true
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            busy = false
            when (ok) {
                true -> Welcome.finish(ctx)
                false -> failed = true
                null -> Unit
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        // Same cards as the entrance, so the two screens read as one.
        AreaCards(animate = false)
        Spacer(Modifier.height(36.dp))
        Text(
            stringResource(R.string.welcome_title),
            fontFamily = Bricolage,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.welcome_text),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.weight(1f))
        if (failed) {
            Text(
                stringResource(R.string.welcome_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = ::signIn,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.inverseSurface, contentColor = colors.inverseOnSurface),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.inverseOnSurface, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.welcome_google), style = MaterialTheme.typography.titleSmall)
                }
            }
            TextButton(onClick = { Welcome.finish(ctx) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_later))
            }
        }
    }
}
