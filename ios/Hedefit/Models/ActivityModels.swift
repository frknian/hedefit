import Foundation

// MARK: - Manuel aktivite

struct ActivityVariant: Hashable, Sendable { let key, titleTr, titleEn: String; let met: Double }

struct ManualActivityType: Identifiable, Hashable, Sendable {
    let key: String
    let emoji: String
    let titleTr: String
    let titleEn: String
    let fallbackMet: Double
    var variants: [ActivityVariant] = []
    var id: String { key }
    @MainActor var title: String { tr(titleTr, titleEn) }
}

struct ManualActivityInput: Sendable {
    var activityKey: String
    var durationMinutes: Int
    var distanceKm: Double?
    var inclinePercent: Double = 0
    var variantKey: String?
    var notes: String = ""
}

struct ActivityEnergyEstimate: Sendable { let activeCalories: Int; let met: Double; let confidence: String; let method: String }

let manualActivityTypes: [ManualActivityType] = [
    .init(key: "walking", emoji: "🚶", titleTr: "Yürüyüş", titleEn: "Walking", fallbackMet: 3.8),
    .init(key: "running", emoji: "🏃", titleTr: "Koşu", titleEn: "Running", fallbackMet: 8.0),
    .init(key: "cycling", emoji: "🚴", titleTr: "Bisiklet", titleEn: "Cycling", fallbackMet: 6.8),
    .init(key: "swimming", emoji: "🏊", titleTr: "Yüzme", titleEn: "Swimming", fallbackMet: 6.0, variants: [
        .init(key: "freestyle", titleTr: "Serbest stil", titleEn: "Freestyle", met: 5.8), .init(key: "breaststroke", titleTr: "Kurbağalama", titleEn: "Breaststroke", met: 5.3),
        .init(key: "backstroke", titleTr: "Sırtüstü", titleEn: "Backstroke", met: 4.8), .init(key: "butterfly", titleTr: "Kelebek", titleEn: "Butterfly", met: 13.8),
        .init(key: "open_water", titleTr: "Açık su", titleEn: "Open water", met: 6.0)]),
    .init(key: "strength", emoji: "🏋️", titleTr: "Kuvvet", titleEn: "Strength", fallbackMet: 5.0, variants: [
        .init(key: "traditional", titleTr: "Geleneksel set", titleEn: "Traditional sets", met: 5.0), .init(key: "circuit", titleTr: "Devre antrenmanı", titleEn: "Circuit training", met: 8.0)]),
    .init(key: "yoga", emoji: "🧘", titleTr: "Yoga", titleEn: "Yoga", fallbackMet: 2.5, variants: [
        .init(key: "hatha", titleTr: "Hatha / klasik", titleEn: "Hatha / traditional", met: 2.5), .init(key: "power", titleTr: "Power yoga", titleEn: "Power yoga", met: 4.0)]),
    .init(key: "football", emoji: "⚽", titleTr: "Futbol", titleEn: "Football", fallbackMet: 7.0, variants: [
        .init(key: "training", titleTr: "Antrenman", titleEn: "Training", met: 7.0), .init(key: "match", titleTr: "Maç", titleEn: "Match", met: 10.0)]),
    .init(key: "basketball", emoji: "🏀", titleTr: "Basketbol", titleEn: "Basketball", fallbackMet: 7.5, variants: [
        .init(key: "shooting", titleTr: "Şut çalışması", titleEn: "Shooting practice", met: 5.0), .init(key: "training", titleTr: "Antrenman", titleEn: "Training", met: 7.5), .init(key: "match", titleTr: "Maç", titleEn: "Game", met: 8.0)]),
    .init(key: "tennis", emoji: "🎾", titleTr: "Tenis", titleEn: "Tennis", fallbackMet: 7.0, variants: [
        .init(key: "doubles", titleTr: "Çiftler", titleEn: "Doubles", met: 5.0), .init(key: "singles", titleTr: "Tekler", titleEn: "Singles", met: 8.0)]),
    .init(key: "boxing", emoji: "🥊", titleTr: "Boks", titleEn: "Boxing", fallbackMet: 7.8, variants: [
        .init(key: "bag", titleTr: "Kum torbası", titleEn: "Punching bag", met: 5.8), .init(key: "sparring", titleTr: "Sparring", titleEn: "Sparring", met: 7.8), .init(key: "ring", titleTr: "Maç", titleEn: "Bout", met: 12.3)]),
    .init(key: "volleyball", emoji: "🏐", titleTr: "Voleybol", titleEn: "Volleyball", fallbackMet: 4.0, variants: [
        .init(key: "recreational", titleTr: "Rekreasyon", titleEn: "Recreational", met: 3.0), .init(key: "match", titleTr: "Maç", titleEn: "Match", met: 6.0)]),
    .init(key: "pilates", emoji: "🤸", titleTr: "Pilates", titleEn: "Pilates", fallbackMet: 3.0),
    .init(key: "hiking", emoji: "🥾", titleTr: "Doğa Yürüyüşü", titleEn: "Hiking", fallbackMet: 6.0),
    .init(key: "rowing", emoji: "🚣", titleTr: "Kürek", titleEn: "Rowing", fallbackMet: 5.8),
    .init(key: "dancing", emoji: "💃", titleTr: "Dans", titleEn: "Dancing", fallbackMet: 5.5),
    .init(key: "hiit", emoji: "🔥", titleTr: "HIIT", titleEn: "HIIT", fallbackMet: 9.0),
    .init(key: "snowboard", emoji: "🏂", titleTr: "Snowboard", titleEn: "Snowboard", fallbackMet: 5.3),
]

