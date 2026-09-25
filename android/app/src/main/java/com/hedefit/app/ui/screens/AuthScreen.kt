package com.hedefit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedefit.app.R
import com.hedefit.app.data.auth.AuthState
import com.hedefit.app.data.auth.RegistrationLegalAcceptance
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.PrimaryButton
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.validation.validateAuthForm
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.withLink
import com.hedefit.app.ui.components.rememberReducedMotion
import com.hedefit.app.ui.components.staggeredEntrance

@Composable
fun AuthGateScreen(
    authState: AuthState,
    busy: Boolean,
    googleBusy: Boolean,
    message: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, RegistrationLegalAcceptance) -> Unit,
    onGoogleSignIn: (RegistrationLegalAcceptance?) -> Unit,
    onClearMessage: () -> Unit,
    onCheckUsername: (String, (String) -> Unit) -> Unit = { _, done -> done("ok") },
    onStartAsGuest: ((RegistrationLegalAcceptance) -> Unit)? = null,
) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    when (authState) {
        AuthState.Loading -> FullScreenLoader(com.hedefit.app.ui.i18n.tr("Oturum kontrol ediliyor", "Checking session"))
        is AuthState.ConfigurationError -> ConfigurationErrorScreen(authState.message)
        AuthState.SignedOut -> if (onStartAsGuest != null && !showForm) {
            WelcomeIntroScreen(busy = busy, message = message, onStart = onStartAsGuest, onHaveAccount = { onClearMessage(); showForm = true })
        } else {
            if (onStartAsGuest != null) BackHandler { onClearMessage(); showForm = false }
            AuthForm(busy, googleBusy, message, onSignIn, onSignUp, onGoogleSignIn, onClearMessage, onCheckUsername)
        }
        is AuthState.SignedIn -> Unit
    }
}

/**
 * İlk açılış: kayıt duvarı yerine değer vaadi ve tek dokunuşla başlama.
 * Kullanıcı misafir olarak girer; hesap, ilk antrenmandan sonra kaydettirilir.
 */
