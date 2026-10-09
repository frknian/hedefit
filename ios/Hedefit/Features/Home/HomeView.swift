import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var app
    @State private var metric: HomeMetric?
    @State private var editingQuick = false
    @State private var showAllQuick = false
    @State private var showProgramPicker = false
    @State private var showGuide = false

    private var d: Dashboard? { app.dashboard }

    var body: some View {
        ScreenScaffold(spacing: 16) {
            header
            if let d {
                if app.guideTotal > 0 && app.guideDone < app.guideTotal { guideCard }
                nextStepCard(d)
                checkinCard
                if let result = app.adaptive.result { AdaptiveCard(result: result) }
                if let challenge = app.challenge.hub?.primaryActive() { ActiveChallengeCard(challenge: challenge, extra: max((app.challenge.hub?.active.count ?? 1) - 1, 0)) }
                let up = wellnessUp
                if up { WellnessRow() }
                HfSectionHeader(title: tr("Hedef yolculuğu", "Goal journey"))
                GoalProjectionCard(d: d, onAddWeight: { metric = .weight })
                let done = [d.nutritionLogs.reduce(0) { $0 + $1.calories } >= Int(Double(d.nutritionGoal.calories) * 0.9), d.steps >= app.prefs.stepGoal, d.waterMl >= app.prefs.waterGoalMl, d.sleepMinutes >= 420].filter { $0 }.count
                HfSectionHeader(title: tr("Günlük denge", "Daily balance"), trailing: nil)
                Text(tr("4 hedefin \(done)'i tamam", "\(done) of 4 goals met")).font(.hfSmall).foregroundStyle(HC.muted).padding(.top, -10)
                DailyBalanceCard(d: d, metric: $metric)
                if !up { WellnessRow() }
                discoverCard
                HfSectionHeader(title: tr("Hızlı işlemler", "Quick actions"), trailing: tr("Özelleştir", "Customize")) { editingQuick = true }
                quickActions(d)
                homePrograms(d)
            } else if app.dataLoading { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding(40) }
            else { HfEmptyState(icon: "wifi.exclamationmark", title: tr("Veriler yüklenemedi", "Couldn't load your data"), message: app.dataError) }
        }
        .refreshable { await app.refreshAll() }
        .sheet(item: $metric) { HomeMetricSheet(metric: $0) }
        .sheet(isPresented: $editingQuick) { QuickActionPicker() }
        .sheet(isPresented: $showProgramPicker) { HomeProgramPicker() }
        .fullScreenCover(isPresented: $showGuide) { WelcomeGuideView() }
        .task { await app.adaptive.loadToday() }
    }

    // MARK: Üst bölüm

    private var header: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(tr("Merhaba, \(app.displayName)", "Hi, \(app.displayName)")).font(.hfTitle).foregroundStyle(HC.text).lineLimit(1).minimumScaleFactor(0.6)
                Text(tr("Bugün • ", "Today • ") + Date().formatted(.dateTime.day().month(.wide).weekday(.wide).locale(AppLang.shared.locale))).font(.hfSmall).foregroundStyle(HC.muted)
            }
            Spacer(minLength: 4)
            StreakTrophyButton(streak: app.dailyStreak) { app.push(.rewards) }
            HfCircleButton(system: "calendar", label: tr("Antrenman takvimi", "Workout calendar")) { app.push(.calendar) }
            Button { app.push(.profile) } label: { AvatarView(url: d?.profile.avatarURL, name: app.displayName, size: 44) }.buttonStyle(.plain)
        }
    }

    private var wellnessUp: Bool {
        guard let p = d?.profile else { return false }
        return wellnessProminent(gender: p.gender, preferredStyles: p.historyAnswers.count > 8 ? p.historyAnswers[8] : "")
    }

    private var guideCard: some View {
        HfCard(padding: 14, onTap: { showGuide = true }) {
            HStack(spacing: 12) {
                ZStack { HfRing(progress: Double(app.guideDone) / Double(max(app.guideTotal, 1)), lineWidth: 5); Text("\(app.guideDone)/\(app.guideTotal)").font(.system(size: 11, weight: .black)).foregroundStyle(HC.text) }.frame(width: 46, height: 46)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tr("Başlangıç görevleri", "Getting started")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                    Text(tr("Uygulamayı yaparak öğren, kalan görevleri tamamla.", "Learn by doing — finish the remaining missions.")).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading)
                }
                Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.lime).font(.system(size: 16, weight: .black))
            }
        }
    }

    // MARK: Bugünün antrenmanı / sıradaki adım

    @ViewBuilder private func nextStepCard(_ d: Dashboard) -> some View {
        let workoutDone = d.sessions.contains { $0.date.map { Dates.isSameDay($0, Date()) } ?? false }
        if d.workouts.isEmpty || !workoutDone { todayCard(d) }
        else {
            let step: (String, String, String, String, () -> Void) =
                d.nutritionLogs.isEmpty ? (tr("Öğününü ekle", "Log your first meal"), tr("Yaz ya da fotoğrafını çek, kalorisini senin için hesaplayalım.", "Type it or snap a photo — calories are worked out for you."), tr("Öğün ekle", "Add meal"), "fork.knife", { app.select(.nutrition) })
                : d.waterMl < app.prefs.waterGoalMl / 2 ? (tr("Su içmeyi unutma", "Drink some water"), tr("Bugünkü su hedefinin yarısının altındasın.", "You're under half of today's water goal."), tr("Su ekle", "Add water"), "drop.fill", { metric = .water })
                : (tr("Bugün harika gidiyor", "Great day so far"), tr("Yarın neye odaklanacağını koçuna sor.", "Ask your coach what to focus on tomorrow."), tr("Koça sor", "Ask Fit Coach"), "sparkles", { app.select(.coach) })
            HeroCard {
                HStack { Text(tr("SIRADAKİ ADIM", "NEXT STEP")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime); Spacer(); HfPill(text: tr("Antrenman tamam ✓", "Workout done ✓")) }
                Text(step.0).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
                Text(step.1).font(.hfBody).foregroundStyle(HC.textSecondary)
                HfButton(title: step.2, icon: step.3, action: step.4)
            }
        }
    }

    private func todayCard(_ d: Dashboard) -> some View {
        let program = app.activeProgram ?? d.workoutPrograms.first
        let done = d.sessions.contains { $0.date.map { Dates.isSameDay($0, Date()) } ?? false }
        let areas = Array(NSOrderedSet(array: d.workouts.map(\.area).filter { !$0.isEmpty })).compactMap { $0 as? String }.prefix(3)
        return HeroCard {
            HStack { Text(tr("BUGÜNÜN ANTRENMANI", "TODAY'S WORKOUT")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime); Spacer(); if done { HfPill(text: tr("Tamamlandı", "Completed")) } }
            Text(program.map { localizedProgramName($0.name, source: $0.source) } ?? tr("Antrenman planını oluştur", "Create your workout plan")).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text).lineLimit(2)
            if !areas.isEmpty { HStack(spacing: 6) { ForEach(Array(areas), id: \.self) { HfTag(text: localizedArea($0)) } } }
            HfButton(title: d.workouts.isEmpty ? tr("Plan oluştur", "Create a plan") : tr("Antrenmanı başlat", "Start workout"), icon: d.workouts.isEmpty ? "plus" : "play.fill") { app.openProgram(program?.id) }
        }
    }

    private var checkinCard: some View {
        let today = app.adaptive.checkinToday
        return HfCard(padding: 14, onTap: { app.showCheckin = true }) {
            HStack(spacing: 12) {
                HfIconBadge(system: "waveform.path.ecg", tint: HC.lime, size: 36, radius: 11)
                VStack(alignment: .leading, spacing: 2) {
                    Text(today == nil ? tr("Bugün nasılsın?", "How are you today?") : tr("Check-in tamam ✓", "Check-in done ✓")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                    Text(today.map { tr("Enerji \($0.energy)/10 • Uyku \($0.sleepQuality)/10 • güncellemek için dokun", "Energy \($0.energy)/10 • Sleep \($0.sleepQuality)/10 • tap to update") } ?? tr("Antrenmanı gününe uyarlamak için 5 dokunuş.", "5 taps to adapt your workout to your day.")).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading)
                }
                Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.lime).font(.system(size: 16, weight: .black))
            }
        }
    }

    private var discoverCard: some View {
        HfCard(padding: 16, onTap: { app.push(.muscleMap) }) {
            HStack(spacing: 14) {
                Image("MuscleAnatomy").resizable().scaledToFit().frame(width: 70, height: 86).clipShape(RoundedCornerShape(radius: 14))
                VStack(alignment: .leading, spacing: 4) {
                    Text(tr("Kas haritası", "Muscle map")).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                    Text(tr("Bir kasa dokun, 600+ hareketi ve araçları keşfet.", "Tap a muscle, browse 600+ exercises and tools.")).font(.hfBody).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading)
                }
                Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.lime).font(.system(size: 18, weight: .black))
            }
        }
    }

    // MARK: Hızlı işlemler

    private func quickActions(_ d: Dashboard) -> some View {
        let catalog = QuickActionCatalog.make(app: app, metric: { metric = $0 })
        let keys = app.prefs.homeQuickActions.filter { catalog[$0] != nil }
        let active = (keys.isEmpty ? AppPreferences.defaultQuickActions : keys).compactMap { catalog[$0] }
        let shown = showAllQuick ? active : Array(active.prefix(4))
        return VStack(spacing: 10) {
            ForEach(Array(stride(from: 0, to: shown.count, by: 2)), id: \.self) { i in
                HStack(spacing: 10) {
                    ForEach(Array(shown[i..<min(i + 2, shown.count)]), id: \.title) { a in HfActionTile(icon: a.icon, tint: HC.lime, title: a.title, subtitle: a.subtitle, action: a.action) }
                    if i + 1 >= shown.count { Color.clear.frame(maxWidth: .infinity) }
                }
            }
            if active.count > 4 {
                Button(showAllQuick ? tr("Daha az göster", "Show less") : tr("Tümünü göster (\(active.count))", "Show all (\(active.count))")) { withAnimation { showAllQuick.toggle() } }
                    .font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime)
            }
        }
    }

    private func homePrograms(_ d: Dashboard) -> some View {
        let programs = d.workoutPrograms.filter(\.showOnHome)
        return VStack(alignment: .leading, spacing: 9) {
            HfSectionHeader(title: tr("Programlar", "Programs"), trailing: tr("Düzenle", "Manage")) { showProgramPicker = true }
            if programs.isEmpty {
                Button { showProgramPicker = true } label: { Label(tr("Program ekle", "Add a program"), systemImage: "plus").foregroundStyle(HC.lime) }
            } else {
                ScrollView(.horizontal) {
                    HStack(spacing: 9) {
                        ForEach(programs) { p in
                            HfCard(padding: 14, onTap: { app.openProgram(p.id) }) {
                                VStack(alignment: .leading) {
                                    HStack(spacing: 8) { HfIconBadge(system: "dumbbell.fill", tint: HC.lime, size: 30, radius: 10); Text("PROGRAM").font(.system(size: 11, weight: .bold)).foregroundStyle(HC.lime) }
                                    Spacer(minLength: 6)
                                    Text(localizedProgramName(p.name, source: p.source)).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text).lineLimit(2).multilineTextAlignment(.leading)
                                }.frame(height: 80, alignment: .topLeading)
                            }.frame(width: 218)
                        }
                    }
                }.scrollIndicators(.hidden)
            }
        }
    }
}

