import SwiftUI

private func displayName(_ u: FriendUser) -> String { u.displayName ?? u.username.map { "@\($0)" } ?? tr("Sporcu", "Athlete") }

struct CommunitySection: View {
    @Environment(AppModel.self) private var app
    @State private var tab = 0
    @State private var query = ""
    @State private var friendSheet: FriendUser?
    @State private var progressChallenge: SocialChallenge?

    var body: some View {
        let s = app.social
        if app.isGuest {
            VStack(spacing: 14) {
                HfEmptyState(icon: "lock.fill", title: tr("Topluluk için hesabını kaydet", "Save your account to use Community"), message: tr("Arkadaş eklemek ve birlikte challenge yapmak için kalıcı bir hesap gerekir. İlerlemen korunur.", "Adding friends and doing challenges together needs a saved account. Your progress is kept."))
                HfButton(title: tr("Hesabı kaydet", "Save account")) { app.showSaveAccount = true }
            }
        } else {
            VStack(alignment: .leading, spacing: 12) {
                HfSegmented(options: [tr("Arkadaşlar", "Friends"), tr("Challenge'lar", "Challenges"), tr("Sıralama", "Ranking")], selection: $tab)
                switch tab {
                case 0: friends(s)
                case 1: challenges(s)
                default: ranking(s)
                }
            }
            .sheet(item: $friendSheet) { FriendProfileSheet(user: $0) }
            .sheet(item: $progressChallenge) { ChallengeProgressSheet(challenge: $0) }
        }
    }

    // MARK: Arkadaşlar

