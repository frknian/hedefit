import Foundation
import UIKit
import GoogleSignIn

/// Google'ın native iOS SDK'sıyla kimlik doğrular, Supabase'in `id_token`
/// grant'ına gönderilecek idToken'ı döner. GoogleSignIn-iOS 7.x'in genel
/// `signIn` API'si nonce parametresi almıyor (Android'in Credential Manager
/// API'sinden farklı); Supabase tarafında nonce isteğe bağlı olduğundan
/// gönderilmez — imza/audience/süre doğrulaması yine de yapılır.
@MainActor
enum GoogleAuthService {
    static func signIn() async throws -> String {
        let clientID = AppConfiguration.value("GOOGLE_IOS_CLIENT_ID")
        guard !clientID.isEmpty else {
            throw AppError.configuration("Google girişi için GOOGLE_IOS_CLIENT_ID yapılandırılmamış (Config.xcconfig).")
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let presenter = scene.windows.first(where: \.isKeyWindow)?.rootViewController else {
            throw AppError.invalidResponse
        }
        return try await withCheckedThrowingContinuation { continuation in
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                if let error {
                    continuation.resume(throwing: AppError.server((error as NSError).code == -5 ? "Google hesap seçimi kapatıldı." : error.localizedDescription))
                    return
                }
                guard let idToken = result?.user.idToken?.tokenString else {
                    continuation.resume(throwing: AppError.server("Google kimlik jetonu alınamadı."))
                    return
                }
                continuation.resume(returning: idToken)
            }
        }
    }
}