// MARK: - Ortak ana ekran parçaları

struct HeroCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 14) { content }
            .padding(20).frame(maxWidth: .infinity, alignment: .leading)
            .background(LinearGradient(colors: [HC.lime.opacity(0.26), HC.surface], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).stroke(HC.lime.opacity(0.4), lineWidth: 1))
    }
}

struct RoundedCornerShape: Shape {
    var radius: CGFloat
    func path(in rect: CGRect) -> Path { RoundedRectangle(cornerRadius: radius, style: .continuous).path(in: rect) }
}

struct AvatarView: View {
    let url: String?
    let name: String
    var size: CGFloat = 44
    var body: some View {
        ZStack {
            Circle().fill(HC.lime)
            Group {
                if let url, let u = URL(string: url) {
                    AsyncImage(url: u) { phase in
                        if let image = phase.image { image.resizable().scaledToFill() } else { initials }
                    }
                } else { initials }
            }.clipShape(Circle()).padding(2)
        }.frame(width: size, height: size)
    }
    private var initials: some View { ZStack { Circle().fill(HC.surfaceHigh); Text(String(name.prefix(2)).uppercased()).font(.system(size: size * 0.34, weight: .bold)).foregroundStyle(HC.lime) } }
}

struct StreakTrophyButton: View {
    let streak: Int
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                ZStack(alignment: .top) {
                    Image(systemName: "trophy.fill").font(.system(size: 17)).foregroundStyle(HC.lime).offset(y: 5)
                    Image(systemName: "flame.fill").font(.system(size: 13)).foregroundStyle(HC.warning).offset(y: -5)
                }.frame(width: 28, height: 30)
                Text("\(streak)").font(.system(size: 16, weight: .black)).foregroundStyle(HC.text)
            }.padding(.leading, 8).padding(.trailing, 12).padding(.vertical, 6).background(HC.surfaceHigh, in: Capsule())
        }.buttonStyle(PressableStyle())
    }
}