@Composable
private fun HeroChip(emoji: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.background(HedefitColors.Surface.copy(alpha = .92f), RoundedCornerShape(50)).border(1.dp, HedefitColors.Divider, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.size(6.dp))
        Text(text, color = HedefitColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WelcomeIntroScreen(busy: Boolean, message: String?, onStart: (RegistrationLegalAcceptance) -> Unit, onHaveAccount: () -> Unit) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    val reduced = rememberReducedMotion()
    val float = if (reduced) 0f else rememberInfiniteTransition(label = "heroFloat")
        .animateFloat(-1f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "float").value

    Box(Modifier.fillMaxSize().background(HedefitColors.Background)) {
        // Arka plan: köşeden yayılan yumuşak vurgu ışığı.
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(HedefitColors.Lime.copy(alpha = .22f), androidx.compose.ui.graphics.Color.Transparent), center = androidx.compose.ui.geometry.Offset(900f, 350f), radius = 900f)))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            // Üst çubuk: marka + giriş kısayolu.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_logo_transparent), contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.size(8.dp))
                Text("Hedefit", color = HedefitColors.TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onHaveAccount) { Text(com.hedefit.app.ui.i18n.tr("Giriş yap", "Log in"), color = HedefitColors.TextPrimary, fontWeight = FontWeight.SemiBold) }
            }

            // Kahraman alanı: iç içe dolan halkalar ve süzülen mini kartlar.
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.staggeredEntrance("intro", 0), contentAlignment = Alignment.Center) {
                    com.hedefit.app.ui.components.ActivityRing(.78f, HedefitColors.Lime, size = 220.dp, stroke = 16.dp)
                    com.hedefit.app.ui.components.ActivityRing(.62f, HedefitColors.Water, size = 172.dp, stroke = 16.dp)
                    com.hedefit.app.ui.components.ActivityRing(.9f, HedefitColors.Sleep, size = 124.dp, stroke = 16.dp) {
                        Image(painterResource(R.drawable.app_logo_transparent), contentDescription = null, modifier = Modifier.size(48.dp))
                    }
                }
                HeroChip("🔥", com.hedefit.app.ui.i18n.tr("7 gün seri", "7-day streak"), Modifier.align(Alignment.TopStart).padding(top = 24.dp).graphicsLayer { translationY = float * 10.dp.toPx() }.staggeredEntrance("intro", 1))
                HeroChip("⚡", "+40 XP", Modifier.align(Alignment.CenterEnd).graphicsLayer { translationY = -float * 12.dp.toPx() }.staggeredEntrance("intro", 2))
                HeroChip("🍽️", "1.850 kcal", Modifier.align(Alignment.BottomStart).padding(bottom = 28.dp).graphicsLayer { translationY = float * 8.dp.toPx() }.staggeredEntrance("intro", 3))
            }

            // Metin bloğu.
            androidx.compose.material3.Surface(color = HedefitColors.Lime.copy(alpha = .14f), shape = RoundedCornerShape(50), modifier = Modifier.staggeredEntrance("intro", 4)) {
                Text(com.hedefit.app.ui.i18n.tr("Üye olmadan · Ücretsiz", "No sign-up · Free"), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = HedefitColors.Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(com.hedefit.app.ui.i18n.tr("Hedefine giden yol\nburada başlar.", "Your path to your goal\nstarts here."), Modifier.staggeredEntrance("intro", 5), color = HedefitColors.TextPrimary, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(com.hedefit.app.ui.i18n.tr("Birkaç soruyu yanıtla; programın, kalori hedefin ve koçun hazır.", "Answer a few questions; your program, calorie target and coach are ready."), Modifier.staggeredEntrance("intro", 6), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)

            // Onay: tüm satır dokunulabilir; belge adları metnin içinde bağlantı.
            val consentText = androidx.compose.ui.text.buildAnnotatedString {
                val link = androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = HedefitColors.TextPrimary, fontWeight = FontWeight.SemiBold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
                withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("kvkk", link) { legalDocument = LegalDocument.Kvkk }) { append(com.hedefit.app.ui.i18n.tr("KVKK Aydınlatma Metni", "Privacy Notice (KVKK)")) }
                append(com.hedefit.app.ui.i18n.tr("'ni okudum, ", " read, and I accept the "))
                withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("privacy", link) { legalDocument = LegalDocument.Privacy }) { append(com.hedefit.app.ui.i18n.tr("Gizlilik Politikası", "Privacy Policy")) }
                append(com.hedefit.app.ui.i18n.tr("'nı kabul ediyorum.", "."))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Checkbox) { accepted = !accepted }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (accepted) HedefitColors.Lime else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.5.dp, if (accepted) HedefitColors.Lime else HedefitColors.TextMuted, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (accepted) Icon(Icons.Default.Check, null, tint = HedefitColors.OnLime, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.size(12.dp))
                Text(consentText, color = HedefitColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (message != null) Text(message, color = HedefitColors.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            androidx.compose.material3.Button(
                onClick = { onStart(RegistrationLegalAcceptance(kvkkNoticeAccepted = true, privacyPolicyAccepted = true)) },
                enabled = accepted && !busy,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime, disabledContainerColor = HedefitColors.SurfaceHigh, disabledContentColor = HedefitColors.TextMuted),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = HedefitColors.OnLime, strokeWidth = 2.dp)
                else Text(com.hedefit.app.ui.i18n.tr("Hemen başla  →", "Start now  →"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    legalDocument?.let { LegalDocumentDialog(it) { legalDocument = null } }
}

@Composable
private fun AuthForm(
    busy: Boolean,
    googleBusy: Boolean,
    message: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, RegistrationLegalAcceptance) -> Unit,
    onGoogleSignIn: (RegistrationLegalAcceptance?) -> Unit,
    onClearMessage: () -> Unit,
    onCheckUsername: (String, (String) -> Unit) -> Unit,
) {
    var login by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var usernameStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username, login) {
        usernameStatus = null
        val local = localUsernameStatus(username)
        if (login || username.isBlank()) return@LaunchedEffect
        if (local != null) { usernameStatus = local; return@LaunchedEffect }
        kotlinx.coroutines.delay(450)
        usernameStatus = "checking"
        onCheckUsername(username) { usernameStatus = it }
    }
    var password by remember { mutableStateOf("") }
    var passwordAgain by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var kvkkAccepted by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    fun legalError(requireSubmission: Boolean): String? = when {
        login || (requireSubmission && !submitted) -> null
        !kvkkAccepted -> "KVKK Aydınlatma Metni'ni okuduğunu onaylamalısın."
        !privacyAccepted -> "Gizlilik Politikası'nı kabul etmelisin."
        else -> null
    }
    val localError = validateAuthForm(email, password, passwordAgain, login, submitted) ?: legalError(requireSubmission = true)
    LaunchedEffect(message) {
        if (message?.contains(com.hedefit.app.ui.i18n.tr("Kayıt Ol", "Sign Up"), ignoreCase = true) == true) {
            login = false
        }
    }

    fun edited(change: () -> Unit) {
        change()
        if (message != null) onClearMessage()
    }

    Box(
        Modifier.fillMaxSize().background(HedefitColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        AuthEnergyBackground()
        Column(
            Modifier.fillMaxWidth().widthIn(max = 460.dp).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthHero(login)
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.fillMaxWidth().staggeredEntrance("authCard", 3)
                    .background(HedefitColors.Surface.copy(alpha = .94f), RoundedCornerShape(28.dp))
                    .border(1.dp, HedefitColors.Divider, RoundedCornerShape(28.dp))
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthModeSwitch(login, onLogin = { edited { login = true; submitted = false } }, onSignUp = { edited { login = false; submitted = false } })
                    androidx.compose.animation.AnimatedVisibility(!login, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AuthTextField(username, { next -> edited { username = next.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20) } }, com.hedefit.app.ui.i18n.tr("Kullanıcı adı", "Username"), Icons.Default.AlternateEmail, KeyboardType.Ascii)
                        usernameStatusText(usernameStatus)?.let { (text, ok) ->
                            Text(text, color = if (ok) HedefitColors.Lime else HedefitColors.Coral, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 0.dp))
                        }
                    } }
                    AuthTextField(email, { next -> edited { email = next } }, com.hedefit.app.ui.i18n.tr("E-posta", "Email"), Icons.Default.Email, KeyboardType.Email)
                    AuthTextField(
                        password, { next -> edited { password = next } }, com.hedefit.app.ui.i18n.tr("Şifre", "Password"), Icons.Default.Lock, KeyboardType.Password,
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (reveal) com.hedefit.app.ui.i18n.tr("Şifreyi gizle", "Hide password") else com.hedefit.app.ui.i18n.tr("Şifreyi göster", "Show password"))
                            }
                        },
                    )
                    androidx.compose.animation.AnimatedVisibility(!login, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AuthTextField(passwordAgain, { next -> edited { passwordAgain = next } }, com.hedefit.app.ui.i18n.tr("Şifre tekrar", "Repeat password"), Icons.Default.Lock, KeyboardType.Password, PasswordVisualTransformation())
                        LegalAcceptanceFields(
                            kvkkAccepted = kvkkAccepted,
                            privacyAccepted = privacyAccepted,
                            onKvkkChange = { kvkkAccepted = it },
                            onPrivacyChange = { privacyAccepted = it },
                            onOpenDocument = { legalDocument = it },
                        )
                    } }
                    (localError ?: message)?.let {
                        Text(it, color = if (message?.contains("gönderildi", true) == true) HedefitColors.Lime else HedefitColors.Coral, style = MaterialTheme.typography.bodyMedium)
                    }
                    PrimaryButton(if (busy) com.hedefit.app.ui.i18n.tr("İşleniyor…", "Processing…") else if (login) com.hedefit.app.ui.i18n.tr("Giriş Yap", "Log In") else com.hedefit.app.ui.i18n.tr("Hesap Oluştur", "Create Account"), onClick = {
                        submitted = true
                        val usernameError = if (login || usernameStatus == "ok") null else usernameStatusText(usernameStatus ?: localUsernameStatus(username) ?: "checking")?.first
                        val error = validateAuthForm(email, password, passwordAgain, login, submitted = true) ?: usernameError ?: legalError(requireSubmission = false)
                        if (!busy && error == null) {
                            if (login) onSignIn(email.trim(), password)
                            else onSignUp(email.trim(), password, username, RegistrationLegalAcceptance(kvkkAccepted, privacyAccepted))
                        }
                    })
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.weight(1f).height(1.dp).background(HedefitColors.Divider))
                        Text(com.hedefit.app.ui.i18n.tr("veya", "or"), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.weight(1f).height(1.dp).background(HedefitColors.Divider))
                    }
                    GoogleSignInButton(disabled = busy, loading = googleBusy, onClick = {
                        if (login) onGoogleSignIn(null)
                        else if (kvkkAccepted && privacyAccepted) onGoogleSignIn(RegistrationLegalAcceptance(kvkkAccepted, privacyAccepted))
                        else submitted = true
                    })
                    Text(
                        if (login) com.hedefit.app.ui.i18n.tr("Hesabın yok mu? Kayıt ol", "No account? Sign up") else com.hedefit.app.ui.i18n.tr("Zaten hesabın var mı? Giriş yap", "Already have an account? Log in"),
                        color = HedefitColors.Lime,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickable { edited { login = !login; submitted = false } },
                    )
                }
            }
        }
    }
    legalDocument?.let { LegalDocumentDialog(it) { legalDocument = null } }
}

