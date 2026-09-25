package com.hedefit.app.data.auth

import com.hedefit.app.BuildConfig
import com.hedefit.app.data.network.JsonHttpClient
import com.hedefit.app.data.network.requireSuccess
import com.hedefit.app.data.network.stringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

data class AuthUser(val id: String, val email: String, val emailVerified: Boolean, val isAnonymous: Boolean = false)

data class RegistrationLegalAcceptance(
    val kvkkNoticeAccepted: Boolean,
    val privacyPolicyAccepted: Boolean,
) {
    fun requireComplete() {
        require(kvkkNoticeAccepted) { "KVKK Aydınlatma Metni'ni onaylamalısın." }
        require(privacyPolicyAccepted) { "Gizlilik Politikası'nı onaylamalısın." }
    }
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val user: AuthUser,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accessToken", accessToken)
        .put("refreshToken", refreshToken)
        .put("expiresAt", expiresAtEpochSeconds)
        .put("user", JSONObject().put("id", user.id).put("email", user.email).put("verified", user.emailVerified).put("anonymous", user.isAnonymous))

    companion object {
        fun fromJson(json: JSONObject): AuthSession {
            val user = json.getJSONObject("user")
            return AuthSession(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                expiresAtEpochSeconds = json.getLong("expiresAt"),
                user = AuthUser(user.getString("id"), user.optString("email"), user.optBoolean("verified"), user.optBoolean("anonymous")),
            )
        }
    }
}

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val session: AuthSession) : AuthState
    data class ConfigurationError(val message: String) : AuthState
}

sealed interface SignUpResult {
    data class SignedIn(val session: AuthSession) : SignUpResult
    data object VerificationRequired : SignUpResult
}

