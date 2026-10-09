import SwiftUI

func categoryIcon(_ c: String) -> String {
    switch c { case "nutrition": return "fork.knife"; case "steps": return "figure.walk"; case "pilates": return "figure.pilates"; case "flexibility": return "figure.flexibility"; case "coach": return "sparkles"; default: return "dumbbell.fill" }
}
@MainActor func categoryColor(_ c: String) -> Color {
    switch c { case "nutrition": return HC.warning; case "steps": return HC.coral; case "pilates": return HC.sleep; case "flexibility": return HC.water; default: return HC.lime }
}

/// ✓ ✓ ● ○ ○ — tamamlanan, toparlanma, bugün ve kalan günler.
struct ChallengeDayDots: View {
    let total: Int
    let statuses: [String]
    let todayDone: Bool
    var compact = false
    var body: some View {
        let size: CGFloat = compact ? 10 : (total <= 14 ? 18 : 16)
        FlowLayout(spacing: 6) {
            ForEach(0..<total, id: \.self) { i in
                let status = i < statuses.count ? statuses[i] : nil
                let isToday = status == nil && i == statuses.count && !todayDone
                let color: Color = status == "recovery" ? HC.water : (status != nil ? HC.lime : (isToday ? HC.lime.opacity(0.35) : HC.surfaceSoft))
                ZStack {
                    Circle().fill(color).frame(width: size, height: size)
                    if !compact, let status { Image(systemName: status == "recovery" ? "leaf.fill" : "checkmark").font(.system(size: 9, weight: .bold)).foregroundStyle(HC.onLime) }
                }
            }
        }
    }
}

// MARK: - Hub

struct ChallengeHubSection: View {
    @Environment(AppModel.self) private var app
    @State private var filter = "for_you"
    private let filters = ["for_you", "popular", "workout", "nutrition", "steps", "pilates", "flexibility", "coach"]

    private func label(_ f: String) -> String {
        switch f { case "for_you": return tr("Sana Özel", "For You"); case "popular": return tr("Popüler", "Popular"); case "coach": return tr("Fit Koç", "Fit Coach"); default: return categoryLabel(f) }
    }

    var body: some View {
        let c = app.challenge
        let coachName = app.prefs.coachName.isEmpty ? tr("Fit Koç", "Fit Coach") : app.prefs.coachName
        VStack(alignment: .leading, spacing: 14) {
            HfCard(padding: 16, onTap: { c.coachPreview = nil; app.push(.coachChallenge) }) {
                HStack(spacing: 14) {
                    ZStack { Circle().fill(HC.lime).frame(width: 48, height: 48); Image("FitCoach").resizable().scaledToFit().frame(width: 38, height: 38) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tr("\(coachName) ile Challenge Oluştur", "Create a Challenge with \(coachName)")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text).multilineTextAlignment(.leading)
                        Text(tr("Hedefin, zamanın ve ekipmanına göre kişisel plan; her gün check-in'ine uyum sağlar.", "A personal plan for your goal, time and equipment that adapts to your daily check-in.")).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.leading)
                    }
                    Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.lime)
                }
            }
            if c.hubOffline { Label(tr("Çevrimdışı: son kaydedilen hâli gösteriliyor.", "Offline: showing the last saved state."), systemImage: "icloud.slash").font(.hfSmall).foregroundStyle(HC.muted) }
            if let hub = c.hub {
                let today = Date()
                if !hub.active.isEmpty {
                    HStack { Text(tr("Devam eden", "In progress")).font(.hfTitleL).foregroundStyle(HC.text); Spacer(); Text("\(hub.active.count)/3").font(.hfLabel.weight(.bold)).foregroundStyle(HC.muted) }
                    ForEach(hub.active) { ch in ActiveChallengeRow(challenge: ch, today: today) { app.push(.challengeDetail(ch.id)) } }
                }
                HfChipRow { ForEach(filters, id: \.self) { f in HfChip(text: label(f), selected: filter == f) { filter = f } } }
                let activeKeys = Set(hub.active.map(\.templateKey))
                if filter == "coach" {
                    let mine = hub.challenges.filter { $0.plan.source == "coach" }
                    if mine.isEmpty { HfEmptyState(icon: "sparkles", title: tr("Henüz Fit Koç challenge'ın yok", "No Fit Coach challenge yet"), message: tr("Hedefine, zamanına ve ekipmanına göre sana özel bir challenge oluştur.", "Create a challenge built around your goal, time and equipment.")) }
                    ForEach(mine) { ch in ActiveChallengeRow(challenge: ch, today: today) { app.push(.challengeDetail(ch.id)) } }
                } else {
                    let list: [ChallengeTemplate] = {
                        switch filter {
                        case "for_you": let r = hub.templates.filter(\.recommended); return r.isEmpty ? hub.templates.filter { $0.fit == "fit" } : r
                        case "popular": return Array(hub.templates.sorted { ($0.participants, $0.popular ? 1 : 0) > ($1.participants, $1.popular ? 1 : 0) }.prefix(6))
                        default: return hub.templates.filter { $0.plan.category == filter }
                        }
                    }()
                    if list.isEmpty { HfEmptyState(icon: "trophy.fill", title: tr("Bu kategoride challenge yok", "No challenges in this category"), message: tr("Başka bir kategoriye göz at.", "Take a look at another category.")) }
                    ForEach(list) { t in
                        ChallengeTemplateCard(template: t, active: activeKeys.contains(t.plan.key),
                                              onOpen: { app.push(.challengeTemplate(t.plan.key)) },
                                              onJoin: { Task { if let id = await c.join(key: t.plan.key) { app.push(.challengeDetail(id)) } } })
                    }
                }
            } else if c.hubBusy { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding(40) }
            else {
                HfEmptyState(icon: "trophy.fill", title: tr("Challenge'lar yüklenemedi", "Couldn't load challenges"), message: c.hubError ?? tr("Bağlantını kontrol edip tekrar dene.", "Check your connection and try again."))
                HfButton(title: tr("Tekrar dene", "Try again")) { Task { await c.loadHub() } }
            }
        }
    }
}

