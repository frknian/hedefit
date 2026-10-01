import SwiftUI

struct RootView: View {
    @Environment(WorkoutManager.self) private var workout

    var body: some View {
        if workout.running {
            ActiveWorkoutView()
        } else {
            TabView {
                SummaryView()
                WaterView()
                StartWorkoutView()
            }
            .tabViewStyle(.verticalPage)
        }
    }
}

/// Üç halka: adım, su, protein.
struct SummaryView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        let s = model.snapshot
        ZStack {
            Ring(progress: ratio(s.steps, s.stepGoal), color: .hedefitGreen, diameter: 150)
            Ring(progress: ratio(s.waterMl, s.waterGoal), color: .hedefitWater, diameter: 122)
            Ring(progress: ratio(s.proteinG, s.proteinGoal), color: .hedefitCoral, diameter: 94)
            VStack(spacing: 0) {
                Text(s.steps.formatted()).font(.system(size: 22, weight: .semibold, design: .rounded)).minimumScaleFactor(0.6)
                Text("adım").font(.caption2).foregroundStyle(.secondary)
                if s.streakDays > 0 { Label("\(s.streakDays) gün", systemImage: "flame.fill").font(.caption2).foregroundStyle(Color.hedefitAmber) }
            }
        }
        .padding(4)
    }

    private func ratio(_ v: Int, _ goal: Int) -> Double { goal > 0 ? min(1, Double(v) / Double(goal)) : 0 }
}

struct Ring: View {
    let progress: Double, color: Color, diameter: CGFloat
    var body: some View {
        ZStack {
            Circle().stroke(Color.hedefitSurface, lineWidth: 9)
            Circle().trim(from: 0, to: progress).stroke(color, style: StrokeStyle(lineWidth: 9, lineCap: .round)).rotationEffect(.degrees(-90))
        }
        .frame(width: diameter, height: diameter)
        .animation(.easeOut(duration: 0.5), value: progress)
    }
}

/// Taç ile miktar seç, tek dokunuşla ekle.
struct WaterView: View {
    @Environment(WatchModel.self) private var model
    @State private var amount = 250.0
    @State private var flash = false

    var body: some View {
        let s = model.snapshot
        VStack(spacing: 6) {
            Label("Su", systemImage: "drop.fill").font(.caption).foregroundStyle(Color.hedefitWater)
            Text(String(format: "%.1f L", Double(s.waterMl) / 1000)).font(.system(size: 34, weight: .semibold, design: .rounded)).foregroundStyle(Color.hedefitWater)
            Text("hedef \(String(format: "%.1f", Double(s.waterGoal) / 1000)) L").font(.caption2).foregroundStyle(.secondary)
            Button {
                model.addWater(Int(amount))
                WKInterfaceDevice.current().play(.success)
                flash = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { flash = false }
            } label: { Text(flash ? "Eklendi" : "+\(Int(amount)) ml").fontWeight(.semibold) }
            .tint(.hedefitWater)
        }
        .focusable()
        .digitalCrownRotation($amount, from: 50, through: 1000, by: 50, sensitivity: .low)
    }
}

struct StartWorkoutView: View {
    @Environment(WatchModel.self) private var model
    @Environment(WorkoutManager.self) private var workout

    var body: some View {
        List {
            Section(model.snapshot.workoutName) {
                ForEach(WorkoutManager.kinds) { kind in
                    Button { Task { await workout.start(kind) } } label: { Label(kind.title, systemImage: kind.icon) }
                }
            }
        }
        .listStyle(.carousel)
    }
}