    @ViewBuilder private func friends(_ s: SocialModel) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(HC.textSecondary)
            TextField(tr("Arkadaş ekle: kullanıcı adıyla ara…", "Add a friend: search by username…"), text: $query).textInputAutocapitalization(.never).autocorrectionDisabled().foregroundStyle(HC.text)
                .onChange(of: query) { _, v in query = String(v.prefix(20)); Task { if query.trimmingCharacters(in: .whitespaces).count >= 2 { await s.search(query) } else { s.searchResults = [] } } }
            if s.searchBusy { ProgressView().tint(HC.lime) }
        }.padding(.horizontal, 14).frame(minHeight: 50).background(HC.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        if !s.searchResults.isEmpty {
            HfSectionHeader(title: tr("Sonuçlar", "Results"))
            ForEach(s.searchResults) { user in
                HfNavRow(icon: "person.fill", tint: HC.lime, title: displayName(user), subtitle: user.username.map { "@\($0)" }, chevron: false, action: nil) {
                    HfCircleButton(system: "person.badge.plus", label: tr("Ekle", "Add"), tint: HC.lime) { if let u = user.username { Task { await s.sendRequest(username: u) }; query = ""; s.searchResults = [] } }
                }
            }
        }
        let incoming = s.summary?.incoming ?? []
        if !incoming.isEmpty {
            HfSectionHeader(title: tr("Arkadaş istekleri", "Friend requests"))
            ForEach(incoming) { r in
                HfNavRow(icon: "person.fill", tint: HC.lime, title: displayName(r.user), chevron: false, action: nil) {
                    HfCircleButton(system: "checkmark", label: tr("Kabul et", "Accept"), tint: HC.lime) { Task { await s.respond(id: r.id, accept: true) } }
                    HfCircleButton(system: "xmark", label: tr("Reddet", "Decline"), tint: HC.coral) { Task { await s.respond(id: r.id, accept: false) } }
                }
            }
        }
        let list = s.summary?.friends ?? []
        HfSectionHeader(title: tr("Arkadaşlarım", "My friends"))
        if s.summary == nil && s.friendsBusy { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding(24) }
        else if list.isEmpty { HfEmptyState(icon: "person.2.fill", title: tr("Henüz arkadaşın yok", "No friends yet"), message: tr("Yukarıdan kullanıcı adıyla ara ve istek gönder. Birlikte challenge yapmak motivasyonu ikiye katlar.", "Search a username above and send a request. Doing challenges together doubles the motivation.")) }
        else { ForEach(list) { f in HfNavRow(icon: "person.fill", tint: HC.lime, title: displayName(f.user)) { friendSheet = f.user; Task { await app.challenge.loadFriendProfile(f.user.id) } } } }
        let out = s.summary?.outgoing ?? []
        if !out.isEmpty {
            HfSectionHeader(title: tr("Gönderilen istekler", "Sent requests"))
            ForEach(out) { r in HfNavRow(icon: "person.fill", tint: HC.textSecondary, title: displayName(r.user), chevron: false, action: nil) { HfPill(text: tr("Bekliyor", "Pending"), color: HC.textSecondary) } }
        }
        HfSectionHeader(title: tr("Gizlilik", "Privacy"))
        if let discoverable = s.discoverable {
            HfNavRow(icon: "magnifyingglass", tint: HC.lime, title: tr("Aramada görün", "Appear in search"), chevron: false, action: { Task { await s.setDiscoverable(!discoverable) } }) {
                Toggle("", isOn: Binding(get: { discoverable }, set: { v in Task { await s.setDiscoverable(v) } })).labelsHidden().tint(HC.lime)
            }
        }
        if let share = app.challenge.shareProgress {
            HfNavRow(icon: "eye.fill", tint: HC.lime, title: tr("İlerlememi arkadaşlarım görsün", "Let friends see my progress"), chevron: false, action: { Task { await app.challenge.setShareProgress(!share) } }) {
                Toggle("", isOn: Binding(get: { share }, set: { v in Task { await app.challenge.setShareProgress(v) } })).labelsHidden().tint(HC.lime)
            }
            Text(tr("Arkadaşların yalnızca level, seri, challenge ve rozetlerini görür. Kilo, kalori, uyku, beslenme, check-in ve döngü bilgilerin hiçbir zaman paylaşılmaz.", "Friends only see your level, streak, challenges and badges. Your weight, calories, sleep, nutrition, check-ins and cycle data are never shared.")).font(.hfSmall).foregroundStyle(HC.muted)
        }
    }

    // MARK: Challenge'lar

    @ViewBuilder private func challenges(_ s: SocialModel) -> some View {
        let invites = s.challenges.filter { $0.myStatus == "invited" || $0.myStatus == "expired" }
        let active = s.challenges.filter { $0.myStatus == "joined" }
        if !invites.isEmpty {
            HfSectionHeader(title: tr("Meydan okumalar", "Invites"))
            ForEach(invites) { c in
                HfCard(padding: 14) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(c.title).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                        Text(metricText(c)).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        if c.myStatus == "invited" {
                            HStack { HfButton(title: tr("Kabul et", "Accept")) { Task { await s.respondToInvite(id: c.id, accept: true); await app.challenge.loadHub() } }; HfButton(title: tr("Reddet", "Decline"), secondary: true) { Task { await s.respondToInvite(id: c.id, accept: false) } } }
                        } else { HfPill(text: tr("Süresi doldu", "Expired"), color: HC.textSecondary) }
                    }
                }
            }
        }
        HfSectionHeader(title: tr("Aktif arkadaş challenge'ları", "Active friend challenges"))
        if active.isEmpty { HfEmptyState(icon: "person.3.fill", title: tr("Aktif arkadaş challenge'ı yok", "No active friend challenge"), message: tr("Bir arkadaşının profilinden Meydan Oku'ya dokun; birlikte tamamlayın ya da rekabet edin.", "Tap Challenge on a friend's profile — complete it together or compete.")) }
        ForEach(active) { c in
            HfCard(padding: 14, onTap: { progressChallenge = c; Task { await s.openProgress(id: c.id) } }) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) { Text(c.title).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text); Text(tr("\(c.participantCount) katılımcı • ", "\(c.participantCount) participants • ") + metricText(c)).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                    Spacer()
                    Menu { Button(c.isCreator ? tr("İptal et", "Cancel challenge") : tr("Ayrıl", "Leave"), role: .destructive) { Task { await s.leaveOrCancel(id: c.id) } } } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).foregroundStyle(HC.muted).frame(width: 30, height: 40) }
                }
            }
        }
    }

    private func metricText(_ c: SocialChallenge) -> String {
        switch c.metric {
        case "xp": return "\(Int(c.targetValue)) XP"
        case "workouts": return tr("\(Int(c.targetValue)) antrenman", "\(Int(c.targetValue)) workouts")
        case "distance_km": return "\(Int(c.targetValue)) km"
        case "steps": return tr("\(Int(c.targetValue)) adım", "\(Int(c.targetValue)) steps")
        case "challenge_days": return tr("\(Int(c.targetValue)) gün", "\(Int(c.targetValue)) days")
        default: return "\(Int(c.targetValue))"
        }
    }

    // MARK: Sıralama

    @ViewBuilder private func ranking(_ s: SocialModel) -> some View {
        HfSectionHeader(title: tr("Bu haftanın XP sıralaması", "This week's XP ranking"))
        if s.leaderboard.isEmpty { HfEmptyState(icon: "trophy.fill", title: tr("Henüz sıralama yok", "No ranking yet"), message: tr("Arkadaş ekleyince haftalık XP sıralaması burada görünür.", "Add friends to see the weekly XP ranking here.")) }
        ForEach(s.leaderboard) { e in RankRow(rank: e.rank, user: e.user, isMe: e.isCurrentUser, value: "\(e.weeklyXp) XP") }
        Text(tr("Challenge sıralamaları için Challenge'lar sekmesinde bir challenge'a dokun.", "For challenge rankings, tap a challenge in the Challenges tab.")).font(.hfSmall).foregroundStyle(HC.muted)
    }
}

