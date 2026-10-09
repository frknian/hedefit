import Foundation
import Network

struct PendingOperation: Codable, Identifiable {
    var id: String
    var type: String // workout | nutrition | route
    var payload: String // JSON metni
}

/// Çevrimdışı kalan yazma işlemleri: cihazda saklanır, bağlantı gelince sırayla yeniden oynatılır.
actor OfflineQueue {
    static let shared = OfflineQueue()
    private var flushing = false
    private let monitor = NWPathMonitor()

    private var file: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("offline-queue.json")
    }

    private func read() -> [PendingOperation] { (try? JSONDecoder().decode([PendingOperation].self, from: Data(contentsOf: file))) ?? [] }
    private func write(_ ops: [PendingOperation]) { try? JSONEncoder().encode(ops).write(to: file, options: .atomic) }

    var count: Int { read().count }

    func enqueue(type: String, payload: JSON) {
        var ops = read()
        ops.append(PendingOperation(id: UUID().uuidString, type: type, payload: payload.text))
        write(ops)
    }

    func startMonitoring() {
        monitor.pathUpdateHandler = { path in
            if path.status == .satisfied { Task { await OfflineQueue.shared.flush() } }
        }
        monitor.start(queue: DispatchQueue(label: "hedefit.offline.monitor"))
    }

    /// Bekleyen işlemleri sırayla gönderir; ilk başarısızlıkta durur.
    func flush() async {
        guard !flushing, await AuthService.shared.userId != nil else { return }
        flushing = true; defer { flushing = false }
        let repo = HedefitRepository.shared
        for op in read() {
            let payload = JSON.parse(op.payload)
            do {
                switch op.type {
                case "workout": try await replayWorkout(repo, payload)
                case "nutrition": try await HedefitAPI.shared.post("/api/nutrition/logs", payload).requireSuccess("sync")
                case "route": try await repo.saveRoutePayload(payload)
                default: break
                }
                write(read().filter { $0.id != op.id })
            } catch { return }
        }
        await MainActor.run { AppModel.shared.offlinePending = 0 }
    }

    private func replayWorkout(_ repo: HedefitRepository, _ payload: JSON) async throws {
        let exercises = WorkoutExercise.list(payload["exercises"])
        let sets = payload["sets"].items.map { s in
            WorkoutSetInput(exerciseId: s.string("exerciseId"), exerciseName: s.string("exerciseName"), exerciseOrder: s.int("exerciseOrder"), setNumber: s.int("setNumber"), weightKg: s.doubleOrNil("weightKg"),
                            reps: s.intOrNil("reps"), durationSeconds: s.intOrNil("durationSeconds"), rpe: s.intOrNil("rpe"), setType: s.string("setType", "normal"), note: s.string("note"))
        }
        let f = payload["feedback"]
        let feedback = WorkoutFeedback(difficulty: f.string("difficulty", "Uygun"), fatigue: f.int("fatigue", 3), painAreas: f.strings("painAreas").isEmpty ? ["Yok"] : f.strings("painAreas"), note: f.string("note"))
        _ = try await repo.recordWorkout(exercises: exercises, sets: sets, durationSeconds: payload.int("durationSeconds"), calories: payload.int("calories"), feedback: feedback)
    }

    static func workoutPayload(exercises: [WorkoutExercise], sets: [WorkoutSetInput], durationSeconds: Int, calories: Int, feedback: WorkoutFeedback) -> JSON {
        ["exercises": WorkoutExercise.jsonArray(exercises),
         "sets": .array(sets.map { ["exerciseId": JSON($0.exerciseId), "exerciseName": JSON($0.exerciseName), "exerciseOrder": JSON($0.exerciseOrder), "setNumber": JSON($0.setNumber), "weightKg": .opt($0.weightKg), "reps": .opt($0.reps),
                                    "durationSeconds": .opt($0.durationSeconds), "rpe": .opt($0.rpe), "setType": JSON($0.setType), "note": JSON($0.note)] as JSON }),
         "durationSeconds": JSON(durationSeconds), "calories": JSON(calories),
         "feedback": ["difficulty": JSON(feedback.difficulty), "fatigue": JSON(feedback.fatigue), "painAreas": .from(feedback.painAreas), "note": JSON(feedback.note)]]
    }
}
