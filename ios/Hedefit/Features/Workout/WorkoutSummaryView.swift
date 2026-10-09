import SwiftUI

struct WorkoutSummaryView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let summary: WorkoutSummaryData
    @State private var shareImage: UIImage?
    @State private var template = 0
    @State private var offerCardio = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(tr("Antrenman özeti", "Workout summary")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime)
                Text(summary.title).font(.system(size: 28, weight: .black)).foregroundStyle(HC.text)
                HStack(spacing: 10) {
                    HfStatTile(label: tr("Süre", "Duration"), value: duration(summary.durationSeconds))
                    HfStatTile(label: tr("Enerji", "Energy"), value: "\(summary.calories) kcal")
                }
                HStack(spacing: 10) {
                    HfStatTile(label: tr("Set", "Sets"), value: "\(summary.setCount)", sub: tr("\(summary.exerciseCount) hareket", "\(summary.exerciseCount) exercises"))
                    HfStatTile(label: tr("Hacim", "Volume"), value: compactKg(summary.volumeKg), sub: tr("\(summary.repetitions) tekrar", "\(summary.repetitions) reps"))
                }
                if !summary.personalRecords.isEmpty {
                    HfCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Label(tr("Kişisel rekorlar", "Personal records"), systemImage: "trophy.fill").font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.lime)
                            ForEach(summary.personalRecords, id: \.exerciseName) { pr in
                                HStack { Text(pr.exerciseName).foregroundStyle(HC.text); Spacer(); Text(tr("1TM ≈ ", "e1RM ≈ ") + Units.formatWeight(pr.estimatedOneRepMax, app.units, decimals: 0)).foregroundStyle(HC.textSecondary) }.font(.hfBody)
                            }
                        }
                    }
                }
                if let strongest = summary.strongestExercise { HfCard { HStack { Image(systemName: "bolt.fill").foregroundStyle(HC.warning); Text(tr("En güçlü hareket: ", "Strongest exercise: ") + strongest).foregroundStyle(HC.text).font(.hfBody); Spacer() } } }
                Picker("", selection: $template) { Text(tr("Özet", "Summary")).tag(0); Text(tr("Rekor", "Record")).tag(1); Text(tr("Kaslar", "Muscles")).tag(2) }.pickerStyle(.segmented)
                ShareCardPreview(summary: summary, template: template).frame(height: 260).clipShape(RoundedRectangle(cornerRadius: 18)).overlay(RoundedRectangle(cornerRadius: 18).stroke(HC.divider))
                ShareLink(item: Image(uiImage: ShareCardRenderer.render(summary: summary, template: template)), preview: SharePreview(summary.title, image: Image(uiImage: ShareCardRenderer.render(summary: summary, template: template)))) {
                    Label(tr("Paylaş", "Share"), systemImage: "square.and.arrow.up").font(.system(size: 15, weight: .heavy)).foregroundStyle(HC.text).frame(maxWidth: .infinity, minHeight: 52).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                HfButton(title: tr("Tamam", "Done")) { done() }
            }.padding(20)
        }.background(HC.bg.ignoresSafeArea())
        .alert(tr("Kardiyoyla bitir?", "Finish with cardio?"), isPresented: $offerCardio) {
            Button(tr("Kardiyoya geç", "Go to cardio")) { dismiss(); app.push(.cardio) }
            Button(tr("Bugün değil", "Not today"), role: .cancel) { dismiss() }
        } message: { Text(tr("Kuvvet antrenmanından sonra 10 dakikalık hafif kardiyo toparlanmaya yardımcı olur.", "A light 10-minute cardio session after strength training helps recovery.")) }
    }

    private func done() {
        app.workout.summary = nil
        AppReview.maybeRequest(completedWorkouts: app.dashboard?.sessions.count ?? 0)
        if app.prefs.cardioAfterStrength { offerCardio = true; app.workout.summary = summary } else { dismiss() }
    }

    private func duration(_ s: Int) -> String { s >= 3600 ? String(format: "%d sa %02d dk", s / 3600, s / 60 % 60) : String(format: "%d dk %02d sn", s / 60, s % 60) }
}

import StoreKit

