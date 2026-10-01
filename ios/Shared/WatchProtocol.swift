import Foundation

// iPhone ve Apple Watch'ın paylaştığı mesaj tipleri. İki hedefe de derlenir; böylece
// gönderen ve alan taraf aynı şemayı kullanır ve doğrulama tek yerde yaşar.

struct WatchExercise: Codable, Hashable { var id = "", name = ""; var sets = 3, reps = 10, rest = 90; var kg: Double? }
struct WatchLeader: Codable, Hashable { var name = ""; var xp = 0; var me = false }
struct WatchSocial: Codable, Equatable { var rank = 0, total = 0, xp = 0; var leaders: [WatchLeader] = []; var challenge = "" }

/// Telefondan saate giden günlük özet.
struct WatchSnapshot: Codable, Equatable {
    var steps = 0, stepGoal = 10_000, waterMl = 0, waterGoal = 2_500
    var calories = 0, streakDays = 0, proteinG = 0, proteinGoal = 0
    var workoutName = "Antrenman"
    var lang = "tr"
    var exercises: [WatchExercise] = []
    var social = WatchSocial()
    var isEnglish: Bool { lang != "tr" }

    init() {}
    // Eksik alanlar varsayılanla okunur; telefon ve saat farklı sürümde olsa da çözümleme kırılmaz.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        steps = try c.decodeIfPresent(Int.self, forKey: .steps) ?? 0
        stepGoal = try c.decodeIfPresent(Int.self, forKey: .stepGoal) ?? 10_000
        waterMl = try c.decodeIfPresent(Int.self, forKey: .waterMl) ?? 0
        waterGoal = try c.decodeIfPresent(Int.self, forKey: .waterGoal) ?? 2_500
        calories = try c.decodeIfPresent(Int.self, forKey: .calories) ?? 0
        streakDays = try c.decodeIfPresent(Int.self, forKey: .streakDays) ?? 0
        proteinG = try c.decodeIfPresent(Int.self, forKey: .proteinG) ?? 0
        proteinGoal = try c.decodeIfPresent(Int.self, forKey: .proteinGoal) ?? 0
        workoutName = try c.decodeIfPresent(String.self, forKey: .workoutName) ?? "Antrenman"
        lang = try c.decodeIfPresent(String.self, forKey: .lang) ?? "tr"
        exercises = try c.decodeIfPresent([WatchExercise].self, forKey: .exercises) ?? []
        social = try c.decodeIfPresent(WatchSocial.self, forKey: .social) ?? WatchSocial()
    }
}

struct WatchSetLog: Codable, Hashable { var exId = "", exName = ""; var order = 0, setNo = 1; var kg = 0.0; var reps = 0 }

/// Saatte biten antrenman; telefona `transferUserInfo` ile gider.
struct WatchWorkoutPayload: Codable, Equatable {
    static let allowedKinds: Set<String> = ["running", "walking", "hiking", "cycling", "strength"]
    var id = "", kind = ""
    var durationSec = 0
    var distanceM = 0.0
    var calories = 0
    var start = 0.0
    var sets: [WatchSetLog] = []

    var minutes: Int { durationSec / 60 }

    /// 1 dakikadan kısa, tanınmayan türde veya saçma uzunlukta kayıtlar reddedilir.
    var isValid: Bool { !id.isEmpty && Self.allowedKinds.contains(kind) && durationSec >= 60 && durationSec <= 86_400 }

    /// Planlı egzersiz kimliğiyle eşleşen ve geçerli tekrar sayısı olan setler.
    func validSets() -> [WatchSetLog] { sets.filter { $0.reps >= 1 && $0.reps <= 200 && $0.kg >= 0 && $0.kg <= 1000 && $0.exId != "free" } }
}

/// Dakika:saniye (ya da saat:dakika:saniye) biçimi.
func clock(_ seconds: Int) -> String {
    let s = max(0, seconds)
    return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
}
