import SwiftUI

private func displayName(_ user: FriendUser) -> String { (user.displayName?.isEmpty == false ? user.displayName! : nil) ?? "@\(user.username ?? "")" }
private func metricLabel(_ metric: String) -> String { switch metric { case "xp": "XP"; case "workouts": "Antrenman"; default: "Mesafe (km)" } }
private func metricIcon(_ metric: String) -> String { switch metric { case "xp": "bolt.fill"; case "workouts": "dumbbell.fill"; default: "figure.run" } }
private func progressText(_ metric: String, _ value: Double) -> String { switch metric { case "workouts": "\(Int(value))"; case "distance_km": String(format: "%.1f km", value); default: "\(Int(value)) XP" } }
private func feedIcon(_ source: String) -> String { switch source { case "WORKOUT_COMPLETED": "dumbbell.fill"; case "ROUTE_DISTANCE": "figure.run"; case "ACHIEVEMENT_UNLOCKED": "trophy.fill"; default: "bolt.fill" } }
private func feedText(_ source: String) -> String { switch source { case "WORKOUT_COMPLETED": "antrenmanı tamamladı"; case "ROUTE_DISTANCE": "bir rotayı tamamladı"; case "ACHIEVEMENT_UNLOCKED": "bir başarım kazandı"; case "WEEKLY_CHALLENGE_COMPLETED": "bir meydan okumayı tamamladı"; default: "hedefini tuttu" } }

struct FriendsView: View {
    @Environment(AppStore.self) private var store
    @State private var tab = 0
    @State private var query = ""

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $tab) { Text("Arkadaşlar").tag(0); Text("Akış").tag(1); Text("Sıralama").tag(2) }
                .pickerStyle(.segmented).padding()
            switch tab {
            case 0: friendsTab
            case 1: feedTab
            default: leaderboardTab
            }
        }
        .navigationTitle("Arkadaşlar")
        .toolbar { NavigationLink("Meydan okumalar") { ChallengesView() } }
        .task { await store.loadFriendsSummary(); await store.loadWeeklyLeaderboard(); await store.loadFriendFeed(); await store.loadDiscoverable() }
    }

    private var friendsTab: some View {
        List {
            Section { TextField("Kullanıcı adıyla ara…", text: $query).onChange(of: query) { _, value in Task { await store.searchUsers(value) } } }
            if let discoverable = store.discoverable {
                Section {
                    Toggle("Aramada görün", isOn: Binding(get: { discoverable }, set: { value in Task { await store.setDiscoverable(value) } }))
                } footer: {
                    Text("Kapalıyken kullanıcı adı aramasında çıkmazsın. Kullanıcı adını tam olarak bilen biri yine de sana arkadaşlık isteği gönderebilir; mevcut arkadaşların etkilenmez.")
                }
            }
            if !store.userSearchResults.isEmpty {
                Section("Sonuçlar") {
                    ForEach(store.userSearchResults) { user in
                        HStack {
                            Text(displayName(user))
                            Spacer()
                            Button("Ekle") { Task { if let username = user.username { await store.sendFriendRequest(username: username); query = "" } } }
                        }
                    }
                }
            }
            if !store.friendsSummary.incomingRequests.isEmpty {
                Section("Gelen istekler") {
                    ForEach(store.friendsSummary.incomingRequests) { request in
                        HStack {
                            Text(displayName(request.user))
                            Spacer()
                            Button("Kabul et") { Task { await store.respondToFriendRequest(id: request.id, accept: true) } }.tint(Color.hedefitGreen)
                            Button("Reddet") { Task { await store.respondToFriendRequest(id: request.id, accept: false) } }.tint(.red)
                        }
                    }
                }
            }
            if !store.friendsSummary.outgoingRequests.isEmpty {
                Section("Gönderilen istekler") {
                    ForEach(store.friendsSummary.outgoingRequests) { request in
                        HStack { Text(displayName(request.user)); Spacer(); Text("Bekliyor").font(.caption).foregroundStyle(.secondary) }
                    }
                }
            }
            Section("Arkadaşların") {
                if store.friendsSummary.friends.isEmpty { ContentUnavailableView("Henüz arkadaşın yok", systemImage: "person.2", description: Text("Arkadaşlık isteği göndermek için yukarıdan kullanıcı adı ara.")) }
                ForEach(store.friendsSummary.friends) { friend in
                    Text(displayName(friend.user)).swipeActions { Button("Çıkar", role: .destructive) { Task { await store.removeFriend(id: friend.id) } } }
                }
            }
        }
    }

    private var feedTab: some View {
        List {
            if store.feed.isEmpty { ContentUnavailableView("Henüz aktivite yok", systemImage: "sparkles", description: Text("Arkadaşlarının antrenman ve başarımları burada görünecek.")) }
            ForEach(store.feed) { item in
                HStack {
                    Image(systemName: feedIcon(item.source)).foregroundStyle(Color.hedefitGreen).frame(width: 28)
                    VStack(alignment: .leading, spacing: 3) { Text("\(displayName(item.user)) \(feedText(item.source))"); Text(item.occurredAt).font(.caption).foregroundStyle(.secondary) }
                    Spacer()
                    Text("+\(item.amount) XP").font(.caption.bold()).foregroundStyle(Color.hedefitGreen)
                }
            }
        }
    }

    private var leaderboardTab: some View {
        List {
            if store.leaderboard.isEmpty { ContentUnavailableView("Henüz sıralama yok", systemImage: "trophy", description: Text("Bu haftaki XP sıralamasını görmek için arkadaş ekle.")) }
            ForEach(store.leaderboard) { entry in
                HStack {
                    Text("\(entry.rank)").font(.headline).frame(width: 28)
                    Text(displayName(entry.user)).fontWeight(entry.isCurrentUser ? .bold : .regular)
                    Spacer()
                    Text("\(entry.weeklyXp) XP").foregroundStyle(Color.hedefitGreen)
                }
            }
        }
    }
}

