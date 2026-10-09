import Foundation
import WidgetKit

/// Ana uygulama ↔ widget / Watch ortak veri (App Group).
@MainActor
enum WidgetBridge {
    static var defaults: UserDefaults? { UserDefaults(suiteName: AppConfiguration.appGroup) }

    static func update(_ app: AppModel) {
        guard let d = app.dashboard, let defaults else { return }
        let workout = d.workoutPrograms.first(where: { $0.isActive })
        defaults.set(d.steps, forKey: "steps")
        defaults.set(app.prefs.stepGoal, forKey: "stepGoal")
        defaults.set(d.waterMl, forKey: "water")
        defaults.set(app.prefs.waterGoalMl, forKey: "waterGoal")
        defaults.set(d.activeCalories, forKey: "calories")
        defaults.set(d.nutritionLogs.reduce(0) { $0 + $1.calories }, forKey: "eaten")
        defaults.set(d.nutritionGoal.calories, forKey: "calorieGoal")
        defaults.set(workout.map { localizedProgramName($0.name, source: $0.source) } ?? tr("Antrenman", "Workout"), forKey: "workout")
        defaults.set(d.workouts.count, forKey: "workoutCount")
        defaults.set(d.streakDays, forKey: "streak")
        defaults.set(Dates.day(), forKey: "updatedDay")
        WidgetCenter.shared.reloadAllTimelines()
        WatchBridge.shared.push(app)
    }

    static func reset() {
        guard let defaults else { return }
        ["steps", "water", "calories", "eaten", "workoutCount", "streak"].forEach { defaults.set(0, forKey: $0) }
        WidgetCenter.shared.reloadAllTimelines()
    }
}
