import SwiftUI
import WatchKit

struct ActiveWorkoutView: View {
    @Environment(WatchModel.self) private var model
    @Environment(WorkoutManager.self) private var workout
    @State private var restLeft = 0
    @State private var confirmEnd = false
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        Group {
            if workout.kind.id == "strength" { StrengthView(restLeft: $restLeft) } else { cardio }
        }
        .onReceive(tick) { _ in
            guard restLeft > 0 else { return }
            restLeft -= 1
            if restLeft == 0 { WKInterfaceDevice.current().play(.notification) }
        }
        .confirmationDialog(model.t("Antrenman bitsin mi?", "End workout?"), isPresented: $confirmEnd) {
            Button(model.t("Bitir ve kaydet", "Finish and save")) { Task { await workout.finish() } }
            Button(model.t("Devam et", "Keep going"), role: .cancel) {}
        }
        .toolbar { ToolbarItemGroup(placement: .bottomBar) {
            Button { workout.togglePause() } label: { Image(systemName: workout.paused ? "play.fill" : "pause.fill") }
            Button { confirmEnd = true } label: { Image(systemName: "stop.fill") }.tint(.hedefitCoral)
        } }
    }

    private var cardio: some View {
        VStack(spacing: 2) {
            Text(model.t(workout.kind.title, workout.kind.titleEn)).font(.caption).foregroundStyle(Color.hedefitGreen)
            Text(String(format: "%.2f km", workout.distance / 1000)).font(.system(size: 34, weight: .semibold, design: .rounded)).minimumScaleFactor(0.6)
            HStack(spacing: 10) {
                Text(workout.paceSecondsPerKm > 0 ? "\(clock(workout.paceSecondsPerKm)) /km" : "--:-- /km")
                Text(clock(workout.elapsed)).monospacedDigit()
            }.font(.footnote)
            Label("\(Int(workout.heartRate))", systemImage: "heart.fill").font(.footnote).foregroundStyle(Color.hedefitCoral)
        }
    }
}

/// Ağırlık: telefondaki programdan egzersiz, Digital Crown ile kg, +/- ile tekrar. Program yoksa yalnızca set sayacı.
struct StrengthView: View {
    @Environment(WatchModel.self) private var model
    @Environment(WorkoutManager.self) private var workout
    @Binding var restLeft: Int
    @State private var index = 0
    @State private var kg = 20.0
    @State private var reps = 10

    private var plan: [WatchExercise] { model.snapshot.exercises }
    private var current: WatchExercise? { plan.isEmpty ? nil : plan[min(index, plan.count - 1)] }
    private func done(_ ex: WatchExercise) -> [WatchSetLog] { workout.sets.filter { $0.exId == ex.id } }

    var body: some View {
        if restLeft > 0 {
            VStack(spacing: 4) {
                Text(model.t("Dinlenme", "Rest")).font(.caption).foregroundStyle(Color.hedefitAmber)
                Text(clock(restLeft)).font(.system(size: 44, weight: .semibold, design: .rounded)).foregroundStyle(Color.hedefitAmber).monospacedDigit()
                HStack {
                    Button("+15 " + model.t("sn", "s")) { restLeft += 15 }
                    Button(model.t("Atla", "Skip")) { restLeft = 0 }
                }.font(.footnote)
            }
        } else if let ex = current {
            let finished = done(ex).count
            VStack(spacing: 3) {
                Button { if plan.count > 1 { index = (index + 1) % plan.count; load() } } label: { Text(ex.name).font(.caption).foregroundStyle(Color.hedefitGreen).lineLimit(1) }.buttonStyle(.plain)
                Text("Set \(finished + 1)/\(ex.sets)  ♥ \(Int(workout.heartRate))").font(.caption2).foregroundStyle(.secondary)
                Text(String(format: "%.1f kg", kg)).font(.system(size: 22, weight: .semibold, design: .rounded))
                    .focusable()
                    .digitalCrownRotation($kg, from: 0, through: 500, by: 2.5, sensitivity: .medium)
                Stepper(value: $reps, in: 1...100) { Text("\(reps) " + model.t("tekrar", "reps")).font(.footnote) }
                Button(model.t("Seti bitir", "Finish set")) {
                    workout.sets.append(WatchSetLog(exId: ex.id, exName: ex.name, order: index, setNo: finished + 1, kg: kg, reps: reps))
                    restLeft = ex.rest
                    WKInterfaceDevice.current().play(.click)
                    // Planlanan setler bitince sıradaki egzersize geç.
                    if finished + 1 >= ex.sets, index < plan.count - 1 { index += 1 }
                    load()
                }.tint(.hedefitGreen)
            }
            .onAppear { load() }
        } else {
            VStack(spacing: 4) {
                Text("Set \(workout.sets.count + 1)").font(.caption).foregroundStyle(Color.hedefitGreen)
                Label("\(Int(workout.heartRate))", systemImage: "heart.fill").foregroundStyle(Color.hedefitCoral)
                Text(clock(workout.elapsed)).font(.title3).monospacedDigit()
                Button(model.t("Seti bitir", "Finish set")) {
                    workout.sets.append(WatchSetLog(exId: "free", exName: model.t("Antrenman", "Workout"), order: 0, setNo: workout.sets.count + 1, kg: 0, reps: 0))
                    restLeft = 90; WKInterfaceDevice.current().play(.click)
                }.tint(.hedefitGreen)
            }
        }
    }

    /// Seçili egzersiz için ağırlık ve tekrarı başlat: son kayıtlı set, yoksa plandaki değer.
    private func load() {
        guard let ex = current else { return }
        let last = done(ex).last
        kg = last?.kg ?? ex.kg ?? 20
        reps = last?.reps ?? ex.reps
    }
}