struct RankRow: View {
    let rank: Int, user: FriendUser, isMe: Bool, value: String
    var body: some View {
        HStack(spacing: 12) {
            Text(rank <= 3 ? ["🥇", "🥈", "🥉"][rank - 1] : "\(rank)").font(.system(size: rank <= 3 ? 22 : 15, weight: .heavy)).foregroundStyle(HC.textSecondary).frame(width: 32)
            AvatarView(url: nil, name: displayName(user), size: 40)
            Text(isMe ? tr("Sen", "You") : displayName(user)).font(.hfBody.weight(isMe ? .heavy : .semibold)).foregroundStyle(HC.text).lineLimit(1)
            Spacer()
            Text(value).font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime)
        }.padding(12).background(isMe ? HC.lime.opacity(0.12) : HC.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct ChallengeProgressSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let challenge: SocialChallenge
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    if app.social.progressBusy { ProgressView().tint(HC.lime).padding(30) }
                    ForEach(app.social.progress) { e in RankRow(rank: e.rank, user: e.user, isMe: e.isCurrentUser, value: String(format: "%g", e.progressValue)) }
                }.padding(20)
            }.background(HC.bg).navigationTitle(challenge.title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.medium, .large])
    }
}

// MARK: - Arkadaş profili

struct FriendProfileSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let user: FriendUser
    @State private var confirmRemove = false
    @State private var challengeSheet = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 14) {
                        AvatarView(url: nil, name: displayName(user), size: 52)
                        VStack(alignment: .leading) { Text(displayName(user)).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text); if let u = user.username { Text("@\(u)").font(.hfSmall).foregroundStyle(HC.muted) } }
                    }
                    let c = app.challenge
                    if c.friendProfileBusy || c.friendProfile == nil { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding(24) }
                    else if let p = c.friendProfile {
                        if !p.shared { Text(tr("Bu arkadaşın ilerlemesini paylaşmıyor.", "This friend keeps their progress private.")).foregroundStyle(HC.textSecondary) }
                        else {
                            let level = GamificationEngine.levelFor(p.totalXp)
                            HfCard { VStack(alignment: .leading, spacing: 8) { HStack { Text(tr("Seviye \(level.level)", "Level \(level.level)")).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text); Spacer(); Text("\(p.totalXp) XP").font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.lime) }; HfProgressBar(progress: level.progress) } }
                            HStack(spacing: 10) { HfStatTile(label: tr("En iyi seri", "Best streak"), value: "🔥 \(p.bestStreak)"); HfStatTile(label: tr("Challenge", "Challenges"), value: "\(p.completedChallenges)", sub: tr("tamamlandı", "completed")) }
                            if !p.activeChallenges.isEmpty {
                                HfSectionHeader(title: tr("Aktif challenge'lar", "Active challenges"))
                                ForEach(Array(p.activeChallenges.enumerated()), id: \.offset) { _, a in
                                    HfCard(padding: 14) { VStack(spacing: 8) { HStack { Text(a.title.text).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Spacer(); Text("\(a.done)/\(a.total)").foregroundStyle(HC.textSecondary) }; HfProgressBar(progress: a.total > 0 ? Double(a.done) / Double(a.total) : 0) } }
                                }
                            }
                            if !p.achievements.isEmpty { HfSectionHeader(title: tr("Rozetler", "Achievements")); HfChipRow { ForEach(p.achievements.prefix(8), id: \.self) { HfTag(text: "🏅 \(achievementTitle($0))") } } }
                        }
                    }
                    HfButton(title: tr("Meydan Oku", "Challenge"), icon: "trophy.fill") { challengeSheet = true }
                    Button { confirmRemove = true } label: { Label(tr("Arkadaşlıktan çıkar", "Remove friend"), systemImage: "person.badge.minus").foregroundStyle(HC.muted) }.frame(maxWidth: .infinity)
                }.padding(20)
            }.background(HC.bg).navigationBarTitleDisplayMode(.inline).toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }
        .sheet(isPresented: $challengeSheet) { FriendChallengeSheet(initialFriendId: user.id) }
        .alert(tr("Arkadaşlıktan çıkarılsın mı?", "Remove this friend?"), isPresented: $confirmRemove) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Çıkar", "Remove"), role: .destructive) { if let id = app.social.summary?.friends.first(where: { $0.user.id == user.id })?.id { Task { await app.social.remove(id: id); dismiss() } } }
        } message: { Text(tr("Ortak challenge'larınız kişisel challenge olarak devam eder; kazanılan XP korunur.", "Your shared challenges continue as personal ones; earned XP is kept.")) }
        .presentationDetents([.large])
    }
}

