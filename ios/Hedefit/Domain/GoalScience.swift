import Foundation

enum GoalPace: CaseIterable, Sendable {
    case slow, steady, fast
    var lossFraction: Double { switch self { case .slow: return 0.005; case .steady: return 0.0075; case .fast: return 0.010 } }
    var gainFraction: Double { switch self { case .slow: return 0.0025; case .steady: return 0.00375; case .fast: return 0.005 } }
}

/// Bilimsel dayanaklı hedef hesapları (sunucu `lib/goal-plan.ts` ve site ile aynı kural).
enum GoalScience {
    static let kcalPerKg = 7_700
    static let stepTarget = 7_000

    static func weeklyRateKg(current: Double, target: Double, pace: GoalPace) -> Double {
        max(current * (target < current ? pace.lossFraction : pace.gainFraction), 0.1)
    }

    static func weeks(current: Double?, target: Double?, pace: GoalPace = .steady) -> Int? {
        guard let current, let target else { return nil }
        let diff = abs(target - current)
        if diff < 0.5 { return 0 }
        return min(max(Int(floor(diff / weeklyRateKg(current: current, target: target, pace: pace) + 0.5)), 1), 208)
    }

    static func dailyEnergyDelta(current: Double, target: Double, pace: GoalPace) -> Int {
        Int((weeklyRateKg(current: current, target: target, pace: pace) * Double(kcalPerKg) / 7).rounded())
    }

    static func recommendedWaterMl(weightKg: Double?, gender: String?, weeklyTrainingMinutes: Int = 0) -> Int {
        let base = gender == "Kadın" ? 1_600.0 : 2_000.0
        let byWeight = weightKg.map { $0 * 30 * 0.8 } ?? base
        let training = Double(weeklyTrainingMinutes) / 60 * 500 / 7
        let ml = min(max((base + byWeight) / 2 + training, 1_500), 3_500)
        return Int((ml / 250).rounded()) * 250
    }

    static func recommendedProteinG(weightKg: Double?, losing: Bool) -> Int? { weightKg.map { Int(($0 * (losing ? 2.0 : 1.6)).rounded()) } }
}

struct GoalSource: Sendable { let claim, claimEn, citation, url: String }

let goalSources: [GoalSource] = [
    .init(claim: "Kilo verirken direnç antrenmanı yağ kaybını artırır, kası korur", claimEn: "Resistance training during weight loss boosts fat loss and preserves muscle", citation: "Frontiers in Endocrinology, 2025", url: "https://www.frontiersin.org/journals/endocrinology/articles/10.3389/fendo.2025.1725500/full"),
    .init(claim: "Yağ kaybı: egzersiz türlerinin karşılaştırması (meta-analiz)", claimEn: "Fat loss: comparison of exercise modes (meta-analysis)", citation: "JISSN, 2025", url: "https://doi.org/10.1080/15502783.2025.2507949"),
    .init(claim: "Kalori fazlası büyüdükçe protein birikimi artar, yağ da artar", claimEn: "A larger surplus increases protein accretion, and fat too", citation: "Clinical Nutrition, 2024 (RCT)", url: "https://www.sciencedirect.com/science/article/pii/S0261561424003467"),
    .init(claim: "Kalori açığında protein alımı ve yağsız kütle (doz-yanıt)", claimEn: "Protein intake and fat-free mass in a deficit (dose-response)", citation: "Strength & Conditioning Journal, 2025", url: "https://journals.lww.com/nsca-scj/fulltext/9900/effect_of_dietary_protein_on_fat_free_mass_in.179.aspx"),
    .init(claim: "Su alımı değişikliklerini test eden klinik çalışmalar (sistematik derleme)", claimEn: "Clinical trials changing daily water intake (systematic review)", citation: "JAMA Network Open, 2024", url: "https://pubmed.ncbi.nlm.nih.gov/39585691/"),
    .init(claim: "Adım: günde ~7.000 adım anlamlı sağlık faydası sağlar", claimEn: "Steps: ~7,000 a day gives meaningful health benefits", citation: "Ding et al., Lancet Public Health 2025", url: "https://www.thelancet.com/journals/lanpub/article/PIIS2468-2667(25)00164-1/fulltext"),
]

// MARK: - Birimler

enum Units {
    private static let kgToLb = 2.2046226218, cmToIn = 1.0 / 2.54, metersPerMile = 1609.344, mlPerFlOz = 29.5735295625