private enum class LegalDocument { Kvkk, Privacy }

@Composable
private fun LegalAcceptanceFields(
    kvkkAccepted: Boolean,
    privacyAccepted: Boolean,
    onKvkkChange: (Boolean) -> Unit,
    onPrivacyChange: (Boolean) -> Unit,
    onOpenDocument: (LegalDocument) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = kvkkAccepted, onCheckedChange = onKvkkChange)
            TextButton(onClick = { onOpenDocument(LegalDocument.Kvkk) }) { Text(com.hedefit.app.ui.i18n.tr("KVKK Aydınlatma Metni'ni okudum", "I have read the Privacy Notice (KVKK)")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = privacyAccepted, onCheckedChange = onPrivacyChange)
            TextButton(onClick = { onOpenDocument(LegalDocument.Privacy) }) { Text(com.hedefit.app.ui.i18n.tr("Gizlilik Politikası'nı okudum ve kabul ediyorum", "I have read and accept the Privacy Policy")) }
        }
    }
}

@Composable
private fun LegalDocumentDialog(document: LegalDocument, onDismiss: () -> Unit) {
    val (title, sections) = when (document) {
        LegalDocument.Kvkk -> com.hedefit.app.ui.i18n.tr("KVKK Aydınlatma Metni", "KVKK Privacy Notice") to kvkkNotice()
        LegalDocument.Privacy -> com.hedefit.app.ui.i18n.tr("Gizlilik Politikası", "Privacy Policy") to privacyPolicy()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(com.hedefit.app.ui.i18n.tr("Sürüm: ", "Version: ") + "${com.hedefit.app.data.auth.AuthRepository.LEGAL_DOCUMENT_VERSION}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                        Text(section.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Okudum") } },
    )
}

