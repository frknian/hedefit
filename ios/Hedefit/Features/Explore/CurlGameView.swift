import SwiftUI

private enum GameMuscle: String, CaseIterable {
    case arms, upper, legs, core
    @MainActor var label: String { switch self { case .arms: return tr("Kol", "Arms"); case .upper: return tr("Omuz & göğüs", "Shoulders & chest"); case .legs: return tr("Bacak", "Legs"); case .core: return tr("Karın", "Core") } }
}

private enum Gear: CaseIterable {
    case dumbbell, barbell, squat, pullBar
    @MainActor var title: String { switch self { case .dumbbell: return tr("Dambıl", "Dumbbell"); case .barbell: return tr("Halter", "Barbell"); case .squat: return "Squat"; case .pullBar: return tr("Barfiks", "Pull-up bar") } }
    @MainActor var move: String { switch self { case .dumbbell: return "Biceps curl"; case .barbell: return tr("Omuz press", "Shoulder press"); case .squat: return tr("Vücut ağırlığı squat", "Bodyweight squat"); case .pullBar: return tr("Bacak kaldırma", "Leg raise") } }
    var muscle: GameMuscle { switch self { case .dumbbell: return .arms; case .barbell: return .upper; case .squat: return .legs; case .pullBar: return .core } }
}

private func growth(_ reps: Int) -> CGFloat { 1 + CGFloat(reps) / (CGFloat(reps) + 40) * 1.4 }

@MainActor private func title(_ total: Int) -> String {
    switch total {
    case 1000...: return tr("Olimpiya adayı 🏆", "Olympic hopeful 🏆"); case 500...: return tr("Salonun yıldızı 🌟", "Gym star 🌟"); case 200...: return tr("Kas makinesi 💪", "Muscle machine 💪")
    case 80...: return tr("Pompa geldi 🔥", "Feeling the pump 🔥"); case 20...: return tr("Isındın", "Warmed up"); case 1...: return tr("Başladın", "Getting started"); default: return tr("Bir alet seç ve dokun", "Pick a tool and tap")
    }
}

/// Mini oyun: alet seç, sporcuya dokundukça tekrar yap; kaslar zamanla büyür.
struct CurlGame: View {
    var status = ""
    var statusIsError = false
    @State private var lifetime: [GameMuscle: Int] = Dictionary(uniqueKeysWithValues: GameMuscle.allCases.map { ($0, UserDefaults.standard.integer(forKey: "hedefit-curl-game.reps_\($0.rawValue)")) })
    @State private var gear: Gear = .dumbbell
    @State private var sessionReps = 0
    @State private var popKey = 0
    @State private var move: CGFloat = 0
    @State private var confirmReset = false