struct ActiveChallengeRow: View {
    let challenge: UserChallenge
    let today: Date
    var onTap: () -> Void
    var body: some View {
        let state = challenge.state(today: today)
        let color = categoryColor(challenge.plan.category)
        HfCard(padding: 16, onTap: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    HfIconBadge(system: categoryIcon(challenge.plan.category), tint: color)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(challenge.plan.title.text).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text).lineLimit(1)
                        Text(challenge.status == "completed" ? tr("Tamamlandı", "Completed") : challenge.status == "abandoned" ? tr("Bırakıldı", "Left")
                             : state.todayDone ? tr("Gün \(state.doneDays) / \(state.totalDays) · bugün tamam", "Day \(state.doneDays) / \(state.totalDays) · done today") : tr("Gün \(state.currentDay) / \(state.totalDays)", "Day \(state.currentDay) / \(state.totalDays)")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    }
                    Spacer()
                    if state.streak > 0 && challenge.status == "active" { HfPill(text: "🔥 \(state.streak)", color: HC.warning) }
                }
                HfProgressBar(progress: Double(state.percent) / 100, color: color)
            }
        }
    }
}

struct ChallengeTemplateCard: View {
    let template: ChallengeTemplate
    let active: Bool
    var onOpen: () -> Void
    var onJoin: () -> Void
    var body: some View {
        let plan = template.plan, color = categoryColor(plan.category)
        HfCard(padding: 16, onTap: onOpen) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 12) {
                    HfIconBadge(system: categoryIcon(plan.category), tint: color, size: 44, radius: 14)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(plan.title.text).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text).multilineTextAlignment(.leading)
                        Text(plan.description.text).font(.hfSmall).foregroundStyle(HC.textSecondary).lineLimit(2).multilineTextAlignment(.leading)
                    }
                }
                HfChipRow {
                    HfTag(text: difficultyLabel(plan.difficulty)); HfTag(text: tr("\(plan.days.count) gün", "\(plan.days.count) days"))
                    if let m = minutesLabel(template.minutes) { HfTag(text: m) }
                    if template.fit != "fit" { HfTag(text: fitLabel(template.fit)) }
                }
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("+\(template.rewardXp) XP").font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime)
                        if let p = participantsLabel(template.participants) { Text(p).font(.system(size: 11)).foregroundStyle(HC.muted) }
                    }
                    Spacer()
                    Button(action: active ? onOpen : onJoin) {
                        Text(active ? tr("Devam et", "Continue") : tr("Katıl", "Join")).font(.system(size: 14, weight: .heavy)).foregroundStyle(active ? HC.text : HC.onLime).padding(.horizontal, 18).frame(minHeight: 40).background(active ? HC.surfaceHigh : HC.lime, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }.buttonStyle(.plain)
                }
            }
        }
    }
}

