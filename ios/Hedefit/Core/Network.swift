import Foundation
import Security

// MARK: - Yapılandırma

enum AppConfiguration {
    static func value(_ key: String, fallback: String = "") -> String {
        let value = (Bundle.main.object(forInfoDictionaryKey: key) as? String)?.trimmingCharacters(in: .whitespaces) ?? fallback
        return value.hasPrefix("$(") || value.isEmpty ? fallback : value
    }
    static var apiBase: String { value("HEDEFIT_API_BASE_URL", fallback: "https://hedefit.frknian.workers.dev").trimmingCharacters(in: CharacterSet(charactersIn: "/")) }
    static var supabaseURL: String { value("SUPABASE_URL").trimmingCharacters(in: CharacterSet(charactersIn: "/")) }
    static var anonKey: String { value("SUPABASE_ANON_KEY") }
    static var configurationError: String? {
        if supabaseURL.isEmpty { return trNow("Supabase adresi iOS derlemesine eklenmemiş.", "The Supabase URL is missing from this iOS build.") }
        if anonKey.isEmpty { return trNow("Supabase anahtarı iOS derlemesine eklenmemiş.", "The Supabase key is missing from this iOS build.") }
        return nil
    }
    static let legalDocumentVersion = "2026-10-03"
    static let appGroup = "group.com.hedefit.app"
}

// MARK: - HTTP

struct HTTPResult: Sendable {
    let status: Int
    let data: Data
    var isSuccessful: Bool { (200..<300).contains(status) }
    var body: String { String(decoding: data, as: UTF8.self) }
    var json: JSON { JSON.parse(data) }
}

struct ApiError: LocalizedError, Sendable {
    let status: Int
    let body: String
    let message: String
    var errorDescription: String? { message }
    /// Sunucunun `error` kodu (ör. "already_joined").
    var code: String? { JSON.parse(body).nonEmptyString("error") }
}

enum AppError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let value) = self { return value }; return nil }
}

extension HTTPResult {
    /// Başarısızsa sunucu mesajıyla (`error_description` → `msg` → `message` → `error`) `ApiError` fırlatır.
    @discardableResult
    func requireSuccess(_ fallback: String) throws -> HTTPResult {
        if isSuccessful { return self }
        let object = json
        let message = [object.string("error_description"), object.string("msg"), object.string("message"), object.string("error")]
            .first(where: { !$0.isEmpty }) ?? fallback
        throw ApiError(status: status, body: body, message: message)
    }
}

enum HTTP {
    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = 60
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: configuration)
    }()

    static func request(_ url: String, method: String = "GET", headers: [String: String] = [:], body: Data? = nil, timeout: TimeInterval = 30) async throws -> HTTPResult {
        guard let target = URL(string: url) else { throw AppError.message(trNow("Geçersiz adres.", "Invalid address.")) }
        var request = URLRequest(url: target, timeoutInterval: timeout)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        request.httpBody = body
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw AppError.message(trNow("Sunucudan geçersiz yanıt alındı.", "Invalid response from the server.")) }
        return HTTPResult(status: http.statusCode, data: data)
    }

    static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))) ?? value
    }
}

// MARK: - Oturum

struct AuthUser: Codable, Sendable, Equatable {
    var id: String
    var email: String
    var emailVerified: Bool
    var isAnonymous: Bool
}

struct AuthSession: Codable, Sendable, Equatable {
    var accessToken: String
    var refreshToken: String
    var expiresAt: TimeInterval
    var user: AuthUser
}

enum KeychainSessionStore {
    private static let service = "com.hedefit.app.session", account = "current"
    private static var base: [CFString: Any] { [kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: account] }

    static func read() -> AuthSession? {
        var query = base
        query[kSecReturnData] = true
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(AuthSession.self, from: data)
    }

