import SwiftUI
import Observation

@MainActor @Observable
final class CoachModel {
    var messages: [ChatMessage] = []
    var busy = false
    var usageUsed: Int?
    var usageLimit: Int?
    var activeContext: WorkoutCoachContext?
    var replacementBusy = false
    var planAdaptationBusy = false
    var planAdaptationResult: WorkoutAdaptationResult?

    private var app: AppModel { AppModel.shared }

    func greetIfNeeded() {
        guard messages.isEmpty else { return }
        let name = app.displayName
        messages = [ChatMessage(text: tr("Merhaba \(name)! Antrenman, beslenme veya ilerlemen hakkında bana bir şey sorabilirsin.", "Hi \(name)! Ask me anything about training, nutrition or your progress."), fromUser: false)]
        if usageLimit == nil { usageLimit = app.limits.dailyCoachQuestions }
    }

    func send(_ text: String, context: WorkoutCoachContext? = nil) async {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, !busy else { return }
        messages.append(ChatMessage(text: clean, fromUser: true))
        busy = true; defer { busy = false }
        do {
            let reply = try await app.repo.sendChat(messages: messages, dashboard: app.dashboard, locale: AppLang.shared.code, workoutContext: context ?? activeContext)
            messages.append(ChatMessage(text: reply.text, fromUser: false, actions: reply.actions, source: reply.source))
            usageUsed = reply.used ?? usageUsed; usageLimit = reply.limit ?? usageLimit
            if app.isGuest, let limit = usageLimit, (usageUsed ?? 0) >= limit { app.showSaveAccount = true }
            if shouldExtractMemory(replySource: reply.source, message: clean) { Task { _ = await app.repo.extractCoachMemory(message: clean, locale: AppLang.shared.code) } }
        } catch {
            let message = error.friendly
            messages.append(ChatMessage(text: tr("Fit Koç şu anda yanıtı tamamlayamadı: \(message)", "Fit Coach couldn't finish the reply: \(message)"), fromUser: false))
            app.errorToast = message
            if app.isGuest && (message.localizedCaseInsensitiveContains("limit") || message.localizedCaseInsensitiveContains("hak")) { app.showSaveAccount = true }
        }
    }

    func clear() { guard !busy else { return }; messages = []; greetIfNeeded(); app.notify(tr("Sohbet temizlendi.", "Chat cleared.")) }
}