struct ChallengesView: View {
    @Environment(AppStore.self) private var store
    @State private var showCreate = false
    @State private var openChallenge: Challenge?

    var body: some View {
        List {
            let invites = store.challenges.filter { $0.myStatus == "invited" }
            let active = store.challenges.filter { $0.myStatus == "joined" }
            if !invites.isEmpty {
                Section("Davetler") {
                    ForEach(invites) { challenge in
                        HStack {
                            VStack(alignment: .leading) { Text(challenge.title).fontWeight(.semibold); Text(metricLabel(challenge.metric)).font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                            Button("Kabul et") { Task { await store.respondToChallengeInvite(id: challenge.id, accept: true) } }.tint(Color.hedefitGreen)
                            Button("Reddet") { Task { await store.respondToChallengeInvite(id: challenge.id, accept: false) } }.tint(.red)
                        }
                    }
                }
            }
            Section("Aktif") {
                if active.isEmpty { ContentUnavailableView("Henüz meydan okuma yok", systemImage: "trophy", description: Text("Bu hafta arkadaşlarınla yarışmak için bir tane oluştur.")) }
                ForEach(active) { challenge in
                    Button { openChallenge = challenge } label: {
                        HStack {
                            Image(systemName: metricIcon(challenge.metric)).foregroundStyle(Color.hedefitGreen)
                            VStack(alignment: .leading) { Text(challenge.title).fontWeight(.semibold); Text("\(metricLabel(challenge.metric)) • \(challenge.participantCount) katılımcı").font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                        }
                    }.buttonStyle(.plain)
                        .swipeActions { Button(challenge.isCreator ? "İptal et" : "Ayrıl", role: .destructive) { Task { await store.leaveOrCancelChallenge(id: challenge.id) } } }
                }
            }
        }
        .navigationTitle("Meydan okumalar")
        .toolbar { Button { showCreate = true } label: { Image(systemName: "plus") } }
        .task { await store.loadChallenges() }
        .sheet(isPresented: $showCreate) { NavigationStack { CreateChallengeView() } }
        .sheet(item: $openChallenge) { challenge in NavigationStack { ChallengeProgressView(challenge: challenge) } }
    }
}

struct CreateChallengeView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var metric = "xp"
    @State private var target = ""
    @State private var days = 7
    @State private var selectedFriends: Set<String> = []
    private var targetValue: Double? { Double(target.replacingOccurrences(of: ",", with: ".")) }

    var body: some View {
        Form {
            Section { TextField("Başlık (ör. birlikte 50km)", text: $title) }
            Section("Metrik") { Picker("Metrik", selection: $metric) { Text("XP").tag("xp"); Text("Antrenman").tag("workouts"); Text("Mesafe (km)").tag("distance_km") }.pickerStyle(.segmented) }
            Section { TextField("Hedef", text: $target).keyboardType(.decimalPad); Stepper("Süre: \(days) gün", value: $days, in: 1...30) }
            if !store.friendsSummary.friends.isEmpty {
                Section("Arkadaş davet et") {
                    ForEach(store.friendsSummary.friends) { friend in
                        Toggle(displayName(friend.user), isOn: Binding(get: { selectedFriends.contains(friend.user.id) }, set: { on in if on { selectedFriends.insert(friend.user.id) } else { selectedFriends.remove(friend.user.id) } }))
                    }
                }
            }
        }
        .navigationTitle("Yeni meydan okuma")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                Button("Oluştur") { let value = targetValue ?? 0; Task { await store.createChallenge(title: title, metric: metric, targetValue: value, days: days, friendIds: Array(selectedFriends)); dismiss() } }
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty || targetValue == nil)
            }
        }
    }
}

struct ChallengeProgressView: View {
    let challenge: Challenge
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            if store.challengeProgress.isEmpty { ContentUnavailableView("Henüz veri yok", systemImage: "chart.bar") }
            ForEach(store.challengeProgress) { entry in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("\(entry.rank)").font(.headline).frame(width: 24)
                        Text(displayName(entry.user)).fontWeight(entry.isCurrentUser ? .bold : .regular)
                        Spacer()
                        Text(progressText(challenge.metric, entry.progressValue)).foregroundStyle(Color.hedefitGreen)
                    }
                    ProgressView(value: entry.progressValue, total: max(challenge.targetValue, max(entry.progressValue, 1))).tint(Color.hedefitGreen)
                }
            }
        }
        .navigationTitle(challenge.title)
        .toolbar { Button("Kapat") { dismiss() } }
        .task { await store.loadChallengeProgress(id: challenge.id) }
    }
}