/// Meydan Oku: challenge seç + mod + arkadaş(lar).
struct FriendChallengeSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    var initialFriendId: String?
    @State private var templateKey: String?
    @State private var mode = 0
    @State private var selected: Set<String> = []
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HfSectionHeader(title: tr("Mod", "Mode"))
                    HfSegmented(options: [tr("Birlikte Tamamla", "Complete Together"), tr("Rekabet Et", "Compete")], selection: $mode)
                    Text(mode == 0 ? tr("Amaç ikinizin de challenge'ı bitirmesi. Bitirince ikiniz de bonus XP kazanırsınız.", "The goal is for both of you to finish. You both earn bonus XP when you do.") : tr("Aynı challenge'da kim daha istikrarlı? Adım challenge'larında toplam adım, diğerlerinde tamamlanan gün sayılır.", "Who's more consistent? Step challenges count total steps; others count completed days.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    HfSectionHeader(title: "Challenge")
                    let eligible = (app.challenge.hub?.templates ?? []).filter { $0.plan.days.count <= 30 }
                    HfChipRow { ForEach(eligible) { t in HfChip(text: t.plan.title.text, selected: templateKey == t.plan.key) { templateKey = t.plan.key } } }
                    HfSectionHeader(title: tr("Arkadaşlar", "Friends"))
                    let friends = app.social.summary?.friends ?? []
                    if friends.isEmpty { Text(tr("Önce Topluluk'tan arkadaş ekle.", "Add a friend in Community first.")).foregroundStyle(HC.muted) }
                    ForEach(friends) { f in
                        let on = selected.contains(f.user.id)
                        HfNavRow(icon: "person.fill", tint: on ? HC.lime : HC.textSecondary, title: displayName(f.user), chevron: false, action: { if on { selected.remove(f.user.id) } else { selected.insert(f.user.id) } }) { if on { Image(systemName: "checkmark").foregroundStyle(HC.lime) } }
                    }
                    HfButton(title: app.challenge.actionBusy ? tr("Gönderiliyor…", "Sending…") : tr("Meydan okumayı gönder", "Send challenge"), icon: "trophy.fill", loading: app.challenge.actionBusy, enabled: templateKey != nil && !selected.isEmpty) {
                        Task { if await app.challenge.createFriendChallenge(templateKey: templateKey!, mode: mode == 0 ? "together" : "compete", friendIds: Array(selected)) { dismiss() } }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Meydan Oku", "Challenge a friend")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }
        .onAppear { if let id = initialFriendId { selected = [id] }; templateKey = templateKey ?? app.challenge.hub?.templates.first { $0.plan.days.count <= 30 }?.plan.key }
        .task { if app.challenge.hub == nil { await app.challenge.loadHub() } }
        .presentationDetents([.large])
    }
}
