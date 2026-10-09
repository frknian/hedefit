import SwiftUI

/// Uygulamalı başlangıç rehberi: her görev ilgili ekrana götürür; yapıldıkça veriye göre otomatik tamamlanır.
struct WelcomeGuideView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss

    private struct Mission { let key: String; let icon: String; let tint: Color; let title: String; let body: String; let cta: String }

    private var missions: [Mission] {
        let coach = app.prefs.coachName.isEmpty ? tr("FitKoç", "Fit Coach") : app.prefs.coachName
        return [
            .init(key: "workout", icon: "dumbbell.fill", tint: HC.lime, title: tr("İlk antrenmanını başlat", "Start your first workout"), body: tr("Programın hazır. Bir seti tamamla, sayacın ve XP'nin nasıl çalıştığını gör.", "Your program is ready. Finish a set and see the timer and XP in action."), cta: tr("Antrenmana git", "Go to workout")),
            .init(key: "nutrition", icon: "fork.knife", tint: HC.warning, title: tr("İlk öğününü ekle", "Log your first meal"), body: tr("Yaz, fotoğraf çek ya da ara. Kalori ve makrolar otomatik hesaplanır.", "Type it, snap a photo or search. Calories and macros are calculated for you."), cta: tr("Öğün ekle", "Add a meal")),
            .init(key: "cardio", icon: "figure.run", tint: HC.coral, title: tr("Kardiyoyu dene", "Try cardio"), body: tr("Koşu bandı, bisiklet ya da açık hava. Yaktığın kalori günlük hedefine eklenir.", "Treadmill, bike or outdoors. Burned calories are added to your daily target."), cta: tr("Kardiyoyu aç", "Open cardio")),
            .init(key: "musclemap", icon: "figure.arms.open", tint: HC.lime, title: tr("Kas haritasını dene", "Try the muscle map"), body: tr("Bir kasa dokun, o kasın hareketlerini animasyonlarıyla gör.", "Tap a muscle and see its exercises with animations."), cta: tr("Aç", "Open")),
            .init(key: "coach", icon: "sparkles", tint: HC.sleep, title: tr("\(coach)'a bir soru sor", "Ask \(coach) a question"), body: tr("Antrenman, beslenme ya da motivasyon: seni tanıyan koçuna sor.", "Training, food or motivation: ask the coach who knows you."), cta: tr("Soru sor", "Ask")),
            .init(key: "reminders", icon: "bell.badge.fill", tint: HC.water, title: tr("Hatırlatma kur", "Set a reminder"), body: tr("Antrenman günlerinde seni programına geri getirir.", "Brings you back to your plan on training days."), cta: tr("Hatırlatma kur", "Set reminder")),
            .init(key: "healthconnect", icon: "applewatch", tint: HC.lime, title: tr("Saatini ya da adımlarını bağla", "Connect your watch or steps"), body: tr("Adım, uyku ve nabız Apple Sağlık'tan otomatik gelsin.", "Get steps, sleep and heart rate automatically from Apple Health."), cta: tr("Bağla", "Connect")),
        ]
    }

    var body: some View {
        let done = app.guideCompleted
        let count = missions.filter { done.contains($0.key) }.count
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(tr("Hedefit'i birlikte keşfedelim", "Let's explore Hedefit together")).font(.hfHeadline.weight(.black)).foregroundStyle(HC.text)
                            Text(tr("Her görevi gerçekten yaparak öğren. Yaptıkça otomatik işaretlenir.", "Learn by doing. Each mission ticks itself off when you complete it.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                        }
                        Spacer(minLength: 12)
                        ZStack { HfRing(progress: Double(count) / Double(missions.count), lineWidth: 7).frame(width: 64, height: 64); Text("\(count)/\(missions.count)").font(.system(size: 14, weight: .black)).foregroundStyle(HC.text) }
                    }
                    ForEach(missions, id: \.key) { m in
                        let isDone = done.contains(m.key)
                        Button { if !isDone { act(m.key) } } label: {
                            HStack(spacing: 12) {
                                ZStack { Circle().fill(isDone ? HC.lime : m.tint.opacity(0.16)).frame(width: 44, height: 44); Image(systemName: isDone ? "checkmark" : m.icon).foregroundStyle(isDone ? HC.onLime : m.tint) }
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(m.title).font(.system(size: 16, weight: .bold)).foregroundStyle(isDone ? HC.muted : HC.text).strikethrough(isDone)
                                    if !isDone { Text(m.body).font(.system(size: 13)).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading) }
                                }
                                Spacer(minLength: 6)
                                if !isDone { Text(m.cta).font(.system(size: 13, weight: .black)).foregroundStyle(m.tint) }
                            }.padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous)).overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(isDone ? HC.lime : HC.divider))
                        }.buttonStyle(.plain)
                        if !isDone && m.key == "healthconnect" { Button(tr("Saatim yok, atla", "No watch, skip")) { app.markTried("healthconnect") }.font(.system(size: 13)).foregroundStyle(HC.muted).frame(maxWidth: .infinity, alignment: .trailing) }
                    }
                }.padding(20)
            }
            HfButton(title: count == missions.count ? tr("Hepsi tamam! 🎉", "All done! 🎉") : tr("Kapat", "Close"), secondary: count != missions.count) { app.prefs.welcomeGuideSeen = true; dismiss() }.padding(20)
        }.background(HC.bg.ignoresSafeArea())
    }

    private func act(_ key: String) {
        app.prefs.welcomeGuideSeen = true
        dismiss()
        switch key {
        case "workout": app.select(.explore)
        case "nutrition": app.select(.nutrition)
        case "cardio": app.markTried("cardio"); app.push(.cardio)
        case "musclemap": app.markTried("musclemap"); app.push(.muscleMap)
        case "coach": app.markTried("coach"); app.select(.coach)
        case "reminders": app.push(.notifications)
        case "healthconnect": app.markTried("healthconnect"); Task { await app.syncHealth() }
        default: break
        }
    }
}
