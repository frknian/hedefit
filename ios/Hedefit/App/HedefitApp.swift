import SwiftUI
import GoogleSignIn

@main
struct HedefitApp: App {
    @State private var app = AppModel.shared
    @State private var theme = Theme.shared
    @State private var lang = AppLang.shared
    @Environment(\.scenePhase) private var scenePhase

    init() {
        NotificationService.shared.configure()
        WatchBridge.shared.activate()
        WatchBridge.shared.onWater = { ml in Task { await AppModel.shared.addWater(ml) } }
        WatchBridge.shared.onWorkout = { kind, minutes, distance in Task { await AppModel.shared.recordWatchWorkout(kind: kind, minutes: minutes, distance: distance) } }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app).environment(theme).environment(lang)
                .preferredColorScheme(theme.preferredScheme)
                .tint(HC.lime)
                .task {
                    await app.bootstrap()
                    #if DEBUG
                    DebugOpen.apply(app)
                    #endif
                }
                .onOpenURL { url in
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    DeepLinks.handle(url, app: app)
                }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active, app.phase == .signedIn { Task { await app.refreshOnForeground() } }
                }
        }
    }
}

enum DeepLinks {
    @MainActor static func handle(_ url: URL, app: AppModel) {
        switch url.host {
        case "home": app.select(.home)
        case "workout": app.select(.explore)
        case "nutrition": app.select(.nutrition)
        case "coach": app.select(.coach)
        case "progress": app.select(.progress)
        case "route": app.select(.home); app.push(.route)
        case "activity": app.select(.home); app.push(.manualActivity)
        default: app.select(.home)
        }
    }
}