func estimateManualActivityEnergy(_ activity: ManualActivityType, input: ManualActivityInput, weightKg: Double?) -> ActivityEnergyEstimate {
    let minutes = min(max(input.durationMinutes, 1), 600)
    let distance = input.distanceKm.flatMap { $0.isFinite && $0 > 0 ? $0 : nil }
    let speed = distance.map { $0 / (Double(minutes) / 60) }
    let variantMet = activity.variants.first(where: { $0.key == input.variantKey })?.met
    func walking(_ s: Double, _ incline: Double) -> Double {
        if incline >= 6 { return 7.0 }; if incline >= 1 { return 5.3 }
        switch s { case ..<3.2: return 2.3; case ..<4.5: return 3.0; case ..<5.6: return 3.8; case ..<6.4: return 4.8; default: return 5.5 }
    }
    func running(_ s: Double, _ incline: Double) -> Double {
        let base: Double
        switch s { case ..<6.4: base = 6.0; case ..<8.0: base = 7.8; case ..<8.9: base = 8.5; case ..<9.7: base = 9.0; case ..<10.7: base = 9.3; case ..<11.3: base = 10.5
                   case ..<12.1: base = 11.0; case ..<12.9: base = 11.8; case ..<13.8: base = 12.0; case ..<14.6: base = 12.5; default: base = 14.8 }
        return incline >= 5 ? base + 3 : base
    }
    func cycling(_ s: Double) -> Double { switch s { case ..<16: return 4.0; case ..<19: return 6.8; case ..<22: return 8.0; case ..<26: return 10.0; default: return 12.0 } }
    func swimming(_ mpm: Double, _ variant: String?) -> Double {
        if variant == "butterfly" { return 13.8 }
        if variant == "breaststroke" && mpm >= 45 { return 10.3 }
        if variant == "backstroke" && mpm >= 45 { return 9.5 }
        switch mpm { case ..<30: return 5.8; case ..<50: return 8.0; case ..<70: return 9.8; default: return 10.5 }
    }
    var measured: Double?
    switch activity.key {
    case "walking": measured = speed.map { walking($0, input.inclinePercent) }
    case "running": measured = speed.map { running($0, input.inclinePercent) }
    case "cycling": measured = speed.map(cycling)
    case "swimming": measured = speed.map { swimming($0 * 1000 / 60, input.variantKey) }
    case "hiking": measured = speed.map { max(walking($0, input.inclinePercent), 5.3) }
    case "rowing": measured = speed.map { $0 < 6.4 ? 2.8 : ($0 < 9.7 ? 5.8 : 12.5) }
    default: measured = nil
    }
    let met = min(max(measured ?? variantMet ?? activity.fallbackMet, 1.3), 23)
    let kg = min(max(weightKg ?? 70, 35), 250)
    let calories = min(max(Int((max(met - 1, 0) * 3.5 * kg / 200) * Double(minutes)), 1), 10_000)
    return ActivityEnergyEstimate(activeCalories: calories, met: met, confidence: measured != nil ? "high" : (variantMet != nil ? "medium" : "low"),
                                  method: measured != nil ? "distance_pace" : (variantMet != nil ? "activity_variant" : "duration_fallback"))
}

