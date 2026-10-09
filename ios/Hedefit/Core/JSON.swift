import Foundation

/// org.json benzeri, tür-güvenli dinamik JSON. Sunucu yanıtlarını Android'deki
/// `optString/optInt/optJSONObject` mantığıyla (eksik alan = varsayılan) okumak ve
/// istek gövdelerini sözlük/dizi literalleriyle kurmak için kullanılır.
enum JSON: Sendable, Equatable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JSON])
    case object([String: JSON])

    // MARK: Okuma

    static func parse(_ data: Data) -> JSON {
        guard !data.isEmpty, let any = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) else { return .null }
        return JSON(any: any)
    }

    static func parse(_ text: String) -> JSON { parse(Data(text.utf8)) }

    init(any: Any?) {
        switch any {
        case nil, is NSNull: self = .null
        case let value as JSON: self = value
        case let value as NSNumber:
            if CFGetTypeID(value) == CFBooleanGetTypeID() { self = .bool(value.boolValue) } else { self = .number(value.doubleValue) }
        case let value as String: self = .string(value)
        case let value as [Any?]: self = .array(value.map { JSON(any: $0) })
        case let value as [String: Any?]: self = .object(value.mapValues { JSON(any: $0) })
        case let value as Date: self = .string(ISO.string(value))
        case let value as UUID: self = .string(value.uuidString.lowercased())
        default: self = .null
        }
    }

    subscript(key: String) -> JSON {
        if case .object(let dict) = self { return dict[key] ?? .null }
        return .null
    }

    subscript(index: Int) -> JSON {
        if case .array(let items) = self, items.indices.contains(index) { return items[index] }
        return .null
    }

    var isNull: Bool { if case .null = self { return true }; return false }
    var isObject: Bool { if case .object = self { return true }; return false }
    func has(_ key: String) -> Bool { if case .object(let dict) = self { return dict[key] != nil }; return false }

    /// Dizi öğeleri (dizi değilse boş).
    var items: [JSON] { if case .array(let value) = self { return value }; return [] }
    var count: Int { items.count }
    var dict: [String: JSON] { if case .object(let value) = self { return value }; return [:] }

    /// `optString`: alan yoksa/null ise `fallback`. Sayılar metne çevrilir.
    func string(_ key: String, _ fallback: String = "") -> String { self[key].stringValue ?? fallback }

    var stringValue: String? {
        switch self {
        case .string(let value): return value
        case .number(let value): return value.rounded() == value && abs(value) < 1e15 ? String(Int64(value)) : String(value)
        case .bool(let value): return value ? "true" : "false"
        default: return nil
        }
    }

    /// `stringOrNull`: kırpılmış, boş değilse.
    func nonEmptyString(_ key: String) -> String? {
        guard let value = self[key].stringValue?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty, value != "null" else { return nil }
        return value
    }

    func int(_ key: String, _ fallback: Int = 0) -> Int { self[key].intValue ?? fallback }
    func double(_ key: String, _ fallback: Double = 0) -> Double { self[key].doubleValue ?? fallback }
    func bool(_ key: String, _ fallback: Bool = false) -> Bool { self[key].boolValue ?? fallback }
    func intOrNil(_ key: String) -> Int? { self[key].intValue }
    func doubleOrNil(_ key: String) -> Double? { self[key].doubleValue }

    var intValue: Int? {
        switch self {
        case .number(let value): return value.isFinite ? Int(value.rounded(.towardZero)) : nil
        case .string(let value): return Int(value) ?? Double(value).map { Int($0) }
        case .bool(let value): return value ? 1 : 0
        default: return nil
        }
    }

    var doubleValue: Double? {
        switch self {
        case .number(let value): return value.isFinite ? value : nil
        case .string(let value): return Double(value)
        default: return nil
        }
    }

    var boolValue: Bool? {
        switch self {
        case .bool(let value): return value
        case .number(let value): return value != 0
        case .string(let value): return value == "true" ? true : (value == "false" ? false : nil)
        default: return nil
        }
    }

    /// Dizi alanı → metin listesi.
    func strings(_ key: String) -> [String] { self[key].items.compactMap(\.stringValue) }

    // MARK: Yazma

    var any: Any {
        switch self {
        case .null: return NSNull()
        case .bool(let value): return value
        case .number(let value): return value
        case .string(let value): return value
        case .array(let value): return value.map(\.any)
        case .object(let value): return value.mapValues(\.any)
        }
    }

    func data() -> Data { (try? JSONSerialization.data(withJSONObject: any, options: [.fragmentsAllowed, .sortedKeys])) ?? Data("null".utf8) }
    var text: String { String(decoding: data(), as: UTF8.self) }

    /// Sözlüğe alan ekler/değiştirir.
    func setting(_ key: String, _ value: JSON?) -> JSON {
        var copy = dict
        copy[key] = value ?? .null
        return .object(copy)
    }

    func merging(_ other: [String: JSON]) -> JSON {
        var copy = dict
        other.forEach { copy[$0.key] = $0.value }
        return .object(copy)
    }

    static func from(_ strings: [String]) -> JSON { .array(strings.map { .string($0) }) }
    static func opt(_ value: Int?) -> JSON { value.map { .number(Double($0)) } ?? .null }
    static func opt(_ value: Double?) -> JSON { value.map { .number($0) } ?? .null }
    static func opt(_ value: String?) -> JSON { value.map { .string($0) } ?? .null }
}

extension JSON: ExpressibleByStringLiteral, ExpressibleByIntegerLiteral, ExpressibleByFloatLiteral,
                ExpressibleByBooleanLiteral, ExpressibleByNilLiteral, ExpressibleByArrayLiteral, ExpressibleByDictionaryLiteral {
    init(stringLiteral value: String) { self = .string(value) }
    init(integerLiteral value: Int) { self = .number(Double(value)) }
    init(floatLiteral value: Double) { self = .number(value) }
    init(booleanLiteral value: Bool) { self = .bool(value) }
    init(nilLiteral: ()) { self = .null }
    init(arrayLiteral elements: JSON...) { self = .array(elements) }
    init(dictionaryLiteral elements: (String, JSON)...) { self = .object(Dictionary(elements, uniquingKeysWith: { $1 })) }
}

extension JSON {
    init(_ value: Int) { self = .number(Double(value)) }
    init(_ value: Double) { self = .number(value) }
    init(_ value: String) { self = .string(value) }
    init(_ value: Bool) { self = .bool(value) }
}

/// ISO-8601 yardımcıları (milisaniyeli ve milisaniyesiz biçimleri okur).
enum ISO {
    private static let withFraction: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter(); formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return formatter
    }()
    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter(); formatter.formatOptions = [.withInternetDateTime]; return formatter
    }()

    static func string(_ date: Date = Date()) -> String { withFraction.string(from: date) }
    static func date(_ value: String) -> Date? { withFraction.date(from: value) ?? plain.date(from: value) }
}

extension Dictionary where Key == String, Value == JSON {
    /// `["a": JSON(1)].data()` — gövde kurarken kısa yol.
    func data() -> Data { JSON.object(self).data() }
    var json: JSON { .object(self) }
}
