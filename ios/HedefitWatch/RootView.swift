import SwiftUI

/// Sayfa sırası: Özet, Su, Sosyal, Sor, Antrenman. Complication'lar `hedefit-watch://<sayfa>` ile açar.
enum WatchPage: Int { case summary, water, social, ask, start }

struct RootView: View {
    @Environment(WorkoutManager.self) private var workout
    @State private var page = WatchPage.summary

    var body: some View {
        if workout.running {
            ActiveWorkoutView()
        } else {
            TabView(selection: $page) {
                SummaryView().tag(WatchPage.summary)
                WaterView().tag(WatchPage.water)
                SocialView().tag(WatchPage.social)
                AskView().tag(WatchPage.ask)
                StartWorkoutView().tag(WatchPage.start)
            }
            .tabViewStyle(.verticalPage)
            .onOpenURL { url in
                switch url.host {
                case "water": page = .water
                case "social": page = .social
                case "workout": page = .start
                default: page = .summary
                }
            }
        }
    }
}

/// Üç halka: adım, su, protein.
struct SummaryView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        let s = model.snapshot
        ZStack {
            Ring(progress: ratio(s.steps, s.stepGoal), color: .hedefitGreen, diameter: 150)
            Ring(progress: ratio(s.waterMl, s.waterGoal), color: .hedefitWater, diameter: 122)
            Ring(progress: ratio(s.proteinG, s.proteinGoal), color: .hedefitCoral, diameter: 94)
            VStack(spacing: 0) {
                Text(s.steps.formatted()).font(.system(size: 22, weight: .semibold, design: .rounded)).minimumScaleFactor(0.6)
                Text(model.t("adım", "steps")).font(.caption2).foregroundStyle(.secondary)
                if s.streakDays > 0 { Label(model.t("\(s.streakDays) gün", "\(s.streakDays) days"), systemImage: "flame.fill").font(.caption2).foregroundStyle(Color.hedefitAmber) }
                if !model.reachable { Text(model.t("iPhone'a ulaşılamıyor", "iPhone unreachable")).font(.system(size: 9)).foregroundStyle(.secondary).multilineTextAlignment(.center) }
            }
        }
        .padding(4)
    }

    private func ratio(_ v: Int, _ goal: Int) -> Double { goal > 0 ? min(1, Double(v) / Double(goal)) : 0 }
}

struct Ring: View {
    let progress: Double, color: Color, diameter: CGFloat
    var body: some View {
        ZStack {
            Circle().stroke(Color.hedefitSurface, lineWidth: 9)
            Circle().trim(from: 0, to: progress).stroke(color, style: StrokeStyle(lineWidth: 9, lineCap: .round)).rotationEffect(.degrees(-90))
        }
        .frame(width: diameter, height: diameter)
        .animation(.easeOut(duration: 0.5), value: progress)
    }
}

/// Taç ile miktar seç, tek dokunuşla ekle.
struct WaterView: View {
    @Environment(WatchModel.self) private var model
    @State private var amount = 250.0
    @State private var flash = false

    var body: some View {
        let s = model.snapshot
        VStack(spacing: 6) {
            Label(model.t("Su", "Water"), systemImage: "drop.fill").font(.caption).foregroundStyle(Color.hedefitWater)
            Text(String(format: "%.1f L", Double(s.waterMl) / 1000)).font(.system(size: 34, weight: .semibold, design: .rounded)).foregroundStyle(Color.hedefitWater)
            Text(model.t("hedef", "goal") + " " + String(format: "%.1f", Double(s.waterGoal) / 1000) + " L").font(.caption2).foregroundStyle(.secondary)
            Button {
                model.addWater(Int(amount))
                WKInterfaceDevice.current().play(.success)
                flash = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { flash = false }
            } label: { Text(flash ? model.t("Eklendi", "Added") : "+\(Int(amount)) ml").fontWeight(.semibold) }
            .tint(.hedefitWater)
        }
        .focusable()
        .digitalCrownRotation($amount, from: 50, through: 1000, by: 50, sensitivity: .low)
    }
}

/// Haftalık sıralama ve meydan okuma; telefon veriyi yüklemediyse boş durum gösterir.
struct SocialView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        let social = model.snapshot.social
        VStack(spacing: 3) {
            Text(model.t("Bu hafta", "This week")).font(.caption).foregroundStyle(Color.hedefitGreen)
            if social.rank <= 0 {
                Text(model.t("Sıralama için telefonda Arkadaşlar sayfasını aç.", "Open Friends on your phone to see rankings.")).font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center)
            } else {
                Text("#\(social.rank)").font(.system(size: 34, weight: .semibold, design: .rounded))
                Text("\(social.xp) XP").font(.caption2).foregroundStyle(Color.hedefitAmber)
                ForEach(Array(social.leaders.enumerated()), id: \.offset) { i, leader in
                    Text("\(i + 1). \(String(leader.name.prefix(12)))  \(leader.xp)").font(.caption2).foregroundStyle(leader.me ? Color.hedefitGreen : .secondary).lineLimit(1)
                }
                if !social.challenge.isEmpty { Text(String(social.challenge.prefix(22))).font(.caption2).foregroundStyle(Color.hedefitCoral).lineLimit(1) }
            }
        }
        .padding(.horizontal, 8)
    }
}

/// Sesle (dikte ile) Fit Koç'a soru sor veya yemek ekle; yanıtı iPhone verir.
struct AskView: View {
    @Environment(WatchModel.self) private var model
    @State private var mode = "chat"
    @State private var text = ""
    @State private var state = State.idle
    enum State: Equatable { case idle, waiting, result(String, Bool) }

    var body: some View {
        ScrollView {
            VStack(spacing: 6) {
                switch state {
                case .idle:
                    Picker(model.t("Tür", "Type"), selection: $mode) {
                        Text(model.t("Fit Koç'a sor", "Ask Fit Coach")).tag("chat")
                        Text(model.t("Yemek ekle", "Log food")).tag("food")
                    }.pickerStyle(.navigationLink)
                    TextField(model.t("Söyle veya yaz", "Speak or type"), text: $text)
                        .submitLabel(.send)
                        .onSubmit { send() }
                case .waiting:
                    ProgressView(model.t("Yanıt bekleniyor…", "Waiting for reply…"))
                case let .result(message, ok):
                    Text(message).font(.footnote).foregroundStyle(ok ? Color.primary : Color.hedefitCoral)
                    Button(model.t("Tekrar", "Again")) { text = ""; state = .idle }
                }
            }
        }
    }

    private func send() {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        state = .waiting
        model.ask(mode: mode, text: clean) { ok, message in state = .result(message, ok) }
    }
}

struct StartWorkoutView: View {
    @Environment(WatchModel.self) private var model
    @Environment(WorkoutManager.self) private var workout

    var body: some View {
        List {
            Section(model.snapshot.workoutName) {
                ForEach(WorkoutManager.kinds) { kind in
                    Button { Task { await workout.start(kind) } } label: { Label(model.t(kind.title, kind.titleEn), systemImage: kind.icon) }
                }
            }
        }
        .listStyle(.carousel)
    }
}