// MARK: - Kardiyo

struct CardioControl: Hashable, Sendable {
    let key: String, label: String, unit: String
    let min: Double, max: Double, step: Double, defaultValue: Double
    var labels: [String]?
}

struct CardioMachine: Identifiable, Hashable, Sendable {
    let key: String, emoji: String, title: String
    let controls: [CardioControl]
    var tracksDistance = true
    var id: String { key }
}

private let speedControl = CardioControl(key: "speed", label: "Hız", unit: "km/s", min: 1, max: 20, step: 0.5, defaultValue: 6)
private let inclineControl = CardioControl(key: "incline", label: "Eğim", unit: "%", min: 0, max: 15, step: 1, defaultValue: 1)
private let levelControl = CardioControl(key: "level", label: "Direnç", unit: "seviye", min: 1, max: 20, step: 1, defaultValue: 6)

let outdoorPaces: [(String, Double)] = [("Yavaş yürüyüş", 4.0), ("Orta tempo", 5.3), ("Hızlı yürüyüş", 6.4), ("Hafif koşu", 8.5), ("Koşu", 10.5)]

let cardioMachines: [CardioMachine] = [
    .init(key: "treadmill", emoji: "🏃", title: "Koşu bandı", controls: [speedControl, inclineControl]),
    .init(key: "bike", emoji: "🚴", title: "Bisiklet", controls: [levelControl, CardioControl(key: "rpm", label: "Devir", unit: "rpm", min: 30, max: 130, step: 5, defaultValue: 75)]),
    .init(key: "elliptical", emoji: "〰️", title: "Eliptik", controls: [levelControl, CardioControl(key: "spm", label: "Adım hızı", unit: "adım/dk", min: 60, max: 200, step: 5, defaultValue: 130)], tracksDistance: false),
    .init(key: "rower", emoji: "🚣", title: "Kürek", controls: [CardioControl(key: "spm", label: "Tempo", unit: "kürek/dk", min: 14, max: 40, step: 1, defaultValue: 24), CardioControl(key: "split", label: "500 m süresi", unit: "sn", min: 90, max: 240, step: 5, defaultValue: 150)]),
    .init(key: "stepper", emoji: "🪜", title: "Merdiven", controls: [CardioControl(key: "spm", label: "Basamak", unit: "basamak/dk", min: 20, max: 160, step: 5, defaultValue: 70)], tracksDistance: false),
    .init(key: "outdoor", emoji: "🌳", title: "Açık hava", controls: [
        CardioControl(key: "pace", label: "Tempo", unit: "", min: 0, max: 4, step: 1, defaultValue: 1, labels: outdoorPaces.map(\.0)),
        CardioControl(key: "terrain", label: "Arazi", unit: "", min: 0, max: 3, step: 1, defaultValue: 0, labels: ["Düz", "Hafif yokuş", "Dik yokuş", "İnişli çıkışlı"])]),
]

struct CardioRate: Sendable { let activeKcalPerMinute: Double; let speedKmh: Double }

