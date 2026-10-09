import WidgetKit
import SwiftUI

// MARK: - Veri (ana uygulama WidgetBridge ile App Group'a yazar)

struct HedefitEntry: TimelineEntry {
    let date: Date
    var steps = 0, stepGoal = 8000, water = 0, waterGoal = 2500, calories = 0, eaten = 0, calorieGoal = 2000, workoutCount = 0, streak = 0
    var workout = "Antrenman"

    static let sample = HedefitEntry(date: Date(), steps: 6432, stepGoal: 8000, water: 1250, waterGoal: 2500, calories: 384, eaten: 1420, calorieGoal: 2100, workoutCount: 6, streak: 5, workout: "Upper / Lower")
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> HedefitEntry { .sample }
    func getSnapshot(in context: Context, completion: @escaping (HedefitEntry) -> Void) { completion(context.isPreview ? .sample : entry()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<HedefitEntry>) -> Void) {
        completion(Timeline(entries: [entry()], policy: .after(Date().addingTimeInterval(900))))
    }
    private func entry() -> HedefitEntry {
        let d = UserDefaults(suiteName: "group.com.hedefit.app")
        // Gün değiştiyse eski sayılar yanıltmasın.
        let today = { () -> String in let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: Date()) }()
        let fresh = d?.string(forKey: "updatedDay") == today
        func int(_ k: String, _ fallback: Int = 0) -> Int { (d?.object(forKey: k) as? Int) ?? fallback }
        return HedefitEntry(date: Date(), steps: fresh ? int("steps") : 0, stepGoal: max(int("stepGoal", 8000), 1), water: fresh ? int("water") : 0, waterGoal: max(int("waterGoal", 2500), 1),
                            calories: fresh ? int("calories") : 0, eaten: fresh ? int("eaten") : 0, calorieGoal: max(int("calorieGoal", 2000), 1), workoutCount: int("workoutCount"), streak: int("streak"),
                            workout: d?.string(forKey: "workout") ?? "Antrenman")
    }
}

// MARK: - Tasarım

private let bg = Color(red: 0.035, green: 0.04, blue: 0.047)
private let surface = Color(red: 0.07, green: 0.082, blue: 0.106)
private let lime = Color(red: 0.47, green: 0.78, blue: 0.40)
private let water = Color(red: 0.22, green: 0.74, blue: 0.97)
private let coral = Color(red: 1, green: 0.42, blue: 0.42)
private let amber = Color(red: 0.98, green: 0.75, blue: 0.14)

private func ratio(_ v: Int, _ goal: Int) -> Double { goal > 0 ? min(Double(v) / Double(goal), 1) : 0 }

private struct Ring: View {
    var progress: Double, color: Color, width: CGFloat = 8
    var body: some View {
        ZStack {
            Circle().stroke(color.opacity(0.18), lineWidth: width)
            Circle().trim(from: 0, to: max(progress, 0.001)).stroke(color, style: StrokeStyle(lineWidth: width, lineCap: .round)).rotationEffect(.degrees(-90))
        }
    }
}