// MARK: - Uyarlama / challenge / wellness kartları

struct AdaptiveCard: View {
    @Environment(AppModel.self) private var app
    let result: AdaptiveResult
    var body: some View {
        HeroCard {
            HStack { Text(tr("BUGÜNÜN PLANI, UYARLANDI", "TODAY'S PLAN, ADAPTED")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime); Spacer(); HfPill(text: "\(result.estimatedMinutes) " + tr("dk", "min")) }
            Text(result.explanation(english: AppLang.shared.en)).font(.hfBody).foregroundStyle(HC.text)
            HfButton(title: tr("Uyarlanmış planı başlat", "Start adapted plan"), icon: "play.fill") {
                app.workout.start(exercises: result.exercises, title: tr("Uyarlanmış plan", "Adapted plan"))
            }
            Button(tr("Orijinal planla devam et", "Keep my original plan")) { app.adaptive.dismissAdaptive() }.font(.hfBody).foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity)
            if let hint = upgradeHint(for: result.lockedActions) {
                Button { app.lock(hint == "switch_pilates" ? .pilatesSwitch : .adaptiveAction) } label: {
                    Text(hint == "switch_pilates" ? tr("Premium bugünü Pilates oturumuna çevirebilir. Görmek için dokun.", "Premium can turn today into a Pilates session. Tap to see.")
                         : tr("Plus ve Premium daha akıllı değişim, mobilite ve toparlanma ekler. Görmek için dokun.", "Plus and Premium add smarter swaps, mobility and recovery. Tap to see.")).font(.hfSmall.weight(.bold)).foregroundStyle(HC.lime).multilineTextAlignment(.leading)
                }
            }
        }
    }
}