    var body: some View {
        let total = lifetime.values.reduce(0, +)
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ForEach(Gear.allCases, id: \.self) { g in
                    let sel = g == gear
                    Button { gear = g; UISelectionFeedbackGenerator().selectionChanged() } label: {
                        VStack(spacing: 2) {
                            GearIcon(gear: g, color: sel ? HC.lime : HC.text).frame(width: 28, height: 28)
                            Text(g.title).font(.system(size: 11, weight: .bold)).foregroundStyle(HC.text).lineLimit(1)
                            Text(g.muscle.label).font(.system(size: 9)).foregroundStyle(HC.muted).lineLimit(1)
                        }.frame(maxWidth: .infinity).padding(.vertical, 8).background(sel ? HC.lime.opacity(0.14) : HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(sel ? HC.lime : HC.divider, lineWidth: sel ? 2 : 1))
                    }.buttonStyle(.plain)
                }
            }
            ZStack {
                Canvas { ctx, size in drawAthlete(&ctx, size, gear, move, lifetime) }
                VStack { HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 0) { Text(tr("TEKRAR", "REPS")).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.muted); Text("\(sessionReps)").font(.system(size: 34, weight: .black)).foregroundStyle(HC.text).scaleEffect(move > 0.6 ? 1.12 : 1, anchor: .leading).animation(.spring(duration: 0.3, bounce: 0.5), value: move > 0.6) }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 0) { Text(tr("TOPLAM", "TOTAL")).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.muted); Text("\(total)").font(.system(size: 20, weight: .black)).foregroundStyle(HC.lime) }
                }; Spacer(); Text("\(gear.move) • " + tr("dokun", "tap")).font(.system(size: 11)).foregroundStyle(HC.muted) }
                if popKey > 0 { PlusOne(key: popKey, muscle: gear.muscle.label).frame(maxHeight: .infinity, alignment: .top).padding(.top, 8) }
            }
            .frame(height: 270).contentShape(Rectangle()).onTapGesture { rep() }
            VStack(spacing: 6) {
                ForEach(GameMuscle.allCases, id: \.self) { m in
                    let reps = lifetime[m] ?? 0
                    HStack {
                        Text(m.label).font(.system(size: 12, weight: m == gear.muscle ? .bold : .regular)).foregroundStyle(m == gear.muscle ? HC.lime : HC.textSecondary).frame(width: 96, alignment: .leading)
                        ProgressView(value: Double(reps % 25) / 25).tint(m == gear.muscle ? HC.lime : HC.water)
                        Text(tr("Sv \(reps / 25 + 1)", "Lv \(reps / 25 + 1)")).font(.system(size: 11)).foregroundStyle(HC.muted).frame(width: 44, alignment: .trailing)
                    }
                }
            }.padding(.top, 8)
            if total > 0 { Button(tr("↺ Gelişimi sıfırla", "↺ Reset progress")) { confirmReset = true }.font(.system(size: 12)).foregroundStyle(HC.muted).padding(.top, 6) }
            Text(title(total)).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
            Text(sessionReps == 0 ? tr("Üstten bir alet seç, adama dokundukça hareketi yap.", "Pick a tool above and tap the athlete to do reps.") : status).font(.hfSmall).foregroundStyle(statusIsError && sessionReps > 0 ? HC.warning : HC.textSecondary).multilineTextAlignment(.center)
        }
        .padding(16).background(HC.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .alert(tr("Gelişim sıfırlansın mı?", "Reset progress?"), isPresented: $confirmReset) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Sıfırla", "Reset"), role: .destructive) {
                for m in GameMuscle.allCases { lifetime[m] = 0; UserDefaults.standard.removeObject(forKey: "hedefit-curl-game.reps_\(m.rawValue)") }
                sessionReps = 0; UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            }
        } message: { Text(tr("Tüm kas seviyeleri ve toplam tekrar sıfırlanır; adam baştan başlar.", "All muscle levels and total reps are reset; the athlete starts over.")) }
    }

    private func rep() {
        let m = gear.muscle
        lifetime[m, default: 0] += 1
        UserDefaults.standard.set(lifetime[m] ?? 0, forKey: "hedefit-curl-game.reps_\(m.rawValue)")
        sessionReps += 1; popKey += 1
        UIImpactFeedbackGenerator(style: sessionReps % 10 == 0 ? .heavy : .light).impactOccurred()
        withAnimation(.easeOut(duration: 0.18)) { move = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) { withAnimation(.easeInOut(duration: 0.26)) { move = 0 } }
    }
}

private struct PlusOne: View {
    let key: Int, muscle: String
    @State private var t: CGFloat = 0
    var body: some View { Text("+1 \(muscle)").font(.system(size: 16, weight: .black)).foregroundStyle(HC.lime).offset(y: -t * 36).opacity(1 - t).id(key).onAppear { t = 0; withAnimation(.easeOut(duration: 0.65)) { t = 1 } } }
}