@Composable
private fun GoogleSignInButton(disabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .height(52.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFDADCE0), RoundedCornerShape(14.dp))
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(12.dp))
        Text(
            if (loading) "Google bekleniyor…" else com.hedefit.app.ui.i18n.tr("Google ile devam et", "Continue with Google"),
            color = Color(0xFF202124),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Giriş / Kayıt geçişi: altında kayan vurgu. */
@Composable
private fun AuthModeSwitch(login: Boolean, onLogin: () -> Unit, onSignUp: () -> Unit) {
    val offset by androidx.compose.animation.core.animateFloatAsState(if (login) 0f else 1f, androidx.compose.animation.core.spring(dampingRatio = .75f, stiffness = 380f), label = "authSwitch")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(50.dp).background(HedefitColors.SurfaceHigh, RoundedCornerShape(16.dp)).padding(4.dp)) {
        val half = maxWidth / 2
        Box(Modifier.width(half).fillMaxHeight().graphicsLayer { translationX = offset * half.toPx() }.background(Brush.horizontalGradient(listOf(HedefitColors.Lime, HedefitColors.LimeDark)), RoundedCornerShape(12.dp)))
        Row(Modifier.fillMaxSize()) {
            listOf(true to com.hedefit.app.ui.i18n.tr("Giriş Yap", "Log In"), false to com.hedefit.app.ui.i18n.tr("Kayıt Ol", "Sign Up")).forEach { (isLogin, label) ->
                val selected = isLogin == login
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).clickable { if (isLogin) onLogin() else onSignUp() }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) HedefitColors.OnLime else HedefitColors.TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Arka plan: yavaşça süzülen iki enerji halesi (açık ve koyu temada tema renkleriyle). */
@Composable
private fun AuthEnergyBackground() {
    val reduced = rememberReducedMotion()
    val t = if (reduced) 0f else rememberInfiniteTransition(label = "authBg").animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "drift").value
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawCircle(Brush.radialGradient(listOf(HedefitColors.Lime.copy(alpha = .26f), androidx.compose.ui.graphics.Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * (.15f + .25f * t), size.height * (.12f + .08f * t)), radius = size.width * .75f), radius = size.width * .75f, center = androidx.compose.ui.geometry.Offset(size.width * (.15f + .25f * t), size.height * (.12f + .08f * t)))
        drawCircle(Brush.radialGradient(listOf(HedefitColors.Water.copy(alpha = .18f), androidx.compose.ui.graphics.Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * (.9f - .3f * t), size.height * (.85f - .1f * t)), radius = size.width * .7f), radius = size.width * .7f, center = androidx.compose.ui.geometry.Offset(size.width * (.9f - .3f * t), size.height * (.85f - .1f * t)))
    }
}