/// Tahmini yakım: koşu bandı/açık havada ACSM, kürekte Concept2 güç formülü, diğerlerinde MET.
func cardioRate(machineKey: String, values: [String: Double], weightKg: Double?) -> CardioRate {
    let kg = min(max(weightKg ?? 70, 35), 250)
    func v(_ key: String, _ fallback: Double) -> Double { values[key] ?? fallback }
    func fromMet(_ met: Double) -> Double { (min(max(met, 1.5), 16) - 1) * 3.5 * kg / 200 }
    switch machineKey {
    case "treadmill", "outdoor":
        let kmh = machineKey == "outdoor" ? outdoorPaces[min(max(Int(v("pace", 1)), 0), outdoorPaces.count - 1)].1 : v("speed", 6)
        let grade: Double = machineKey == "outdoor" ? { switch Int(v("terrain", 0)) { case 1: return 0.03; case 2: return 0.07; case 3: return 0.04; default: return 0 } }() : v("incline", 0) / 100
        let mPerMin = kmh * 1000 / 60
        let vo2 = kmh < 6.4 ? 0.1 * mPerMin + 1.8 * mPerMin * grade : 0.2 * mPerMin + 0.9 * mPerMin * grade
        return CardioRate(activeKcalPerMinute: vo2 * kg / 1000 * 5, speedKmh: kmh)
    case "bike":
        let level = v("level", 6), rpm = v("rpm", 75)
        return CardioRate(activeKcalPerMinute: fromMet(3.5 + level * 0.35 + (rpm - 60) * 0.05), speedKmh: min(max(rpm * 0.3, 0), 45))
    case "elliptical":
        return CardioRate(activeKcalPerMinute: fromMet(4.5 + v("level", 6) * 0.3 + (v("spm", 130) - 120) * 0.02), speedKmh: 0)
    case "rower":
        let split = min(max(v("split", 150), 60), 300)
        let watts = 2.8 / pow(split / 500, 3)
        return CardioRate(activeKcalPerMinute: 4 * watts * 0.8604 / 60, speedKmh: 500 / split * 3.6)
    case "stepper": return CardioRate(activeKcalPerMinute: fromMet(4 + v("spm", 70) * 0.05), speedKmh: 0)
    default: return CardioRate(activeKcalPerMinute: fromMet(6), speedKmh: 0)
    }
}

struct CardioSegment: Hashable, Sendable { let seconds: Int; let label: String; let targets: [String: Double] }

struct CardioPreset: Identifiable, Hashable, Sendable {
    let key: String, title: String, description: String, machineKey: String
    let segments: [CardioSegment]
    var totalSeconds: Int { segments.reduce(0) { $0 + $1.seconds } }
    var id: String { key }
}

private func intervals(_ rounds: Int, _ fast: CardioSegment, _ slow: CardioSegment) -> [CardioSegment] { (0..<rounds).flatMap { _ in [fast, slow] } }

let cardioPresets: [CardioPreset] = [
    .init(key: "12-3-30", title: "12-3-30 yürüyüşü", description: "%12 eğim, 3 km/s, 30 dakika. Eklemleri yormadan yüksek yakım.", machineKey: "treadmill",
          segments: [CardioSegment(seconds: 1800, label: "Tempo yürüyüş", targets: ["speed": 3, "incline": 12])]),
    .init(key: "hiit-treadmill", title: "Aralıklı koşu (HIIT)", description: "Isınma, 8 × (1 dk hızlı + 2 dk yavaş), soğuma.", machineKey: "treadmill",
          segments: [CardioSegment(seconds: 300, label: "Isınma", targets: ["speed": 5.5, "incline": 1])]
            + intervals(8, CardioSegment(seconds: 60, label: "Hızlı", targets: ["speed": 11, "incline": 1]), CardioSegment(seconds: 120, label: "Yavaş", targets: ["speed": 5.5, "incline": 1]))
            + [CardioSegment(seconds: 240, label: "Soğuma", targets: ["speed": 4.5, "incline": 0])]),
    .init(key: "fat-burn", title: "Yağ yakım bölgesi", description: "Konuşabileceğin tempoda 40 dakika sabit yürüyüş.", machineKey: "treadmill",
          segments: [CardioSegment(seconds: 300, label: "Isınma", targets: ["speed": 4.5, "incline": 1]), CardioSegment(seconds: 1800, label: "Sabit tempo", targets: ["speed": 5.8, "incline": 4]), CardioSegment(seconds: 300, label: "Soğuma", targets: ["speed": 4, "incline": 0])]),
    .init(key: "hill-climb", title: "Tepe tırmanışı", description: "Eğim her 3 dakikada artar, sonra iner.", machineKey: "treadmill",
          segments: [0.0, 3, 5, 7, 9, 7, 5, 2].enumerated().map { CardioSegment(seconds: 180, label: $0.offset < 5 ? "Tırmanış" : "İniş", targets: ["speed": 5.2, "incline": $0.element]) }),
    .init(key: "hiit-bike", title: "Bisiklet sprintleri", description: "Isınma, 10 × (30 sn sprint + 90 sn kolay), soğuma.", machineKey: "bike",
          segments: [CardioSegment(seconds: 300, label: "Isınma", targets: ["level": 5, "rpm": 75])]
            + intervals(10, CardioSegment(seconds: 30, label: "Sprint", targets: ["level": 12, "rpm": 105]), CardioSegment(seconds: 90, label: "Kolay", targets: ["level": 5, "rpm": 70]))
            + [CardioSegment(seconds: 300, label: "Soğuma", targets: ["level": 3, "rpm": 65])]),
    .init(key: "rower-pyramid", title: "Kürek piramidi", description: "Tempo 20 → 28 → 20, her basamak 3 dakika.", machineKey: "rower",
          segments: [20.0, 22, 24, 26, 28, 24, 20].map { CardioSegment(seconds: 180, label: "\(Int($0)) kürek/dk", targets: ["spm": $0, "split": 190 - $0 * 2]) }),
]