class AuthRepository(
    private val store: SecureSessionStore,
    private val http: JsonHttpClient,
) {
    private val refreshMutex = Mutex()
    private var current: AuthSession? = null

    fun configurationError(): String? = when {
        BuildConfig.SUPABASE_URL.isBlank() -> "Supabase adresi Android derlemesine eklenmemiş."
        BuildConfig.SUPABASE_ANON_KEY.isBlank() -> "Supabase publishable/anon anahtarı Android derlemesine eklenmemiş."
        else -> null
    }

    suspend fun bootstrap(): AuthState {
        configurationError()?.let { return AuthState.ConfigurationError(it) }
        // Android Keystore may take hundreds of milliseconds on a cold start.
        // Never block Compose's first frame while decrypting the saved session.
        val saved = withContext(Dispatchers.IO) { store.read() } ?: return AuthState.SignedOut
        current = saved
        return runCatching {
            val token = validAccessToken()
            AuthState.SignedIn(requireNotNull(current).copy(accessToken = token))
        }.getOrElse { error ->
            // Yalnızca sunucu yenileme jetonunu açıkça reddederse oturumu sil. Ağ hatası,
            // zaman aşımı ya da yarıda kalan bir yenileme oturumu silmemeli: misafir
            // hesaplarda e-posta olmadığından silinen oturum geri getirilemez.
            if (error is com.hedefit.app.data.network.ApiException && error.status in setOf(400, 401, 403)) {
                clearLocalSession()
                AuthState.SignedOut
            } else AuthState.SignedIn(saved)
        }
    }

    suspend fun signIn(email: String, password: String): AuthSession {
        val response = http.request(
            url = authUrl("token?grant_type=password"),
            method = "POST",
            headers = authHeaders(),
            body = JSONObject().put("email", email.trim()).put("password", password).toString(),
        ).requireSuccess("Giriş yapılamadı.")
        return parseSession(response.jsonObject()).also(::save)
    }

    /**
     * Üye olmadan dene: Supabase anonim oturumu açar. Kullanıcı gerçek bir user_id alır,
     * böylece tablolar ve RLS kuralları olduğu gibi çalışır. Sonradan [linkEmail] veya
     * [linkGoogle] ile aynı hesap kalıcı hale getirilir; veriler kaybolmaz.
     * Supabase panelinde Authentication → Sign In / Providers → "Allow anonymous sign-ins" açık olmalı.
     */
    suspend fun signInAsGuest(legalAcceptance: RegistrationLegalAcceptance): AuthSession {
        legalAcceptance.requireComplete()
        val response = http.request(
            url = authUrl("signup"),
            method = "POST",
            headers = authHeaders(),
            body = JSONObject().put("data", legalAcceptancePayload().put("guest", true)).toString(),
        ).requireSuccess("Misafir oturumu açılamadı.")
        return parseSession(response.jsonObject()).also(::save)
    }

    /** Misafir hesaba e-posta/parola bağlar. Supabase doğrulama e-postası gönderir; doğrulanınca hesap kalıcı olur. */
    suspend fun linkEmail(email: String, password: String, username: String) {
        http.request(
            url = authUrl("user"),
            method = "PUT",
            headers = authHeaders() + ("Authorization" to "Bearer ${validAccessToken()}"),
            body = JSONObject().put("email", email.trim()).put("password", password)
                .put("data", JSONObject().put("username", username.trim().lowercase())).toString(),
        ).requireSuccess("Hesap kaydedilemedi.")
    }

    /** Misafir hesaba Google kimliği bağlar; aynı user_id korunur. */
    suspend fun linkGoogle(idToken: String, nonce: String): AuthSession {
        val response = http.request(
            url = authUrl("token?grant_type=id_token"),
            method = "POST",
            headers = authHeaders() + ("Authorization" to "Bearer ${validAccessToken()}"),
            body = JSONObject()
                .put("provider", "google")
                .put("id_token", idToken)
                .put("nonce", nonce)
                .put("link_identity", true)
                .toString(),
        ).requireSuccess("Google hesabı bağlanamadı.")
        return parseSession(response.jsonObject()).also(::save)
    }

    /** E-posta doğrulandıktan sonra oturumu yenileyip misafir bayrağını günceller. */
    suspend fun refreshSession(): AuthSession {
        validAccessToken(forceRefresh = true)
        return requireNotNull(current)
    }

    suspend fun checkUsername(username: String): String {
        val response = http.request(
            url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/rpc/hedefit_check_username",
            method = "POST",
            headers = authHeaders() + ("Authorization" to "Bearer ${current?.accessToken ?: BuildConfig.SUPABASE_ANON_KEY}"),
            body = JSONObject().put("p_username", username).toString(),
        ).requireSuccess("Kullanıcı adı kontrol edilemedi.")
        return response.body.trim().trim('"')
    }

    suspend fun signUp(email: String, password: String, username: String, legalAcceptance: RegistrationLegalAcceptance): SignUpResult {
        legalAcceptance.requireComplete()
        val response = http.request(
            url = authUrl("signup"),
            method = "POST",
            headers = authHeaders(),
            body = JSONObject().put("email", email.trim()).put("password", password)
                .put("data", legalAcceptancePayload().put("username", username.trim().lowercase())).toString(),
        ).requireSuccess("Kayıt oluşturulamadı.")
        val json = response.jsonObject()
        return if (json.stringOrNull("access_token") != null) {
            SignUpResult.SignedIn(parseSession(json).also(::save))
        } else SignUpResult.VerificationRequired
    }

    suspend fun signInWithGoogle(idToken: String, nonce: String, legalAcceptance: RegistrationLegalAcceptance? = null): AuthSession {
        legalAcceptance?.requireComplete()
        val response = http.request(
            url = authUrl("token?grant_type=id_token"),
            method = "POST",
            headers = authHeaders(),
            body = JSONObject()
                .put("provider", "google")
                .put("id_token", idToken)
                .put("nonce", nonce)
                .toString(),
        ).requireSuccess("Google ile giriş yapılamadı.")
        val json = response.jsonObject()
        val userJson = json.optJSONObject("user")
        val metadata = userJson?.optJSONObject("user_metadata")
        val isRegistered = metadata?.optString("kvkk_notice_version")?.isNotBlank() == true ||
            metadata?.optString("legal_accepted_at")?.isNotBlank() == true

        if (legalAcceptance != null) {
            val session = parseSession(json).also(::save)
            try {
                saveRegistrationLegalAcceptance()
            } catch (error: Throwable) {
                clearLocalSession()
                throw error
            }
            return session
        } else {
            if (!isRegistered) {
                throw IllegalStateException("Bu Google hesabına ait kayıtlı üyelik bulunamadı. Lütfen 'Kayıt Ol' sekmesinden KVKK ve sözleşmeleri onaylayarak kayıt olun.")
            }
            return parseSession(json).also(::save)
        }
    }

    private suspend fun saveRegistrationLegalAcceptance() {
        http.request(
            url = authUrl("user"),
            method = "PUT",
            headers = authHeaders() + ("Authorization" to "Bearer ${validAccessToken()}"),
            body = JSONObject().put("data", legalAcceptancePayload()).toString(),
        ).requireSuccess("Yasal onay kaydedilemedi.")
    }

    private fun legalAcceptancePayload() = JSONObject()
        .put("kvkk_notice_version", LEGAL_DOCUMENT_VERSION)
        .put("privacy_policy_version", LEGAL_DOCUMENT_VERSION)
        .put("legal_accepted_at", Instant.now().toString())


    suspend fun validAccessToken(forceRefresh: Boolean = false): String = refreshMutex.withLock {
        val session = current ?: store.read()?.also { current = it } ?: error("Oturum bulunamadı.")
        val now = System.currentTimeMillis() / 1000
        if (!forceRefresh && session.expiresAtEpochSeconds - now > 90) return@withLock session.accessToken
        // İstek gönderildikten sonra iptal edilirse sunucu jetonu döndürmüş ama biz kaydetmemiş
        // oluruz; NonCancellable ile istek ve kayıt birlikte tamamlanır.
        withContext(kotlinx.coroutines.NonCancellable) {
            val response = http.request(
                url = authUrl("token?grant_type=refresh_token"),
                method = "POST",
                headers = authHeaders(),
                body = JSONObject().put("refresh_token", session.refreshToken).toString(),
            ).requireSuccess("Oturum yenilenemedi.")
            parseSession(response.jsonObject()).also(::save).accessToken
        }
    }

    suspend fun signOut() {
        val token = runCatching { validAccessToken() }.getOrNull()
        if (token != null) runCatching {
            http.request(authUrl("logout"), "POST", authHeaders() + ("Authorization" to "Bearer $token"))
        }
        clearLocalSession()
    }

    fun userId(): String? = current?.user?.id
    fun currentSession(): AuthSession? = current

    private fun save(session: AuthSession) {
        current = session
        store.write(session)
    }

    private fun clearLocalSession() {
        current = null
        store.clear()
    }

    private fun parseSession(json: JSONObject): AuthSession {
        val access = json.getString("access_token")
        val refresh = json.getString("refresh_token")
        val expiresIn = json.optLong("expires_in", 3600)
        val userJson = json.getJSONObject("user")
        val identities = userJson.optJSONArray("identities")
        var providerVerified = false
        if (identities != null) {
            for (index in 0 until identities.length()) {
                val identity = identities.optJSONObject(index) ?: continue
                if (identity.optJSONObject("identity_data")?.optBoolean("email_verified") == true) providerVerified = true
            }
        }
        val user = AuthUser(
            id = userJson.getString("id"),
            email = userJson.optString("email"),
            emailVerified = userJson.stringOrNull("email_confirmed_at") != null || providerVerified,
            isAnonymous = userJson.optBoolean("is_anonymous"),
        )
        if (!user.emailVerified && !user.isAnonymous) error("E-posta adresini doğruladıktan sonra giriş yapabilirsin.")
        return AuthSession(access, refresh, System.currentTimeMillis() / 1000 + expiresIn, user)
    }

    private fun authUrl(path: String) = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/$path"
    private fun authHeaders() = mapOf("apikey" to BuildConfig.SUPABASE_ANON_KEY, "Content-Type" to "application/json")

    internal companion object {
        const val LEGAL_DOCUMENT_VERSION = "2026-09-23"
    }
}