// MARK: - Detay

func earnedChallengeXp(_ challenge: UserChallenge, rules: ChallengeRules) -> Int {
    let state = challenge.state()
    let worked = challenge.days.filter { $0.status != "recovery" }.count, recovery = challenge.days.filter { $0.status == "recovery" }.count
    return worked * rules.dayXp + recovery * rules.recoveryXp + (state.longestStreak >= 3 ? rules.streak3Xp : 0) + (state.longestStreak / 7) * rules.streak7Xp
        + (challenge.status == "completed" ? rules.completeXp + (challenge.socialChallengeId != nil ? rules.friendBonusXp : 0) : 0)
}

struct ChallengeDetailView: View {
    @Environment(AppModel.self) private var app
    let challengeId: String?
    let templateKey: String?
    @State private var confirmAbandon = false
    @State private var confirmRecovery = false
    @State private var friendPicker: String?

    private var hub: ChallengeHub? { app.challenge.hub }
    private var challenge: UserChallenge? { challengeId.flatMap { id in hub?.challenges.first { $0.id == id } } }
    private var template: ChallengeTemplate? { templateKey.flatMap { k in hub?.templates.first { $0.plan.key == k } } }

    var body: some View {
        let rules = hub?.rules ?? ChallengeRules()
        if let plan = challenge?.plan ?? template?.plan {
            let state = challenge?.state()
            let active = challenge?.status == "active"
            let color = categoryColor(plan.category)
            let reward = template?.rewardXp ?? (plan.days.count * rules.dayXp + (plan.days.count >= 3 ? rules.streak3Xp : 0) + (plan.days.count / 7) * rules.streak7Xp + rules.completeXp)
            ScreenScaffold(spacing: 14) {
                HfScreenHeader(title: "Challenge", onBack: { app.path.removeLast() }) {
                    if let ch = challenge { ShareLink(item: tr("\(ch.plan.title.text) challenge'ında \(earnedChallengeXp(ch, rules: rules)) XP kazandım! #Hedefit", "I earned \(earnedChallengeXp(ch, rules: rules)) XP in the \(ch.plan.title.text) challenge! #Hedefit")) { Image(systemName: "square.and.arrow.up").foregroundStyle(HC.text).frame(width: 44, height: 44).background(HC.surfaceHigh, in: Circle()) } }
                }
                HfCard(padding: 18) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 12) {
                            HfIconBadge(system: categoryIcon(plan.category), tint: color, size: 48, radius: 15)
                            VStack(alignment: .leading, spacing: 2) { Text(categoryLabel(plan.category).upperLocalized + " · +\(reward) XP").font(.hfLabel.weight(.heavy)).foregroundStyle(color); Text(plan.title.text).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text) }
                            Spacer(); if plan.source == "coach" { HfPill(text: tr("Fit Koç", "Fit Coach")) }
                        }
                        Text(plan.description.text).font(.hfBody).foregroundStyle(HC.textSecondary)
                        HfChipRow {
                            HfTag(text: tr("\(plan.days.count) gün", "\(plan.days.count) days")); HfTag(text: difficultyLabel(plan.difficulty))
                            if let m = minutesLabel(template?.minutes ?? minRange(plan)) { HfTag(text: m) }
                            if let e = equipmentLabel(plan.equipment) { HfTag(text: e) }
                        }
                    }
                }
                if let challenge, let state {
                    HfCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text(challenge.status == "completed" ? tr("Tamamlandı 🎉", "Completed 🎉") : challenge.status == "abandoned" ? tr("Bırakıldı", "Left") : tr("Gün \(state.currentDay) / \(state.totalDays)", "Day \(state.currentDay) / \(state.totalDays)")).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                                Spacer(); Text(tr("%\(state.percent)", "\(state.percent)%")).font(.system(size: 16, weight: .heavy)).foregroundStyle(HC.lime)
                            }
                            HfProgressBar(progress: Double(state.percent) / 100, color: color)
                            ChallengeDayDots(total: state.totalDays, statuses: challenge.days.map(\.status), todayDone: state.todayDone || !active)
                        }
                    }
                    HStack(spacing: 10) {
                        HfStatTile(label: tr("Seri", "Streak"), value: "🔥 \(state.streak)", sub: tr("En uzun \(state.longestStreak)", "Best \(state.longestStreak)"))
                        HfStatTile(label: tr("Toparlanma", "Recovery"), value: "\(state.recoveryAllowance - state.recoveryUsed)/\(state.recoveryAllowance)", sub: tr("hak kaldı", "days left"))
                        HfStatTile(label: "XP", value: "+\(earnedChallengeXp(challenge, rules: rules))", sub: tr("kazanıldı", "earned"), valueColor: HC.lime)
                    }
                    if active && !state.todayDone { todayCard(challenge, state: state, dayXp: rules.dayXp) }
                    else if active { HfCard { Text(tr("Bugünkü görev tamam. Yarın Gün \(min(state.doneDays + 1, state.totalDays)) seni bekliyor.", "Today's task is done. Day \(min(state.doneDays + 1, state.totalDays)) is waiting tomorrow.")).foregroundStyle(HC.textSecondary).font(.hfBody) } }
                } else if template != nil {
                    HfButton(title: app.challenge.actionBusy ? tr("Başlatılıyor…", "Starting…") : tr("Challenge'a Katıl", "Join Challenge"), icon: "play.fill", loading: app.challenge.actionBusy) {
                        Task { if let id = await app.challenge.join(key: plan.key) { app.path.removeLast(); app.push(.challengeDetail(id)) } }
                    }
                    if !app.isGuest && plan.days.count <= 30 { HfButton(title: tr("Arkadaşınla yap", "Do it with a friend"), icon: "person.2.fill", secondary: true) { friendPicker = plan.key } }
                }
                HfSectionHeader(title: tr("Gün gün plan", "Day-by-day plan"))
                HfCard(padding: 12) {
                    VStack(spacing: 0) {
                        ForEach(Array(plan.days.enumerated()), id: \.offset) { i, task in
                            let log = challenge?.days.first { $0.dayIndex == i }
                            let meta: String = {
                                switch log?.status { case "recovery": return tr("Toparlanma günü", "Recovery day"); case "adapted": return tr("Uyarlandı · tamamlandı", "Adapted · completed"); case "completed": return tr("Tamamlandı", "Completed")
                                default: return (challenge != nil && i == (state?.doneDays ?? -1) && active) ? tr("Sıradaki", "Up next") : "+\(rules.dayXp) XP" }
                            }()
                            HfStepRow(index: i + 1, title: taskLabel(task), meta: meta, done: log != nil)
                        }
                    }
                }
                HfSectionHeader(title: tr("Ödüller", "Rewards"))
                HfCard {
                    VStack(spacing: 6) {
                        rewardLine(tr("Her tamamlanan gün", "Each completed day"), "+\(rules.dayXp) XP"); rewardLine(tr("3 günlük seri", "3-day streak"), "+\(rules.streak3Xp) XP")
                        if plan.days.count >= 7 { rewardLine(tr("Her 7 günlük seri", "Every 7-day streak"), "+\(rules.streak7Xp) XP") }
                        rewardLine(tr("Challenge'ı bitir", "Finish the challenge"), "+\(rules.completeXp) XP"); rewardLine(tr("Arkadaşınla bitir", "Finish with a friend"), "+\(rules.friendBonusXp) XP"); rewardLine(tr("Toparlanma günü", "Recovery day"), "+\(rules.recoveryXp) XP")
                        Text(tr("Rozetler: İlk Challenge, 3/7/30 Günlük Seri, 10 Challenge ve kategori rozetleri.", "Badges: First Challenge, 3/7/30-Day Streak, 10 Challenges and category badges.")).font(.hfSmall).foregroundStyle(HC.muted).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                HfSectionHeader(title: tr("Kurallar", "Rules"))
                HfCard {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach([tr("Her gün bir görev var; bir günde yalnızca bir gün tamamlanır.", "There's one task per day; you can complete one day per calendar day."),
                                 tr("Gün kaçırırsan challenge bozulmaz, kaldığın günden devam edersin; yalnızca serin sıfırlanır.", "Missing a day doesn't break the challenge — you continue where you left off; only your streak resets."),
                                 tr("Check-in'ine göre görev hafifletilebilir; uyarlanmış gün tam sayılır.", "Your task can be lightened based on your check-in; an adapted day counts in full."),
                                 tr("Toparlanma günü seriyi bozmaz ama antrenman sayılmaz. Her 7 gün için 1 hakkın var.", "A recovery day keeps your streak but doesn't count as a workout. You get 1 per 7 days."),
                                 tr("Kazandığın XP asla silinmez; bırakırsan da seninle kalır.", "XP you earn is never taken away, even if you leave.")], id: \.self) { Text("• \($0)").font(.hfBody).foregroundStyle(HC.textSecondary) }
                    }
                }
                if let challenge, active {
                    if !app.isGuest && challenge.socialChallengeId == nil && challenge.plan.source == "catalog" { HfButton(title: tr("Arkadaşa meydan oku", "Challenge a friend"), icon: "person.2.fill", secondary: true) { friendPicker = challenge.templateKey } }
                    Button { confirmAbandon = true } label: { Label(tr("Challenge'ı bırak", "Leave challenge"), systemImage: "rectangle.portrait.and.arrow.right").foregroundStyle(HC.muted) }.frame(maxWidth: .infinity)
                }
            }
            .task { if let ch = challenge, ch.status == "active", state?.todayDone == false { await app.challenge.loadToday(ch.id) } }
            .alert(tr("Challenge'ı bırakmak istiyor musun?", "Leave this challenge?"), isPresented: $confirmAbandon) {
                Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
                Button(tr("Bırak", "Leave"), role: .destructive) { if let ch = challenge { Task { if await app.challenge.abandon(ch.id) { app.path.removeLast() } } } }
            } message: { Text(tr("Kazandığın XP ve rozetler seninle kalır. İstersen daha sonra baştan başlayabilirsin.", "Your XP and badges stay with you. You can start again from the beginning later.")) }
            .alert(tr("Toparlanma günü kullanılsın mı?", "Use a recovery day?"), isPresented: $confirmRecovery) {
                Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
                Button(tr("Kullan", "Use it")) { if let ch = challenge { Task { await app.challenge.useRecoveryDay(ch.id) } } }
            } message: { Text(tr("Serin bozulmaz ve gün ilerler; bu gün antrenman olarak sayılmaz.", "Your streak stays and the day moves on; it won't count as a workout.")) }
            .sheet(item: Binding(get: { friendPicker.map { StringID(id: $0) } }, set: { friendPicker = $0?.id })) { FriendChallengePicker(templateKey: $0.id) }
        } else { ProgressView().tint(HC.lime).task { await app.challenge.loadHub() } }
    }

    private func minRange(_ plan: ChallengePlan) -> (Int, Int)? {
        let m = plan.days.compactMap(\.minutes); return m.isEmpty ? nil : (m.min()!, m.max()!)
    }

    private func rewardLine(_ a: String, _ b: String) -> some View { HStack { Text(a).foregroundStyle(HC.textSecondary); Spacer(); Text(b).foregroundStyle(HC.lime).fontWeight(.heavy) }.font(.hfBody) }

    private func todayCard(_ ch: UserChallenge, state: ChallengeState, dayXp: Int) -> some View {
        let today = app.challenge.today?.challenge.id == ch.id ? app.challenge.today : nil
        let planned = ch.nextTask(), adaptation = today?.adaptation
        let effective = adaptation?.task ?? planned
        let recoveryLeft = state.recoveryAllowance - state.recoveryUsed
        return HfCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack { Text(tr("BUGÜNKÜ GÖREV", "TODAY'S TASK")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime); Spacer(); if app.challenge.todayBusy { ProgressView().tint(HC.lime) } else { Text("+\(dayXp) XP").font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime) } }
                if let effective { Text(taskLabel(effective)).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text) }
                if adaptation?.adapted == true, let planned {
                    HStack(alignment: .top, spacing: 10) {
                        HfIconBadge(system: "sparkles", tint: HC.lime, size: 32, radius: 10)
                        VStack(alignment: .leading, spacing: 2) { if let m = adaptation?.message { Text(m).font(.hfBody).foregroundStyle(HC.text) }; Text(tr("Planlanan: \(taskLabel(planned)) · seri bozulmaz", "Planned: \(taskLabel(planned)) · streak stays")).font(.hfSmall).foregroundStyle(HC.muted) }
                    }
                }
                if let session = today?.session, effective?.kind == "session" { Text(session.exercises.prefix(4).map(\.name).joined(separator: " · ") + (session.exercises.count > 4 ? " …" : "")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                if adaptation?.suggestRecovery == true && recoveryLeft > 0 { Text(tr("Fit Koç bugün dinlenmeni öneriyor. Toparlanma günü serini korur.", "Fit Coach suggests resting today. A recovery day keeps your streak.")).font(.hfSmall.weight(.bold)).foregroundStyle(HC.water) }
                let auto = ["steps", "water", "meals"].contains(effective?.kind ?? "")
                HfButton(title: app.challenge.actionBusy ? tr("Hazırlanıyor…", "Preparing…") : (auto ? tr("İlerlemeyi kontrol et", "Check my progress") : tr("Bugünkü görevi yap", "Do today's task")), icon: "play.fill", loading: app.challenge.actionBusy) { Task { await app.challenge.startTask(ch.id) } }
                if recoveryLeft > 0 { HfButton(title: tr("Toparlanma günü kullan (\(recoveryLeft))", "Use a recovery day (\(recoveryLeft))"), icon: "leaf.fill", secondary: true) { confirmRecovery = true } }
                if auto { Text(tr("Bu görev kayıtlarından otomatik doğrulanır; hedefe ulaşınca gün kendiliğinden tamamlanır.", "This task is verified from your logs; the day completes on its own when you hit the goal.")).font(.hfSmall).foregroundStyle(HC.muted) }
            }
        }
    }
}

