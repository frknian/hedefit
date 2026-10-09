import SwiftUI

@MainActor private func bmiCategory(_ bmi: Double) -> (String, Color) {
    switch bmi {
    case ..<18.5: return (tr("Zayıf", "Underweight"), HC.water)
    case ..<25: return (tr("Normal", "Healthy"), HC.lime)
    case ..<30: return (tr("Fazla kilolu", "Overweight"), HC.warning)
    default: return (tr("Obez", "Obese"), HC.coral)
    }
}

/// Onboarding'in ilk adımı: yaş, cinsiyet, boy ve kilo. Ortadaki figür değerlere göre canlı değişir.
struct BodyProfileView: View {
    @Environment(AppModel.self) private var app
    @State private var gender = "Erkek"
    @State private var age = 28.0
    @State private var height = 172.0
    @State private var weight = 72.0
    @State private var loaded = false
    @State private var saving = false

    var body: some View {
        let bmi = weight / pow(height / 100, 2)
        let (category, color) = bmiCategory(bmi)
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(tr("Seni tanıyalım", "Let's get to know you")).font(.system(size: 30, weight: .black)).foregroundStyle(HC.text)
                    Text(tr("Kalori, su ve antrenman hedeflerin bu bilgilere göre hesaplanır.", "Your calorie, water and training targets are based on these.")).foregroundStyle(HC.textSecondary)
                    HStack(spacing: 8) {
                        ForEach([("Erkek", tr("Erkek", "Male")), ("Kadın", tr("Kadın", "Female")), ("Diğer", tr("Diğer", "Other"))], id: \.0) { v, l in
                            let on = gender == v
                            Button { gender = v; UISelectionFeedbackGenerator().selectionChanged() } label: {
                                Text(l).font(.hfBody.weight(on ? .bold : .medium)).foregroundStyle(HC.text).frame(maxWidth: .infinity, minHeight: 46)
                                    .background(on ? HC.lime.opacity(0.16) : HC.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(on ? HC.lime : HC.divider, lineWidth: on ? 2 : 1))
                            }.buttonStyle(.plain)
                        }
                    }.padding(.top, 6)
                    ZStack(alignment: .topTrailing) {
                        BodyFigure(heightCm: height, bmi: bmi, gender: gender).frame(height: 300).animation(.spring(duration: 0.5, bounce: 0.25), value: height).animation(.spring(duration: 0.5, bounce: 0.25), value: weight)
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(tr("VKİ", "BMI")).font(.system(size: 11, weight: .bold)).foregroundStyle(HC.muted)
                            Text(String(format: "%.1f", bmi)).font(.system(size: 26, weight: .black)).foregroundStyle(HC.text)
                            Text(category).font(.system(size: 12, weight: .bold)).foregroundStyle(color).padding(.horizontal, 10).padding(.vertical, 3).background(color.opacity(0.16), in: Capsule())
                        }
                    }.padding(.vertical, 10)
                    valueSlider(tr("Yaş", "Age"), $age, 14...90, 1, tr("yaş", "yrs"))
                    valueSlider(tr("Boy", "Height"), $height, 130...220, 1, "cm")
                    valueSlider(tr("Kilo", "Weight"), $weight, 35...200, 0.5, "kg")
                }.padding(.horizontal, 22).padding(.vertical, 12)
            }
            HfButton(title: saving ? tr("Kaydediliyor…", "Saving…") : tr("Devam", "Continue"), enabled: !saving) { save() }.padding(.horizontal, 20).padding(.vertical, 14)
        }
        .background(HC.bg.ignoresSafeArea())
        .onAppear {
            guard !loaded, let p = app.dashboard?.profile else { return }
            loaded = true
            if !p.gender.isEmpty { gender = p.gender }
            age = Double(p.age ?? 28); height = p.heightCm ?? 172; weight = p.weightKg ?? 72
        }
    }

    private func valueSlider(_ label: String, _ value: Binding<Double>, _ range: ClosedRange<Double>, _ step: Double, _ unit: String) -> some View {
        VStack(spacing: 4) {
            HStack(alignment: .bottom) {
                Text(label).font(.hfBody.weight(.bold)).foregroundStyle(HC.textSecondary); Spacer()
                Text(step < 1 ? String(format: "%.1f", value.wrappedValue) : "\(Int(value.wrappedValue))").font(.system(size: 28, weight: .black)).foregroundStyle(HC.text)
                Text(" \(unit)").foregroundStyle(HC.muted).padding(.bottom, 4)
            }
            Slider(value: value, in: range, step: step).tint(HC.lime)
        }.padding(.horizontal, 16).padding(.vertical, 10).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func save() {
        guard let p = app.dashboard?.profile else { return }
        saving = true
        let update = ProfileUpdate(displayName: p.displayName, age: Int(age), gender: gender, heightCm: height.rounded(), weightKg: (weight * 2).rounded() / 2, goalType: p.goal, targetWeightKg: p.targetWeightKg, targetWeeks: p.targetWeeks, environment: p.environment, equipment: p.equipment, historyAnswers: p.historyAnswers)
        Task { _ = await app.saveProfile(update); saving = false }
    }
}

/// Önden figür. Boy figürün yüksekliğini, VKİ gövde/uzuv kalınlığını belirler.
struct BodyFigure: View {
    var heightCm: Double
    var bmi: Double
    var gender: String

