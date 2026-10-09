import Foundation

struct FriendUser: Codable, Identifiable, Hashable { let id: String; let username, displayName, avatarPath: String? }
struct FriendRequest: Codable, Identifiable { let id, status, createdAt: String; let isIncoming: Bool; let user: FriendUser }
struct FriendsSummary: Codable { var friends: [FriendRequest] = []; var incomingRequests: [FriendRequest] = []; var outgoingRequests: [FriendRequest] = [] }
struct LeaderboardEntry: Codable, Identifiable { var id: String { user.id }; let rank, weeklyXp: Int; let isCurrentUser: Bool; let user: FriendUser }
struct FeedItem: Codable, Identifiable { let id, source, occurredAt: String; let amount: Int; let user: FriendUser }
struct Challenge: Codable, Identifiable { let id, title, metric, startsAt, endsAt, creatorId, myStatus: String; let targetValue: Double; let isCreator: Bool; let participantCount: Int }
struct ChallengeProgressEntry: Codable, Identifiable { var id: String { user.id }; let rank: Int; let progressValue: Double; let isCurrentUser: Bool; let user: FriendUser }