enum AppReview {
    /// 3. tamamlanan antrenmanda, sonra her 10'da bir ve en sık 60 günde bir.
    @MainActor static func maybeRequest(completedWorkouts: Int) {
        let d = UserDefaults.standard
        let lastAt = d.double(forKey: "review.lastAt"), lastWorkouts = d.object(forKey: "review.lastWorkouts") as? Int ?? -1
        let dueByCount = lastWorkouts < 0 ? completedWorkouts >= 3 : completedWorkouts >= lastWorkouts + 10
        let dueByTime = Date().timeIntervalSince1970 - lastAt >= 60 * 86_400
        guard dueByCount, dueByTime, let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        d.set(Date().timeIntervalSince1970, forKey: "review.lastAt"); d.set(completedWorkouts, forKey: "review.lastWorkouts")
        SKStoreReviewController.requestReview(in: scene)
    }
}

// MARK: - Paylaşım kartı

struct ShareCardPreview: View {
    let summary: WorkoutSummaryData
    let template: Int
    var body: some View { Image(uiImage: ShareCardRenderer.render(summary: summary, template: template)).resizable().scaledToFill() }
}

@MainActor
enum ShareCardRenderer {
    static func render(summary: WorkoutSummaryData, template: Int) -> UIImage {
        let view = ShareCard(summary: summary, template: template).frame(width: 1080 / 3, height: 1920 / 3)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 3
        return renderer.uiImage ?? UIImage()
    }
}

struct ShareCard: View {
    let summary: WorkoutSummaryData
    let template: Int
    private let lime = Color(red: 0.71, green: 1, blue: 0.17)
    var body: some View {
        ZStack(alignment: .topLeading) {
            Color(red: 0.03, green: 0.04, blue: 0.035)
            VStack(alignment: .leading, spacing: 14) {
                Text("HEDEFİT").font(.system(size: 20, weight: .black)).foregroundStyle(lime)
                Spacer(minLength: 10)
                switch template {
                case 1:
                    let pr = summary.personalRecords.first
                    Text(tr("YENİ REKOR", "NEW RECORD")).font(.system(size: 24, weight: .black)).foregroundStyle(lime)
                    Text((pr?.exerciseName ?? summary.strongestExercise ?? summary.title).uppercased()).font(.system(size: 38, weight: .black)).foregroundStyle(.white).minimumScaleFactor(0.5)
                    Text(pr.map { String(format: "1TM ≈ %.0f kg", $0.estimatedOneRepMax) } ?? "\(summary.setCount) SET").font(.system(size: 30, weight: .heavy)).foregroundStyle(lime)
                case 2:
                    Text(tr("ÇALIŞAN KASLAR", "MUSCLES WORKED")).font(.system(size: 24, weight: .black)).foregroundStyle(lime)
                    let muscles = Array(Set(summary.sets.map { normalizeMuscle(summary.exerciseAreas[$0.exerciseId].flatMap { $0.isEmpty ? nil : $0 } ?? $0.exerciseName) }))
                    Text(muscles.map { muscleNameEn($0).uppercased() }.joined(separator: " • ")).font(.system(size: 22, weight: .heavy)).foregroundStyle(.white)
                default:
                    Text(summary.title.uppercased()).font(.system(size: 34, weight: .black)).foregroundStyle(.white).lineLimit(3).minimumScaleFactor(0.5)
                    Text("\(summary.setCount) SET").font(.system(size: 40, weight: .black)).foregroundStyle(lime)
                    Text(compactKg(summary.volumeKg).uppercased()).font(.system(size: 30, weight: .black)).foregroundStyle(.white)
                    Text(String(format: "%d:%02d", summary.durationSeconds / 60, summary.durationSeconds % 60)).font(.system(size: 30, weight: .black)).foregroundStyle(.white)
                }
                Spacer()
                Text("\(summary.exerciseCount) \(tr("hareket", "exercises")) • \(summary.repetitions) \(tr("tekrar", "reps")) • \(summary.calories) kcal").font(.system(size: 14, weight: .semibold)).foregroundStyle(Color(white: 0.62))
            }.padding(28)
        }
    }
}
