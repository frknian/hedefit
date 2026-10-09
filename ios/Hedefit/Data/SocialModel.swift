import SwiftUI
import Observation

@MainActor @Observable
final class SocialModel {
    var friendsBusy = false
    var summary: FriendsSummary?
    var discoverable: Bool?
    var searchBusy = false
    var searchResults: [FriendUser] = []
    var leaderboardBusy = false
    var leaderboard: [LeaderboardEntry] = []
    var feedBusy = false
    var feed: [FeedItem] = []
    var challengesBusy = false
    var challenges: [SocialChallenge] = []
    var creating = false
    var progressBusy = false
    var progress: [ChallengeProgressEntry] = []
    var activeChallengeId: String?

    private var app: AppModel { AppModel.shared }

    func loadSummary() async {
        friendsBusy = true; defer { friendsBusy = false }
        do { summary = try await app.repo.loadFriendsSummary() } catch { app.fail(error) }
    }

    func loadDiscoverable() async { if let v = try? await app.repo.loadDiscoverable() { discoverable = v } }

    func setDiscoverable(_ value: Bool) async {
        let previous = discoverable; discoverable = value
        do { discoverable = try await app.repo.setDiscoverable(value) } catch { discoverable = previous; app.fail(error) }
    }

    func search(_ query: String) async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { searchResults = []; return }
        searchBusy = true; defer { searchBusy = false }
        do { searchResults = try await app.repo.searchUsers(q) } catch { app.fail(error) }
    }

    func sendRequest(username: String) async {
        do { try await app.repo.sendFriendRequest(username: username); app.notify(tr("İstek gönderildi.", "Request sent.")); await loadSummary() } catch { app.fail(error) }
    }

    func respond(id: String, accept: Bool) async {
        do { try await app.repo.respondToFriendRequest(id: id, accept: accept); await loadSummary(); await loadLeaderboard() } catch { app.fail(error) }
    }

    func remove(id: String) async {
        do { try await app.repo.removeFriend(id: id); await loadSummary(); await loadLeaderboard() } catch { app.fail(error) }
    }

    func loadLeaderboard() async {
        leaderboardBusy = true; defer { leaderboardBusy = false }
        do { leaderboard = try await app.repo.loadWeeklyLeaderboard() } catch { app.fail(error) }
    }

    func loadFeed() async {
        feedBusy = true; defer { feedBusy = false }
        do { feed = try await app.repo.loadFriendActivityFeed() } catch { app.fail(error) }
    }

    func loadChallenges() async {
        challengesBusy = true; defer { challengesBusy = false }
        do { challenges = try await app.repo.loadChallenges() } catch { app.fail(error) }
    }

    func create(title: String, metric: String, target: Double, days: Int, friendIds: [String]) async -> Bool {
        creating = true; defer { creating = false }
        do { _ = try await app.repo.createChallenge(title: title, metric: metric, targetValue: target, days: days, friendIds: friendIds); await loadChallenges(); return true } catch { app.fail(error); return false }
    }

    func respondToInvite(id: String, accept: Bool) async {
        do { try await app.repo.respondToChallengeInvite(id: id, accept: accept); await loadChallenges() } catch { app.fail(error) }
    }

    func leaveOrCancel(id: String) async {
        do { try await app.repo.leaveOrCancelChallenge(id: id); await loadChallenges() } catch { app.fail(error) }
    }

    func openProgress(id: String) async {
        activeChallengeId = id; progressBusy = true; progress = []; defer { progressBusy = false }
        do { progress = try await app.repo.loadChallengeProgress(id: id) } catch { app.fail(error) }
    }
}
