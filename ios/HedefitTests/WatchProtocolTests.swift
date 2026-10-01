import XCTest

final class WatchProtocolTests: XCTestCase {
    func testClockFormatsMinutesAndHours() {
        XCTAssertEqual(clock(5), "0:05")
        XCTAssertEqual(clock(754), "12:34")
        XCTAssertEqual(clock(3661), "1:01:01")
        XCTAssertEqual(clock(-4), "0:00")
    }

    func testSnapshotDecodesWithMissingFieldsUsingDefaults() throws {
        let json = #"{"steps": 4200, "waterMl": 750}"#.data(using: .utf8)!
        let snapshot = try JSONDecoder().decode(WatchSnapshot.self, from: json)
        XCTAssertEqual(snapshot.steps, 4200)
        XCTAssertEqual(snapshot.stepGoal, 10_000)
        XCTAssertEqual(snapshot.lang, "tr")
        XCTAssertTrue(snapshot.exercises.isEmpty)
        XCTAssertFalse(snapshot.isEnglish)
    }

    func testSnapshotRoundTripKeepsExercisesAndSocial() throws {
        var snapshot = WatchSnapshot()
        snapshot.lang = "en"
        snapshot.exercises = [WatchExercise(id: "a", name: "Bench", sets: 4, reps: 8, rest: 120, kg: 60)]
        snapshot.social = WatchSocial(rank: 2, total: 5, xp: 340, leaders: [WatchLeader(name: "Ali", xp: 400, me: false)], challenge: "10K")
        let decoded = try JSONDecoder().decode(WatchSnapshot.self, from: JSONEncoder().encode(snapshot))
        XCTAssertEqual(decoded, snapshot)
        XCTAssertTrue(decoded.isEnglish)
    }

    func testWorkoutValidationRejectsShortUnknownOrAbsurdWorkouts() {
        var w = WatchWorkoutPayload(id: "1", kind: "running", durationSec: 1800)
        XCTAssertTrue(w.isValid)
        w.durationSec = 30; XCTAssertFalse(w.isValid)
        w.durationSec = 1_000_000; XCTAssertFalse(w.isValid)
        w = WatchWorkoutPayload(id: "1", kind: "skydiving", durationSec: 1800); XCTAssertFalse(w.isValid)
        w = WatchWorkoutPayload(id: "", kind: "running", durationSec: 1800); XCTAssertFalse(w.isValid)
    }

    func testValidSetsDropFreeModeAndInvalidReps() {
        let w = WatchWorkoutPayload(id: "1", kind: "strength", durationSec: 1800, sets: [
            WatchSetLog(exId: "a", exName: "Bench", order: 0, setNo: 1, kg: 60, reps: 8),
            WatchSetLog(exId: "a", exName: "Bench", order: 0, setNo: 2, kg: 60, reps: 0),
            WatchSetLog(exId: "free", exName: "Workout", order: 0, setNo: 3, kg: 0, reps: 10),
            WatchSetLog(exId: "a", exName: "Bench", order: 0, setNo: 4, kg: 5000, reps: 5),
        ])
        XCTAssertEqual(w.validSets().map(\.setNo), [1])
    }
}