struct ActiveChallengeCard: View {
    @Environment(AppModel.self) private var app
    let challenge: UserChallenge
    var extra = 0
    var body: some View {
        let state = challenge.state()
        let task = challenge.nextTask()
        HfCard(padding: 18, onTap: { app.push(.challengeDetail(challenge.id)) }) {
            VStack(alignment: .leading, spacing: 12) {
                HStack { Text(tr("AKTİF CHALLENGE", "ACTIVE CHALLENGE")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime); Spacer(); if extra > 0 { Text(tr("+\(extra) tane daha", "+\(extra) more")).font(.hfLabel).foregroundStyle(HC.muted) } }
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(challenge.plan.title.text).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text).multilineTextAlignment(.leading)
                        Text(tr("Gün \(state.currentDay) / \(state.totalDays)", "Day \(state.currentDay) / \(state.totalDays)")).font(.hfBody).foregroundStyle(HC.textSecondary)
                    }
                    Spacer()
                    if state.streak > 0 { HStack(spacing: 4) { Image(systemName: "flame.fill").foregroundStyle(HC.warning); Text("\(state.streak)").font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.warning) }.padding(.horizontal, 10).padding(.vertical, 6).background(HC.warning.opacity(0.15), in: Capsule()) }
                }
                HStack(spacing: 7) {
                    ForEach(0..<min(state.totalDays, 14), id: \.self) { i in
                        Circle().fill(i < state.doneDays ? HC.lime : (i == state.doneDays && !state.finished ? HC.lime.opacity(0.4) : HC.surfaceSoft)).frame(width: 11, height: 11)
                    }
                }
                if let task, !state.todayDone, !state.finished {
                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 2) { Text(tr("Bugünün görevi", "Today's task")).font(.hfSmall).foregroundStyle(HC.muted); Text(taskLabel(task)).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text) }
                        Spacer()
                        Text("+\(app.challenge.hub?.rules.dayXp ?? 25) XP").font(.system(size: 18, weight: .heavy)).foregroundStyle(HC.lime)
                    }
                    HfButton(title: tr("Görevi yap", "Do today's task"), icon: "play.fill", loading: app.challenge.actionBusy) { Task { await app.challenge.startTask(challenge.id) } }
                } else {
                    Label(state.finished ? tr("Challenge tamamlandı!", "Challenge complete!") : tr("Bugünün görevi tamam ✓", "Today's task done ✓"), systemImage: "checkmark.circle.fill").font(.hfTitleM.weight(.bold)).foregroundStyle(HC.lime)
                }
            }
        }
    }
}

