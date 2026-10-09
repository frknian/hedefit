import Foundation

// Otomatik üretildi (Android EquipmentCatalog).

struct EquipmentInfo: Identifiable, Hashable, Sendable {
    let id: String, name: String, category: String, description: String
    let primaryMuscles: [String], secondaryMuscles: [String]
    let instructions: [String], commonMistakes: [String], safetyNotes: [String]
    var aliases: [String] = []
    var exerciseTerms: [String] = []
}

struct RecognitionAlternative: Sendable { let equipment: EquipmentInfo; let confidence: Double }

enum RecognitionResult: Sendable {
    case recognized(EquipmentInfo, confidence: Double, alternatives: [RecognitionAlternative], features: [String])
    case unknown(confidence: Double, features: [String])
}

enum EquipmentCatalog {
    private static let setup = ["Oturağı, pedleri ve başlangıç konumunu vücut ölçülerine göre ayarla.", "Hafif bir deneme ağırlığıyla hareket yolunu kontrol et.", "Gövdeni sabit tutup tekrarı kontrollü tamamla."]
    private static let mistakes = ["Ağırlığı savurmak", "Eklemleri kilitlemek veya hareket açıklığını kontrolsüz kısaltmak"]
    private static let safety = ["Emniyet pimini ve kilitleri başlamadan kontrol et.", "Keskin ağrıda hareketi hemen bırak."]

    private static func item(_ id: String, _ name: String, _ category: String, _ description: String, _ primary: [String], _ secondary: [String], _ terms: [String], _ aliases: [String] = []) -> EquipmentInfo {
        EquipmentInfo(id: id, name: name, category: category, description: description, primaryMuscles: primary, secondaryMuscles: secondary, instructions: setup, commonMistakes: mistakes, safetyNotes: safety, aliases: aliases, exerciseTerms: terms)
    }

