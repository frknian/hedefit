import Foundation

extension HedefitRepository {
    // MARK: Arkadaşlık & sıralama

    func searchUsers(_ query: String) async throws -> [FriendUser] {
        guard query.trimmingCharacters(in: .whitespaces).count >= 2 else { return [] }
        return try await api.get("/api/social/users/search?q=\(HTTP.encode(query.trimmingCharacters(in: .whitespaces)))").requireSuccess(trNow("Kullanıcı aranamadı.", "Couldn't search users.")).json["users"].items.map(FriendUser.init(json:))
    }

    func loadDiscoverable() async throws -> Bool { try await api.get("/api/social/settings").requireSuccess(trNow("Ayar yüklenemedi.", "Couldn't load the setting.")).json.bool("discoverable", true) }
    func setDiscoverable(_ value: Bool) async throws -> Bool { try await api.patch("/api/social/settings", ["discoverable": JSON(value)]).requireSuccess(trNow("Ayar kaydedilemedi.", "Couldn't save the setting.")).json.bool("discoverable", value) }
    func loadShareProgress() async throws -> Bool { try await api.get("/api/social/settings").requireSuccess(trNow("Ayar yüklenemedi.", "Couldn't load the setting.")).json.bool("shareProgress", true) }
    func setShareProgress(_ value: Bool) async throws -> Bool { try await api.patch("/api/social/settings", ["shareProgress": JSON(value)]).requireSuccess(trNow("Ayar kaydedilemedi.", "Couldn't save the setting.")).json.bool("shareProgress", value) }

    func loadFriendsSummary() async throws -> FriendsSummary {
        let json = try await api.get("/api/social/friends").requireSuccess(trNow("Arkadaşlar yüklenemedi.", "Couldn't load friends.")).json
        return FriendsSummary(friends: json["friends"].items.map(FriendRequest.init(json:)), incoming: json["incomingRequests"].items.map(FriendRequest.init(json:)), outgoing: json["outgoingRequests"].items.map(FriendRequest.init(json:)))
    }

    func sendFriendRequest(username: String) async throws {
        try await api.post("/api/social/friends", ["username": JSON(username.trimmingCharacters(in: .whitespaces).lowercased())]).requireSuccess(trNow("İstek gönderilemedi.", "Couldn't send the request."))
    }
    func respondToFriendRequest(id: String, accept: Bool) async throws {
        try await api.patch("/api/social/friends/\(id)", ["status": JSON(accept ? "accepted" : "declined")]).requireSuccess(trNow("İstek güncellenemedi.", "Couldn't update the request."))
    }
    func removeFriend(id: String) async throws { try await api.delete("/api/social/friends/\(id)").requireSuccess(trNow("Arkadaşlık sonlandırılamadı.", "Couldn't remove the friend.")) }

    func loadWeeklyLeaderboard() async throws -> [LeaderboardEntry] {
        try await api.get("/api/social/leaderboard").requireSuccess(trNow("Sıralama yüklenemedi.", "Couldn't load the leaderboard.")).json["entries"].items.map(LeaderboardEntry.init(json:))
    }

    func loadFriendActivityFeed(before: String? = nil) async throws -> [FeedItem] {
        let path = before.map { "/api/social/feed?before=\(HTTP.encode($0))" } ?? "/api/social/feed"
        return try await api.get(path).requireSuccess(trNow("Akış yüklenemedi.", "Couldn't load the feed.")).json["items"].items.map(FeedItem.init(json:))
    }

    func friendProfile(userId: String) async throws -> FriendProfile {
        let response = try await api.get("/api/social/profile/\(userId)")
        if !response.isSuccessful { throw await challengeFailure(response) }
        return FriendProfile(json: response.json["profile"])
    }

    // MARK: Ortak meydan okumalar

    func loadChallenges() async throws -> [SocialChallenge] {
        try await api.get("/api/social/challenges").requireSuccess(trNow("Meydan okumalar yüklenemedi.", "Couldn't load challenges.")).json["challenges"].items.map(SocialChallenge.init(json:))
    }

    func createChallenge(title: String, metric: String, targetValue: Double, days: Int, friendIds: [String]) async throws -> String {
        let body: JSON = ["title": JSON(title.trimmingCharacters(in: .whitespaces)), "metric": JSON(metric), "targetValue": JSON(targetValue), "days": JSON(days), "friendIds": .from(friendIds)]
        return try await api.post("/api/social/challenges", body).requireSuccess(trNow("Meydan okuma oluşturulamadı.", "Couldn't create the challenge.")).json.string("id")
    }