private struct Stat: View {
    let icon: String, value: String, label: String, color: Color
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Image(systemName: icon).font(.system(size: 13, weight: .semibold)).foregroundStyle(color)
            Text(value).font(.system(size: 17, weight: .bold, design: .rounded)).foregroundStyle(.white).minimumScaleFactor(0.6).lineLimit(1)
            Text(label).font(.system(size: 10)).foregroundStyle(.white.opacity(0.55))
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

private extension View {
    func widgetCard() -> some View { containerBackground(bg, for: .widget) }
}

// MARK: - Bugün (small / medium / large)

struct DashboardWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitDashboard", provider: Provider()) { e in DashboardView(entry: e).widgetURL(URL(string: "hedefit://home")).widgetCard() }
            .configurationDisplayName("Hedefit • Bugün").description("Adım, su ve kalori durumunu gösterir.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

private struct DashboardView: View {
    @Environment(\.widgetFamily) private var family
    let entry: HedefitEntry
    var body: some View {
        switch family {
        case .systemSmall:
            VStack(spacing: 6) {
                HStack { Text("HEDEFİT").font(.system(size: 10, weight: .heavy)).foregroundStyle(lime); Spacer(); if entry.streak > 0 { Label("\(entry.streak)", systemImage: "flame.fill").font(.system(size: 10, weight: .bold)).foregroundStyle(amber) } }
                ZStack {
                    Ring(progress: ratio(entry.steps, entry.stepGoal), color: lime, width: 9)
                    VStack(spacing: 0) { Text(entry.steps.formatted()).font(.system(size: 20, weight: .heavy, design: .rounded)).foregroundStyle(.white).minimumScaleFactor(0.6); Text("adım").font(.system(size: 10)).foregroundStyle(.white.opacity(0.55)) }
                }.padding(.horizontal, 6)
            }
        case .systemMedium:
            HStack(spacing: 14) {
                ZStack {
                    Ring(progress: ratio(entry.steps, entry.stepGoal), color: lime, width: 11)
                    Ring(progress: ratio(entry.water, entry.waterGoal), color: water, width: 9).padding(14)
                    Ring(progress: ratio(entry.eaten, entry.calorieGoal), color: coral, width: 7).padding(26)
                }.frame(width: 118, height: 118)
                VStack(alignment: .leading, spacing: 10) {
                    HStack { Text("HEDEFİT").font(.system(size: 11, weight: .heavy)).foregroundStyle(lime); Spacer(); if entry.streak > 0 { Label("\(entry.streak) gün", systemImage: "flame.fill").font(.system(size: 11, weight: .bold)).foregroundStyle(amber) } }
                    HStack(spacing: 8) {
                        Stat(icon: "shoeprints.fill", value: entry.steps.formatted(), label: "adım", color: lime)
                        Stat(icon: "drop.fill", value: "\(entry.water)", label: "ml su", color: water)
                        Stat(icon: "flame.fill", value: "\(entry.calories)", label: "aktif kcal", color: coral)
                    }
                }
            }
        default:
            VStack(alignment: .leading, spacing: 14) {
                HStack { Text("HEDEFİT").font(.system(size: 12, weight: .heavy)).foregroundStyle(lime); Spacer(); if entry.streak > 0 { Label("\(entry.streak) günlük seri", systemImage: "flame.fill").font(.system(size: 12, weight: .bold)).foregroundStyle(amber) } }
                HStack(spacing: 18) {
                    ZStack { Ring(progress: ratio(entry.steps, entry.stepGoal), color: lime, width: 12)
                        VStack(spacing: 0) { Text(entry.steps.formatted()).font(.system(size: 26, weight: .heavy, design: .rounded)).foregroundStyle(.white); Text("/ \(entry.stepGoal.formatted()) adım").font(.system(size: 10)).foregroundStyle(.white.opacity(0.55)) } }.frame(width: 140, height: 140)
                    VStack(alignment: .leading, spacing: 14) {
                        bar("Su", "\(entry.water) / \(entry.waterGoal) ml", ratio(entry.water, entry.waterGoal), water)
                        bar("Yenen", "\(entry.eaten) / \(entry.calorieGoal) kcal", ratio(entry.eaten, entry.calorieGoal), coral)
                        bar("Aktif", "\(entry.calories) kcal", min(Double(entry.calories) / 500, 1), amber)
                    }
                }
                HStack(spacing: 10) { Image(systemName: "dumbbell.fill").foregroundStyle(lime); VStack(alignment: .leading, spacing: 0) { Text(entry.workout).font(.system(size: 15, weight: .bold)).foregroundStyle(.white).lineLimit(1); Text("\(entry.workoutCount) hareket • Antrenmanı aç").font(.system(size: 11)).foregroundStyle(.white.opacity(0.55)) }; Spacer(); Image(systemName: "chevron.right").foregroundStyle(lime) }
                    .padding(12).background(surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).widgetURL(URL(string: "hedefit://workout"))
                Spacer(minLength: 0)
            }
        }
    }
    private func bar(_ title: String, _ value: String, _ p: Double, _ c: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack { Text(title).font(.system(size: 11, weight: .semibold)).foregroundStyle(.white.opacity(0.7)); Spacer(); Text(value).font(.system(size: 11, weight: .bold)).foregroundStyle(.white) }
            ProgressView(value: p).tint(c)
        }
    }
}

// MARK: - Antrenman

struct WorkoutWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitWorkout", provider: Provider()) { e in
            HStack(spacing: 12) {
                Image(systemName: "dumbbell.fill").font(.system(size: 26)).foregroundStyle(.black).frame(width: 52, height: 52).background(lime, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                VStack(alignment: .leading, spacing: 2) { Text("BUGÜNÜN ANTRENMANI").font(.system(size: 10, weight: .heavy)).foregroundStyle(lime); Text(e.workout).font(.system(size: 17, weight: .bold)).foregroundStyle(.white).lineLimit(1); Text("\(e.workoutCount) hareket").font(.system(size: 12)).foregroundStyle(.white.opacity(0.6)) }
                Spacer(); Image(systemName: "play.circle.fill").font(.system(size: 30)).foregroundStyle(lime)
            }.widgetURL(URL(string: "hedefit://workout")).widgetCard()
        }.configurationDisplayName("Hedefit • Antrenman").description("Aktif antrenmanına hızlıca ulaş.").supportedFamilies([.systemMedium])
    }
}

