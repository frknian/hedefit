import SwiftUI

private let leagues: [(Int, String)] = [(0, "Bronz Lig"), (2_000, "Gümüş Lig"), (5_000, "Altın Lig"), (10_000, "Elmas Lig"), (20_000, "Şampiyon")]
private func leagueFor(_ xp: Int) -> String { leagues.last { xp >= $0.0 }!.1 }
private func leagueLabel(_ name: String) -> String {
    if !LangStore.english { return name }
    switch name { case "Bronz Lig": return "Bronze League"; case "Gümüş Lig": return "Silver League"; case "Altın Lig": return "Gold League"; case "Elmas Lig": return "Diamond League"; default: return "Champion" }
}

/// Ödüller: seviye, görevler, alışkanlık haftası, haftalık challenge, başarımlar, lig.
struct RewardsView: View {
    @Environment(AppModel.self) private var app
    @State private var selected: Achievement?
    @State private var showAll = false

    var body: some View {
        if let snap = app.gamification() {
            let unlocked = snap.achievements.filter { $0.unlockedAt != nil }.count
            let shown = showAll ? snap.achievements : Array(snap.achievements.prefix(8))
            let name = app.dashboard?.profile.displayName ?? ""
            ScreenScaffold(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    HfScreenHeader(title: tr("Ödüller", "Rewards")) { if !app.path.isEmpty { app.path.removeLast() } }
                    Text(tr("Seviye", "Level") + " \(snap.level.level) • \(leagueLabel(leagueFor(snap.totalXp)))").font(.hfSmall).foregroundStyle(HC.muted).padding(.leading, 56)
                }
                hero(snap)
                HfNavRow(icon: "person.2.fill", tint: HC.lime, title: tr("Arkadaşlar", "Friends"), subtitle: tr("XP'ni karşılaştır, akışı ve haftalık meydan okumaları gör", "Compare XP, feed and weekly challenges")) { app.push(.friends) }
                HfSectionHeader(title: tr("Neler kazanacaksın", "What you'll earn"))
                roadmap(snap.totalXp)
                HfSectionHeader(title: tr("Bugünün görevleri", "Today's quests"), trailing: "\(snap.dailyQuests.filter(\.completed).count) / \(snap.dailyQuests.count)")
                quests(snap)
                HfSectionHeader(title: tr("Alışkanlık gücü", "Habit strength"), trailing: "%\(snap.habitStrength)")
                habit(snap.habitWeek)
                HfSectionHeader(title: tr("Haftalık meydan okuma", "Weekly challenge"), trailing: "250 XP")
                weekly(snap.challenge)
                HfSectionHeader(title: tr("Başarımlar", "Achievements"), trailing: "\(unlocked) / \(snap.achievements.count)")
                HfCard(padding: 14) {
                    VStack(spacing: 12) {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8, alignment: .top), count: 4), spacing: 12) {
                            ForEach(shown) { a in badge(a) }
                        }
                        if snap.achievements.count > 8 { Button(showAll ? tr("Daha az", "Show less") : tr("Tümünü gör", "See all")) { showAll.toggle() }.font(.hfBody.weight(.bold)).foregroundStyle(HC.lime) }
                    }
                }
                HfSectionHeader(title: tr("Lig durumu", "League"), trailing: leagueLabel(leagueFor(snap.totalXp)))
                league(name.isEmpty ? tr("Sen", "You") : name, snap)
                HfCard(padding: 20) { HStack(alignment: .top, spacing: 12) { Image(systemName: "brain.head.profile").font(.system(size: 26)).foregroundStyle(HC.lime); Text(snap.motivation).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text) } }
            }
            .sheet(item: $selected) { a in
                VStack(alignment: .leading, spacing: 12) {
                    Text(a.title).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
                    Text(a.description).foregroundStyle(HC.textSecondary)
                    HfProgressBar(progress: min(a.progress / max(a.target, 1), 1), height: 8)
                    Text("\(Int(a.progress))/\(a.target.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(a.target))" : "\(a.target)") • +\(a.rewardXp) XP").font(.hfBody.weight(.bold)).foregroundStyle(HC.lime)
                    Spacer()
                }.padding(24).frame(maxWidth: .infinity, alignment: .leading).background(HC.bg).presentationDetents([.height(280)])
            }
        } else { ProgressView().tint(HC.lime).frame(maxWidth: .infinity, maxHeight: .infinity) }
    }

    private func hero(_ s: GamificationSnapshot) -> some View {
        let remaining = max(s.level.nextLevelXp - s.level.currentXp, 0)
        let r = s.aiReward
        return HfCard(padding: 20) {
            VStack(spacing: 16) {
                HStack(spacing: 16) {
                    ZStack { HfRing(progress: s.level.progress, color: HC.warning, lineWidth: 9).frame(width: 88, height: 88)
                        VStack(spacing: 0) { Text("\(s.level.level)").font(.system(size: 22, weight: .heavy)).foregroundStyle(HC.text); Text(tr("seviye", "level")).font(.system(size: 10, weight: .semibold)).foregroundStyle(HC.textSecondary) } }
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(s.level.currentXp) XP").font(.system(size: 22, weight: .heavy)).foregroundStyle(HC.text).contentTransition(.numericText())
                        Text(tr("Seviye \(s.level.level + 1) için \(remaining) XP kaldı", "\(remaining) XP to level \(s.level.level + 1)")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        HStack(spacing: 5) { Text("🔥"); Text(tr("\(s.streakDays) günlük seri", "\(s.streakDays) day streak")).font(.system(size: 12, weight: .heavy)).foregroundStyle(HC.coral) }
                            .padding(.horizontal, 10).padding(.vertical, 5).background(HC.coral.opacity(0.15), in: Capsule())
                    }
                    Spacer(minLength: 0)
                }
                HfDivider()
                HStack(spacing: 12) {
                    HfIconBadge(system: "brain.head.profile", tint: HC.lime, size: 34, radius: 11)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(r.nextThreshold.map { tr("Sonraki ödül: \($0) XP", "Next reward at \($0) XP") } ?? (r.extraQuestions > 0 ? tr("+\(r.extraQuestions) günlük FitKoç sorusu", "+\(r.extraQuestions) daily AI questions") : tr("FitKoç ödülü", "Fit Coach reward"))).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                        if let t = r.nextThreshold { HfProgressBar(progress: min(Double(s.totalXp) / Double(t), 1), height: 6) }
                        if r.extraQuestions > 0 { Text(tr("+\(r.extraQuestions) günlük soru hakkı kazandın", "You earned +\(r.extraQuestions) daily questions")).font(.hfSmall).foregroundStyle(HC.lime) }
                    }
                }
            }
        }
    }

    private func roadmap(_ xp: Int) -> some View {
        let q = { (n: Int) in (n, tr("+\(n == 300 ? 1 : n == 500 ? 2 : n == 750 ? 3 : n == 1000 ? 4 : 5) günlük FitKoç sorusu", "+\(n == 300 ? 1 : n == 500 ? 2 : n == 750 ? 3 : n == 1000 ? 4 : 5) daily Fit Coach question\(n == 300 ? "" : "s")")) }
        let items = [q(300), q(500), q(750), q(1000), q(1250)] + leagues.dropFirst().map { ($0.0, tr("\(leagueLabel($0.1)) rozeti", "\(leagueLabel($0.1)) badge")) }
        let next = items.first { xp < $0.0 }
        return HfCard(padding: 20) {
            VStack(alignment: .leading, spacing: 0) {
                if let next {
                    Text(tr("Sıradaki: \(next.1)", "Next: \(next.1)")).font(.hfBody.weight(.heavy)).foregroundStyle(HC.text)
                    HfProgressBar(progress: min(Double(xp) / Double(next.0), 1), height: 8).padding(.top, 8)
                    Text("\(xp) / \(next.0) XP").font(.hfBody.weight(.bold)).foregroundStyle(HC.lime).padding(.vertical, 4)
                }
                ForEach(Array(items.enumerated()), id: \.offset) { i, m in
                    let earned = xp >= m.0
                    if i > 0 { HfDivider() }
                    HStack(spacing: 12) {
                        Image(systemName: earned ? "checkmark" : "lock.fill").font(.system(size: 13, weight: .bold)).foregroundStyle(earned ? HC.onLime : HC.textSecondary).frame(width: 28, height: 28).background(earned ? HC.lime : HC.surfaceHigh, in: Circle())
                        Text(m.1).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text.opacity(earned ? 1 : 0.8)); Spacer()
                        Text("\(m.0) XP").font(.system(size: 12, weight: .bold)).foregroundStyle(earned ? HC.lime : HC.text)
                    }.padding(.vertical, 9)
                }
            }
        }
    }

    private func quests(_ s: GamificationSnapshot) -> some View {
        HfCard(padding: 20) {
            VStack(spacing: 0) {
                ForEach(Array(s.dailyQuests.enumerated()), id: \.element.id) { i, q in
                    if i > 0 { HfDivider() }
                    HStack(spacing: 12) {
                        Image(systemName: q.completed ? "checkmark" : "circle").font(.system(size: 14, weight: .bold)).foregroundStyle(q.completed ? HC.onLime : HC.textSecondary).frame(width: 28, height: 28).background(q.completed ? HC.lime : HC.surfaceHigh, in: Circle())
                            .scaleEffect(q.completed ? 1 : 0.9).animation(.spring(duration: 0.5, bounce: 0.5), value: q.completed)
                        Text(q.title).font(.hfBody.weight(.bold)).foregroundStyle(q.completed ? HC.textSecondary : HC.text).strikethrough(q.completed).lineLimit(2); Spacer()
                        Text("+\(q.xp) XP").font(.system(size: 12, weight: .heavy)).foregroundStyle(HC.warning)
                    }.padding(.vertical, 10)
                }
            }
        }
    }

    private func habit(_ days: [HabitDay]) -> some View {
        HfCard(padding: 20) {
            HStack(spacing: 6) {
                ForEach(days) { d in
                    VStack(spacing: 5) {
                        Circle().fill(d.today ? HC.lime : .clear).frame(width: 7, height: 7)
                        Capsule().fill(d.active ? HC.lime : HC.surfaceHigh).frame(height: d.today ? 76 : 68)
                            .overlay(alignment: .bottom) { if d.active { Capsule().fill(HC.textSecondary.opacity(0.35)).frame(height: 22).padding(4) } }
                        Text(d.date.formatted(.dateTime.weekday(.abbreviated).locale(AppLang.shared.locale)).uppercased().prefix(3)).font(.system(size: 11, weight: .bold)).foregroundStyle(d.today ? HC.text : HC.textSecondary).padding(.top, 3)
                    }.frame(maxWidth: .infinity)
                }
            }
        }
    }

    private func weekly(_ c: WeeklyChallenge) -> some View {
        HfCard(padding: 20) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(c.title).font(.system(size: 20, weight: .heavy)).foregroundStyle(HC.text)
                    Text(String(format: "%.1f / %d km", c.progress, Int(c.target))).font(.hfBody.weight(.bold)).foregroundStyle(HC.textSecondary)
                }
                Spacer()
                ZStack { HfRing(progress: c.progress / max(c.target, 1), color: HC.warning, lineWidth: 7).frame(width: 70, height: 70)
                    VStack(spacing: 0) { Text(c.completed ? "🏁" : "🚶").font(.system(size: 16)); Text(String(format: "%.1f", c.progress)).font(.system(size: 11, weight: .black)).foregroundStyle(HC.warning) } }
            }
        }
    }

    private func badge(_ a: Achievement) -> some View {
        let unlocked = a.unlockedAt != nil
        let color: Color = a.rarity == .common ? HC.lime : a.rarity == .rare ? HC.water : HC.sleep
        return Button { selected = a } label: {
            VStack(spacing: 6) {
                Image(systemName: unlocked ? "trophy.fill" : "lock.fill").font(.system(size: 22)).foregroundStyle(unlocked ? color : HC.textSecondary).frame(width: 52, height: 52)
                    .background(unlocked ? color.opacity(0.18) : HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay { if unlocked { RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(color.opacity(0.6), lineWidth: 1.5) } }
                Text(a.title).font(.system(size: 11, weight: .bold)).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center).lineLimit(2)
            }.opacity(unlocked ? 1 : 0.5)
        }.buttonStyle(.plain)
    }

    private func league(_ name: String, _ s: GamificationSnapshot) -> some View {
        HfCard(padding: 20) {
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    Text(String(name.prefix(1)).uppercased()).font(.system(size: 16, weight: .heavy)).foregroundStyle(HC.onLime).frame(width: 36, height: 36).background(HC.lime, in: Circle())
                    Text(name + (LangStore.english ? " (you)" : " (sen)")).font(.hfBody.weight(.heavy)).foregroundStyle(HC.text).lineLimit(1); Spacer()
                    Text(tr("Bu hafta \(s.weeklyXp) XP", "\(s.weeklyXp) XP this week")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                }.padding(12).background(HC.lime.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                ForEach(Array(leagues.enumerated()), id: \.offset) { i, l in
                    let current = leagueFor(s.totalXp) == l.1, reached = s.totalXp >= l.0
                    if i > 0 { HfDivider() }
                    HStack(spacing: 12) {
                        Image(systemName: reached ? "trophy.fill" : "lock.fill").foregroundStyle(current ? HC.warning : reached ? HC.lime : HC.textSecondary).frame(width: 20)
                        Text(leagueLabel(l.1)).font(.hfBody.weight(current ? .heavy : .semibold)).foregroundStyle(current ? HC.warning : HC.text); Spacer()
                        Text(current ? tr("Buradasın", "You are here") : "\(l.0) XP").font(.hfBody.weight(.bold)).foregroundStyle(current ? HC.warning : HC.text)
                    }.padding(.vertical, 6)
                }
            }
        }
    }
}