    static func write(_ session: AuthSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        SecItemDelete(base as CFDictionary)
        var row = base
        row[kSecValueData] = data
        row[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(row as CFDictionary, nil)
    }

    static func clear() { SecItemDelete(base as CFDictionary) }
}

struct LegalAcceptance: Sendable {
    var kvkkNotice: Bool, privacyPolicy: Bool, healthData: Bool, crossBorder: Bool
    func validate() throws {
        if !kvkkNotice { throw AppError.message(trNow("KVKK Aydınlatma Metni'ni onaylamalısın.", "You must accept the KVKK notice.")) }
        if !privacyPolicy { throw AppError.message(trNow("Gizlilik Politikası'nı onaylamalısın.", "You must accept the Privacy Policy.")) }
        if !healthData { throw AppError.message(trNow("Sağlık verilerinin işlenmesine açık rıza vermelisin.", "You must give explicit consent to health data processing.")) }
        if !crossBorder { throw AppError.message(trNow("Verilerin yurt dışına aktarılmasına açık rıza vermelisin.", "You must give explicit consent to cross-border data transfer.")) }
    }
}

struct ConsentStatus: Sendable, Equatable {
    var healthDataConsentAt: String?
    var crossBorderConsentAt: String?
    var textVersion: String?
    var withdrawnAt: String?
}

/// Supabase Auth (GoTrue) REST istemcisi; Android `AuthRepository` karşılığı.
actor AuthService {
    static let shared = AuthService()

    private var current: AuthSession?
    private var refreshTask: Task<AuthSession, Error>?

    private func authURL(_ path: String) -> String { "\(AppConfiguration.supabaseURL)/auth/v1/\(path)" }
    private var anonHeaders: [String: String] { ["apikey": AppConfiguration.anonKey] }

    private func authorized(_ extra: [String: String] = [:]) async throws -> [String: String] {
        var headers = anonHeaders
        headers["Authorization"] = "Bearer \(try await validAccessToken())"
        extra.forEach { headers[$0.key] = $0.value }
        return headers
    }

    // MARK: Oturum yaşam döngüsü

    enum Bootstrap { case signedOut, signedIn(AuthSession), configurationError(String) }

    func bootstrap() async -> Bootstrap {
        if let message = AppConfiguration.configurationError { return .configurationError(message) }
        guard let saved = KeychainSessionStore.read() else { return .signedOut }
        current = saved
        do {
            let token = try await validAccessToken()
            var session = current ?? saved
            session.accessToken = token
            return .signedIn(session)
        } catch let error as ApiError where [400, 401, 403].contains(error.status) {
            // Yalnızca sunucu yenileme jetonunu açıkça reddederse oturumu sil: ağ hatası misafir hesabı yok etmemeli.
            clearLocal()
            return .signedOut
        } catch {
            return .signedIn(saved)
        }
    }

    var userId: String? { current?.user.id }
    var session: AuthSession? { current }

    func validAccessToken(forceRefresh: Bool = false) async throws -> String {
        guard let session = current ?? KeychainSessionStore.read() else { throw AppError.message(trNow("Oturum bulunamadı.", "No session found.")) }
        current = session
        if !forceRefresh && session.expiresAt - Date().timeIntervalSince1970 > 90 { return session.accessToken }
        if let refreshTask { return try await refreshTask.value.accessToken }
        let task = Task<AuthSession, Error> { [anonHeaders, url = authURL("token?grant_type=refresh_token")] in
            let response = try await HTTP.request(url, method: "POST", headers: anonHeaders, body: ["refresh_token": JSON(session.refreshToken)].data())
                .requireSuccess(trNow("Oturum yenilenemedi.", "Couldn't refresh the session."))
            return try Self.parseSession(response.json)
        }
        refreshTask = task
        defer { refreshTask = nil }
        let refreshed = try await task.value
        save(refreshed)
        return refreshed.accessToken
    }

    private func save(_ session: AuthSession) { current = session; KeychainSessionStore.write(session) }
    private func clearLocal() { current = nil; KeychainSessionStore.clear() }

    // MARK: Giriş / kayıt

    func signIn(email: String, password: String) async throws -> AuthSession {
        let response = try await HTTP.request(authURL("token?grant_type=password"), method: "POST", headers: anonHeaders,
            body: ["email": JSON(email.trimmingCharacters(in: .whitespaces)), "password": JSON(password)].data()).requireSuccess(trNow("Giriş yapılamadı.", "Couldn't sign in."))
        let session = try Self.parseSession(response.json); save(session); return session
    }

    func signInAsGuest(_ legal: LegalAcceptance) async throws -> AuthSession {
        try legal.validate()
        var meta = legalPayload(); meta["guest"] = JSON(true)
        let response = try await HTTP.request(authURL("signup"), method: "POST", headers: anonHeaders, body: ["data": JSON.object(meta)].data())
            .requireSuccess(trNow("Misafir oturumu açılamadı.", "Couldn't start a guest session."))
        let session = try Self.parseSession(response.json); save(session); return session
    }

    enum SignUpResult { case signedIn(AuthSession), verificationRequired }

    func signUp(email: String, password: String, username: String, legal: LegalAcceptance) async throws -> SignUpResult {
        try legal.validate()
        var meta = legalPayload(); meta["username"] = JSON(username.trimmingCharacters(in: .whitespaces).lowercased())
        let response = try await HTTP.request(authURL("signup"), method: "POST", headers: anonHeaders,
            body: ["email": JSON(email.trimmingCharacters(in: .whitespaces)), "password": JSON(password), "data": JSON.object(meta)].data())
            .requireSuccess(trNow("Kayıt oluşturulamadı.", "Couldn't create the account."))
        if response.json.nonEmptyString("access_token") != nil {
            let session = try Self.parseSession(response.json); save(session); return .signedIn(session)
        }
        return .verificationRequired
    }

    /// Google / Apple id_token'ını Supabase oturumuna çevirir. `legal` doluysa kayıt akışıdır.
    func signInWithIdToken(provider: String, idToken: String, nonce: String?, legal: LegalAcceptance?) async throws -> AuthSession {
        try legal?.validate()
        var body: [String: JSON] = ["provider": JSON(provider), "id_token": JSON(idToken)]
        if let nonce { body["nonce"] = JSON(nonce) }
        let response = try await HTTP.request(authURL("token?grant_type=id_token"), method: "POST", headers: anonHeaders, body: JSON.object(body).data())
            .requireSuccess(provider == "apple" ? trNow("Apple ile giriş yapılamadı.", "Couldn't sign in with Apple.") : trNow("Google ile giriş yapılamadı.", "Couldn't sign in with Google."))
        let meta = response.json["user"]["user_metadata"]
        let registered = meta.nonEmptyString("kvkk_notice_version") != nil || meta.nonEmptyString("legal_accepted_at") != nil
        if legal == nil && !registered {
            throw AppError.message(trNow("Bu hesaba ait kayıtlı üyelik bulunamadı. Lütfen 'Kayıt Ol' sekmesinden KVKK ve sözleşmeleri onaylayarak kayıt olun.", "No registered account was found. Please sign up from the 'Sign up' tab and accept the legal terms."))
        }
        let session = try Self.parseSession(response.json); save(session)
        if legal != nil {
            do { try await putUserData(legalPayload()) } catch { clearLocal(); throw error }
        }
        return session
    }

    /// Misafir hesaba e-posta/parola bağlar (doğrulama e-postası gider).
    func linkEmail(email: String, password: String, username: String) async throws {
        try await HTTP.request(authURL("user"), method: "PUT", headers: try await authorized(),
            body: ["email": JSON(email.trimmingCharacters(in: .whitespaces)), "password": JSON(password), "data": ["username": JSON(username.trimmingCharacters(in: .whitespaces).lowercased())].json].data())
            .requireSuccess(trNow("Hesap kaydedilemedi.", "Couldn't save the account."))
    }

    func linkIdentity(provider: String, idToken: String, nonce: String?) async throws -> AuthSession {
        var body: [String: JSON] = ["provider": JSON(provider), "id_token": JSON(idToken), "link_identity": JSON(true)]
        if let nonce { body["nonce"] = JSON(nonce) }
        let response = try await HTTP.request(authURL("token?grant_type=id_token"), method: "POST", headers: try await authorized(), body: JSON.object(body).data())
            .requireSuccess(trNow("Hesap bağlanamadı.", "Couldn't link the account."))
        let session = try Self.parseSession(response.json); save(session); return session
    }

    func refreshSession() async throws -> AuthSession {
        _ = try await validAccessToken(forceRefresh: true)
        guard let current else { throw AppError.message(trNow("Oturum bulunamadı.", "No session found.")) }
        return current
    }

    func signOut() async {
        if let headers = try? await authorized() { _ = try? await HTTP.request(authURL("logout"), method: "POST", headers: headers) }
        clearLocal()
    }

    func checkUsername(_ username: String) async throws -> String {
        var headers = anonHeaders
        headers["Authorization"] = "Bearer \(current?.accessToken ?? AppConfiguration.anonKey)"
        let response = try await HTTP.request("\(AppConfiguration.supabaseURL)/rest/v1/rpc/hedefit_check_username", method: "POST", headers: headers,
            body: ["p_username": JSON(username)].data()).requireSuccess(trNow("Kullanıcı adı kontrol edilemedi.", "Couldn't check the username."))
        return response.body.trimmingCharacters(in: CharacterSet(charactersIn: "\" \n"))
    }

    // MARK: Rızalar

    func consentStatus() async throws -> ConsentStatus {
        let response = try await HTTP.request(authURL("user"), headers: try await authorized()).requireSuccess(trNow("Rıza durumu okunamadı.", "Couldn't read consent status."))
        let meta = response.json["user_metadata"]
        return ConsentStatus(healthDataConsentAt: meta.nonEmptyString("health_data_consent_at"), crossBorderConsentAt: meta.nonEmptyString("cross_border_consent_at"),
                             textVersion: meta.nonEmptyString("consent_text_version"), withdrawnAt: meta.nonEmptyString("consent_withdrawn_at"))
    }

    func hasExplicitConsents() async throws -> Bool {
        let status = try await consentStatus()
        return status.healthDataConsentAt != nil && status.crossBorderConsentAt != nil
    }

    func saveExplicitConsents() async throws {
        let now = ISO.string()
        try await putUserData(["consent_text_version": JSON(AppConfiguration.legalDocumentVersion), "health_data_consent_at": JSON(now), "cross_border_consent_at": JSON(now)])
    }

    func withdrawConsents(health: Bool, crossBorder: Bool) async throws {
        guard health || crossBorder else { throw AppError.message(trNow("Geri çekilecek en az bir rıza seçilmeli.", "Select at least one consent to withdraw.")) }
        var patch: [String: JSON] = ["consent_withdrawn_at": JSON(ISO.string())]
        if health { patch["health_data_consent_at"] = JSON("") }
        if crossBorder { patch["cross_border_consent_at"] = JSON("") }
        try await putUserData(patch)
    }

    private func putUserData(_ data: [String: JSON]) async throws {
        try await HTTP.request(authURL("user"), method: "PUT", headers: try await authorized(), body: ["data": JSON.object(data)].data())
            .requireSuccess(trNow("Kaydedilemedi.", "Couldn't save."))
    }

    private func legalPayload() -> [String: JSON] {
        let now = ISO.string(), version = AppConfiguration.legalDocumentVersion
        return ["kvkk_notice_version": JSON(version), "privacy_policy_version": JSON(version), "legal_accepted_at": JSON(now),
                "consent_text_version": JSON(version), "health_data_consent_at": JSON(now), "cross_border_consent_at": JSON(now)]
    }

    // MARK: Ayrıştırma

    static func parseSession(_ json: JSON) throws -> AuthSession {
        guard let access = json.nonEmptyString("access_token"), let refresh = json.nonEmptyString("refresh_token"), let id = json["user"].nonEmptyString("id") else {
            throw AppError.message(trNow("Sunucudan geçersiz oturum yanıtı alındı.", "Invalid session response from the server."))
        }
        let user = json["user"]
        let providerVerified = user["identities"].items.contains { $0["identity_data"].bool("email_verified") }
        let verified = user.nonEmptyString("email_confirmed_at") != nil || providerVerified
        let anonymous = user.bool("is_anonymous")
        if !verified && !anonymous { throw AppError.message(trNow("E-posta adresini doğruladıktan sonra giriş yapabilirsin.", "Please verify your email address before signing in.")) }
        return AuthSession(accessToken: access, refreshToken: refresh, expiresAt: Date().timeIntervalSince1970 + json.double("expires_in", 3600),
                           user: AuthUser(id: id, email: user.string("email"), emailVerified: verified, isAnonymous: anonymous))
    }
}

// MARK: - Supabase REST (PostgREST)

actor SupabaseREST {
    static let shared = SupabaseREST()
    private let auth = AuthService.shared

    private func send(_ path: String, method: String = "GET", body: JSON? = nil, prefer: String? = nil) async throws -> HTTPResult {
        func execute(_ token: String) async throws -> HTTPResult {
            var headers = ["apikey": AppConfiguration.anonKey, "Authorization": "Bearer \(token)"]
            if let prefer { headers["Prefer"] = prefer }
            return try await HTTP.request("\(AppConfiguration.supabaseURL)/rest/v1/\(path)", method: method, headers: headers, body: body?.data())
        }
        let first = try await execute(try await auth.validAccessToken())
        return first.status == 401 ? try await execute(try await auth.validAccessToken(forceRefresh: true)) : first
    }

    func select(_ table: String, _ query: String) async throws -> [JSON] {
        try await send("\(table)?\(query)").requireSuccess(trNow("\(table) verileri yüklenemedi.", "Couldn't load \(table).")).json.items
    }

    @discardableResult
    func insert(_ table: String, _ row: JSON) async throws -> JSON {
        try await send(table, method: "POST", body: row, prefer: "return=representation").requireSuccess(trNow("\(table) kaydı oluşturulamadı.", "Couldn't create \(table) record.")).json[0]
    }

    @discardableResult
    func upsert(_ table: String, _ row: JSON, onConflict: String) async throws -> JSON {
        try await send("\(table)?on_conflict=\(HTTP.encode(onConflict))", method: "POST", body: row, prefer: "resolution=merge-duplicates,return=representation")
            .requireSuccess(trNow("\(table) kaydı güncellenemedi.", "Couldn't update \(table).")).json[0]
    }

    @discardableResult
    func update(_ table: String, _ query: String, _ row: JSON) async throws -> JSON {
        try await send("\(table)?\(query)", method: "PATCH", body: row, prefer: "return=representation")
            .requireSuccess(trNow("\(table) verileri güncellenemedi.", "Couldn't update \(table).")).json[0]
    }

    func delete(_ table: String, _ query: String) async throws {
        try await send("\(table)?\(query)", method: "DELETE", prefer: "return=minimal").requireSuccess(trNow("\(table) kaydı silinemedi.", "Couldn't delete \(table) record."))
    }

    /// Storage'a ham bayt yükler (profil fotoğrafı).
    func upload(bucket: String, path: String, bytes: Data, mime: String) async throws {
        let token = try await auth.validAccessToken()
        guard let url = URL(string: "\(AppConfiguration.supabaseURL)/storage/v1/object/\(bucket)/\(path)") else { return }
        var request = URLRequest(url: url, timeoutInterval: 45)
        request.httpMethod = "POST"
        request.setValue(AppConfiguration.anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue(mime, forHTTPHeaderField: "Content-Type")
        request.setValue("true", forHTTPHeaderField: "x-upsert")
        request.httpBody = bytes
        let (data, response) = try await URLSession.shared.data(for: request)
        let result = HTTPResult(status: (response as? HTTPURLResponse)?.statusCode ?? 0, data: data)
        try result.requireSuccess(trNow("Profil fotoğrafı yüklenemedi.", "Couldn't upload the profile photo."))
    }

    func signedURL(bucket: String, path: String, expiresIn: Int = 86_400) async throws -> String {
        let token = try await auth.validAccessToken()
        let encoded = path.split(separator: "/").map { HTTP.encode(String($0)) }.joined(separator: "/")
        let response = try await HTTP.request("\(AppConfiguration.supabaseURL)/storage/v1/object/sign/\(bucket)/\(encoded)", method: "POST",
            headers: ["apikey": AppConfiguration.anonKey, "Authorization": "Bearer \(token)"], body: ["expiresIn": JSON(expiresIn)].data())
            .requireSuccess(trNow("Profil fotoğrafı hazırlanamadı.", "Couldn't prepare the profile photo."))
        let signed = [response.json.string("signedURL"), response.json.string("signedUrl")].first(where: { !$0.isEmpty }) ?? ""
        guard !signed.isEmpty else { throw AppError.message(trNow("Profil fotoğrafı bağlantısı oluşturulamadı.", "Couldn't build the profile photo link.")) }
        return signed.hasPrefix("http") ? signed : "\(AppConfiguration.supabaseURL)/storage/v1\(signed)"
    }
}

// MARK: - Hedefit API (Cloudflare Worker)

actor HedefitAPI {
    static let shared = HedefitAPI()
    private let auth = AuthService.shared

    private func timeout(for path: String) -> TimeInterval {
        if path.contains("generate-plan") { return 75 }
        if path.contains("/api/nutrition/parse-text") { return 50 }
        if path.contains("/api/nutrition/analyze-photo") || path.contains("/api/equipment/recognize") { return 70 }
        if path.contains("/api/chat") { return 30 }
        return 40
    }

    func request(_ path: String, method: String, body: JSON? = nil) async throws -> HTTPResult {
        let url = "\(AppConfiguration.apiBase)/\(path.hasPrefix("/") ? String(path.dropFirst()) : path)"
        func execute(_ token: String) async throws -> HTTPResult {
            try await HTTP.request(url, method: method, headers: ["Authorization": "Bearer \(token)"], body: body?.data(), timeout: timeout(for: path))
        }
        let first = try await execute(try await auth.validAccessToken())
        return first.status == 401 ? try await execute(try await auth.validAccessToken(forceRefresh: true)) : first
    }

    func get(_ path: String) async throws -> HTTPResult { try await request(path, method: "GET") }
    func post(_ path: String, _ body: JSON) async throws -> HTTPResult { try await request(path, method: "POST", body: body) }
    func put(_ path: String, _ body: JSON) async throws -> HTTPResult { try await request(path, method: "PUT", body: body) }
    func patch(_ path: String, _ body: JSON) async throws -> HTTPResult { try await request(path, method: "PATCH", body: body) }
    func delete(_ path: String) async throws -> HTTPResult { try await request(path, method: "DELETE") }
}
