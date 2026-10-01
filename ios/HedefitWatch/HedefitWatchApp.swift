import SwiftUI

@main
struct HedefitWatchApp: App {
    @State private var model = WatchModel()
    @State private var workout = WorkoutManager()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(workout)
                .tint(.hedefitGreen)
        }
    }
}
