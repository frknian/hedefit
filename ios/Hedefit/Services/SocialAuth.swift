import Foundation
import UIKit
import AuthenticationServices
import CryptoKit
import GoogleSignIn

struct SocialCredential { var idToken: String; var nonce: String?; var fullName: String? }

@MainActor
enum GoogleAuthService {
    /// Google'ın native SDK'sıyla kimlik doğrular. GoogleSignIn-iOS 7.x nonce almaz; Supabase'de nonce isteğe bağlıdır.
    static func signIn() async throws -> SocialCredential {
        let clientID = AppConfiguration.value("GOOGLE_IOS_CLIENT_ID")
        guard !clientID.isEmpty else { throw AppError.message(tr("Google girişi için GOOGLE_IOS_CLIENT_ID yapılandırılmamış (Config.xcconfig).", "Google sign-in isn't configured (GOOGLE_IOS_CLIENT_ID).")) }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let presenter = UIApplication.topViewController() else { throw AppError.message(tr("Giriş ekranı açılamadı.", "Couldn't open the sign-in screen.")) }
        return try await withCheckedThrowingContinuation { continuation in
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                if let error {
                    let canceled = (error as NSError).code == GIDSignInError.canceled.rawValue
                    continuation.resume(throwing: AppError.message(canceled ? tr("Google hesap seçimi kapatıldı.", "Google account selection was dismissed.") : error.localizedDescription))
                    return
                }
                guard let token = result?.user.idToken?.tokenString else { continuation.resume(throwing: AppError.message(tr("Google kimlik jetonu alınamadı.", "Couldn't get the Google identity token."))); return }
                continuation.resume(returning: SocialCredential(idToken: token, nonce: nil, fullName: result?.user.profile?.name))
            }
        }
    }
}

/// Apple ile Giriş (App Store kuralı 4.8: üçüncü taraf girişle birlikte sunulmalı).
@MainActor
final class AppleAuthService: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<SocialCredential, Error>?
    private var rawNonce = ""

    static func signIn() async throws -> SocialCredential { try await AppleAuthService().run() }

    private func run() async throws -> SocialCredential {
        rawNonce = Self.randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(rawNonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self; controller.presentationContextProvider = self
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            controller.performRequests()
            // ARC: denetleyici bitene kadar canlı kalsın
            objc_setAssociatedObject(self, &AppleAuthService.key, controller, .OBJC_ASSOCIATION_RETAIN)
        }
    }
    private static var key = 0

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential, let data = credential.identityToken, let token = String(data: data, encoding: .utf8) else {
            continuation?.resume(throwing: AppError.message(tr("Apple kimlik jetonu alınamadı.", "Couldn't get the Apple identity token."))); continuation = nil; return
        }
        let name = [credential.fullName?.givenName, credential.fullName?.familyName].compactMap { $0 }.joined(separator: " ")
        continuation?.resume(returning: SocialCredential(idToken: token, nonce: rawNonce, fullName: name.isEmpty ? nil : name)); continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let canceled = (error as? ASAuthorizationError)?.code == .canceled
        continuation?.resume(throwing: AppError.message(canceled ? tr("Apple girişi iptal edildi.", "Apple sign-in was cancelled.") : error.localizedDescription)); continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        return String(bytes.map { charset[Int($0) % charset.count] })
    }
    private static func sha256(_ input: String) -> String { SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined() }
}

extension UIApplication {
    @MainActor static func topViewController() -> UIViewController? {
        var top = shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
