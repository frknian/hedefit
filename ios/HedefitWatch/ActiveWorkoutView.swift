import SwiftUI
import WatchKit

struct ActiveWorkoutView: View {
    @Environment(WorkoutManager.self) private var workout
    @State private var setCount = 1
    @State private var restLeft = 0
    @State private var confirmEnd = false
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    private let restSeconds = 90

    var body: some View {
        Group {
            if workout.kind.id == "strength" { strength } else { cardio }
        }
        .onReceive(tick) { _ in
            guard restLeft > 0 else { return }
            restLeft -= 1
            if restLeft == 0 { WKInterfaceDevice.current().play(.notification) }
        }
        .confirmationDialog("Antrenman bitsin mi?", isPresented: $confirmEnd) {
            Button("Bitir ve kaydet") { Task { await workout.finish() } }
            Button("Devam et", role: .cancel) {}
        }
    }

    private var cardio: some View {
        VStack(spacing: 2) {
            Text(workout.kind.title).font(.caption).foregroundStyle(Color.hedefitGreen)
            Text(String(format: "%.2f km", workout.distance / 1000)).font(.system(size: 34, weight: .semibold, design: .rounded)).minimumScaleFactor(0.6)
            HStack(spacing: 10) {
                Text(workout.paceSecondsPerKm > 0 ? "\(clock(workout.paceSecondsPerKm)) /km" : "--:-- /km")
                Text(clock(workout.elapsed)).monospacedDigit()
            }.font(.footnote)
            Label("\(Int(workout.heartRate))", systemImage: "heart.fill").font(.footnote).foregroundStyle(Color.hedefitCoral)
            controls
        }
    }

    private var strength: some View {
        VStack(spacing: 4) {
            if restLeft > 0 {
                Text("Dinlenme").font(.caption).foregroundStyle(Color.hedefitAmber)
                Text(clock(restLeft)).font(.system(size: 44, weight: .semibold, design: .rounded)).foregroundStyle(Color.hedefitAmber).monospacedDigit()
                HStack {
                    Button("+15 sn") { restLeft += 15 }
                    Button("Atla") { restLeft = 0 }
                }.font(.footnote)
            } else {
                Text("Set \(setCount)").font(.caption).foregroundStyle(Color.hedefitGreen)
                Label("\(Int(workout.heartRate))", systemImage: "heart.fill").foregroundStyle(Color.hedefitCoral)
                Text(clock(workout.elapsed)).font(.title3).monospacedDigit()
                Button("Seti bitir") { setCount += 1; restLeft = restSeconds; WKInterfaceDevice.current().play(.click) }.tint(.hedefitGreen)
            }
            controls
        }
    }

    private var controls: some View {
        HStack {
            Button { workout.togglePause() } label: { Image(systemName: workout.paused ? "play.fill" : "pause.fill") }
            Button { confirmEnd = true } label: { Image(systemName: "stop.fill") }.tint(.hedefitCoral)
        }.buttonStyle(.bordered).controlSize(.small)
    }
}
