import SwiftUI
import AVFoundation

private let cardioPrefix = "hedefit-cardio"

/// Antrenman sonrası kardiyo önerisi açık mı.
func cardioFinisherEnabled() -> Bool { UserDefaults.standard.bool(forKey: "\(cardioPrefix).suggest_after_workout") }

private struct CardioSample { let second: Int; let progress: Double; let kcalPerMin: Double }

private struct CardioSummaryData {
    let machine: CardioMachine
    let seconds: Int, kcal: Int
    let distanceKm: Double
    let samples: [CardioSample]
    let gameScore: Int
    let newRecord: Bool
    let presetTitle: String?
    var firstSession = false
}

/// Rekorla yarış: en uzun seansın ilerleme eğrisi cihazda saklanır.
private enum GhostStore {
    static func read(_ key: String) -> [(Int, Double)] {
        (UserDefaults.standard.string(forKey: "\(cardioPrefix).ghost_\(key)") ?? "").split(separator: ";").compactMap { part in
            let p = part.split(separator: ":"); guard p.count == 2, let s = Int(p[0]), let d = Double(p[1]) else { return nil }; return (s, d)
        }
    }
    static func saveIfBest(_ key: String, _ samples: [CardioSample]) -> Bool {
        guard let total = samples.last?.progress else { return false }
        if total <= (read(key).last?.1 ?? 0) { return false }
        UserDefaults.standard.set(samples.map { "\($0.second):\(String(format: "%.4f", $0.progress))" }.joined(separator: ";"), forKey: "\(cardioPrefix).ghost_\(key)")
        return true
    }
    static func at(_ ghost: [(Int, Double)], _ second: Int) -> Double? {
        guard let last = ghost.last, second <= last.0, let after = ghost.firstIndex(where: { $0.0 >= second }) else { return nil }
        if after == 0 { return ghost[0].1 * Double(second) / Double(max(ghost[0].0, 1)) }
        let (s0, d0) = ghost[after - 1], (s1, d1) = ghost[after]
        return d0 + (d1 - d0) * Double(second - s0) / Double(max(s1 - s0, 1))
    }
}