/// Arkadaş seçip ortak challenge başlatır.
struct FriendChallengePicker: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let templateKey: String
    @State private var mode = 0
    @State private var selected: Set<String> = []
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HfSegmented(options: [tr("Rekabet Et", "Compete"), tr("Birlikte Tamamla", "Complete together")], selection: $mode)
                    let friends = app.social.summary?.friends ?? []
                    if friends.isEmpty { HfEmptyState(icon: "person.2", title: tr("Henüz arkadaşın yok", "No friends yet"), message: tr("Topluluk sekmesinden arkadaş ekle.", "Add friends from the Community tab.")) }
                    ForEach(friends) { f in
                        Button { if selected.contains(f.user.id) { selected.remove(f.user.id) } else { selected.insert(f.user.id) } } label: {
                            HStack(spacing: 12) { AvatarView(url: nil, name: f.user.label, size: 40); Text(f.user.label).foregroundStyle(HC.text).font(.hfBody.weight(.semibold)); Spacer(); Image(systemName: selected.contains(f.user.id) ? "checkmark.circle.fill" : "circle").foregroundStyle(selected.contains(f.user.id) ? HC.lime : HC.muted).font(.system(size: 22)) }
                                .padding(12).background(HC.surface, in: RoundedRectangle(cornerRadius: 16))
                        }.buttonStyle(.plain)
                    }
                    HfButton(title: tr("Meydan oku", "Challenge"), icon: "paperplane.fill", loading: app.challenge.actionBusy, enabled: !selected.isEmpty) {
                        Task { if await app.challenge.createFriendChallenge(templateKey: templateKey, mode: mode == 0 ? "compete" : "together", friendIds: Array(selected)) { dismiss() } }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Arkadaşını seç", "Pick friends")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.task { if app.social.summary == nil { await app.social.loadSummary() } }.presentationDetents([.large])
    }
}