/** Üst bölüm: nabız gibi atan logo halkası ve dönen motivasyon cümleleri. */
@Composable
private fun AuthHero(login: Boolean) {
    val reduced = rememberReducedMotion()
    val pulse = if (reduced) 0f else rememberInfiniteTransition(label = "authPulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1800)), label = "ring").value
    val lines = listOf(
        com.hedefit.app.ui.i18n.tr("Bugün 1. gün olsun.", "Make today day one."),
        com.hedefit.app.ui.i18n.tr("Her tekrar sayılır.", "Every rep counts."),
        com.hedefit.app.ui.i18n.tr("Güçlü ol, istikrarlı kal.", "Get strong, stay consistent."),
        com.hedefit.app.ui.i18n.tr("Küçük adımlar, büyük değişim.", "Small steps, big change."),
    )
    var index by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2800); index = (index + 1) % lines.size } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(128.dp).staggeredEntrance("authHero", 0), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(HedefitColors.Lime.copy(alpha = (1f - pulse) * .45f), radius = size.minDimension / 2f * (.62f + .38f * pulse), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                drawCircle(HedefitColors.Lime.copy(alpha = .14f), radius = size.minDimension / 2f * .6f)
            }
            Image(painterResource(R.drawable.app_logo_transparent), contentDescription = "Hedefit", modifier = Modifier.size(66.dp))
        }
        Text("HEDEFIT", color = HedefitColors.TextPrimary, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = 2.sp, modifier = Modifier.staggeredEntrance("authHero", 1))
        Spacer(Modifier.height(6.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = lines[index],
            transitionSpec = { (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut()) },
            label = "authLine",
        ) { line -> Text(line, color = HedefitColors.Lime, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(4.dp))
        Text(
            if (login) com.hedefit.app.ui.i18n.tr("Tekrar hoş geldin! Serin seni bekliyor.", "Welcome back! Your streak is waiting.")
            else com.hedefit.app.ui.i18n.tr("Hesabını oluştur, ilerlemen hep güvende olsun.", "Create your account and keep your progress safe."),
            color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AuthModeChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) HedefitColors.Lime else HedefitColors.SurfaceHigh, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) HedefitColors.OnLime else HedefitColors.TextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = HedefitColors.TextSecondary) },
        trailingIcon = trailing,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HedefitColors.Lime,
            unfocusedBorderColor = HedefitColors.Divider,
            focusedContainerColor = HedefitColors.SurfaceHigh,
            unfocusedContainerColor = HedefitColors.SurfaceHigh,
        ),
    )
}