private func clock(_ s: Int) -> String { s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%02d:%02d", s / 60, s % 60) }
private func formatValue(_ v: Double, _ step: Double) -> String { step < 1 ? String(format: "%.1f", v) : String(Int(v.rounded())) }

struct CardioView: View {
    @Environment(AppModel.self) private var app
    @State private var live: (machine: CardioMachine, preset: CardioPreset?, game: Bool)?
    @State private var summary: CardioSummaryData?
    @State private var game = false
    @State private var finisher = cardioFinisherEnabled()

    var body: some View {
        Group {
            if let summary { CardioSummaryView(data: summary, onDiscard: { self.summary = nil }) }
            else if let live { CardioLiveView(machine: live.machine, preset: live.preset, game: live.game, onCancel: { self.live = nil }) { self.summary = $0; self.live = nil } }
            else { picker }
        }.background(HC.bg.ignoresSafeArea())
    }

    private var picker: some View {
        ScreenScaffold(spacing: 14) {
            HfScreenHeader(title: ct("Kardiyo")) { if !app.path.isEmpty { app.path.removeLast() } }
            Text(ct("Makineni seç, ayarları canlı değiştir; yaktığın kalori günlük hesabına eklenir.")).foregroundStyle(HC.textSecondary)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(cardioMachines) { m in
                    Button { live = (m, nil, game) } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(m.emoji).font(.system(size: 32)).frame(width: 58, height: 58).background(HC.lime.opacity(0.14), in: Circle())
                            Text(ct(m.title)).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                            Text(m.controls.map { ct($0.label) }.joined(separator: " • ")).font(.system(size: 12)).foregroundStyle(HC.muted).lineLimit(2).multilineTextAlignment(.leading)
                        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(HC.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }.buttonStyle(.plain)
                }
            }
            toggleRow("gamecontroller.fill", ct("Oyun modu"), ct("Sanal rota, rekorunla yarış ve hedef görevleri"), game, locked: !app.limits.cardioGame) {
                if !app.limits.cardioGame { app.lock(.cardioGame) } else { game.toggle() }
            }
            toggleRow("dumbbell.fill", ct("Antrenman sonrası kardiyo öner"), ct("Kuvvet antrenmanını bitirince kardiyoya geçmeyi hatırlatır"), finisher, locked: false) {
                finisher.toggle(); UserDefaults.standard.set(finisher, forKey: "\(cardioPrefix).suggest_after_workout")
            }
            HStack { Text(ct("Hazır programlar")).font(.hfTitleL.weight(.bold)).foregroundStyle(HC.text); Spacer(); if !app.limits.cardioPrograms { Image(systemName: "lock.fill").foregroundStyle(HC.warning) } }.padding(.top, 8)
            ForEach(cardioPresets) { preset in
                let machine = cardioMachines.first { $0.key == preset.machineKey }!
                Button { if app.limits.cardioPrograms { live = (machine, preset, game) } else { app.lock(.cardioPrograms) } } label: {
                    HStack(spacing: 12) {
                        Text(machine.emoji).font(.system(size: 22)).frame(width: 40, height: 40).background(HC.lime.opacity(0.14), in: Circle())
                        VStack(alignment: .leading, spacing: 2) { Text(ct(preset.title)).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Text(ct(preset.description)).font(.system(size: 12)).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading) }
                        Spacer()
                        Text(tr("\(preset.totalSeconds / 60) dk", "\(preset.totalSeconds / 60) min")).font(.hfBody.weight(.bold)).foregroundStyle(HC.lime)
                    }.padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }.buttonStyle(.plain)
            }
            Text(ct("Kalori tahminidir: kilo, hız, eğim ve dirence göre hesaplanır. Makinelerin gösterdiği değer genelde daha yüksektir.")).font(.system(size: 11)).foregroundStyle(HC.muted)
        }
    }

    private func toggleRow(_ icon: String, _ title: String, _ sub: String, _ on: Bool, locked: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 22)).foregroundStyle(HC.sleep).frame(width: 30)
                VStack(alignment: .leading, spacing: 2) { Text(title).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Text(sub).font(.system(size: 12)).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading) }
                Spacer()
                if locked { Image(systemName: "lock.fill").foregroundStyle(HC.warning) } else { Toggle("", isOn: .constant(on)).labelsHidden().tint(HC.lime).allowsHitTesting(false) }
            }.padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }.buttonStyle(.plain)
    }
}

// MARK: - Canlı seans

private struct CardioLiveView: View {
    @Environment(AppModel.self) private var app
    let machine: CardioMachine
    let preset: CardioPreset?
    let game: Bool
    var onCancel: () -> Void
    var onFinish: (CardioSummaryData) -> Void

    @State private var values: [String: Double] = [:]
    @State private var elapsed = 0
    @State private var kcal = 0.0
    @State private var distance = 0.0
    @State private var running = true
    @State private var locked = false
    @State private var confirmExit = false
    @State private var samples: [CardioSample] = []
    @State private var ghost: [(Int, Double)] = []
    @State private var score = 0
    @State private var challengeText: String?
    @State private var challengeKey: String?
    @State private var challengeTarget = 0.0
    @State private var challengeHeld = 0
    @State private var challengeLeft = 0
    @State private var celebrate = 0
    @State private var lastSegment = -1
    private let speech = AVSpeechSynthesizer()

    private func say(_ text: String) {
        let u = AVSpeechUtterance(string: text); u.voice = AVSpeechSynthesisVoice(language: LangStore.english ? "en-US" : "tr-TR"); speech.speak(u)
    }
    private var weightKg: Double? { app.dashboard?.profile.weightKg }
    private var rate: CardioRate { cardioRate(machineKey: machine.key, values: values, weightKg: weightKg) }
    private var progressMetric: Double { machine.tracksDistance ? distance : kcal }
    private var segmentIndex: Int? {
        guard let preset else { return nil }
        var acc = 0
        for (i, s) in preset.segments.enumerated() { acc += s.seconds; if elapsed < acc { return i } }
        return preset.segments.count - 1
    }

