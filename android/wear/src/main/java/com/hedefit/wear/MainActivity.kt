package com.hedefit.wear

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hedefit.wear.data.NavCue
import com.hedefit.wear.data.PhoneLink
import com.hedefit.wear.data.SnapshotStore
import com.hedefit.wear.data.WatchEvents
import com.hedefit.wear.exercise.ExerciseService
import com.hedefit.wear.exercise.WorkoutKind
import com.hedefit.wear.ui.AskState
import com.hedefit.wear.ui.HedefitWearApp
import com.hedefit.wear.ui.Pages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private var pendingKind: WorkoutKind? = null
    private var initialPage by mutableIntStateOf(Pages.SUMMARY)
    private var askState by mutableStateOf<AskState>(AskState.Idle)
    private var askMode = "chat"

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Reddedilen izinde de antrenmanı başlat; Health Services eksik sensörü boş bırakır.
        pendingKind?.let { ExerciseService.start(this, it) }
        pendingKind = null
    }

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (text.isEmpty()) { askState = AskState.Idle; return@registerForActivityResult }
        askState = AskState.Waiting
        lifecycleScope.launch {
            val id = PhoneLink.sendAsk(this@MainActivity, askMode, text)
            if (id == null) { askState = AskState.Result(getString(R.string.phone_not_connected), false); return@launch }
            val reply = withTimeoutOrNull(60_000) { WatchEvents.replies.first { it.id == id } }
            askState = if (reply == null) AskState.Result(getString(R.string.no_reply), false) else AskState.Result(reply.text, reply.ok)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SnapshotStore.load(this)
        initialPage = intent?.getIntExtra(EXTRA_PAGE, Pages.SUMMARY) ?: Pages.SUMMARY
        setContent {
            val snapshot by SnapshotStore.snapshot.collectAsState()
            val exercise by ExerciseService.ui.collectAsState()
            val sets by ExerciseService.sets.collectAsState()
            val scope = rememberCoroutineScope()
            var connected by remember { mutableStateOf<Boolean?>(null) }
            var nav by remember { mutableStateOf<NavCue?>(null) }
            LaunchedEffect(Unit) {
                while (true) { connected = PhoneLink.isConnected(this@MainActivity); if (connected == true) PhoneLink.flush(this@MainActivity); delay(20_000) }
            }
            LaunchedEffect(Unit) {
                WatchEvents.nav.collect { cue -> nav = cue; delay(12_000); if (nav == cue) nav = null }
            }
            HedefitWearApp(
                snapshot = snapshot,
                exercise = exercise,
                loggedSets = sets,
                connected = connected,
                initialPage = initialPage,
                nav = nav,
                askState = askState,
                onAddWater = { ml -> scope.launch { PhoneLink.addWater(this@MainActivity, ml) } },
                onStart = { kind ->
                    pendingKind = kind
                    val needed = buildList {
                        add(Manifest.permission.BODY_SENSORS); add(Manifest.permission.ACTIVITY_RECOGNITION); add(Manifest.permission.POST_NOTIFICATIONS)
                        if (kind.outdoor) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    }
                    permissions.launch(needed.toTypedArray())
                },
                onTogglePause = { ExerciseService.togglePause(this) },
                onEnd = { ExerciseService.end(this) },
                onLogSet = { ExerciseService.logSet(it) },
                onVoice = { mode ->
                    askMode = mode
                    askState = AskState.Listening
                    val prompt = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (snapshot.isEnglish) "en-US" else "tr-TR")
                    runCatching { speech.launch(prompt) }.onFailure { askState = AskState.Result(getString(R.string.no_speech), false) }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialPage = intent.getIntExtra(EXTRA_PAGE, Pages.SUMMARY)
    }

    companion object { const val EXTRA_PAGE = "page" }
}