@Composable
fun FullScreenLoader(message: String) {
    ScreenContainer {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = HedefitColors.Lime)
                Text(message, color = HedefitColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ConfigurationErrorScreen(message: String) {
    ScreenContainer {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            HedefitCard(Modifier.widthIn(max = 520.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(com.hedefit.app.ui.i18n.tr("Android yapılandırması eksik", "Android configuration missing"), style = MaterialTheme.typography.headlineSmall, color = HedefitColors.Coral)
                    Text(message)
                    Text("Kök .env dosyasında NEXT_PUBLIC_SUPABASE_URL ve NEXT_PUBLIC_SUPABASE_ANON_KEY değerlerini tanımlayıp uygulamayı yeniden derle.", color = HedefitColors.TextSecondary)
                }
            }
        }
    }
}

internal fun localUsernameStatus(username: String): String? = when {
    username.isBlank() -> "empty"
    username.length < 3 -> "too_short"
    !Regex("^[a-z0-9._]{3,20}$").matches(username) -> "invalid"
    else -> null
}

internal fun usernameStatusText(status: String?): Pair<String, Boolean>? = when (status) {
    null -> null
    "ok" -> "Bu kullanıcı adı kullanılabilir." to true
    "checking" -> "Kontrol ediliyor…" to true
    "empty" -> "Bir kullanıcı adı seç." to false
    "too_short" -> "Kullanıcı adı en az 3 karakter olmalı." to false
    "invalid" -> "Yalnızca küçük harf, rakam, nokta ve alt çizgi kullanabilirsin." to false
    "taken" -> "Bu kullanıcı adı alınmış." to false
    "blocked" -> "Bu kullanıcı adı uygun değil." to false
    else -> "Kullanıcı adı şu an kontrol edilemedi." to false
}

@Composable
fun UsernameSetupDialog(onCheck: (String, (String) -> Unit) -> Unit, onSave: (String, (String?) -> Unit) -> Unit) {
    var username by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(username) {
        status = null
        saveError = null
        if (username.isBlank()) return@LaunchedEffect
        localUsernameStatus(username)?.let { status = it; return@LaunchedEffect }
        kotlinx.coroutines.delay(450)
        status = "checking"
        onCheck(username) { status = it }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(com.hedefit.app.ui.i18n.tr("Kullanıcı adını seç", "Choose a username")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthTextField(username, { next -> username = next.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20) }, com.hedefit.app.ui.i18n.tr("Kullanıcı adı", "Username"), Icons.Default.AlternateEmail, KeyboardType.Ascii)
                (saveError?.let { it to false } ?: usernameStatusText(status))?.let { (text, ok) ->
                    Text(text, color = if (ok) HedefitColors.Lime else HedefitColors.Coral, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                enabled = status == "ok" && !saving,
                onClick = { saving = true; onSave(username) { error -> saving = false; saveError = error } },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) { Text(if (saving) "Kaydediliyor…" else com.hedefit.app.ui.i18n.tr("Kaydet", "Save")) }
        },
    )
}