private struct GearIcon: View {
    let gear: Gear, color: Color
    var body: some View {
        Canvas { ctx, size in
            let u = size.width / 24, h = size.height, plate = HC.warning
            func line(_ a: CGPoint, _ b: CGPoint, _ w: CGFloat, _ c: Color) { var p = Path(); p.move(to: a); p.addLine(to: b); ctx.stroke(p, with: .color(c), style: StrokeStyle(lineWidth: w, lineCap: .round)) }
            func rr(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ hh: CGFloat, _ c: Color) { ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: hh), cornerRadius: u), with: .color(c)) }
            switch gear {
            case .dumbbell:
                line(CGPoint(x: 6 * u, y: h / 2), CGPoint(x: 18 * u, y: h / 2), 2 * u, color)
                for x in [3.5, 17.0] { rr(x * u, h / 2 - 5 * u, 3.5 * u, 10 * u, plate) }
                for x in [1.5, 20.5] { rr(x * u, h / 2 - 3 * u, 2 * u, 6 * u, plate.opacity(0.75)) }
            case .barbell:
                line(CGPoint(x: 0, y: h / 2), CGPoint(x: size.width, y: h / 2), 1.6 * u, color)
                for x in [3.0, 18.5] { rr(x * u, h / 2 - 7 * u, 2.5 * u, 14 * u, plate) }
                for x in [5.8, 16.0] { rr(x * u, h / 2 - 5 * u, 2.2 * u, 10 * u, plate.opacity(0.8)) }
            case .squat:
                ctx.fill(Path(ellipseIn: CGRect(x: 6.6 * u, y: 2.1 * u, width: 4.8 * u, height: 4.8 * u)), with: .color(color))
                line(CGPoint(x: 9 * u, y: 7 * u), CGPoint(x: 7 * u, y: 14 * u), 2 * u, color); line(CGPoint(x: 9 * u, y: 9 * u), CGPoint(x: 17 * u, y: 9 * u), 1.8 * u, color)
                line(CGPoint(x: 7 * u, y: 14 * u), CGPoint(x: 14 * u, y: 16 * u), 2.2 * u, color); line(CGPoint(x: 14 * u, y: 16 * u), CGPoint(x: 12 * u, y: 22 * u), 2.2 * u, color)
                line(CGPoint(x: 3 * u, y: 22.5 * u), CGPoint(x: 21 * u, y: 22.5 * u), 1.2 * u, plate)
            case .pullBar:
                line(CGPoint(x: 3 * u, y: 3 * u), CGPoint(x: 3 * u, y: 23 * u), 1.6 * u, color.opacity(0.6)); line(CGPoint(x: 21 * u, y: 3 * u), CGPoint(x: 21 * u, y: 23 * u), 1.6 * u, color.opacity(0.6))
                line(CGPoint(x: 1.5 * u, y: 4 * u), CGPoint(x: 22.5 * u, y: 4 * u), 2 * u, plate)
                line(CGPoint(x: 9 * u, y: 4 * u), CGPoint(x: 10.5 * u, y: 9 * u), 1.4 * u, color); line(CGPoint(x: 15 * u, y: 4 * u), CGPoint(x: 13.5 * u, y: 9 * u), 1.4 * u, color)
                ctx.fill(Path(ellipseIn: CGRect(x: 10.2 * u, y: 8.2 * u, width: 3.6 * u, height: 3.6 * u)), with: .color(color))
                line(CGPoint(x: 12 * u, y: 12 * u), CGPoint(x: 12 * u, y: 17 * u), 1.8 * u, color)
                line(CGPoint(x: 12 * u, y: 17 * u), CGPoint(x: 10.5 * u, y: 21.5 * u), 1.5 * u, color); line(CGPoint(x: 12 * u, y: 17 * u), CGPoint(x: 13.5 * u, y: 21.5 * u), 1.5 * u, color)
            }
        }
    }
}