struct WellnessRow: View {
    @Environment(AppModel.self) private var app
    @State private var busy: WellnessKind?
    private var cards: [(WellnessKind, String, String, String)] {
        [(.pilatesToday, tr("Bugün için Pilates", "Pilates for Today"), tr("20 dk • Core ve duruş", "20 min • Core & posture"), "figure.pilates"),
         (.lowImpactRecovery, tr("Düşük Etkili Toparlanma", "Low Impact Recovery"), tr("15 dk • Yumuşak ve eklem dostu", "15 min • Gentle & joint-friendly"), "leaf.fill"),
         (.postureMobility, tr("Duruş ve Mobilite", "Posture & Mobility"), tr("15 dk • Sırt, omuz, kalça", "15 min • Back, shoulders, hips"), "figure.cooldown")]
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HfSectionHeader(title: tr("Bugün için öneriler", "Suggested for today"))
            ScrollView(.horizontal) {
                HStack(spacing: 10) {
                    ForEach(cards, id: \.0) { kind, title, subtitle, icon in
                        HfCard(padding: 16, onTap: { Task { await start(kind) } }) {
                            VStack(alignment: .leading, spacing: 10) {
                                HfIconBadge(system: icon, tint: HC.lime, size: 36, radius: 11)
                                Text(title).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text).lineLimit(2).multilineTextAlignment(.leading)
                                Text(subtitle).font(.hfSmall).foregroundStyle(HC.textSecondary).lineLimit(2).multilineTextAlignment(.leading)
                                HStack(spacing: 6) { if busy == kind { ProgressView().tint(HC.lime) }; Text(tr("Başla", "Start") + " ›").font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime) }
                            }
                        }.frame(width: 214)
                    }
                }
            }.scrollIndicators(.hidden)
        }
    }

    private func start(_ kind: WellnessKind) async {
        if app.limits.wellnessStarterOnly && kind != .pilatesToday && false { app.lock(.wellnessContent); return }
        busy = kind; defer { busy = nil }
        let minutes = kind == .pilatesToday ? 20 : 15
        if let session = await app.adaptive.loadWellnessSession(kind, minutes: minutes) { app.workout.start(exercises: session.exercises, title: session.title) }
    }
}

// MARK: - Hedef yolculuğu & günlük denge

struct GoalProjectionCard: View {
    @Environment(AppModel.self) private var app
    let d: Dashboard
    var onAddWeight: () -> Void
    var body: some View {
        let current = d.measurements.last(where: { $0.weightKg != nil })?.weightKg ?? d.profile.weightKg
        let target = d.profile.targetWeightKg
        let weeks = GoalScience.weeks(current: current, target: target)
        let initial = d.measurements.first(where: { $0.weightKg != nil })?.weightKg ?? current
        let total = (initial != nil && target != nil) ? abs(target! - initial!) : 0
        let completed = (initial != nil && current != nil) ? abs(current! - initial!) : 0
        let progress = total > 0.05 ? min(max(completed / total, 0), 1) : 0
        HfCard(padding: 18, onTap: { app.push(.goalJourney) }) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 2) { Text(tr("ŞİMDİ", "NOW")).font(.system(size: 11, weight: .bold)).foregroundStyle(HC.muted); Text(current.map { Units.formatWeight($0, app.units) } ?? "—").font(.system(size: 24, weight: .heavy)).foregroundStyle(HC.text) }
                    Spacer()
                    HfPill(text: weeks.map { tr("\($0) hafta kaldı", "\($0) weeks left") } ?? tr("Hedef belirle", "Set a goal"))
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) { Text(tr("HEDEF", "TARGET")).font(.system(size: 11, weight: .bold)).foregroundStyle(HC.muted); Text(target.map { Units.formatWeight($0, app.units) } ?? "—").font(.system(size: 24, weight: .heavy)).foregroundStyle(HC.textSecondary) }
                }
                HfProgressBar(progress: progress, height: 8)
                Button(action: onAddWeight) { Label(tr("Kilo ekle", "Log weight"), systemImage: "plus").font(.system(size: 14, weight: .bold)).foregroundStyle(HC.lime) }
            }
        }
    }
}