    static let items: [EquipmentInfo] = [
    item("bench_press", "Bench Press Sehpası", "Serbest ağırlık", "Barbell bench press için sehpa ve bar askıları.", ["chest"], ["triceps", "front delts"], ["bench press", "chest press"], []),
    item("squat_rack", "Squat Rack", "Serbest ağırlık", "Squat ve barbell hareketleri için güvenlik kolları bulunan rack.", ["quadriceps", "glutes"], ["hamstrings", "lower back"], ["squat", "rack"], []),
    item("smith_machine", "Smith Machine", "Makine", "Ray üzerinde yönlendirilmiş bar sistemi.", ["full body"], [], ["smith machine"], ["smith rack"]),
    item("cable_machine", "Cable Machine", "Kablo", "Ayarlanabilir makara ve kablo istasyonu.", ["full body"], [], ["cable"], ["functional trainer", "crossover"]),
    item("lat_pulldown", "Lat Pulldown", "Makine", "Üstten çekiş için diz pedli kablo makinesi.", ["lats"], ["biceps", "rear delts"], ["lat pulldown", "pulldown"], []),
    item("seated_row", "Seated Row", "Makine", "Oturarak yatay çekiş makinesi.", ["middle back", "lats"], ["biceps", "rear delts"], ["seated row", "cable row"], []),
    item("chest_press", "Chest Press", "Makine", "Kontrollü yatay itiş makinesi.", ["chest"], ["triceps", "front delts"], ["chest press"], []),
    item("shoulder_press", "Shoulder Press", "Makine", "Baş üstü itiş makinesi.", ["shoulders"], ["triceps"], ["shoulder press"], []),
    item("leg_press", "Leg Press", "Makine", "Platforma karşı alt vücut itiş makinesi.", ["quadriceps", "glutes"], ["hamstrings"], ["leg press"], []),
    item("leg_extension", "Leg Extension", "Makine", "Diz ekstansiyonu makinesi.", ["quadriceps"], [], ["leg extension"], []),
    item("leg_curl", "Leg Curl", "Makine", "Diz fleksiyonu makinesi.", ["hamstrings"], ["calves"], ["leg curl"], []),
    item("calf_raise", "Calf Raise", "Makine", "Baldır yükseltme makinesi.", ["calves"], [], ["calf raise"], []),
    item("hip_abductor", "Hip Abductor", "Makine", "Kalçayı dışa açma makinesi.", ["abductors", "glutes"], [], ["hip abduction", "abductor"], []),
    item("hip_adductor", "Hip Adductor", "Makine", "Bacakları içe kapatma makinesi.", ["adductors"], [], ["hip adduction", "adductor"], []),
    item("pec_deck", "Pec Deck", "Makine", "Göğüs sıkıştırma ve ters fly makinesi.", ["chest"], ["front delts"], ["pec deck", "machine fly"], []),
    item("assisted_pullup_dip", "Assisted Pull-up / Dip", "Makine", "Diz platformuyla destekli çekiş ve dip istasyonu.", ["lats", "triceps"], ["biceps", "chest"], ["assisted pull", "assisted dip"], []),
    item("treadmill", "Koşu Bandı", "Kardiyo", "Motorlu yürüyüş ve koşu platformu.", ["quadriceps", "hamstrings", "calves"], ["glutes"], ["treadmill", "running"], ["koşu bandı"]),
    item("elliptical", "Eliptik Bisiklet", "Kardiyo", "Düşük darbeli el ve ayak pedallı kardiyo cihazı.", ["quadriceps", "glutes"], ["hamstrings", "shoulders"], ["elliptical"], []),
    item("stationary_bike", "Sabit Bisiklet", "Kardiyo", "Salon tipi sabit bisiklet.", ["quadriceps"], ["hamstrings", "calves"], ["cycling", "bike"], ["exercise bike", "spin bike"]),
    item("rowing_machine", "Kürek Ergometresi", "Kardiyo", "Raylı oturak ve zincir/kablo tutamaklı kürek cihazı.", ["middle back", "quadriceps"], ["lats", "biceps", "hamstrings"], ["rowing", "rower"], ["rowing ergometer"]),
    item("stair_climber", "Merdiven Tırmanma", "Kardiyo", "Dönen basamaklı tırmanma cihazı.", ["quadriceps", "glutes"], ["hamstrings", "calves"], ["stair", "step"], ["stairmaster"]),
    item("dumbbell_rack", "Dumbbell Rafı", "Serbest ağırlık", "Farklı ağırlıklarda dambılların bulunduğu raf.", ["full body"], [], ["dumbbell"], ["dumbbells", "dumbbell rack"]),
    item("barbell", "Barbell", "Serbest ağırlık", "Plaka takılabilen düz uzun bar.", ["full body"], [], ["barbell"], ["olympic bar"]),
    item("kettlebell", "Kettlebell", "Serbest ağırlık", "Üstten saplı döküm ağırlık.", ["full body"], [], ["kettlebell"], []),
    item("ez_bar", "EZ Bar", "Serbest ağırlık", "Açılı kavrama bölgeleri olan kısa bar.", ["biceps"], ["forearms", "triceps"], ["ez bar", "e-z"], ["curl bar"]),
    item("resistance_band", "Direnç Bandı", "Aksesuar", "Elastik egzersiz bandı.", ["full body"], [], ["band"], ["resistance band"]),
    item("pullup_bar", "Pull-up Barı", "Vücut ağırlığı", "Asılarak çekiş için sabit bar.", ["lats"], ["biceps", "forearms"], ["pull-up", "pullup"], ["chin-up bar"]),
    item("dip_station", "Dip İstasyonu", "Vücut ağırlığı", "Paralel tutamaklı dip istasyonu.", ["triceps", "chest"], ["front delts"], ["dip"], ["parallel bars"]),
    ]

    private static func normalize(_ value: String) -> String {
        value.lowercased().replacingOccurrences(of: "ı", with: "i").replacingOccurrences(of: "ş", with: "s").replacingOccurrences(of: "ğ", with: "g")
            .replacingOccurrences(of: "ü", with: "u").replacingOccurrences(of: "ö", with: "o").replacingOccurrences(of: "ç", with: "c").filter { $0.isLetter || $0.isNumber }
    }

    static func find(byLabel label: String) -> EquipmentInfo? {
        let n = normalize(label)
        return items.first { e in ([e.id, e.name] + e.aliases).contains { normalize($0) == n } }
    }

    static func matchingExercises(_ equipment: EquipmentInfo, in catalog: [ExerciseCatalogItem]) -> [ExerciseCatalogItem] {
        let terms = (equipment.exerciseTerms + equipment.aliases + [equipment.name]).map(normalize).filter { !$0.isEmpty }
        var seen = Set<String>()
        return catalog.filter { ex in
            let hay = normalize("\(ex.equipment) \(ex.name)")
            return terms.contains { hay.contains($0) || $0.contains(hay) } && seen.insert(ex.id).inserted
        }.sorted { $0.name < $1.name }
    }
}