/// Oyun modunda sanal rota (km).
let virtualRouteLandmarks: [(Double, String)] = [(0, "Kadıköy iskelesi"), (1.2, "Moda"), (2.5, "Kalamış"), (3.4, "Fenerbahçe feneri"), (5.5, "Caddebostan"), (7, "Suadiye"), (8.3, "Bostancı"), (12, "Maltepe sahili"), (21.1, "Yarı maraton!")]

private let cardioEnglish: [String: String] = [
    "Koşu bandı": "Treadmill", "Bisiklet": "Bike", "Eliptik": "Elliptical", "Kürek": "Rower", "Merdiven": "Stair climber", "Açık hava": "Outdoor",
    "Hız": "Speed", "Eğim": "Incline", "Direnç": "Resistance", "Devir": "Cadence", "Adım hızı": "Stride rate", "Tempo": "Stroke rate", "500 m süresi": "500 m split",
    "Basamak": "Steps", "Arazi": "Terrain", "km/s": "km/h", "seviye": "level", "adım/dk": "strides/min", "kürek/dk": "strokes/min", "sn": "sec", "basamak/dk": "steps/min",
    "Düz": "Flat", "Yavaş yürüyüş": "Slow walk", "Orta tempo": "Moderate pace", "Hızlı yürüyüş": "Brisk walk", "Hafif koşu": "Easy run", "Koşu": "Run",
    "Hafif yokuş": "Gentle hill", "Dik yokuş": "Steep hill", "İnişli çıkışlı": "Rolling",
    "12-3-30 yürüyüşü": "12-3-30 walk", "%12 eğim, 3 km/s, 30 dakika. Eklemleri yormadan yüksek yakım.": "12% incline, 3 km/h, 30 minutes. High burn, easy on joints.",
    "Aralıklı koşu (HIIT)": "Interval run (HIIT)", "Isınma, 8 × (1 dk hızlı + 2 dk yavaş), soğuma.": "Warm-up, 8 × (1 min fast + 2 min easy), cool-down.",
    "Yağ yakım bölgesi": "Fat-burn zone", "Konuşabileceğin tempoda 40 dakika sabit yürüyüş.": "40 minutes of steady walking at a conversational pace.",
    "Tepe tırmanışı": "Hill climb", "Eğim her 3 dakikada artar, sonra iner.": "Incline rises every 3 minutes, then comes down.",
    "Bisiklet sprintleri": "Bike sprints", "Isınma, 10 × (30 sn sprint + 90 sn kolay), soğuma.": "Warm-up, 10 × (30 s sprint + 90 s easy), cool-down.",
    "Kürek piramidi": "Rowing pyramid", "Tempo 20 → 28 → 20, her basamak 3 dakika.": "Stroke rate 20 → 28 → 20, 3 minutes per step.",
    "Tempo yürüyüş": "Brisk walk", "Isınma": "Warm-up", "Hızlı": "Fast", "Yavaş": "Easy", "Soğuma": "Cool-down", "Sabit tempo": "Steady pace", "Tırmanış": "Climb", "İniş": "Descent",
    "Sprint": "Sprint", "Kolay": "Easy", "Kadıköy iskelesi": "Kadıköy pier", "Fenerbahçe feneri": "Fenerbahçe lighthouse", "Maltepe sahili": "Maltepe shore", "Yarı maraton!": "Half marathon!",
]