struct DailyBalanceCard: View {
    @Environment(AppModel.self) private var app
    let d: Dashboard
    @Binding var metric: HomeMetric?
    private func fmt(_ n: Int) -> String { let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = AppLang.shared.locale; return f.string(from: NSNumber(value: n)) ?? "\(n)" }

    var body: some View {
        let consumed = d.nutritionLogs.reduce(0) { $0 + $1.calories }, target = max(d.nutritionGoal.calories, 1)
        HfCard(padding: 16) {
            VStack(spacing: 12) {
                HStack(alignment: .top, spacing: 0) {
                    ring(tr("Kalori", "Calories"), fmt(consumed), "/ \(fmt(target))", Double(consumed) / Double(target), HC.lime, .calories)
                    ring(tr("Adım", "Steps"), fmt(d.steps), "/ \(fmt(app.prefs.stepGoal))", Double(d.steps) / Double(max(app.prefs.stepGoal, 1)), HC.water, .steps)
                    ring(tr("Su", "Water"), String(format: "%.1f", Double(d.waterMl) / 1000), String(format: "/ %.1f L", Double(app.prefs.waterGoalMl) / 1000), Double(d.waterMl) / Double(max(app.prefs.waterGoalMl, 1)), HC.water, .water)
                    ring(tr("Uyku", "Sleep"), d.sleepMinutes > 0 ? "\(d.sleepMinutes / 60)\(tr("s", "h")) \(d.sleepMinutes % 60)\(tr("dk", "m"))" : "—", tr("/ 8s", "/ 8h"), Double(d.sleepMinutes) / 480, HC.sleep, .sleep)
                }
                if let hint = hint {
                    Button(action: hint.2) {
                        HStack { Text(hint.0).font(.hfBody).foregroundStyle(HC.textSecondary); Spacer(); Text(hint.1 + " ›").font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime) }
                            .padding(.horizontal, 14).padding(.vertical, 10).background(HC.lime.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }.buttonStyle(.plain)
                }
            }
        }
    }

    private var hint: (String, String, () -> Void)? {
        if d.nutritionLogs.isEmpty { return (tr("Bugün henüz öğün eklemedin", "No meals logged today"), tr("Öğün ekle", "Add meal"), { app.select(.nutrition) }) }
        if d.waterMl == 0 { return (tr("Henüz su eklemedin", "Haven't had water yet"), tr("Su ekle", "Add water"), { metric = .water }) }
        return nil
    }

    private func ring(_ label: String, _ value: String, _ target: String, _ progress: Double, _ color: Color, _ m: HomeMetric) -> some View {
        let closed = progress >= 1
        return Button { metric = m } label: {
            VStack(spacing: 8) {
                ZStack {
                    HfRing(progress: progress, color: color, lineWidth: 7).frame(width: 70, height: 70)
                    VStack(spacing: 0) {
                        Text(value).font(.system(size: 13, weight: .heavy)).foregroundStyle(HC.text).lineLimit(1).minimumScaleFactor(0.6)
                        Text(closed ? "✓" : target).font(.system(size: closed ? 12 : 9, weight: .semibold)).foregroundStyle(closed ? color : HC.muted).lineLimit(1).minimumScaleFactor(0.6)
                    }.frame(width: 56)
                }
                Text(label).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
            }.frame(maxWidth: .infinity)
        }.buttonStyle(.plain).sensoryFeedback(.success, trigger: closed)
    }
}