    func respondToChallengeInvite(id: String, accept: Bool) async throws {
        try await api.patch("/api/social/challenges/\(id)", ["status": JSON(accept ? "joined" : "declined"), "localDate": JSON(Dates.day())]).requireSuccess(trNow("Davet güncellenemedi ya da süresi doldu.", "Couldn't update the invite, or it has expired."))
    }

    func leaveOrCancelChallenge(id: String) async throws { try await api.delete("/api/social/challenges/\(id)").requireSuccess(trNow("Meydan okuma güncellenemedi.", "Couldn't update the challenge.")) }

    func loadChallengeProgress(id: String) async throws -> [ChallengeProgressEntry] {
        try await api.get("/api/social/challenges/\(id)/progress").requireSuccess(trNow("İlerleme yüklenemedi.", "Couldn't load progress.")).json["entries"].items.map(ChallengeProgressEntry.init(json:))
    }

    func createFriendChallenge(templateKey: String, mode: String, friendIds: [String], locale: String) async throws -> String {
        let body: JSON = ["templateKey": JSON(templateKey), "mode": JSON(mode), "friendIds": .from(friendIds), "locale": JSON(locale), "localDate": JSON(Dates.day())]
        let response = try await api.post("/api/social/challenges", body)
        if !response.isSuccessful { throw await challengeFailure(response) }
        return response.json.string("id")
    }

    // MARK: Keşfet > Challenge (/api/challenges)

    func challengeFailure(_ response: HTTPResult) async -> ApiError {
        let code = response.json.nonEmptyString("error")
        let message = await MainActor.run { challengeErrorText(code) }
        return ApiError(status: response.status, body: response.body, message: message)
    }

    private func challengeProfile(_ profile: Profile?) -> JSON {
        ["goal": JSON(profile.map { $0.goal.components(separatedBy: " | ").first ?? $0.goal } ?? ""), "environment": JSON(profile?.environment ?? ""), "equipment": JSON(profile?.equipment ?? ""), "history": .from(aiSafeHistory(profile?.historyAnswers ?? []))]
    }

    func loadChallengeHub(profile: Profile?, wellnessProminent: Bool) async throws -> ChallengeHub {
        let response = try await api.post("/api/challenges", ["action": "hub", "localDate": JSON(Dates.day()), "profile": challengeProfile(profile), "wellnessProminent": JSON(wellnessProminent)])
        if !response.isSuccessful { throw await challengeFailure(response) }
        return ChallengeHub(json: response.json)
    }

    func joinChallenge(key: String) async throws -> String {
        let response = try await api.post("/api/challenges", ["action": "join", "key": JSON(key), "localDate": JSON(Dates.day())])
        if !response.isSuccessful { throw await challengeFailure(response) }
        return response.json.string("id")
    }

    func coachChallenge(preferences: CoachChallengePreferences, profile: Profile?, recentWorkouts14d: Int, start: Bool) async throws -> JSON {
        let response = try await api.post("/api/challenges", ["action": JSON(start ? "coach_start" : "coach_preview"), "preferences": preferences.json, "profile": challengeProfile(profile), "recentWorkouts14d": JSON(recentWorkouts14d), "localDate": JSON(Dates.day())])
        if !response.isSuccessful { throw await challengeFailure(response) }
        return response.json
    }

    func challengeToday(id: String, profile: Profile?, locale: String) async throws -> ChallengeToday {
        let response = try await api.post("/api/challenges/\(id)", ["action": "today", "localDate": JSON(Dates.day()), "locale": JSON(locale), "profile": challengeProfile(profile)])
        if !response.isSuccessful { throw await challengeFailure(response) }
        return ChallengeToday(json: response.json)
    }

    /// action: complete | recovery. Sunucu görevi mevcut kayıtlardan doğrular; aynı gün ikinci çağrı XP vermez.
    func completeChallengeDay(id: String, action: String, status: String, minutes: Int?, sessionId: String?) async throws -> (ChallengeCompletion, UserChallenge?) {
        var body: [String: JSON] = ["action": JSON(action), "status": JSON(status), "localDate": JSON(Dates.day())]
        if let minutes { body["minutes"] = JSON(minutes) }; if let sessionId { body["sessionId"] = JSON(sessionId) }
        let response = try await api.post("/api/challenges/\(id)", body.json)
        if !response.isSuccessful { throw await challengeFailure(response) }
        let json = response.json
        return (ChallengeCompletion(json: json["result"]), json["challenge"].isObject ? UserChallenge(json: json["challenge"]) : nil)
    }

    func abandonChallenge(id: String) async throws {
        let response = try await api.post("/api/challenges/\(id)", ["action": "abandon"])
        if !response.isSuccessful { throw await challengeFailure(response) }
    }
}
