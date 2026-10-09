import SwiftUI

struct MainShell: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app
        NavigationStack(path: $app.path) {
            ZStack(alignment: .bottom) {
                Group {
                    switch app.tab {
                    case .home: HomeView()
                    case .explore: ExploreView()
                    case .coach: CoachView()
                    case .nutrition: NutritionView()
                    case .progress: ProgressTabView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .safeAreaPadding(.bottom, 84)
                FloatingTabBar()
            }
            .background(HC.bg.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { route in
                RouteDestination(route: route).toolbar(.hidden, for: .navigationBar).background(HC.bg.ignoresSafeArea())
            }
        }
        .sheet(item: $app.lockedFeature) { PremiumLockSheet(feature: $0) }
        .sheet(isPresented: $app.showPlans) { PlansSheet() }
        .sheet(isPresented: $app.showCheckin) { DailyCheckinSheet() }
        .sheet(isPresented: $app.showSaveAccount) { SaveAccountSheet() }
        .fullScreenCover(isPresented: Binding(get: { app.needsPersonalDetails }, set: { _ in })) { BodyProfileView() }
        .sheet(isPresented: Binding(get: { app.needsUsername }, set: { _ in })) { UsernameSetupView() }
        .fullScreenCover(isPresented: Binding(get: { app.needsQuestionnaire }, set: { _ in })) { QuestionnaireView(quickStart: false) {} }
        .fullScreenCover(isPresented: Binding(get: { app.showWelcomeGuide && app.onboardingComplete }, set: { app.showWelcomeGuide = $0 })) { WelcomeGuideView() }
        .fullScreenCover(isPresented: Bindable(app.workout).isPresented) { ActiveWorkoutHost() }
        .fullScreenCover(item: Bindable(app.workout).summary) { WorkoutSummaryView(summary: $0) }
        .overlay { CelebrationHost() }
    }
}

struct FloatingTabBar: View {
    @Environment(AppModel.self) private var app

    private func item(_ tab: AppTab, _ icon: String, _ label: String) -> some View {
        let on = app.tab == tab
        return Button {
            if app.tab == tab { app.path.removeAll() } else { app.select(tab) }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            VStack(spacing: 4) {
                Image(systemName: icon).font(.system(size: 20, weight: .semibold))
                Text(label).font(.system(size: 11, weight: on ? .bold : .medium)).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(on ? HC.lime : HC.muted).frame(maxWidth: .infinity).padding(.vertical, 8)
        }.buttonStyle(.plain)
    }

    var body: some View {
        HStack(spacing: 0) {
            item(.home, "house.fill", tr("Bugün", "Today"))
            item(.explore, "safari.fill", tr("Keşfet", "Explore"))
            Button { app.select(.coach) } label: {
                VStack(spacing: 2) {
                    ZStack {
                        Circle().fill(HC.surfaceHigh).frame(width: 62, height: 62).overlay(Circle().stroke(app.tab == .coach ? HC.lime : HC.divider, lineWidth: 3))
                        Image("FitCoach").resizable().scaledToFit().frame(width: 46, height: 46).clipShape(Circle())
                    }.offset(y: -14)
                    Text(tr("Fit Koç", "Fit Coach")).font(.system(size: 11, weight: .bold)).foregroundStyle(app.tab == .coach ? HC.lime : HC.muted).offset(y: -12)
                }.frame(maxWidth: .infinity).frame(height: 44)
            }.buttonStyle(.plain)
            item(.nutrition, "fork.knife", tr("Beslenme", "Nutrition"))
            item(.progress, "chart.bar.fill", tr("İlerleme", "Progress"))
        }
        .padding(.horizontal, 6).background(HC.surface, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 30, style: .continuous).stroke(HC.divider)).shadow(color: .black.opacity(0.3), radius: 16, y: 6)
        .padding(.horizontal, 14).padding(.bottom, 6)
        .opacity(app.path.isEmpty ? 1 : 0).allowsHitTesting(app.path.isEmpty)
    }
}