// MARK: - Hızlı işlem kataloğu

struct QuickActionItem { let key: String; let icon: String; let title: String; let subtitle: String; let action: () -> Void }

@MainActor
enum QuickActionCatalog {
    static let orderedKeys = ["nutrition", "route", "sleep", "coach", "workout", "goal", "atlas", "musclemap", "water", "steps", "calories", "calendar", "activity", "progress", "watch", "rewards", "cardio", "curlgame", "friends"]

    static func make(app: AppModel, metric: @escaping (HomeMetric) -> Void) -> [String: QuickActionItem] {
        let d = app.dashboard
        let sleep = d?.sleepMinutes ?? 0
        let items: [QuickActionItem] = [
            .init(key: "nutrition", icon: "fork.knife", title: tr("Öğün ekle", "Log meal"), subtitle: tr("Yazı, foto veya arama", "Text, photo or search"), action: { app.select(.nutrition) }),
            .init(key: "route", icon: "location.fill", title: tr("Hedefit Rota", "Hedefit Route"), subtitle: tr("GPS ile koşu, yürüyüş", "GPS run or walk"), action: { app.push(.route) }),
            .init(key: "sleep", icon: "bed.double.fill", title: tr("Uyku gir", "Log sleep"), subtitle: sleep > 0 ? tr("Dün gece: \(sleep / 60)s \(sleep % 60)dk", "Last night: \(sleep / 60)h \(sleep % 60)m") : tr("Henüz girilmedi", "Not logged yet"), action: { metric(.sleep) }),
            .init(key: "coach", icon: "sparkles", title: tr("FitKoç'a sor", "Ask Fit Coach"), subtitle: tr("Antrenman ve beslenme", "Training and nutrition"), action: { app.select(.coach) }),
            .init(key: "workout", icon: "dumbbell.fill", title: tr("Antrenmanı başlat", "Start workout"), subtitle: tr("Bugünün planına atla", "Jump into today's plan"), action: { app.openProgram(app.activeProgram?.id) }),
            .init(key: "goal", icon: "flag.fill", title: tr("Hedef yolculuğu", "Goal journey"), subtitle: tr("Kilo & tempo", "Weight pace"), action: { app.push(.goalJourney) }),
            .init(key: "atlas", icon: "book.fill", title: tr("Hareket Atlası", "Movement Atlas"), subtitle: tr("Teknik ve hareketler", "Technique and exercises"), action: { app.push(.library) }),
            .init(key: "musclemap", icon: "figure.arms.open", title: tr("Kas Haritası", "Muscle Map"), subtitle: tr("Kasa dokun, hareketleri gör", "Tap a muscle, see exercises"), action: { app.push(.muscleMap) }),
            .init(key: "water", icon: "drop.fill", title: tr("Su ekle", "Add water"), subtitle: "", action: { metric(.water) }),
            .init(key: "steps", icon: "figure.walk", title: tr("Adımlarım", "Steps"), subtitle: "", action: { metric(.steps) }),
            .init(key: "calories", icon: "flame.fill", title: tr("Kalori özeti", "Calories"), subtitle: "", action: { metric(.calories) }),
            .init(key: "calendar", icon: "calendar", title: tr("Antrenman takvimi", "Workout calendar"), subtitle: "", action: { app.push(.calendar) }),
            .init(key: "activity", icon: "plus", title: tr("Aktivite ekle", "Log activity"), subtitle: "", action: { app.push(.manualActivity) }),
            .init(key: "progress", icon: "chart.line.uptrend.xyaxis", title: tr("İlerleme", "Progress"), subtitle: "", action: { app.select(.progress) }),
            .init(key: "watch", icon: "applewatch", title: tr("Akıllı saat", "Smart watch"), subtitle: "", action: { app.push(.wearables) }),
            .init(key: "rewards", icon: "trophy.fill", title: tr("Ödüller", "Rewards"), subtitle: "", action: { app.push(.rewards) }),
            .init(key: "cardio", icon: "figure.run", title: tr("Kardiyo", "Cardio"), subtitle: tr("Koşu bandı, bisiklet, kürek", "Treadmill, bike, rower"), action: { app.push(.cardio) }),
            .init(key: "curlgame", icon: "gamecontroller.fill", title: tr("Oyun", "Game"), subtitle: tr("Sporcunu çalıştır", "Train your athlete"), action: { app.push(.curlGame) }),
            .init(key: "friends", icon: "person.2.fill", title: tr("Arkadaşlar", "Friends"), subtitle: tr("Karşılaştır, akış ve meydan okumalar", "Compare, feed & challenges"), action: { app.push(.friends) }),
        ]
        return Dictionary(uniqueKeysWithValues: items.map { ($0.key, $0) })
    }
}

