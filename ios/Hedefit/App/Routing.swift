import SwiftUI

/// Itme (push) sayfalarının hedefleri.
struct RouteDestination: View {
    @Environment(AppModel.self) private var app
    let route: Route

    var body: some View {
        switch route {
        case .programHub: ProgramHubView()
        case .quickWorkout: QuickWorkoutView()
        case .aiProgram: AIProgramView()
        case .readyPrograms: ReadyProgramsView()
        case .customProgram: CustomProgramView()
        case .regionalPrograms: RegionalProgramsView()
        case .challengeDetail(let id): ChallengeDetailView(challengeId: id, templateKey: nil)
        case .challengeTemplate(let key): ChallengeDetailView(challengeId: nil, templateKey: key)
        case .coachChallenge: CoachChallengeView()
        case .settings, .profile: SettingsView()
        case .legal(let key): if let d = LegalDocument(rawValue: key) { LegalSheet(document: d) }
        case .manualActivity: ManualActivityView()
        case .notifications: NotificationSettingsView()
        case .aiMemory: AiMemoryView()
        case .consents: ConsentSettingsView()
        case .healthPrivacy: HealthPrivacyView()
        case .questionnaire: QuestionnaireRoute()
        case .library: LibraryView()
        case .muscleMap: LibraryView(startWithMap: true)
        case .workoutHistory: MuscleAtlasView()
        case .bodyProfile: BodyProfileView()
        case .weightTracking: ProgressTabView()
        case .calendar: WorkoutCalendarView()
        case .cardio: CardioView()
        case .route, .routePlanner: RouteView()
        case .routeDetail(let id): RouteDetailView(id: id)
        case .goalJourney: GoalJourneyView()
        case .wearables: WearablesView()
        case .equipmentScanner: EquipmentScannerView()
        case .curlGame: CurlGameScreen()
        case .programs: ProgramHubView()
        case .rewards: RewardsView()
        case .friends, .challenges: SubPage(title: tr("Arkadaşlar", "Friends")) { CommunitySection() }
        default: Placeholder(title: "\(route)")
        }
    }
}

/// Gezinme çubuğu gizliyken de kenardan kaydırarak geri dönmeyi sağlar.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }
    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool { viewControllers.count > 1 }
}

#if DEBUG
/// QA için: `-HedefitOpen <rota>` ile doğrudan bir ekranı açar (ör. calendar, cardio, route).
enum DebugOpen {
    @MainActor static func apply(_ app: AppModel) {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-HedefitOpen"), args.indices.contains(i + 1) else { return }
        let key = args[i + 1]
        let map: [String: Route] = ["calendar": .calendar, "cardio": .cardio, "route": .route, "goal": .goalJourney, "library": .library, "atlas": .workoutHistory, "muscle": .muscleMap, "wearables": .wearables,
                                    "notifications": .notifications, "rewards": .rewards, "settings": .settings, "activity": .manualActivity, "questionnaire": .questionnaire, "game": .curlGame, "scanner": .equipmentScanner,
                                    "health": .healthPrivacy, "consents": .consents, "memory": .aiMemory, "friends": .friends, "body": .bodyProfile]
        if let r = map[key] { app.push(r) }
        else if key == "plans" { app.showPlans = true }
        else if key == "lock" { app.lock(.mealPlanner) }
        else if key == "save" { app.showSaveAccount = true }
    }
}
#endif
