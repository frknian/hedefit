import Foundation
import StoreKit
import Observation

/// App Store ürün kimlikleri (App Store Connect'te oluşturulacak abonelik grubu "Hedefit").
enum BillingProduct {
    static let plusMonthly = "hedefit_plus_monthly", plusYearly = "hedefit_plus_yearly"
    static let premiumMonthly = "hedefit_premium_monthly", premiumYearly = "hedefit_premium_yearly"
    static let all = [plusMonthly, plusYearly, premiumMonthly, premiumYearly]

    static func id(_ tier: Tier, yearly: Bool) -> String {
        tier == .premium ? (yearly ? premiumYearly : premiumMonthly) : (yearly ? plusYearly : plusMonthly)
    }
}

/// StoreKit 2. Plan yalnızca sunucu doğrulamasından sonra açılır: istemci imzalı işlemi
/// (JWS) `/api/billing/apple/verify`'e gönderir, sunucu Apple zinciriyle doğrular.
@MainActor @Observable
final class BillingManager {
    static let shared = BillingManager()

    var products: [String: Product] = [:]
    var loading = false
    var loadFailed = false
    var busy = false
    var message: String?
    private var updatesTask: Task<Void, Never>?

    func start() {
        guard updatesTask == nil else { return }
        updatesTask = Task { [weak self] in
            for await result in Transaction.updates { await self?.handle(result, userInitiated: false) }
        }
    }

    func loadOffers() async {
        guard !loading else { return }
        loading = true; loadFailed = false; defer { loading = false }
        do {
            let list = try await Product.products(for: BillingProduct.all)
            products = Dictionary(list.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            loadFailed = products.isEmpty
        } catch { loadFailed = true }
    }

    func freeTrialDays(_ product: Product) -> Int? {
        guard let offer = product.subscription?.introductoryOffer, offer.paymentMode == .freeTrial else { return nil }
        let u = offer.period.value
        switch offer.period.unit { case .day: return u; case .week: return u * 7; case .month: return u * 30; case .year: return u * 365; @unknown default: return nil }
    }

    func purchase(_ product: Product, app: AppModel) async {
        guard !busy, !app.isGuest, let userId = await AuthService.shared.userId else { return }
        busy = true; message = nil; defer { busy = false }
        do {
            let token = UUID(uuidString: userId) ?? UUID()
            switch try await product.purchase(options: [.appAccountToken(token)]) {
            case .success(let verification): await handle(verification, userInitiated: true)
            case .pending: message = tr("Satın alma onay bekliyor. Onaylanınca paketin açılır.", "The purchase is awaiting approval. Your plan unlocks once it's approved.")
            case .userCancelled: break
            @unknown default: break
            }
        } catch { message = tr("Satın alma şu an başlatılamadı. App Store'u kontrol edip yeniden dene.", "Couldn't start the purchase. Check the App Store and try again.") }
    }

    /// Açılışta mevcut yetkileri sunucuya yeniden doğrulatır (başka cihaz / kesilen doğrulama).
    func restore(app: AppModel, userInitiated: Bool = false) async {
        guard !app.isGuest else { return }
        if userInitiated { busy = true; message = nil }
        defer { if userInitiated { busy = false } }
        if userInitiated { try? await AppStore.sync() }
        var found = false
        for await result in Transaction.currentEntitlements { found = true; await handle(result, userInitiated: userInitiated, quiet: !userInitiated) }
        if userInitiated && !found { message = tr("Geri yüklenecek aktif abonelik bulunamadı.", "No active subscription to restore.") }
    }

    private func handle(_ result: VerificationResult<Transaction>, userInitiated: Bool, quiet: Bool = false) async {
        let app = AppModel.shared
        guard case .verified(let transaction) = result, BillingProduct.all.contains(transaction.productID), !app.isGuest else { return }
        do {
            let response = try await HedefitAPI.shared.post("/api/billing/apple/verify", ["signedTransaction": JSON(result.jwsRepresentation), "productId": JSON(transaction.productID)] as JSON)
                .requireSuccess(trNow("Satın alma doğrulanamadı.", "Couldn't verify the purchase."))
            if response.status == 202 { message = tr("Ödeme henüz tamamlanmadı.", "The payment hasn't completed yet."); return }
            await transaction.finish()
            await app.refreshAll()
            if !quiet { message = tr("Paketin etkinleştirildi. Teşekkürler!", "Your plan is active. Thank you!") }
        } catch {
            // `finish` çağrılmaz: Transaction.updates / currentEntitlements yeniden dener.
            if !quiet { message = error.friendly }
        }
    }

    func manageSubscriptions() async {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        try? await AppStore.showManageSubscriptions(in: scene)
    }
}
