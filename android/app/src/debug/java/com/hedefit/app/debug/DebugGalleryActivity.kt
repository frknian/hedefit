package com.hedefit.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hedefit.app.data.model.ProfileData
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
