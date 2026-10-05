package com.hedefit.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hedefit.app.data.model.ExerciseCatalogData
import com.hedefit.app.data.model.ProfileData
import com.hedefit.app.data.network.JsonHttpClient
import com.hedefit.app.data.repository.parseExerciseCatalog
import com.hedefit.app.ui.screens.ExerciseLibraryScreen
import com.hedefit.app.ui.state.TIER_LIMITS
import com.hedefit.app.ui.state.Tier
import com.hedefit.app.ui.state.canUseModalityExercise
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.hedefit.app.ui.i18n.AppLang
import com.hedefit.app.ui.screens.ProfileQuestionnaireScreen
import com.hedefit.app.ui.theme.HedefitTheme

/**
 * YALNIZ DEBUG. Yeni ekranları gerçek hesap ya da sunucu olmadan sahte verilerle açar:
 *   adb shell am start -n com.hedefit.app/com.hedefit.app.debug.DebugGalleryActivity \
 *       --es screen onboarding --es gender Kadın --es lang tr
 * Hiçbir ağ çağrısı yapmaz; kaydetme callback'leri yalnızca logcat'e (tag HedefitDebug) yazar.
 */
class DebugGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "onboarding"
        val gender = intent.getStringExtra("gender") ?: "Kadın"
        val lang = intent.getStringExtra("lang") ?: "tr"
        val quick = intent.getBooleanExtra("quick", false)
        setContent {
            AppLang.en = lang == "en"
            HedefitTheme(darkTheme = true) {
                when (screen) {
                    "library" -> {
                        val tier = Tier.valueOf(intent.getStringExtra("tier") ?: "Free")
                        var items by remember { mutableStateOf<List<ExerciseCatalogData>>(emptyList()) }
                        var busy by remember { mutableStateOf(false) }
                        val load = { modality: String, subcategory: String, muscle: String ->
                            busy = true
                            lifecycleScope.launch {
                                // Herkese açık katalog uç noktası: hesap/oturum gerekmez.
                                val url = "https://hedefit.frknian.workers.dev/api/exercises?limit=1000&locale=$lang&modality=$modality&subcategory=$subcategory&muscle=$muscle"
                                val response = JsonHttpClient().request(url)
                                items = parseExerciseCatalog(JSONObject(response.body).optJSONArray("items") ?: org.json.JSONArray())
                                busy = false
                            }
                        }
                        androidx.compose.runtime.LaunchedEffect(Unit) { load(intent.getStringExtra("modality") ?: "", "", "") }
                        ExerciseLibraryScreen(
                            items = items, loading = busy, language = lang, onBack = { finish() },
                            onSearch = { _, muscle, _, _, _, _, _, _, _, modality, subcategory -> load(modality, subcategory, muscle) },
                            onUse = {}, onStart = {},
                            isLocked = { item -> !TIER_LIMITS.getValue(tier).canUseModalityExercise(item.modalities, item.subcategories) },
                        )
                    }
                    "onboarding" -> ProfileQuestionnaireScreen(
                        profile = demoProfile(gender),
                        saving = false,
                        quickStart = quick,
                        onClose = { finish() },
                        onSave = { android.util.Log.i("HedefitDebug", "onSave history=${it.historyAnswers.size}") },
                        onSaveCycle = { android.util.Log.i("HedefitDebug", "onSaveCycle enabled=${it.trackingEnabled} cycle=${it.cycleLengthDays} period=${it.periodLengthDays} regularity=${it.regularity}") },
                    )
                }
            }
        }
    }

    private fun demoProfile(gender: String) = ProfileData(
        id = "debug", displayName = "Demo", weightKg = 70.0, heightCm = 170.0, goal = "Formu koruma", isPremium = false,
        age = 30, gender = gender, historyAnswers = emptyList(),
    )
}
