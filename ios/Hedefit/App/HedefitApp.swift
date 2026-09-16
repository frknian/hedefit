import SwiftUI
import BackgroundTasks

@main
struct HedefitApp: App {
    @State private var store = AppStore()

    init() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: "com.hedefit.app.sync", using: nil) { task in
            Task { await OfflineQueue.shared.flush(); task.setTaskCompleted(success: true) }
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .tint(Color.hedefitGreen)
                // SF Pro, iOS'un yerel ve tüm Dynamic Type boyutlarıyla uyumlu
                // arayüz yazı ailesidir; bütün sekmeler aynı aileyi miras alır.
                .fontDesign(.default)
                .background(Color.hedefitBackground.ignoresSafeArea())
                .preferredColorScheme(store.settings.darkMode ? .dark : .light)
                .task { await store.bootstrap() }
                .onOpenURL { store.handleDeepLink($0) }
        }
    }
}

extension Color {
    static let hedefitGreen = Color(red: 61/255, green: 220/255, blue: 132/255)
    static let hedefitBlue = Color(red: 90/255, green: 169/255, blue: 1)
    static let hedefitPurple = Color(red: 0.45, green: 0.35, blue: 0.96)
    static let hedefitOrange = Color(red: 1.0, green: 0.55, blue: 0.18)
    static let hedefitBackground = Color(red: 10/255, green: 11/255, blue: 13/255)
    static let hedefitSurface = Color(red: 19/255, green: 22/255, blue: 26/255)
    static let hedefitSurfaceHigh = Color(red: 27/255, green: 31/255, blue: 37/255)
    static let hedefitMuted = Color(red: 102/255, green: 110/255, blue: 121/255)
    static let panel = Color.hedefitSurface
}