    var body: some View {
        Canvas { ctx, size in
            let ink = HC.text
            let floor = size.height - 6
            let figH = (size.height - 16) * CGFloat(heightCm / 220)
            let top = floor - figH
            let u = figH / 100
            let cx = size.width / 2
            let fat = CGFloat(min(max((bmi - 22) / 12, -0.45), 1.1))
            let w = 1 + fat * 0.85
            let female = gender == "Kadın"
            func y(_ p: CGFloat) -> CGFloat { top + p * u }
            func line(_ a: CGPoint, _ b: CGPoint, _ width: CGFloat, _ color: Color) {
                var p = Path(); p.move(to: a); p.addLine(to: b)
                ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
            }
            line(CGPoint(x: cx - 60, y: floor), CGPoint(x: cx + 60, y: floor), 2, ink.opacity(0.12))
            line(CGPoint(x: cx + 58, y: top), CGPoint(x: cx + 58, y: floor), 1.5, HC.lime.opacity(0.5))
            line(CGPoint(x: cx + 52, y: top), CGPoint(x: cx + 64, y: top), 1.5, HC.lime.opacity(0.5))
            let legW = 5.5 * u * (1 + fat * 0.7)
            let hipHalf = (7 + (female ? 1.5 : 0)) * u * w
            for s in [CGFloat(-1), 1] { line(CGPoint(x: cx + s * hipHalf * 0.55, y: y(54)), CGPoint(x: cx + s * (hipHalf * 0.55 + 0.5 * u), y: y(97)), legW, ink) }
            let shoulderHalf = (11 - (female ? 1.5 : 0)) * u * (1 + fat * 0.35)
            let waistHalf = (7.5 - (female ? 1.5 : 0)) * u * w
            var torso = Path()
            torso.move(to: CGPoint(x: cx - shoulderHalf, y: y(19)))
            torso.addLine(to: CGPoint(x: cx + shoulderHalf, y: y(19)))
            torso.addQuadCurve(to: CGPoint(x: cx + waistHalf, y: y(40)), control: CGPoint(x: cx + waistHalf * 1.15, y: y(34)))
            torso.addLine(to: CGPoint(x: cx + hipHalf, y: y(56)))
            torso.addLine(to: CGPoint(x: cx - hipHalf, y: y(56)))
            torso.addLine(to: CGPoint(x: cx - waistHalf, y: y(40)))
            torso.addQuadCurve(to: CGPoint(x: cx - shoulderHalf, y: y(19)), control: CGPoint(x: cx - waistHalf * 1.15, y: y(34)))
            torso.closeSubpath()
            ctx.fill(torso, with: .color(ink))
            if fat > 0.15 { ctx.fill(Path(ellipseIn: CGRect(x: cx - waistHalf * 1.1, y: y(34), width: waistHalf * 2.2, height: 16 * u)), with: .color(ink)) }
            let armW = 4.2 * u * (1 + fat * 0.6)
            for s in [CGFloat(-1), 1] { line(CGPoint(x: cx + s * shoulderHalf, y: y(20.5)), CGPoint(x: cx + s * (shoulderHalf + 3 * u + fat * 2 * u), y: y(52)), armW, ink) }
            line(CGPoint(x: cx, y: y(12)), CGPoint(x: cx, y: y(20)), 4 * u, ink)
            let headR = 6.2 * u * (1 + fat * 0.12)
            ctx.fill(Path(ellipseIn: CGRect(x: cx - headR, y: y(7) - headR, width: headR * 2, height: headR * 2)), with: .color(ink))
            if female { ctx.fill(Path(roundedRect: CGRect(x: cx - 7 * u, y: y(4), width: 14 * u, height: 12 * u), cornerRadius: 5 * u), with: .color(ink)) }
            for dx in [CGFloat(-2.2), 2.2] { ctx.fill(Path(ellipseIn: CGRect(x: cx + dx * u - 0.9 * u, y: y(6.5) - 0.9 * u, width: 1.8 * u, height: 1.8 * u)), with: .color(HC.bg)) }
            line(CGPoint(x: cx - waistHalf, y: y(40)), CGPoint(x: cx + waistHalf, y: y(40)), 1.6 * u, bmiCategory(bmi).1)
        }
    }
}

// MARK: - Kullanıcı adı kurulumu

/// Gerçek hesabın kullanıcı adı yoksa (kapatılamaz) seçtirilir.
struct UsernameSetupView: View {
    @Environment(AppModel.self) private var app
    @State private var username = ""
    @State private var status: String?
    @State private var saving = false
    @State private var saveError: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(tr("Kullanıcı adını seç", "Choose a username")).font(.hfHeadline.weight(.bold)).foregroundStyle(HC.text)
            Text(tr("Arkadaşların seni bu adla bulur.", "Friends find you with this name.")).foregroundStyle(HC.textSecondary)
            HfField(title: tr("Kullanıcı adı", "Username"), text: Binding(get: { username }, set: { username = String($0.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }.prefix(20)) }), keyboard: .asciiCapable)
            if let (text, ok) = saveError.map({ ($0, false) }) ?? usernameStatusText(status) { Text(text).font(.hfBody).foregroundStyle(ok ? HC.lime : HC.coral) }
            HfButton(title: saving ? tr("Kaydediliyor…", "Saving…") : tr("Kaydet", "Save"), enabled: status == "ok" && !saving) {
                saving = true
                Task {
                    do { let saved = try await app.repo.saveUsername(username); app.updateDashboard { $0.profile.username = saved } } catch { saveError = error.friendly }
                    saving = false
                }
            }
            Spacer()
        }
        .padding(24).background(HC.bg.ignoresSafeArea()).interactiveDismissDisabled()
        .task(id: username) {
            status = nil; saveError = nil
            if username.isEmpty { return }
            if let local = localUsernameStatus(username) { status = local; return }
            try? await Task.sleep(for: .milliseconds(450))
            guard !Task.isCancelled else { return }
            status = "checking"
            let result = (try? await AuthService.shared.checkUsername(username)) ?? "error"
            if !Task.isCancelled { status = result }
        }
    }
}
