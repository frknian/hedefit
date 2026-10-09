import SwiftUI

/// Merkezden patlayıp düşen konfeti. `burstKey` her değiştiğinde yeniden patlar.
struct ConfettiBurst: View {
    var burstKey: AnyHashable
    var pieceCount = 90
    @State private var start = Date()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private struct Piece { let angle: Double, speed: Double, spin: Double, color: Color, size: Double, drift: Double }

    private var pieces: [Piece] {
        let palette = [HC.lime, HC.warning, HC.water, HC.coral, HC.sleep]
        var generator = SeededGenerator(seed: UInt64(abs(burstKey.hashValue)))
        return (0..<pieceCount).map { i in
            Piece(angle: Double.random(in: 0..<360, using: &generator), speed: 0.35 + Double.random(in: 0..<0.65, using: &generator), spin: Double.random(in: -360..<360, using: &generator),
                  color: palette[i % palette.count], size: 6 + Double.random(in: 0..<8, using: &generator), drift: Double.random(in: -1..<1, using: &generator))
        }
    }

    var body: some View {
        if reduceMotion { EmptyView() }
        else {
            let items = pieces
            TimelineView(.animation) { context in
                let t = min(context.date.timeIntervalSince(start) / 2.2, 1)
                Canvas { ctx, size in
                    guard t < 1 else { return }
                    let origin = CGPoint(x: size.width / 2, y: size.height * 0.38)
                    let reach = min(size.width, size.height) * 0.75
                    for p in items {
                        let rad = p.angle * .pi / 180
                        let burst = (1 - (1 - t) * (1 - t)) * p.speed * reach
                        let x = origin.x + cos(rad) * burst + p.drift * t * 60
                        let y = origin.y + sin(rad) * burst + t * t * size.height * 0.55
                        var piece = ctx
                        piece.translateBy(x: x, y: y); piece.rotate(by: .degrees(p.spin * t))
                        piece.opacity = 1 - t
                        piece.fill(Path(CGRect(x: 0, y: 0, width: p.size, height: p.size * 0.55)), with: .color(p.color))
                    }
                }
            }.allowsHitTesting(false).onAppear { start = Date() }.onChange(of: burstKey) { _, _ in start = Date() }
        }
    }
}

struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64
    init(seed: UInt64) { state = seed &+ 0x9E3779B97F4A7C15 }
    mutating func next() -> UInt64 { state = state &* 6364136223846793005 &+ 1442695040888963407; var x = state; x ^= x >> 33; x = x &* 0xff51afd7ed558ccd; x ^= x >> 33; return x }
}

/// 0'dan hedefe doğru sayarak artan sayı.
struct CountUpText: View {
    var target: Int
    var prefix = ""
    var suffix = ""
    var color: Color? = nil
    var size: CGFloat = 44
    @State private var shown = 0
    var body: some View {
        Text("\(prefix)\(shown)\(suffix)").font(.system(size: size, weight: .black)).foregroundStyle(color ?? HC.lime).contentTransition(.numericText())
            .onAppear { withAnimation(.easeOut(duration: 0.9)) { shown = target } }
    }
}

enum CelebrationKind { case workout, challenge, achievement, level }

struct CelebrationEvent: Identifiable, Equatable {
    let id = UUID()
    var kind: CelebrationKind
    var title: String
    var subtitle: String
    var xpGained = 0
    var level: Int?
    var streakDays: Int?
    var emoji = "💪"
}

/// Tam ekran kutlama kartı (antrenman, challenge günü, başarım).
struct CelebrationCard: View {
    let event: CelebrationEvent
    var onDone: () -> Void
    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture(perform: onDone)
            ConfettiBurst(burstKey: event.id)
            VStack(spacing: 14) {
                Text(event.emoji).font(.system(size: 64))
                Text(event.title).font(.system(size: 26, weight: .black)).foregroundStyle(HC.text).multilineTextAlignment(.center)
                Text(event.subtitle).font(.hfBody).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center)
                if event.xpGained > 0 { CountUpText(target: event.xpGained, prefix: "+", suffix: " XP", size: 38) }
                HStack(spacing: 10) {
                    if let level = event.level { HfPill(text: "Lv \(level)") }
                    if let streak = event.streakDays, streak > 0 { HfPill(text: "🔥 \(streak)", color: HC.warning) }
                }
                HfButton(title: tr("Harika!", "Awesome!"), action: onDone)
            }
            .padding(26).frame(maxWidth: 340).background(HC.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous)).overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(HC.lime.opacity(0.4)))
            .padding(24)
        }.transition(.opacity).sensoryFeedback(.success, trigger: event.id)
    }
}

/// Kutlama kuyruğunu (antrenman bitişi, challenge günü) gösterir.
struct CelebrationHost: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        ZStack {
            if let event = app.celebrations.first {
                CelebrationCard(event: event) { withAnimation { _ = app.celebrations.removeFirst() } }.zIndex(10)
            } else if let c = app.challenge.celebration {
                CelebrationCard(event: CelebrationEvent(kind: .challenge, title: c.finished ? tr("Challenge tamamlandı!", "Challenge complete!") : tr("Gün tamamlandı!", "Day complete!"),
                                                        subtitle: [c.title, c.dayText].filter { !$0.isEmpty }.joined(separator: " • "), xpGained: c.xp, streakDays: c.streak, emoji: c.finished ? "🏆" : "🔥")) {
                    withAnimation { app.challenge.celebration = nil }
                }.zIndex(10)
            }
        }.animation(.easeOut(duration: 0.25), value: app.celebrations.count)
    }
}
