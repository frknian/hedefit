package com.hedefit.app.data.model

// avatarPath ham depolama yolu; imzalı URL üretimi özel/kendi profil akışına
// özgü (bkz. HedefitRepository.signedAvatarUrl) ve v1'de başka kullanıcıların
// depolama yollarını imzalamıyoruz — liste/sıralama ekranlarında baş harf
// rozeti gösteriliyor.
data class FriendUserData(
    val id: String,
    val username: String?,
    val displayName: String?,
    val avatarPath: String?,
)

data class FriendRequestData(
    val id: String,
    val status: String,
    val createdAt: String,
    val isIncoming: Boolean,
    val user: FriendUserData,
)

data class FriendsSummaryData(
    val friends: List<FriendRequestData>,
    val incomingRequests: List<FriendRequestData>,
    val outgoingRequests: List<FriendRequestData>,
)

data class LeaderboardEntryData(
    val rank: Int,
    val weeklyXp: Long,
    val isCurrentUser: Boolean,
    val user: FriendUserData,
)

data class FeedItemData(
    val id: String,
    val source: String,
    val amount: Int,
    val occurredAt: String,
    val user: FriendUserData,
)

/** metric: "xp" | "workouts" | "distance_km" | "steps" | "challenge_days". myStatus: "invited" | "joined" | "declined" | "expired". */
data class ChallengeData(
    val id: String,
    val title: String,
    val metric: String,
    val targetValue: Double,
    val startsAt: String,
    val endsAt: String,
    val creatorId: String,
    val isCreator: Boolean,
    val myStatus: String,
    val participantCount: Int,
    /** compete (Rekabet Et) | together (Birlikte Tamamla). */
    val mode: String = "compete",
    /** Katalog challenge'ına bağlıysa anahtarı (ör. steps_7); eski serbest hedefli meydan okumalarda null. */
    val templateKey: String? = null,
)

data class ChallengeProgressEntryData(
    val rank: Int,
    val progressValue: Double,
    val isCurrentUser: Boolean,
    val user: FriendUserData,
)
