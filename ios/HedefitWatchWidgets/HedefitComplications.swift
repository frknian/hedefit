import WidgetKit
import SwiftUI

struct ComplicationEntry: TimelineEntry {
    let date: Date
    let steps, stepGoal, water, waterGoal, streak: Int
    var english = false
}

struct ComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> ComplicationEntry { .init(date: Date(), steps: 7412, stepGoal: 10_000, water: 1250, waterGoal: 2500, streak: 12, english: false) }
    func getSnapshot(in context: Context, completion: @escaping (ComplicationEntry) -> Void) { completion(entry()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<ComplicationEntry>) -> Void) {
        completion(Timeline(entries: [entry()], policy: .after(Date().addingTimeInterval(900))))
    }

    /// Saat uygulaması her güncellemede bu paylaşılan alana yazar.
    private func entry() -> ComplicationEntry {
        let d = UserDefaults(suiteName: "group.com.hedefit.app")
        return .init(date: Date(), steps: d?.integer(forKey: "watch.steps") ?? 0, stepGoal: max(d?.integer(forKey: "watch.stepGoal") ?? 10_000, 1),
                     water: d?.integer(forKey: "watch.water") ?? 0, waterGoal: max(d?.integer(forKey: "watch.waterGoal") ?? 2_500, 1), streak: d?.integer(forKey: "watch.streak") ?? 0, english: (d?.string(forKey: "watch.lang") ?? "tr") != "tr")
    }
}

private let green = Color(red: 0.133, green: 0.773, blue: 0.369)
private let water = Color(red: 0.22, green: 0.74, blue: 0.97)
private let amber = Color(red: 0.98, green: 0.75, blue: 0.14)

private func fraction(_ v: Int, _ goal: Int) -> Double { min(1, Double(v) / Double(max(goal, 1))) }
private func short(_ n: Int) -> String { n >= 1000 ? String(format: "%.1fK", Double(n) / 1000) : "\(n)" }
private func liters(_ ml: Int) -> String { String(format: "%.1f", Double(ml) / 1000) }

struct StepsComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let e: ComplicationEntry
    var body: some View {
        switch family {
        case .accessoryCorner:
            Text(short(e.steps)).widgetCurvesContent().widgetLabel { ProgressView(value: fraction(e.steps, e.stepGoal)).tint(green) }
        case .accessoryInline:
            Label("\(e.steps.formatted()) " + (e.english ? "steps" : "adım"), systemImage: "figure.walk")
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Label("Hedefit", systemImage: "bolt.heart.fill").font(.caption2).foregroundStyle(green)
                HStack { Text("\(e.steps.formatted()) " + (e.english ? "steps" : "adım")).font(.headline); Spacer(); Label("\(e.streak)", systemImage: "flame.fill").font(.caption).foregroundStyle(amber) }
                ProgressView(value: fraction(e.steps, e.stepGoal)).tint(green)
                Label("\(liters(e.water)) / \(liters(e.waterGoal)) L " + (e.english ? "water" : "su"), systemImage: "drop.fill").font(.caption2).foregroundStyle(water)
            }
        default:
            Gauge(value: fraction(e.steps, e.stepGoal)) { Image(systemName: "figure.walk") } currentValueLabel: { Text(short(e.steps)) }
                .gaugeStyle(.accessoryCircular).tint(green)
        }
    }
}

struct WaterComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let e: ComplicationEntry
    var body: some View {
        switch family {
        case .accessoryCorner:
            Image(systemName: "drop.fill").widgetCurvesContent().foregroundStyle(water).widgetLabel { ProgressView(value: fraction(e.water, e.waterGoal)).tint(water) }
        case .accessoryInline:
            Label("\(liters(e.water)) L " + (e.english ? "water" : "su"), systemImage: "drop.fill")
        default:
            Gauge(value: fraction(e.water, e.waterGoal)) { Image(systemName: "drop.fill") } currentValueLabel: { Text(liters(e.water)) }
                .gaugeStyle(.accessoryCircular).tint(water)
        }
    }
}

struct StreakComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let e: ComplicationEntry
    var body: some View {
        switch family {
        case .accessoryInline: Label(e.english ? "\(e.streak)-day streak" : "\(e.streak) gün seri", systemImage: "flame.fill")
        case .accessoryCorner: Text("\(e.streak)").widgetCurvesContent().foregroundStyle(amber).widgetLabel(e.english ? "day streak" : "gün seri")
        default:
            ZStack { AccessoryWidgetBackground(); VStack(spacing: 0) { Image(systemName: "flame.fill").foregroundStyle(amber); Text("\(e.streak)").font(.headline) } }
        }
    }
}

struct StepsComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitWatchSteps", provider: ComplicationProvider()) { StepsComplicationView(e: $0).widgetURL(URL(string: "hedefit-watch://summary")).containerBackground(.clear, for: .widget) }
            .configurationDisplayName("Adım").description("Günlük adım hedefin ve özet.")
            .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryRectangular, .accessoryInline])
    }
}
struct WaterComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitWatchWater", provider: ComplicationProvider()) { WaterComplicationView(e: $0).widgetURL(URL(string: "hedefit-watch://water")).containerBackground(.clear, for: .widget) }
            .configurationDisplayName("Su").description("Günlük su hedefin.")
            .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryInline])
    }
}
struct StreakComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitWatchStreak", provider: ComplicationProvider()) { StreakComplicationView(e: $0).widgetURL(URL(string: "hedefit-watch://social")).containerBackground(.clear, for: .widget) }
            .configurationDisplayName("Seri").description("Günlük seri sayın.")
            .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryInline])
    }
}

@main struct HedefitWatchWidgetBundle: WidgetBundle {
    var body: some Widget { StepsComplication(); WaterComplication(); StreakComplication() }
}