/// Kardiyo metnini uygulama diline çevirir; sözlükte yoksa olduğu gibi döner.
@MainActor func ct(_ text: String) -> String { tr(text, cardioEnglish[text] ?? text.replacingOccurrences(of: " kürek/dk", with: " strokes/min")) }

@MainActor var cardioActivityTypes: [ManualActivityType] {
    cardioMachines.map { ManualActivityType(key: "cardio_\($0.key)", emoji: $0.emoji, titleTr: $0.title, titleEn: cardioEnglish[$0.title] ?? $0.title, fallbackMet: 6) }
}

@MainActor func activityType(for key: String?) -> ManualActivityType? {
    guard let key else { return nil }
    return (manualActivityTypes + cardioActivityTypes).first { $0.key == key }
}

// MARK: - Sosyal

struct FriendUser: Identifiable, Equatable, Sendable, Hashable {
    var id: String
    var username: String?
    var displayName: String?
    var avatarPath: String?
    init(json: JSON) { id = json.string("id"); username = json.nonEmptyString("username"); displayName = json.nonEmptyString("displayName"); avatarPath = json.nonEmptyString("avatarPath") }
    var label: String { displayName ?? username.map { "@\($0)" } ?? "—" }
}

struct FriendRequest: Identifiable, Equatable, Sendable {
    var id: String, status: String, createdAt: String, isIncoming: Bool
    var user: FriendUser
    init(json: JSON) { id = json.string("id"); status = json.string("status"); createdAt = json.string("createdAt"); isIncoming = json.bool("isIncoming"); user = FriendUser(json: json["user"]) }
}

struct FriendsSummary: Equatable, Sendable {
    var friends: [FriendRequest] = []
    var incoming: [FriendRequest] = []
    var outgoing: [FriendRequest] = []
}

struct LeaderboardEntry: Identifiable, Equatable, Sendable {
    var rank: Int, weeklyXp: Int64, isCurrentUser: Bool
    var user: FriendUser
    var id: String { user.id }
    init(json: JSON) { rank = json.int("rank"); weeklyXp = Int64(json.double("weeklyXp")); isCurrentUser = json.bool("isCurrentUser"); user = FriendUser(json: json["user"]) }
}

struct FeedItem: Identifiable, Equatable, Sendable {
    var id: String, source: String, amount: Int, occurredAt: String
    var user: FriendUser
    init(json: JSON) { id = json.string("id"); source = json.string("source"); amount = json.int("amount"); occurredAt = json.string("occurredAt"); user = FriendUser(json: json["user"]) }
}

struct SocialChallenge: Identifiable, Equatable, Sendable {
    var id: String, title: String, metric: String, targetValue: Double, startsAt: String, endsAt: String
    var creatorId: String, isCreator: Bool, myStatus: String, participantCount: Int
    var mode: String, templateKey: String?
    init(json: JSON) {
        id = json.string("id"); title = json.string("title"); metric = json.string("metric"); targetValue = json.double("targetValue"); startsAt = json.string("startsAt"); endsAt = json.string("endsAt")
        creatorId = json.string("creatorId"); isCreator = json.bool("isCreator"); myStatus = json.string("myStatus"); participantCount = json.int("participantCount")
        mode = json.string("mode", "compete"); templateKey = json.nonEmptyString("templateKey")
    }
}

struct ChallengeProgressEntry: Identifiable, Equatable, Sendable {
    var rank: Int, progressValue: Double, isCurrentUser: Bool
    var user: FriendUser
    var id: String { user.id }
    init(json: JSON) { rank = json.int("rank"); progressValue = json.double("progressValue"); isCurrentUser = json.bool("isCurrentUser"); user = FriendUser(json: json["user"]) }
}
