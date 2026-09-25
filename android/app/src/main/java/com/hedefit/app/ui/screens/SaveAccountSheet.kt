package com.hedefit.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.R
import com.hedefit.app.ui.components.staggeredEntrance
import com.hedefit.app.ui.state.SaveAccountTrigger
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.validation.validateAuthForm

/**
 * Misafir kullanıcıya, emek verdiği bir anın hemen ardından hesabını kaydetmeyi önerir.
 * Aynı user_id'ye kimlik bağlandığı için hiçbir veri taşınmaz ya da kaybolmaz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveAccountSheet(
    trigger: SaveAccountTrigger,
    busy: Boolean,
    message: String?,
    onGoogle: () -> Unit,
    onEmail: (email: String, password: String, username: String) -> Unit,
    onDismiss: () -> Unit,
    lockedFeature: com.hedefit.app.ui.state.LockedFeature? = null,
) {
    val (emoji, title, body) = when (trigger) {
        SaveAccountTrigger.Limit -> Triple(lockedFeature?.emoji ?: "🔒", lockedFeature?.title ?: com.hedefit.app.ui.i18n.tr("Daha fazlası için hesabını kaydet", "Save your account for more"), (lockedFeature?.body ?: "") + com.hedefit.app.ui.i18n.tr(" Ücretsiz hesap aç; sınırların genişlesin, ilerlemen güvende kalsın.", " Create a free account to raise your limits and keep your progress safe."))
        SaveAccountTrigger.WorkoutCompleted -> Triple("💪", com.hedefit.app.ui.i18n.tr("İlk antrenmanın kayıtlı kalsın", "Keep your first workout"), com.hedefit.app.ui.i18n.tr("Serin, XP'in ve programın şu an sadece bu cihazda. Hesabını kaydet, hiçbirini kaybetme.", "Your streak, XP and program live only on this device. Save your account so you never lose them."))
        SaveAccountTrigger.CoachLimit -> Triple("🤖", com.hedefit.app.ui.i18n.tr("Koçunla konuşmaya devam et", "Keep talking to your coach"), com.hedefit.app.ui.i18n.tr("Misafir sohbet hakkın doldu. Ücretsiz hesap ile koçun seni tanımaya devam etsin.", "You've used your guest chats. A free account lets your coach keep learning about you."))
        SaveAccountTrigger.Sync -> Triple("⌚", com.hedefit.app.ui.i18n.tr("Cihazlarını eşitle", "Sync your devices"), com.hedefit.app.ui.i18n.tr("Saat ve sağlık verisi eşitlemesi için hesabını kaydetmelisin.", "Save your account to sync watch and health data."))
        SaveAccountTrigger.Manual -> Triple("🛡️", com.hedefit.app.ui.i18n.tr("Hesabını kaydet", "Save your account"), com.hedefit.app.ui.i18n.tr("İlerlemen güvende olsun, telefon değiştirsen bile kaldığın yerden devam et.", "Keep your progress safe and pick up where you left off, even on a new phone."))
    }
    var useEmail by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val formError = validateAuthForm(email, password, password, login = false, submitted = submitted)
        ?: if (submitted && username.trim().length < 3) com.hedefit.app.ui.i18n.tr("Kullanıcı adı en az 3 karakter olmalı.", "Username must be at least 3 characters.") else null

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HedefitColors.Surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(emoji, fontSize = 52.sp, modifier = Modifier.staggeredEntrance(trigger, 0))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = HedefitColors.TextPrimary, modifier = Modifier.staggeredEntrance(trigger, 1))
            Text(body, color = HedefitColors.TextSecondary, modifier = Modifier.staggeredEntrance(trigger, 2))
            if (message != null) Surface(color = HedefitColors.SurfaceHigh, shape = RoundedCornerShape(14.dp)) {
                Text(message, Modifier.padding(14.dp), color = HedefitColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
            }
            if (!useEmail) {
                OutlinedButton(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp).staggeredEntrance(trigger, 3), shape = RoundedCornerShape(16.dp)) {
                    Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(com.hedefit.app.ui.i18n.tr("Google ile kaydet", "Save with Google"), color = HedefitColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { useEmail = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp).staggeredEntrance(trigger, 4), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
                ) { Text(com.hedefit.app.ui.i18n.tr("E-posta ile kaydet", "Save with email"), fontWeight = FontWeight.Bold) }
            } else {
                OutlinedTextField(username, { username = it }, label = { Text(com.hedefit.app.ui.i18n.tr("Kullanıcı adı", "Username")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text(com.hedefit.app.ui.i18n.tr("E-posta", "Email")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(com.hedefit.app.ui.i18n.tr("Parola", "Password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (formError != null) Text(formError, color = HedefitColors.Coral, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        submitted = true
                        if (validateAuthForm(email, password, password, login = false, submitted = true) == null && username.trim().length >= 3) onEmail(email, password, username)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
                ) { Text(if (busy) com.hedefit.app.ui.i18n.tr("Kaydediliyor…", "Saving…") else com.hedefit.app.ui.i18n.tr("Hesabımı kaydet", "Save my account"), fontWeight = FontWeight.Bold) }
            }
            TextButton(onClick = onDismiss) { Text(com.hedefit.app.ui.i18n.tr("Şimdi değil", "Not now"), color = HedefitColors.TextSecondary) }
        }
    }
}