// MARK: - Fit Koç challenge oluştur

struct CoachChallengeView: View {
    @Environment(AppModel.self) private var app
    @State private var focus: String?
    @State private var days: Int?
    @State private var minutes: Int?
    @State private var equipment: String?
    @State private var level: String?

    private var prefs: CoachChallengePreferences { CoachChallengePreferences(focus: focus, days: days, minutes: minutes, equipment: equipment, level: level) }

    private func focusLabel(_ v: String?) -> String { switch v { case "core": return "Core"; case "full_body": return tr("Tüm vücut", "Full body"); case "pilates": return "Pilates"; case "flexibility": return tr("Esneklik", "Flexibility"); case "steps": return tr("Adım", "Steps"); default: return tr("Otomatik", "Auto") } }
    private func eqLabel(_ v: String?) -> String { switch v { case "none": return tr("Ekipmansız", "No equipment"); case "band": return tr("Direnç bandı", "Resistance band"); case "gym": return tr("Spor salonu", "Gym"); default: return tr("Otomatik", "Auto") } }

    var body: some View {
        let coachName = app.prefs.coachName.isEmpty ? tr("Fit Koç", "Fit Coach") : app.prefs.coachName
        let c = app.challenge
        SubPage(title: tr("\(coachName) ile Challenge", "Challenge with \(coachName)")) {
            HfCard(padding: 16) {
                HStack(alignment: .top, spacing: 12) {
                    ZStack { Circle().fill(HC.lime).frame(width: 42, height: 42); Image("FitCoach").resizable().scaledToFit().frame(width: 34, height: 34) }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(tr("Bildiklerimi kullanıyorum", "I'll use what I already know")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                        Text(tr("Profilin, check-in'lerin ve antrenman geçmişin. Seviyen, kısıtlamaların ve son günlerdeki enerjin de hesaba katılır. Aşağıdakiler isteğe bağlı.", "Your profile, check-ins and training history. Your level, limitations and recent energy are taken into account too. Everything below is optional.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    }
                }
            }
            HfSectionHeader(title: tr("Hedef", "Focus")); HfChipRow { ForEach([nil, "core", "full_body", "pilates", "flexibility", "steps"] as [String?], id: \.self) { v in HfChip(text: focusLabel(v), selected: focus == v) { focus = v } } }
            HfSectionHeader(title: tr("Süre", "Length")); HfChipRow { ForEach([nil, 7, 14, 21, 30] as [Int?], id: \.self) { v in HfChip(text: v.map { tr("\($0) gün", "\($0) days") } ?? tr("Otomatik", "Auto"), selected: days == v) { days = v } } }
            HfSectionHeader(title: tr("Günde ayırabileceğin süre", "Time per day")); HfChipRow { ForEach([nil, 10, 15, 20, 30] as [Int?], id: \.self) { v in HfChip(text: v.map { tr("\($0) dk", "\($0) min") } ?? tr("Otomatik", "Auto"), selected: minutes == v) { minutes = v } } }
            HfSectionHeader(title: tr("Ekipman", "Equipment")); HfChipRow { ForEach([nil, "none", "band", "gym"] as [String?], id: \.self) { v in HfChip(text: eqLabel(v), selected: equipment == v) { equipment = v } } }
            HfSectionHeader(title: tr("Seviye", "Level")); HfChipRow { ForEach([nil, "beginner", "intermediate", "advanced"] as [String?], id: \.self) { v in HfChip(text: v.map(difficultyLabel) ?? tr("Otomatik", "Auto"), selected: level == v) { level = v } } }
            HfButton(title: c.coachBusy && c.coachPreview == nil ? tr("Hazırlanıyor…", "Preparing…") : tr("Planı hazırla", "Build my plan"), icon: "sparkles", secondary: c.coachPreview != nil, loading: c.coachBusy && c.coachPreview == nil) { Task { await c.previewCoach(prefs) } }
            if let preview = c.coachPreview {
                HfCard(padding: 18) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(preview.plan.title.text).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
                        Text(preview.plan.description.text).font(.hfBody).foregroundStyle(HC.textSecondary)
                        HfChipRow { HfTag(text: difficultyLabel(preview.plan.difficulty)); if let m = minutesLabel(preview.minutes) { HfTag(text: m) }; if let e = equipmentLabel(preview.plan.equipment) { HfTag(text: e) } }
                        Text("+\(preview.rewardXp) XP").font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.lime)
                        ForEach(Array(preview.plan.days.prefix(7).enumerated()), id: \.offset) { i, t in HfStepRow(index: i + 1, title: taskLabel(t), meta: tr("Gün \(i + 1)", "Day \(i + 1)")) }
                        if preview.plan.days.count > 7 { Text(tr("+\(preview.plan.days.count - 7) gün daha", "+\(preview.plan.days.count - 7) more days")).font(.hfSmall).foregroundStyle(HC.muted) }
                        HfButton(title: c.coachBusy ? tr("Başlatılıyor…", "Starting…") : tr("Challenge'ı başlat", "Start challenge"), icon: "play.fill", loading: c.coachBusy) {
                            Task { if let id = await c.startCoach(prefs) { app.path.removeAll(); app.select(.explore); app.exploreSegmentRequest = .challenges; app.push(.challengeDetail(id)) } }
                        }
                    }
                }
            }
        }
    }
}