// MARK: - Kalori

struct CaloriesWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitCalories", provider: Provider()) { e in
            ZStack {
                Ring(progress: ratio(e.eaten, e.calorieGoal), color: coral, width: 10)
                VStack(spacing: 0) { Text("\(max(e.calorieGoal - e.eaten, 0))").font(.system(size: 22, weight: .heavy, design: .rounded)).foregroundStyle(.white).minimumScaleFactor(0.6); Text("kcal kaldı").font(.system(size: 10)).foregroundStyle(.white.opacity(0.55)) }
            }.padding(8).widgetURL(URL(string: "hedefit://nutrition")).widgetCard()
        }.configurationDisplayName("Hedefit • Kalori").description("Günlük kalori durumun.").supportedFamilies([.systemSmall])
    }
}

// MARK: - Aktivite (adım + su kısayolu)

struct ActivityWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitActivity", provider: Provider()) { e in
            HStack(spacing: 14) {
                ZStack { Ring(progress: ratio(e.steps, e.stepGoal), color: lime, width: 9); Image(systemName: "figure.walk").foregroundStyle(lime) }.frame(width: 76, height: 76)
                VStack(alignment: .leading, spacing: 8) {
                    Stat(icon: "shoeprints.fill", value: "\(e.steps.formatted()) / \(e.stepGoal.formatted())", label: "adım", color: lime)
                    HStack(spacing: 12) { Link(destination: URL(string: "hedefit://activity")!) { Label("Aktivite", systemImage: "plus").font(.system(size: 11, weight: .bold)).foregroundStyle(.black).padding(.horizontal, 10).padding(.vertical, 6).background(lime, in: Capsule()) }
                        Link(destination: URL(string: "hedefit://route")!) { Label("Rota", systemImage: "location.fill").font(.system(size: 11, weight: .bold)).foregroundStyle(.white).padding(.horizontal, 10).padding(.vertical, 6).background(surface, in: Capsule()) } }
                }
            }.widgetCard()
        }.configurationDisplayName("Hedefit • Aktivite").description("Adım ve hızlı aktivite/rota kısayolları.").supportedFamilies([.systemMedium])
    }
}

// MARK: - Kilit ekranı

struct LockScreenWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HedefitLockScreen", provider: Provider()) { e in LockView(entry: e).widgetURL(URL(string: "hedefit://home")).containerBackground(.clear, for: .widget) }
            .configurationDisplayName("Hedefit • Kilit ekranı").description("Adım halkası ve günün özeti.")
            .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

private struct LockView: View {
    @Environment(\.widgetFamily) private var family
    let entry: HedefitEntry
    var body: some View {
        switch family {
        case .accessoryCircular:
            Gauge(value: ratio(entry.steps, entry.stepGoal)) { Image(systemName: "shoeprints.fill") } currentValueLabel: { Text(entry.steps >= 1000 ? "\(entry.steps / 1000).\(entry.steps % 1000 / 100)k" : "\(entry.steps)").font(.system(size: 13, weight: .bold)) }.gaugeStyle(.accessoryCircular)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Text("Hedefit").font(.system(size: 12, weight: .bold))
                Label("\(entry.steps.formatted()) adım", systemImage: "shoeprints.fill").font(.system(size: 12))
                Label("\(entry.water) ml • \(entry.calories) kcal", systemImage: "drop.fill").font(.system(size: 12))
            }
        default:
            Text("Hedefit • \(entry.steps.formatted()) adım • \(entry.water) ml")
        }
    }
}

@main struct HedefitWidgetBundle: WidgetBundle {
    var body: some Widget { DashboardWidget(); WorkoutWidget(); CaloriesWidget(); ActivityWidget(); LockScreenWidget() }
}