struct QuickActionPicker: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var picked: [String] = []
    var body: some View {
        let catalog = QuickActionCatalog.make(app: app, metric: { _ in })
        NavigationStack {
            List {
                ForEach(QuickActionCatalog.orderedKeys, id: \.self) { key in
                    if let a = catalog[key] {
                        Button {
                            if let i = picked.firstIndex(of: key) { picked.remove(at: i) } else if picked.count < 10 { picked.append(key) }
                        } label: {
                            HStack(spacing: 12) {
                                HfIconBadge(system: a.icon, tint: HC.lime, size: 36, radius: 11)
                                Text(a.title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                                Spacer()
                                Image(systemName: picked.contains(key) ? "checkmark.square.fill" : "square").foregroundStyle(picked.contains(key) ? HC.lime : HC.muted).font(.system(size: 22))
                            }
                        }.listRowBackground(HC.surface)
                    }
                }
            }
            .scrollContentBackground(.hidden).background(HC.bg)
            .navigationTitle(tr("Hızlı işlemleri özelleştir", "Customize quick actions")).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(tr("Kaydet", "Save")) { app.prefs.homeQuickActions = picked; dismiss() }.disabled(!(2...10).contains(picked.count)) }
            }
        }.onAppear { picked = app.prefs.homeQuickActions }
    }
}

struct HomeProgramPicker: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                if (app.dashboard?.workoutPrograms ?? []).isEmpty { Text(tr("Önce Keşfet sekmesinden bir program oluştur.", "Create a program from the Explore tab first.")).foregroundStyle(HC.textSecondary).listRowBackground(HC.surface) }
                ForEach(app.dashboard?.workoutPrograms ?? []) { p in
                    HStack {
                        VStack(alignment: .leading) { Text(localizedProgramName(p.name, source: p.source)).foregroundStyle(HC.text); Text(tr("\(p.exercises.count) hareket", "\(p.exercises.count) exercises")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                        Spacer()
                        Toggle("", isOn: Binding(get: { p.showOnHome }, set: { v in Task { await app.setProgramHome(p, v) } })).labelsHidden().tint(HC.lime)
                    }.listRowBackground(HC.surface)
                }
            }.scrollContentBackground(.hidden).background(HC.bg)
            .navigationTitle(tr("Program ekle", "Add programs")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Tamam", "Done")) { dismiss() } } }
        }.presentationDetents([.medium, .large])
    }
}

extension AppModel {
    /// Başlangıç görevleri (Android WelcomeGuide) ilerlemesi.
    static let guideActions = ["workout", "nutrition", "cardio", "musclemap", "coach", "reminders", "healthconnect"]
    var guideTotal: Int { Self.guideActions.count }
    var guideCompleted: Set<String> {
        var done = guideTried
        if let d = dashboard {
            if !d.sessions.filter({ $0.manualActivityKey == nil }).isEmpty { done.insert("workout") }
            if !d.nutritionLogs.isEmpty { done.insert("nutrition") }
            if d.sessions.contains(where: { $0.manualActivityKey?.hasPrefix("cardio_") == true }) || !d.routeActivities.isEmpty { done.insert("cardio") }
        }
        if prefs.notificationsEnabled { done.insert("reminders") }
        return done
    }
    var guideDone: Int { Self.guideActions.filter { guideCompleted.contains($0) }.count }

    func openProgram(_ id: String?) { select(.explore) }

    func setProgramHome(_ program: WorkoutProgram, _ show: Bool) async {
        guard var d = dashboard else { return }
        do {
            let updated = try await repo.setProgramHomeVisibility(program, showOnHome: show)
            d.workoutPrograms = d.workoutPrograms.map { $0.id == updated.id ? updated : $0 }; dashboard = d
        } catch { fail(error) }
    }
}