    static func isImperial(_ system: String) -> Bool { system == "imperial" }
    static func weightValue(_ kg: Double, _ system: String) -> Double { isImperial(system) ? kg * kgToLb : kg }
    static func weightToKg(_ value: Double, _ system: String) -> Double { isImperial(system) ? value / kgToLb : value }
    static func weightUnit(_ system: String) -> String { isImperial(system) ? "lb" : "kg" }
    static func formatWeight(_ kg: Double, _ system: String, decimals: Int = 1) -> String { String(format: "%.\(decimals)f %@", weightValue(kg, system), weightUnit(system)) }
    static func heightValue(_ cm: Double, _ system: String) -> Double { isImperial(system) ? cm * cmToIn : cm }
    static func heightToCm(_ value: Double, _ system: String) -> Double { isImperial(system) ? value / cmToIn : value }
    static func heightUnit(_ system: String) -> String { isImperial(system) ? "in" : "cm" }
    static func formatLength(_ cm: Double, _ system: String, signed: Bool = false) -> String { String(format: signed ? "%+.1f %@" : "%.1f %@", heightValue(cm, system), heightUnit(system)) }
    static func formatDistance(_ meters: Double, _ system: String) -> String {
        let safe = meters.isFinite && meters >= 0 ? meters : 0
        return isImperial(system) ? String(format: "%.2f mi", safe / metersPerMile) : String(format: "%.2f km", safe / 1000)
    }
    static func formatPace(_ secondsPerKm: Int?, _ system: String) -> String {
        let unit = isImperial(system) ? "mi" : "km"
        guard let s = secondsPerKm else { return "— /\(unit)" }
        let converted = isImperial(system) ? Int(Double(s) * 1.609344) : s
        return String(format: "%d:%02d /%@", converted / 60, converted % 60, unit)
    }
    static func formatWater(_ ml: Int, _ system: String) -> String { isImperial(system) ? String(format: "%.0f fl oz", Double(ml) / mlPerFlOz) : String(format: "%.1f L", Double(ml) / 1000) }
}

// MARK: - Kilo trendi

enum WeightTrend {
    struct Point: Equatable { var date: Date; var kg: Double }
    enum BmiBand { case low, healthy, high }

    static func points(_ measurements: [BodyMeasurement]) -> [Point] {
        measurements.compactMap { m in
            guard let kg = m.weightKg, let date = Dates.parse(m.date) else { return nil }
            return Point(date: date, kg: kg)
        }.sorted { $0.date < $1.date }
    }

    static func smooth(_ points: [Point], windowDays: Int = 7) -> [Double] {
        points.map { p in
            let window = points.filter { $0.date <= p.date && Dates.epochDay(p.date) - Dates.epochDay($0.date) < windowDays }
            return window.isEmpty ? p.kg : window.map(\.kg).reduce(0, +) / Double(window.count)
        }
    }

    /// Son `lookbackDays` gündeki en küçük kareler eğiminden haftalık değişim.
    static func weeklyRateKg(_ points: [Point], today: Date = Date(), lookbackDays: Int = 28) -> Double? {
        let recent = points.filter { let d = Dates.epochDay(today) - Dates.epochDay($0.date); return d >= 0 && d < lookbackDays }
        guard recent.count >= 2, let origin = recent.first?.date else { return nil }
        let xs = recent.map { Double(Dates.epochDay($0.date) - Dates.epochDay(origin)) }
        guard let first = xs.first, let last = xs.last, last - first >= 5 else { return nil }
        let ys = recent.map(\.kg)
        let xMean = xs.reduce(0, +) / Double(xs.count), yMean = ys.reduce(0, +) / Double(ys.count)
        let denominator = xs.reduce(0) { $0 + ($1 - xMean) * ($1 - xMean) }
        guard denominator != 0 else { return nil }
        let numerator = xs.indices.reduce(0.0) { $0 + (xs[$1] - xMean) * (ys[$1] - yMean) }
        return numerator / denominator * 7
    }

    static func bmi(weightKg: Double?, heightCm: Double?) -> Double? {
        guard let w = weightKg, let h = heightCm, (100...250).contains(h), (20...400).contains(w) else { return nil }
        return w / pow(h / 100, 2)
    }
    static func bmiBand(_ bmi: Double) -> BmiBand { bmi < 18.5 ? .low : (bmi < 25 ? .healthy : .high) }
    static func bmiApplies(age: Int?) -> Bool { age == nil || age! >= 18 }

    static func goalProgress(start: Double, current: Double, target: Double) -> Double? {
        let total = target - start
        guard abs(total) >= 0.05 else { return nil }
        return min(max((current - start) / total, 0), 1)
    }

    static func projectedDate(current: Double, target: Double, weeklyRateKg: Double?, today: Date = Date()) -> Date? {
        guard let rate = weeklyRateKg else { return nil }
        let remaining = target - current
        guard abs(remaining) >= 0.05, abs(rate) >= 0.05, remaining * rate >= 0 else { return nil }
        let weeks = remaining / rate
        return weeks > 104 ? nil : Dates.add(Int(weeks * 7), to: today)
    }
}
