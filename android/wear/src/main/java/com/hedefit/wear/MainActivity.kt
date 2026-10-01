package com.hedefit.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.hedefit.wear.data.PhoneLink
import com.hedefit.wear.data.SnapshotStore
import com.hedefit.wear.exercise.ExerciseService
import com.hedefit.wear.exercise.WorkoutKind
import com.hedefit.wear.ui.HedefitWearApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingKind: WorkoutKind? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Reddedilen izinde de antrenmanı başlat; Health Services eksik sensörü boş bırakır.
        pendingKind?.let { ExerciseService.start(this, it) }
        pendingKind = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SnapshotStore.load(this)
        setContent {
            val snapshot by SnapshotStore.snapshot.collectAsState()
            val exercise by ExerciseService.ui.collectAsState()
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { PhoneLink.flush(this@MainActivity) }
            HedefitWearApp(
                snapshot = snapshot,
                exercise = exercise,
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
            )
        }
    }
}