    var body: some View {
        let r = rate
        ZStack {
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    Text(machine.emoji).font(.system(size: 20)).frame(width: 40, height: 40).background(HC.lime.opacity(0.14), in: Circle())
                    VStack(alignment: .leading, spacing: 0) {
                        Text(ct(machine.title)).font(.hfBody.weight(.black)).foregroundStyle(HC.text)
                        Text(running ? ct("Devam ediyor") : ct("Duraklatıldı")).font(.system(size: 12, weight: .bold)).foregroundStyle(running ? HC.lime : HC.warning)
                    }
                    Spacer()
                    Button { locked = true } label: { Label(ct("Kilitle"), systemImage: "lock.fill").font(.system(size: 13, weight: .bold)).foregroundStyle(HC.text).padding(.horizontal, 14).padding(.vertical, 9).background(HC.surface, in: Capsule()) }
                }.padding(.vertical, 8)
                ring(r)
                HStack(spacing: 8) {
                    tile("\(Int(kcal.rounded()))", ct("kcal (tahmini)"), HC.lime)
                    if machine.tracksDistance { tile(String(format: "%.2f", distance), ct("km"), HC.text) }
                    tile(String(format: "%.1f", r.activeKcalPerMinute), ct("kcal/dk"), HC.text)
                }
                if let preset, let idx = segmentIndex { segmentCard(preset, idx) }
                if game { gamePanel }
                VStack(spacing: 8) {
                    ForEach(Array(machine.controls.chunked2().enumerated()), id: \.offset) { _, row in
                        HStack(spacing: 8) { ForEach(row, id: \.key) { control(r, $0) } }
                    }
                }.padding(.top, 10)
                HStack(spacing: 12) {
                    Button { running.toggle() } label: { Label(running ? ct("Duraklat") : ct("Devam"), systemImage: running ? "pause.fill" : "play.fill").font(.hfBody.weight(.bold)).foregroundStyle(HC.text).frame(maxWidth: .infinity, minHeight: 60).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 18, style: .continuous)) }
                    Button { finish() } label: { Label(ct("Bitir"), systemImage: "stop.fill").font(.hfBody.weight(.bold)).foregroundStyle(HC.onLime).frame(maxWidth: .infinity, minHeight: 60).background(HC.lime, in: RoundedRectangle(cornerRadius: 18, style: .continuous)) }
                }.padding(.vertical, 12)
            }.padding(.horizontal, 18)
            ConfettiBurst(burstKey: celebrate, pieceCount: 50)
            if locked {
                VStack(spacing: 12) {
                    Text(clock(elapsed)).font(.system(size: 88, weight: .black)).foregroundStyle(HC.text).minimumScaleFactor(0.5)
                    Text("\(Int(kcal.rounded())) kcal" + (machine.tracksDistance ? " • " + String(format: "%.2f km", distance) : "")).font(.system(size: 28, weight: .bold)).foregroundStyle(HC.lime)
                    Text(ct("🔒 Açmak için uzun bas")).foregroundStyle(HC.muted).padding(.top, 30)
                }.frame(maxWidth: .infinity, maxHeight: .infinity).background(HC.bg.opacity(0.94)).contentShape(Rectangle())
                .onLongPressGesture(minimumDuration: 0.8) { locked = false; UIImpactFeedbackGenerator(style: .heavy).impactOccurred() }
            }
        }
        .background(HC.bg)
        .onAppear {
            ghost = GhostStore.read(machine.key)
            for c in machine.controls { values[c.key] = preset?.segments.first?.targets[c.key] ?? c.defaultValue }
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false; speech.stopSpeaking(at: .immediate) }
        .task(id: running) { await clockLoop() }
        .onChange(of: segmentIndex) { _, new in applySegment(new) }
        .alert(ct("Seans bitsin mi?"), isPresented: $confirmExit) {
            Button(ct("Bitir ve özetle")) { finish() }
            Button(ct("Kaydetmeden çık"), role: .destructive) { running = false; onCancel() }
            Button(ct("Vazgeç"), role: .cancel) {}
        } message: { Text(ct("Bitirirsen özet ekranına geçersin. Vazgeçersen kayıt yapılmaz.")) }
        .overlay(alignment: .topLeading) { Color.clear.frame(width: 1, height: 1) }
    }

    private func ring(_ r: CardioRate) -> some View {
        let p = preset.map { min(Double(elapsed) / Double($0.totalSeconds), 1) } ?? Double(elapsed % 60) / 60
        let zone = r.activeKcalPerMinute < 5 ? 0 : r.activeKcalPerMinute < 10 ? 1 : 2
        return ZStack {
            Circle().stroke(HC.surface, lineWidth: 14)
            Circle().trim(from: 0, to: p).stroke(HC.lime, style: StrokeStyle(lineWidth: 14, lineCap: .round)).rotationEffect(.degrees(-90)).animation(.linear(duration: 0.5), value: p)
            VStack(spacing: 2) {
                Text(ct("SÜRE")).font(.system(size: 12, weight: .black)).tracking(2).foregroundStyle(HC.muted)
                Text(clock(elapsed)).font(.system(size: 60, weight: .black)).foregroundStyle(HC.text).minimumScaleFactor(0.5).monospacedDigit()
                HStack(spacing: 3) {
                    ForEach(0..<3, id: \.self) { i in Capsule().fill(i <= zone ? HC.lime : HC.divider).frame(width: 10, height: 6) }
                    Text([ct("Hafif"), ct("Orta"), ct("Yüksek")][zone]).font(.system(size: 12, weight: .bold)).foregroundStyle(HC.lime).padding(.leading, 5)
                }.padding(.horizontal, 12).padding(.vertical, 5).background(HC.lime.opacity(0.14), in: Capsule()).padding(.top, 6)
            }
        }.padding(12).frame(maxWidth: .infinity).aspectRatio(1, contentMode: .fit).frame(maxHeight: 280)
    }

    private func tile(_ v: String, _ l: String, _ c: Color) -> some View {
        VStack(spacing: 0) { Text(v).font(.system(size: 24, weight: .black)).foregroundStyle(c).lineLimit(1); Text(l).font(.system(size: 11)).foregroundStyle(HC.muted).lineLimit(1) }
            .frame(maxWidth: .infinity).padding(.vertical, 12).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func segmentCard(_ preset: CardioPreset, _ idx: Int) -> some View {
        let seg = preset.segments[idx]
        let end = preset.segments.prefix(idx + 1).reduce(0) { $0 + $1.seconds }, start = end - seg.seconds
        return VStack(alignment: .leading, spacing: 8) {
            HStack { Text(ct(seg.label)).font(.hfBody.weight(.black)).foregroundStyle(HC.lime); Spacer(); Text("\(idx + 1)/\(preset.segments.count) • \(clock(max(end - elapsed, 0)))").foregroundStyle(HC.textSecondary) }
            ProgressView(value: min(max(Double(elapsed - start) / Double(seg.seconds), 0), 1)).tint(HC.lime)
            if idx + 1 < preset.segments.count { Text(tr("Sıradaki: ", "Next: ") + ct(preset.segments[idx + 1].label)).font(.system(size: 12)).foregroundStyle(HC.muted) }
        }.padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous)).padding(.top, 10)
    }

    private var gamePanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack { Text(ct("Oyun modu")).font(.hfBody.weight(.black)).foregroundStyle(HC.sleep); Spacer(); Text(tr("\(score) puan", "\(score) pts")).font(.hfBody.weight(.black)).foregroundStyle(HC.warning) }
            if machine.tracksDistance {
                let next = virtualRouteLandmarks.first { $0.0 > distance }, prev = virtualRouteLandmarks.last { $0.0 <= distance }
                let sp: Double = (next != nil && prev != nil) ? (distance - prev!.0) / (next!.0 - prev!.0) : 1
                Text("📍 " + ct(prev?.1 ?? ct("Başlangıç"))).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                ProgressView(value: min(max(sp, 0), 1)).tint(HC.sleep)
                if let next { Text(tr("Sıradaki: ", "Next: ") + "\(ct(next.1)) (\(String(format: "%.1f", next.0 - distance)) km)").font(.system(size: 12)).foregroundStyle(HC.textSecondary) }
            }
            if let g = GhostStore.at(ghost, elapsed) {
                let diff = progressMetric - g
                let unit = machine.tracksDistance ? "m" : "kcal"
                let shown = machine.tracksDistance ? Int((diff * 1000).rounded()) : Int(diff.rounded())
                Text(diff >= 0 ? tr("👻 Rekorunun \(shown) \(unit) önündesin", "👻 \(shown) \(unit) ahead of your record") : tr("👻 Rekorunun \(-shown) \(unit) gerisindesin", "👻 \(-shown) \(unit) behind your record")).font(.hfBody.weight(.bold)).foregroundStyle(diff >= 0 ? HC.lime : HC.coral)
            } else if ghost.isEmpty { Text(ct("👻 Bu ilk seansın; bitirince rekorun olacak.")).font(.system(size: 12)).foregroundStyle(HC.muted) }
            if let challengeText { Text("🎯 \(challengeText)").font(.hfBody.weight(.bold)).foregroundStyle(HC.warning); ProgressView(value: Double(challengeHeld) / 60).tint(HC.warning) }
        }.padding(14).frame(maxWidth: .infinity, alignment: .leading).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(HC.sleep.opacity(0.5), lineWidth: 1)).padding(.top, 14)
    }

    private func control(_ r: CardioRate, _ c: CardioControl) -> some View {
        let value = values[c.key] ?? c.defaultValue
        let isLabel = c.labels != nil
        return HStack(spacing: 4) {
            stepButton("minus") { values[c.key] = max(value - c.step, c.min); UISelectionFeedbackGenerator().selectionChanged() }
            VStack(spacing: 0) {
                Text(c.labels.flatMap { $0.indices.contains(Int(value)) ? ct($0[Int(value)]) : nil } ?? formatValue(value, c.step)).font(.system(size: isLabel ? 16 : 26, weight: .black)).foregroundStyle(HC.text).lineLimit(1).minimumScaleFactor(0.6)
                Text(c.unit.isEmpty ? ct(c.label) : "\(ct(c.label)) (\(ct(c.unit)))").font(.system(size: 10)).foregroundStyle(HC.textSecondary).lineLimit(1).minimumScaleFactor(0.7)
            }.frame(maxWidth: .infinity)
            stepButton("plus") { values[c.key] = min(value + c.step, c.max); UISelectionFeedbackGenerator().selectionChanged() }
        }.padding(8).background(HC.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func stepButton(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).font(.system(size: 20, weight: .bold)).foregroundStyle(HC.lime).frame(width: 48, height: 48).background(HC.lime.opacity(0.16), in: Circle()) }.buttonStyle(.plain)
    }

    // MARK: Mantık

    private func applySegment(_ idx: Int?) {
        guard let idx, let seg = preset?.segments[safe2: idx] else { return }
        for (k, v) in seg.targets { values[k] = v }
        if elapsed > 0 {
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            let detail = machine.controls.compactMap { c in seg.targets[c.key].map { "\(ct(c.label)) \(formatValue($0, c.step))" } }.joined(separator: ", ")
            say("\(ct(seg.label)). \(detail)")
        }
    }

    private func clockLoop() async {
        while running, !Task.isCancelled {
            try? await Task.sleep(for: .seconds(1))
            guard running, !Task.isCancelled else { return }
            let r = cardioRate(machineKey: machine.key, values: values, weightKg: weightKg)
            elapsed += 1; kcal += r.activeKcalPerMinute / 60; distance += r.speedKmh / 3600
            if elapsed % 10 == 0 { samples.append(CardioSample(second: elapsed, progress: machine.tracksDistance ? distance : kcal, kcalPerMin: r.activeKcalPerMinute)) }
            if let preset, elapsed == preset.totalSeconds / 2 { say(ct("Yarıya geldin, harika gidiyorsun.")) }
            if preset == nil, elapsed % 600 == 0 { say(tr("\(elapsed / 60) dakika oldu. \(Int(kcal.rounded())) kalori yaktın.", "\(elapsed / 60) minutes done. \(Int(kcal.rounded())) calories burned.")) }
            if game { gameTick() }
            if let preset, elapsed >= preset.totalSeconds { running = false; say(ct("Program tamamlandı. Tebrikler!")) }
        }
    }

    private func gameTick() {
        if challengeKey == nil, elapsed % 180 == 90, let control = machine.controls.first {
            let target = min((values[control.key] ?? control.defaultValue) + control.step * 2, control.max)
            challengeKey = control.key; challengeTarget = target; challengeHeld = 0; challengeLeft = 90
            challengeText = tr("60 sn boyunca \(control.label) ≥ \(formatValue(target, control.step)) \(control.unit)", "Hold \(ct(control.label)) ≥ \(formatValue(target, control.step)) \(ct(control.unit)) for 60 s")
            say(tr("Yeni görev. ", "New quest. ") + (challengeText ?? ""))
        }
        if let key = challengeKey {
            if (values[key] ?? 0) >= challengeTarget { challengeHeld += 1 }
            challengeLeft -= 1
            if challengeHeld >= 60 { score += 10; celebrate += 1; challengeKey = nil; challengeText = nil; UIImpactFeedbackGenerator(style: .heavy).impactOccurred(); say(ct("Görev tamam! On puan.")) }
            else if challengeLeft <= 0 { challengeKey = nil; challengeText = nil }
        }
    }

    private func finish() {
        running = false
        let final = samples + [CardioSample(second: elapsed, progress: progressMetric, kcalPerMin: rate.activeKcalPerMinute)]
        let record = elapsed >= 60 && GhostStore.saveIfBest(machine.key, final)
        onFinish(CardioSummaryData(machine: machine, seconds: elapsed, kcal: Int(kcal.rounded()), distanceKm: distance, samples: final, gameScore: score, newRecord: record, presetTitle: preset?.title, firstSession: ghost.isEmpty))
    }
}