/// Önden görünen sporcu; t = hareketin anlık konumu (0 başlangıç, 1 tepe).
@MainActor private func drawAthlete(_ ctx: inout GraphicsContext, _ size: CGSize, _ gear: Gear, _ t: CGFloat, _ lifetime: [GameMuscle: Int]) {
    let u = min(size.width, size.height) / 100, cx = size.width / 2
    let ink = HC.text, muscle = HC.lime
    func g(_ m: GameMuscle) -> CGFloat { growth(lifetime[m] ?? 0) }
    let arms = g(.arms), upper = g(.upper), legs = g(.legs), core = g(.core)
    func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: cx + x * u, y: y * u) }
    func line(_ a: CGPoint, _ b: CGPoint, _ w: CGFloat, _ c: Color) { var path = Path(); path.move(to: a); path.addLine(to: b); ctx.stroke(path, with: .color(c), style: StrokeStyle(lineWidth: w, lineCap: .round)) }
    func oval(_ r: CGRect, _ c: Color) { ctx.fill(Path(ellipseIn: r), with: .color(c)) }

    let squat = gear == .squat ? t : 0, drop = 12 * squat, hanging = gear == .pullBar
    if hanging {
        line(p(-30, 4), p(30, 4), 2.2 * u, HC.textSecondary); line(p(-30, 4), p(-30, 96), 1.2 * u, HC.muted); line(p(30, 4), p(30, 96), 1.2 * u, HC.muted)
    }
    oval(CGRect(x: cx - 24 * u, y: 94 * u, width: 48 * u, height: 4 * u), ink.opacity(0.07))
    let headY = 14 + drop, shoulderY = 26 + drop, hipY = 58 + drop
    let shoulderHalf = 10 + 4 * (upper - 1), waistHalf = 6.5 + 1 * (core - 1)
    let legW = (3.2 + 2.6 * (legs - 1)) * u, kneeY = 76 + drop * 0.45, raise = hanging ? t : 0
    for side in [CGFloat(-1), 1] {
        let hip = p(side * 4.5, hipY)
        let knee = raise > 0 ? p(side * 5, hipY + 14 - 10 * raise) : p(side * (5 + 7 * squat), kneeY)
        let foot = raise > 0 ? p(side * 5.5, hipY + 30 - 30 * raise) : p(side * 8, 93)
        line(hip, knee, legW, ink); line(knee, foot, legW * 0.85, ink)
        if legs > 1.08 { let mid = CGPoint(x: (hip.x + knee.x) / 2, y: (hip.y + knee.y) / 2); oval(CGRect(x: mid.x - legW * 0.55, y: mid.y - legW, width: legW * 1.1, height: legW * 2), muscle.opacity(min(max(Double((legs - 1) / 1.2), 0), 0.9))) }
    }
    var torso = Path()
    torso.move(to: p(-shoulderHalf, shoulderY)); torso.addLine(to: p(shoulderHalf, shoulderY)); torso.addLine(to: p(waistHalf, hipY)); torso.addLine(to: p(-waistHalf, hipY)); torso.closeSubpath()
    ctx.fill(torso, with: .color(ink))
    if upper > 1.05 {
        let a = min(max(Double((upper - 1) / 1.2), 0.15), 0.9), pecW = (shoulderHalf - 1.5) * u
        ctx.fill(Path(roundedRect: CGRect(x: cx - pecW - 0.5 * u, y: (shoulderY + 3) * u, width: pecW, height: 8 * u), cornerRadius: 4 * u), with: .color(muscle.opacity(a)))
        ctx.fill(Path(roundedRect: CGRect(x: cx + 0.5 * u, y: (shoulderY + 3) * u, width: pecW, height: 8 * u), cornerRadius: 4 * u), with: .color(muscle.opacity(a)))
    }
    let abAlpha = min(max(Double((core - 1) / 1.2) + (hanging ? Double(t) * 0.3 : 0), 0.08), 0.95)
    for row in 0..<3 { for col in [CGFloat(-1), 1] {
        let x = cx + (col < 0 ? -3.6 : 0.4) * u, y = (shoulderY + 14 + CGFloat(row) * 5.5) * u
        ctx.fill(Path(roundedRect: CGRect(x: x, y: y, width: 3.2 * u, height: 4.4 * u), cornerRadius: 1.2 * u), with: .color(muscle.opacity(abAlpha)))
    } }
    oval(CGRect(x: cx - 7.5 * u, y: headY * u - 7.5 * u, width: 15 * u, height: 15 * u), ink)
    let eyeH = t > 0.5 ? 0.5 * u : 1.3 * u
    oval(CGRect(x: cx - 3.2 * u, y: (headY - 1) * u, width: 1.4 * u, height: eyeH), HC.bg); oval(CGRect(x: cx + 1.8 * u, y: (headY - 1) * u, width: 1.4 * u, height: eyeH), HC.bg)

    let armW = (3 + 2.2 * (arms - 1)) * u, weight = HC.warning
    var hands: [CGPoint] = []
    for side in [CGFloat(-1), 1] {
        let shoulder = p(side * shoulderHalf, shoulderY + 1)
        let elbow: CGPoint, hand: CGPoint
        switch gear {
        case .dumbbell: elbow = p(side * (shoulderHalf + 2), shoulderY + 17); hand = p(side * (shoulderHalf + 3 - 1 * t), shoulderY + 31 - 26 * t)
        case .barbell: elbow = p(side * (shoulderHalf + 7), shoulderY + 3 - 12 * t); hand = p(side * (shoulderHalf + 5), shoulderY - 4 - 22 * t)
        case .squat: elbow = p(side * (shoulderHalf + 1), shoulderY + 8 - 4 * t); hand = p(side * 4, shoulderY + 6 - 6 * t)
        case .pullBar: elbow = p(side * (shoulderHalf + 4), shoulderY - 10); hand = p(side * (shoulderHalf + 4), 4)
        }
        line(shoulder, elbow, armW, ink); line(elbow, hand, armW * 0.85, ink)
        if upper > 1.05 { let r = (2.4 + 1.8 * (upper - 1)) * u; oval(CGRect(x: shoulder.x - r, y: shoulder.y - r, width: r * 2, height: r * 2), muscle.opacity(min(max(Double((upper - 1) / 1.2), 0.15), 0.9))) }
        let flex = gear == .dumbbell ? t : 0, b = (2.6 + 2 * flex) * arms * u
        let mid = CGPoint(x: (shoulder.x + elbow.x) / 2, y: (shoulder.y + elbow.y) / 2)
        oval(CGRect(x: mid.x - b * 0.6, y: mid.y - b / 2, width: b * 1.2, height: b), muscle)
        hands.append(hand)
    }
    switch gear {
    case .dumbbell:
        for h in hands {
            line(CGPoint(x: h.x - 4 * u, y: h.y), CGPoint(x: h.x + 4 * u, y: h.y), 1.4 * u, HC.textSecondary)
            ctx.fill(Path(roundedRect: CGRect(x: h.x - 5.5 * u, y: h.y - 2.6 * u, width: 2.4 * u, height: 5.2 * u), cornerRadius: u), with: .color(weight))
            ctx.fill(Path(roundedRect: CGRect(x: h.x + 3.1 * u, y: h.y - 2.6 * u, width: 2.4 * u, height: 5.2 * u), cornerRadius: u), with: .color(weight))
        }
    case .barbell:
        let y = (hands[0].y + hands[1].y) / 2
        line(CGPoint(x: cx - 34 * u, y: y), CGPoint(x: cx + 34 * u, y: y), 1.6 * u, HC.textSecondary)
        for side in [CGFloat(-1), 1] {
            ctx.fill(Path(roundedRect: CGRect(x: cx + side * 29 * u - 2 * u, y: y - 7 * u, width: 4 * u, height: 14 * u), cornerRadius: u), with: .color(weight))
            ctx.fill(Path(roundedRect: CGRect(x: cx + side * 33 * u - 1.5 * u, y: y - 5 * u, width: 3 * u, height: 10 * u), cornerRadius: u), with: .color(weight.opacity(0.8)))
        }
    default: break
    }
}

struct CurlGameScreen: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        ScreenScaffold(spacing: 14) {
            HfScreenHeader(title: tr("Oyun", "Game")) { if !app.path.isEmpty { app.path.removeLast() } }
            CurlGame(status: tr("Her tekrar sporcunu geliştirir.", "Every rep builds your athlete."))
        }
    }
}