private extension Array {
    subscript(safe2 i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}
private extension Array where Element == CardioControl {
    func chunked2() -> [[CardioControl]] { stride(from: 0, to: count, by: 2).map { Array(self[$0..<Swift.min($0 + 2, count)]) } }
}

// MARK: - Özet

private struct CardioSummaryView: View {
    @Environment(AppModel.self) private var app
    let data: CardioSummaryData
    var onDiscard: () -> Void
    @State private var confirmDiscard = false

    var body: some View {
        let avg = data.seconds > 0 ? Double(data.kcal) * 60 / Double(data.seconds) : 0
        let pts = data.samples.map(\.kcalPerMin)
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                Text(data.machine.emoji).font(.system(size: 28)).frame(width: 52, height: 52).background(HC.lime.opacity(0.14), in: Circle())
                VStack(alignment: .leading, spacing: 0) {
                    Text(data.newRecord && data.firstSession ? ct("İlk rekorun kaydedildi! 🎉") : data.newRecord ? ct("Yeni rekor! 🏆") : ct("Kardiyo tamam!")).font(.hfHeadline.weight(.black)).foregroundStyle(HC.text)
                    Text(data.presetTitle.map(ct) ?? ct(data.machine.title)).foregroundStyle(HC.textSecondary)
                }
                Spacer()
            }
            ScrollView {
                VStack(spacing: 12) {
                    ZStack {
                        Circle().stroke(HC.surface, lineWidth: 14)
                        Circle().trim(from: 0, to: min(max(Double(data.seconds) / 1800, 0.04), 1)).stroke(HC.lime, style: StrokeStyle(lineWidth: 14, lineCap: .round)).rotationEffect(.degrees(-90))
                        VStack(spacing: 0) { Text("\(data.kcal)").font(.system(size: 52, weight: .black)).foregroundStyle(HC.lime).contentTransition(.numericText()); Text(ct("kcal (tahmini)")).font(.system(size: 12)).foregroundStyle(HC.muted) }
                    }.frame(width: 200, height: 200).padding(.top, 12)
                    Text(ct("Günlük kalori hedefine eklenecek (tahmini)")).font(.system(size: 12)).foregroundStyle(HC.muted)
                    HStack(spacing: 8) {
                        sTile(clock(data.seconds), ct("süre"), HC.text)
                        if data.machine.tracksDistance { sTile(String(format: "%.2f", data.distanceKm), ct("km"), HC.text) }
                        sTile(String(format: "%.1f", avg), ct("ort. kcal/dk"), HC.text)
                        if data.gameScore > 0 { sTile("\(data.gameScore)", ct("oyun puanı"), HC.warning) }
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text(ct("Yoğunluk (kcal/dk)")).font(.system(size: 12, weight: .bold)).foregroundStyle(HC.textSecondary)
                        if pts.count >= 3 {
                            let mx = max(pts.max() ?? 1, 1)
                            GeometryReader { g in
                                let path = Path { p in for (i, v) in pts.enumerated() { let pt = CGPoint(x: g.size.width * CGFloat(i) / CGFloat(pts.count - 1), y: g.size.height - CGFloat(v / mx) * g.size.height * 0.9); if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) } } }
                                var fill = path; let _ = fill.addLine(to: CGPoint(x: g.size.width, y: g.size.height)); let _ = fill.addLine(to: CGPoint(x: 0, y: g.size.height)); let _ = fill.closeSubpath()
                                ZStack { fill.fill(HC.lime.opacity(0.15)); path.stroke(HC.lime, style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)) }
                            }.frame(height: 110)
                        } else { Text(ct("Grafik için en az 30 saniye gerekir.")).font(.system(size: 12)).foregroundStyle(HC.muted) }
                    }.padding(14).frame(maxWidth: .infinity, alignment: .leading).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    if data.seconds < 60 { Text(ct("1 dakikadan kısa seanslar kaydedilmez.")).font(.system(size: 12)).foregroundStyle(HC.warning) }
                }
            }
            HStack(spacing: 12) {
                Button { confirmDiscard = true } label: { Text(ct("Kaydetmeden çık")).font(.hfBody.weight(.bold)).foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity, minHeight: 58).overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(HC.divider)) }
                HfButton(title: app.workoutSaving ? ct("Kaydediliyor…") : ct("Kaydet"), enabled: !app.workoutSaving && data.seconds >= 60) {
                    let distance = data.machine.tracksDistance ? " • \(String(format: "%.2f", data.distanceKm)) km" : ""
                    let preset = data.presetTitle.map { " • \($0)" } ?? ""
                    Task { if await app.recordCardioSession(machineKey: data.machine.key, durationSeconds: data.seconds, calories: data.kcal, summary: "\(ct(data.machine.title))\(distance)\(preset)") { if !app.path.isEmpty { app.path.removeLast() } } }
                }
            }
        }.padding(.horizontal, 20).padding(.vertical, 12).frame(maxWidth: .infinity, maxHeight: .infinity).background(HC.bg)
        .overlay { if data.newRecord { ConfettiBurst(burstKey: data.seconds) } }
        .alert(ct("Kaydetmeden çıkılsın mı?"), isPresented: $confirmDiscard) {
            Button(ct("Çık"), role: .destructive) { onDiscard() }
            Button(ct("Vazgeç"), role: .cancel) {}
        } message: { Text(ct("Bu seansın kalorisi günlük hesabına eklenmez.")) }
    }

    private func sTile(_ v: String, _ l: String, _ c: Color) -> some View {
        VStack(spacing: 0) { Text(v).font(.system(size: 22, weight: .black)).foregroundStyle(c).lineLimit(1).minimumScaleFactor(0.6); Text(l).font(.system(size: 11)).foregroundStyle(HC.muted).lineLimit(1) }
            .frame(maxWidth: .infinity).padding(.vertical, 12).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
