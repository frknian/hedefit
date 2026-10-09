import SwiftUI
import PhotosUI

@MainActor private func mealStyle(_ meal: String) -> (String, Color) {
    switch meal { case "Kahvaltı": return ("sunrise.fill", HC.warning); case "Öğle yemeği": return ("sun.max.fill", HC.lime); case "Akşam yemeği": return ("moon.stars.fill", HC.sleep); default: return ("carrot.fill", HC.coral) }
}
@MainActor private func mealLabel(_ meal: String) -> String {
    switch meal { case "Kahvaltı": return tr("Kahvaltı", "Breakfast"); case "Öğle yemeği": return tr("Öğle yemeği", "Lunch"); case "Akşam yemeği": return tr("Akşam yemeği", "Dinner"); case "Atıştırmalık": return tr("Atıştırmalık", "Snack"); default: return meal }
}
func smartMealForCurrentTime() -> String {
    switch Calendar.current.component(.hour, from: Date()) { case 4...10: return "Kahvaltı"; case 11...15: return "Öğle yemeği"; case 16...21: return "Akşam yemeği"; default: return "Atıştırmalık" }
}

struct NutritionView: View {
    @Environment(AppModel.self) private var app
    @State private var addMeal: String?
    @State private var showCalendar = false
    @State private var showPlanner = false
    @State private var showTraining = false
    @State private var showMicros = false
    @State private var tipDismissed = false
    @State private var waterGoalOpen = false

    private var n: NutritionModel { app.nutrition }

    var body: some View {
        @Bindable var n = app.nutrition
        let d = app.dashboard
        let logs = n.logs
        ZStack(alignment: .bottomTrailing) {
            ScreenScaffold(spacing: 14, bottomInset: n.isToday ? 110 : 24) {
                header
                weekStrip
                if !n.isToday {
                    HStack {
                        Text(tr("\(Dates.parse(Dates.day(n.viewingDate))?.formatted(.dateTime.day().month(.wide).locale(AppLang.shared.locale)) ?? "") kaydı • sadece görüntüleme", "Viewing \(n.viewingDate.formatted(.dateTime.day().month(.wide).locale(AppLang.shared.locale))) • read-only")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        Spacer(); Button(tr("Bugüne dön", "Back to today")) { n.selectDate(Date()) }.font(.system(size: 14, weight: .bold)).foregroundStyle(HC.water)
                    }.padding(.leading, 14).padding(.trailing, 8).padding(.vertical, 6).background(HC.water.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                if let d { summaryCard(d, logs: logs) }
                if n.isToday, isTrainingDay(d, n.viewingDate) { expandable("dumbbell.fill", HC.warning, tr("Spor günü öğünleri", "Training day meals"), tr("Antrenman öncesi ve sonrası", "Before and after training"), $showTraining) { TrainingDayMeals() } }
                let weekStart = Dates.weekStart(Date())
                let planned = Set((d?.mealPlanItems ?? []).compactMap { Dates.parse($0.plannedDate) }.filter { $0 >= weekStart && $0 < Dates.add(7, to: weekStart) }.map(Dates.epochDay)).count
                expandable("calendar", HC.sleep, tr("Bu haftaki öğün planı", "This week's meal plan"), planned == 0 ? tr("Henüz plan yok", "Nothing planned yet") : tr("\(planned) gün planlandı", "\(planned) days planned"), $showPlanner) { WeeklyMealPlanner() }
                expandable("leaf.fill", HC.lime, tr("Lif ve mikro besinler", "Fibre and micronutrients"), tr("Lif \(Int(logs.reduce(0) { $0 + $1.fiber })) g • Sodyum \(Int(logs.reduce(0) { $0 + $1.sodiumMg })) mg", "Fibre \(Int(logs.reduce(0) { $0 + $1.fiber })) g • Sodium \(Int(logs.reduce(0) { $0 + $1.sodiumMg })) mg"), $showMicros) { MicroNutrientCard(logs: logs, profile: d?.profile) }
                HStack(alignment: .bottom) {
                    Text(tr("Öğünler", "Meals")).font(.hfTitleL).foregroundStyle(HC.text); Spacer()
                    Text(tr("\(Set(logs.map(\.meal)).count) öğün • \(logs.reduce(0) { $0 + $1.calories }) kcal", "\(Set(logs.map(\.meal)).count) meals • \(logs.reduce(0) { $0 + $1.calories }) kcal")).font(.hfLabel).foregroundStyle(HC.muted)
                }.padding(.top, 4)
                ForEach(NutritionModel.mealTypes, id: \.self) { type in
                    MealSection(type: type, entries: logs.filter { type == "Atıştırmalık" ? ($0.meal == type || !NutritionModel.mealTypes.contains($0.meal)) : $0.meal == type }, canLog: n.isToday) { addMeal = type }
                }
                if n.isToday, let favs = d?.favoriteMeals, !favs.isEmpty { FavoriteChips(favorites: favs) }
                if n.isToday, let w = app.adaptive.nutritionWellness { WellnessTipsCard(wellness: w) }
                if n.isToday && !tipDismissed {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "lightbulb.fill").foregroundStyle(HC.warning)
                        Text(tr("İpucu: 'omlet, 2 dilim ekmek, domates' gibi tüm öğünü tek cümleyle yazabilirsin.", "Tip: you can type a whole meal in one sentence, like 'omelette, 2 slices of bread, tomato'.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        Spacer(); Button { tipDismissed = true } label: { Image(systemName: "xmark").font(.system(size: 11)).foregroundStyle(HC.muted) }
                    }.padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
            if n.isToday {
                Button { addMeal = smartMealForCurrentTime() } label: { Image(systemName: "plus").font(.system(size: 26, weight: .bold)).foregroundStyle(HC.onLime).frame(width: 60, height: 60).background(HC.lime, in: Circle()).shadow(color: .black.opacity(0.3), radius: 10, y: 4) }
                    .padding(.trailing, 20).padding(.bottom, 20).accessibilityLabel(tr("Yiyecek ekle", "Add food"))
            }
        }
        .sheet(item: Binding(get: { addMeal.map { StringID(id: $0) } }, set: { addMeal = $0?.id })) { MealEntrySheet(initialMeal: $0.id) }
        .sheet(isPresented: $showCalendar) { NutritionCalendarSheet() }
        .sheet(isPresented: Binding(get: { !n.reviewItems.isEmpty }, set: { if !$0 { n.clearReview() } })) { MealReviewSheet() }
        .overlay { if n.photoBusy { ProgressOverlay(title: tr("Öğün analiz ediliyor…", "Analyzing meal…"), text: tr("Besinler, porsiyonlar ve değerler tahmin ediliyor.", "Foods, portions and nutrients are being estimated.")) } }
        .task { await app.adaptive.loadNutritionWellness(); await n.loadMonth(n.viewingDate) }
        .onAppear { if app.openMealComposer { app.openMealComposer = false; addMeal = smartMealForCurrentTime() } }
    }

    private var header: some View {
        let date = n.viewingDate
        let prefix = Dates.isSameDay(date, Date()) ? tr("Bugün", "Today") : (Dates.isSameDay(date, Dates.add(-1)) ? tr("Dün", "Yesterday") : nil)
        let formatted = date.formatted(.dateTime.day().month(.wide).weekday(.wide).locale(AppLang.shared.locale))
        return HStack {
            VStack(alignment: .leading, spacing: 2) { Text(tr("Beslenme", "Nutrition")).font(.hfTitle).foregroundStyle(HC.text); Text(prefix.map { "\($0) • \(formatted)" } ?? formatted).font(.hfBody).foregroundStyle(HC.textSecondary) }
            Spacer()
            HfCircleButton(system: "calendar", label: tr("Kalori takvimini aç", "Open calorie calendar")) { showCalendar = true }
        }
    }

    private var weekStrip: some View {
        let today = Date(), selected = n.viewingDate, weekStart = Dates.weekStart(selected)
        let daily = Dictionary(grouping: n.history + (app.dashboard?.nutritionLogs ?? []), by: { String($0.date.prefix(10)) }).mapValues { $0.reduce(0) { $0 + $1.calories } }
        return VStack(spacing: 6) {
            HStack(spacing: 0) {
                Button { n.selectDate(Dates.add(-7, to: selected)) } label: { Image(systemName: "chevron.left").foregroundStyle(HC.textSecondary).frame(width: 34, height: 44) }
                HStack(spacing: 4) {
                    ForEach(0..<7, id: \.self) { i in
                        let day = Dates.add(i, to: weekStart)
                        let sel = Dates.isSameDay(day, selected), future = day > today, isToday = Dates.isSameDay(day, today)
                        let kcal = daily[Dates.day(day)]
                        let color: Color = sel ? HC.onLime : (future ? HC.muted.opacity(0.5) : (isToday ? HC.lime : HC.textSecondary))
                        Button { n.selectDate(day) } label: {
                            VStack(spacing: 3) {
                                Text(String(day.formatted(.dateTime.weekday(.abbreviated).locale(AppLang.shared.locale)).prefix(3))).font(.system(size: 11, weight: .bold)).foregroundStyle(color)
                                Text("\(Dates.calendar.component(.day, from: day))").font(.system(size: 16, weight: sel ? .heavy : .semibold)).foregroundStyle(color)
                                Circle().fill((kcal ?? 0) > 0 ? (sel ? HC.onLime : HC.lime) : .clear).frame(width: 5, height: 5)
                            }.frame(maxWidth: .infinity).padding(.vertical, 8).background(sel ? HC.lime : .clear, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }.buttonStyle(.plain).disabled(future)
                    }
                }
                Button { n.selectDate(min(Dates.add(7, to: selected), today)) } label: { Image(systemName: "chevron.right").foregroundStyle(HC.textSecondary).frame(width: 34, height: 44) }.disabled(Dates.add(7, to: weekStart) > today)
            }
            if n.dateLoading { ProgressView().progressViewStyle(.linear).tint(HC.lime) }
        }
    }

    private func summaryCard(_ d: Dashboard, logs: [NutritionLog]) -> some View {
        let consumed = logs.reduce(0) { $0 + $1.calories }
        let protein = logs.reduce(0) { $0 + $1.protein }, carbs = logs.reduce(0) { $0 + $1.carbs }, fat = logs.reduce(0) { $0 + $1.fat }
        let training = isTrainingDay(d, n.viewingDate)
        let activeCalories = n.isToday ? d.activeCalories : 0
        let logged = n.isToday ? d.sessions.filter { $0.manualActivityKey != nil && ($0.date.map { Dates.isSameDay($0, n.viewingDate) } ?? false) }.reduce(0) { $0 + $1.calories } : 0
        let activityBonus = min(max(activeCalories, 0), 600), activityExtra = min(max(logged, 0), 600)
        let bonus = min(max(activityBonus, training ? 150 : 0) + activityExtra, 800)
        let target = max(d.nutritionGoal.calories + bonus, 1)
        let over = consumed > target
        let goal = d.nutritionGoal
        return HfCard(padding: 18) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 18) {
                    ZStack { HfRing(progress: Double(consumed) / Double(target), color: over ? HC.coral : HC.lime, lineWidth: 10).frame(width: 104, height: 104)
                        VStack(spacing: 0) { Text("\(consumed)").font(.system(size: 22, weight: .heavy)).foregroundStyle(HC.text); Text("/ \(target)").font(.hfLabel).foregroundStyle(HC.muted) } }
                    VStack(alignment: .leading, spacing: 8) {
                        Text(tr("GÜNLÜK KALORİ", "DAILY CALORIES")).font(.hfLabel).foregroundStyle(HC.textSecondary)
                        let accent = over ? HC.coral : HC.lime
                        Text(over ? tr("\(consumed - target) kcal aştın", "\(consumed - target) kcal over") : tr("\(target - consumed) kcal kaldı", "\(target - consumed) kcal left")).font(.system(size: 14, weight: .bold)).foregroundStyle(accent).padding(.horizontal, 12).padding(.vertical, 6).background(accent.opacity(0.15), in: Capsule())
                        if training { HfPill(text: tr("Spor günü +\(bonus - activityExtra) kcal", "Training day +\(bonus - activityExtra) kcal"), color: HC.warning) }
                        if activityExtra > 0 { HfPill(text: tr("Kardiyo/aktivite +\(activityExtra) kcal", "Cardio & activity +\(activityExtra) kcal"), color: HC.coral) }
                        else if bonus > 0 { HfPill(text: tr("Aktivite +\(bonus) kcal", "Activity +\(bonus) kcal"), color: HC.water) }
                    }
                }
                HfDivider()
                HStack(spacing: 12) {
                    macro("Protein", protein, goal.protein + (training ? 20 : 0), HC.lime)
                    macro(tr("Karb.", "Carbs"), carbs, goal.carbs + (training ? 40 : 0), HC.water)
                    macro(tr("Yağ", "Fat"), fat, goal.fat, HC.warning)
                }
                if n.isToday { HfDivider(); waterRow(d) }
            }
        }
    }

    private func macro(_ label: String, _ value: Double, _ goal: Int, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 6) { Circle().fill(color).frame(width: 8, height: 8); Text(label).font(.hfLabel).foregroundStyle(HC.textSecondary) }
            Text("\(Int(value)) / \(goal)g").font(.system(size: 14, weight: .semibold)).foregroundStyle(HC.text)
            HfProgressBar(progress: value / Double(max(goal, 1)), color: color)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }

    private func waterRow(_ d: Dashboard) -> some View {
        let goalMl = app.prefs.waterGoalMl, glasses = min(max(goalMl / 250, 1), 12), filled = min(max(d.waterMl / 250, 0), glasses)
        return HStack(spacing: 10) {
            Image(systemName: "drop.fill").foregroundStyle(HC.water)
            HStack(spacing: 3) { ForEach(0..<glasses, id: \.self) { i in RoundedRectangle(cornerRadius: 4).fill(i < filled ? HC.water : HC.surfaceSoft).frame(height: 16) } }.onTapGesture { waterGoalOpen = true }
            Text("\(d.waterMl)/\(goalMl) ml").font(.hfLabel).foregroundStyle(HC.muted)
            Button { Task { await app.addWater(250) } } label: { Image(systemName: "plus").font(.system(size: 15, weight: .bold)).foregroundStyle(HC.water).frame(width: 36, height: 36).background(HC.water.opacity(0.16), in: Circle()) }
        }.sheet(isPresented: $waterGoalOpen) { HomeMetricSheet(metric: .water) }
    }

    private func expandable<C: View>(_ icon: String, _ tint: Color, _ title: String, _ subtitle: String, _ expanded: Binding<Bool>, @ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 10) {
            HfCard(padding: 12, onTap: { withAnimation(.easeOut(duration: 0.2)) { expanded.wrappedValue.toggle() } }) {
                HStack(spacing: 12) {
                    HfIconBadge(system: icon, tint: tint)
                    VStack(alignment: .leading, spacing: 2) { Text(title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text); Text(subtitle).font(.hfSmall).foregroundStyle(HC.muted) }
                    Spacer(); Image(systemName: "chevron.down").rotationEffect(.degrees(expanded.wrappedValue ? 180 : 0)).foregroundStyle(HC.muted)
                }
            }
            if expanded.wrappedValue { content() }
        }
    }
}

@MainActor func isTrainingDay(_ d: Dashboard?, _ date: Date) -> Bool {
    guard let d else { return false }
    let day = Dates.day(date)
    return d.schedule.contains { $0.date.prefix(10) == day && ["planned", "completed"].contains($0.status) } || d.sessions.contains { $0.date.map { Dates.isSameDay($0, date) } ?? false }
}

struct ProgressOverlay: View {
    let title: String, text: String
    var body: some View {
        ZStack { Color.black.opacity(0.45).ignoresSafeArea()
            VStack(spacing: 12) { ProgressView().tint(HC.lime).scaleEffect(1.3); Text(title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text); Text(text).font(.hfSmall).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center) }
                .padding(24).frame(maxWidth: 300).background(HC.surface, in: RoundedRectangle(cornerRadius: 22, style: .continuous)) }
    }
}

// MARK: - Öğün bölümü

struct MealSection: View {
    @Environment(AppModel.self) private var app
    let type: String
    let entries: [NutritionLog]
    let canLog: Bool
    var onAdd: () -> Void
    @State private var editing: NutritionLog?
    @State private var removing: NutritionLog?
    var body: some View {
        let (icon, tint) = mealStyle(type)
        HfCard(padding: 14) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    HfIconBadge(system: icon, tint: tint)
                    VStack(alignment: .leading, spacing: 2) { Text(mealLabel(type)).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text); Text(entries.isEmpty ? tr("Henüz eklenmedi", "No food yet") : tr("\(entries.count) besin", "\(entries.count) foods")).font(.hfSmall).foregroundStyle(HC.muted) }
                    Spacer()
                    Text("\(entries.reduce(0) { $0 + $1.calories }) kcal").font(.system(size: 14, weight: .bold)).foregroundStyle(entries.isEmpty ? HC.muted : HC.text)
                    if canLog { Button(action: onAdd) { Image(systemName: "plus").font(.system(size: 14, weight: .bold)).foregroundStyle(HC.text).frame(width: 32, height: 32).background(HC.surfaceHigh, in: Circle()) }.disabled(app.nutrition.busy) }
                }
                ForEach(entries) { log in
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(log.name).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text).lineLimit(2)
                            Text("\(log.grams.map { "\(Int($0)) g • " } ?? "")P \(Int(log.protein)) • K \(Int(log.carbs)) • Y \(Int(log.fat))").font(.hfSmall).foregroundStyle(HC.muted)
                        }
                        Spacer()
                        Text("\(log.calories) kcal").font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                        if canLog {
                            Menu {
                                Button(tr("Düzenle", "Edit"), systemImage: "pencil") { editing = log }
                                Button(tr("Favorilere ekle", "Add to favorites"), systemImage: "star") { Task { await app.nutrition.addFavorite(log) } }
                                Button(tr("Kaldır", "Remove"), systemImage: "trash", role: .destructive) { removing = log }
                            } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).foregroundStyle(HC.muted).frame(width: 28, height: 36) }
                        }
                    }.padding(.vertical, 4)
                }
            }
        }
        .sheet(item: $editing) { EditMealSheet(log: $0) }
        .alert(tr("Besin kaldırılsın mı?", "Remove this food?"), isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) { removing = nil }
            Button(tr("Kaldır", "Remove"), role: .destructive) { if let l = removing { Task { await app.nutrition.remove(l) } }; removing = nil }
        } message: { Text(removing?.name ?? "") }
    }
}

struct EditMealSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let log: NutritionLog
    @State private var grams = ""
    @State private var meal = "Kahvaltı"
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text(log.name).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                HfField(title: tr("Miktar (g)", "Amount (g)"), text: $grams, keyboard: .decimalPad)
                Text(tr("Öğün", "Meal")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                FlowLayout(spacing: 8) { ForEach(NutritionModel.mealTypes, id: \.self) { m in HfChip(text: mealLabel(m), selected: meal == m) { meal = m } } }
                HfButton(title: tr("Kaydet", "Save"), loading: app.nutrition.busy, enabled: (Double(grams.replacingOccurrences(of: ",", with: ".")) ?? 0) > 0) {
                    let g = min(max(Double(grams.replacingOccurrences(of: ",", with: ".")) ?? 100, 1), 5000)
                    Task { await app.nutrition.update(log, grams: g, meal: meal); dismiss() }
                }
                Spacer()
            }.padding(20).background(HC.bg).navigationTitle(tr("Öğünü düzenle", "Edit meal")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } } }
        }.onAppear { grams = log.grams.map { String(Int($0)) } ?? "100"; meal = NutritionModel.mealTypes.contains(log.meal) ? log.meal : "Atıştırmalık" }.presentationDetents([.medium])
    }
}

struct FavoriteChips: View {
    @Environment(AppModel.self) private var app
    let favorites: [FavoriteMeal]
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HfSectionHeader(title: tr("Favoriler", "Favorites"))
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(favorites) { f in
                        HStack(spacing: 6) {
                            Button { Task { await app.nutrition.repeatFavorite(f) } } label: { Text("\(f.name) • \(f.calories) kcal").font(.system(size: 13, weight: .bold)).foregroundStyle(HC.text) }
                            Button { Task { await app.nutrition.removeFavorite(f.id) } } label: { Image(systemName: "xmark").font(.system(size: 10, weight: .bold)).foregroundStyle(HC.muted) }
                        }.padding(.horizontal, 12).padding(.vertical, 9).background(HC.surface, in: Capsule())
                    }
                }
            }.scrollIndicators(.hidden)
        }
    }
}

struct WellnessTipsCard: View {
    @Environment(AppModel.self) private var app
    let wellness: NutritionWellness
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(tr("BUGÜNÜN BESLENME NOTLARI", "TODAY'S NUTRITION NOTES")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime)
            ForEach(wellness.tips) { tip in Text(tip.title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text); Text(tip.body).font(.hfSmall).foregroundStyle(HC.textSecondary) }
            if wellness.proteinBonusGrams > 0 { Text(tr("İsteğe bağlı: bugün yaklaşık +\(wellness.proteinBonusGrams) g protein toparlanmaya destek olabilir.", "Optional: about +\(wellness.proteinBonusGrams) g protein today can support recovery.")).font(.hfSmall).foregroundStyle(HC.muted) }
            if wellness.lockedTipCount > 0 { Button { app.showPlans = true } label: { Text(tr("Plus ve Premium antrenman günü ve kişiselleştirilmiş notlar ekler. Görmek için dokun.", "Plus and Premium add training-day and personalised notes. Tap to see.")).font(.hfSmall.weight(.bold)).foregroundStyle(HC.lime).multilineTextAlignment(.leading) } }
            Text(tr("Genel bilgidir, tıbbi tavsiye değildir.", "General information, not medical advice.")).font(.system(size: 11)).foregroundStyle(HC.muted)
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(HC.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct MicroNutrientCard: View {
    let logs: [NutritionLog]
    let profile: Profile?
    var body: some View {
        let fiber = logs.reduce(0) { $0 + $1.fiber }, sugar = logs.reduce(0) { $0 + $1.sugar }, sodium = logs.reduce(0) { $0 + $1.sodiumMg }, potassium = logs.reduce(0) { $0 + $1.potassiumMg }
        let calcium = logs.reduce(0) { $0 + $1.calciumMg }, iron = logs.reduce(0) { $0 + $1.ironMg }, vitC = logs.reduce(0) { $0 + $1.vitaminCMg }
        let age = profile?.age ?? 30, g = (profile?.gender ?? "").lowercased()
        let female = ["kadın", "kadin", "female", "woman"].contains { g.contains($0) }, male = ["erkek", "male", "man"].contains { g.contains($0) }
        let fiberTarget = (age > 50 && !male) ? 21 : (age > 50 ? 30 : (male ? 38 : 25)), potassiumTarget = male ? 3400 : 2600
        let calciumTarget = (age >= 71 || (female && age >= 51)) ? 1200 : 1000, ironTarget = (female && (19...50).contains(age)) ? 18 : 8, vitCTarget = male ? 90 : 75
        return HfCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack { Text(tr("Lif", "Fibre")).foregroundStyle(HC.textSecondary); Spacer(); Text("\(Int(fiber)) / \(fiberTarget) g").foregroundStyle(HC.text) }.font(.hfBody)
                HfProgressBar(progress: fiber / Double(fiberTarget))
                HStack(spacing: 8) { cell(tr("Toplam şeker (hedef değil)", "Total sugar (not a target)"), "\(Int(sugar)) g"); cell(tr("Sodyum sınırı", "Sodium limit"), "\(Int(sodium)) / ≤2300 mg") }
                HStack(spacing: 8) { cell(tr("Potasyum", "Potassium"), "\(Int(potassium)) / \(potassiumTarget) mg"); cell(tr("Kalsiyum", "Calcium"), "\(Int(calcium)) / \(calciumTarget) mg"); cell(tr("Demir", "Iron"), String(format: "%.1f / %d mg", iron, ironTarget)) }
                cell(tr("C Vitamini", "Vitamin C"), "\(Int(vitC)) / \(vitCTarget) mg")
            }
        }
    }
    private func cell(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) { Text(label).font(.hfSmall).foregroundStyle(HC.textSecondary).lineLimit(2); Text(value).font(.system(size: 13, weight: .bold)).foregroundStyle(HC.text).lineLimit(1).minimumScaleFactor(0.7) }
            .padding(9).frame(maxWidth: .infinity, alignment: .leading).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }
}

// MARK: - Spor günü öğünleri

struct TrainingFood { let catalogName: String, tr: String, en: String; let kcal, protein, carbs, fat, grams: Double; let meal: String; var countTr: String? = nil; var countEn: String? = nil; var unit = "g" }

private let preWorkout: [TrainingFood] = [
    TrainingFood(catalogName: "Yulaf ezmesi", tr: "Yulaf ezmesi", en: "Oats", kcal: 379, protein: 13, carbs: 68, fat: 6.5, grams: 60, meal: "Kahvaltı"),
    TrainingFood(catalogName: "Muz", tr: "Muz", en: "Banana", kcal: 89, protein: 1.1, carbs: 23, fat: 0.3, grams: 120, meal: "Atıştırmalık", countTr: "1 adet", countEn: "1 piece"),
    TrainingFood(catalogName: "Tam buğday ekmeği", tr: "Tam buğday ekmeği", en: "Whole wheat bread", kcal: 247, protein: 13, carbs: 41, fat: 3.4, grams: 60, meal: "Atıştırmalık", countTr: "2 dilim", countEn: "2 slices"),
    TrainingFood(catalogName: "Süt, yarım yağlı", tr: "Süt", en: "Milk", kcal: 50, protein: 3.4, carbs: 4.8, fat: 1.8, grams: 200, meal: "Atıştırmalık", unit: "ml"),
]
private let postWorkout: [TrainingFood] = [
    TrainingFood(catalogName: "Tavuk göğsü, pişmiş", tr: "Tavuk göğsü", en: "Chicken breast", kcal: 165, protein: 31, carbs: 0, fat: 3.6, grams: 150, meal: "Akşam yemeği"),
    TrainingFood(catalogName: "Pirinç pilavı, pişmiş", tr: "Pirinç pilavı", en: "Rice", kcal: 130, protein: 2.7, carbs: 28, fat: 0.3, grams: 150, meal: "Akşam yemeği"),
    TrainingFood(catalogName: "Somon, pişmiş", tr: "Somon", en: "Salmon", kcal: 206, protein: 22, carbs: 0, fat: 12, grams: 150, meal: "Akşam yemeği"),
    TrainingFood(catalogName: "Tatlı patates, pişmiş", tr: "Tatlı patates", en: "Sweet potato", kcal: 90, protein: 2, carbs: 21, fat: 0.2, grams: 200, meal: "Akşam yemeği"),
    TrainingFood(catalogName: "Ton Balıklı Sandviç", tr: "Ton balıklı sandviç", en: "Tuna sandwich", kcal: 235, protein: 15, carbs: 25, fat: 8, grams: 180, meal: "Öğle yemeği", countTr: "1 adet", countEn: "1 piece"),
    TrainingFood(catalogName: "Yumurta, bütün", tr: "Yumurta", en: "Eggs", kcal: 143, protein: 13, carbs: 0.7, fat: 9.5, grams: 100, meal: "Atıştırmalık", countTr: "2 adet", countEn: "2 eggs"),
    TrainingFood(catalogName: "Süzme yoğurt", tr: "Süzme yoğurt", en: "Greek yogurt", kcal: 97, protein: 9, carbs: 3.9, fat: 5, grams: 200, meal: "Atıştırmalık"),
]

struct TrainingDayMeals: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        let d = app.dashboard
        let now = d?.measurements.last(where: { $0.weightKg != nil })?.weightKg ?? d?.profile.weightKg, target = d?.profile.targetWeightKg
        let direction = (now == nil || target == nil || abs(target! - now!) < 0.5) ? 0 : (target! < now! ? -1 : 1)
        let factor = direction < 0 ? 0.8 : (direction > 0 ? 1.2 : 1.0)
        let time = d?.schedule.first { $0.date.prefix(10) == Dates.day() }?.time
        return HfCard(padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                Text(direction < 0 ? tr("Porsiyonlar yağ kaybı hedefine göre ayarlandı (×0.8).", "Portions are trimmed for fat loss (×0.8).") : direction > 0 ? tr("Porsiyonlar kas/kilo alma hedefine göre artırıldı (×1.2).", "Portions are increased for muscle/weight gain (×1.2).") : tr("Porsiyonlar kilo koruma hedefine göre ayarlandı.", "Portions are set for maintenance.")).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text)
                section(tr("Antrenman öncesi", "Before training"), tr("60–90 dk önce", "60–90 min before") + (time.map { " • \($0)" } ?? ""), tr("Kolay sindirilen karbonhidrat enerji verir; yağ ve lifi düşük tut.", "Easily digested carbs fuel the session; keep fat and fibre low."), preWorkout, factor)
                HfDivider()
                VStack(alignment: .leading, spacing: 6) {
                    Text(tr("Antrenman sırasında", "During training")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.water)
                    Text(tr("15–20 dakikada bir yudumla, saatte yaklaşık 500 ml su iç.", "Sip water every 15–20 min, about 500 ml per hour.")).font(.hfBody).foregroundStyle(HC.text)
                    HfButton(title: tr("+500 ml su", "+500 ml water"), secondary: true) { Task { await app.addWater(500) } }
                }
                HfDivider()
                section(tr("Antrenman sonrası", "After training"), tr("2 saat içinde", "Within 2 hours"), tr("Protein (25–40 g) kası onarır, karbonhidrat glikojen depolarını doldurur.", "Protein (25–40 g) repairs muscle; carbs refill glycogen."), postWorkout, factor)
                HfButton(title: tr("FitKoç'tan menü iste", "Ask Fit Coach for a menu"), icon: "sparkles", secondary: true) {
                    app.select(.coach); Task { await app.coach.send(tr("Bugün spor günüm. Hedefime ve bugün yediklerime göre antrenman öncesi ve sonrası öğünlerimi porsiyonlarıyla önerir misin?", "Today is a training day. Based on my goal and what I've eaten so far, suggest my pre- and post-workout meals with portions.")) }
                }
            }
        }
    }

    private func section(_ title: String, _ timing: String, _ why: String, _ foods: [TrainingFood], _ factor: Double) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.lime)
            Text(timing).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Text(why).font(.hfSmall).foregroundStyle(HC.textSecondary)
            ForEach(foods, id: \.catalogName) { f in
                let grams = f.countTr != nil ? f.grams : max((f.grams * factor / 10).rounded() * 10, 10), r = grams / 100
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(AppLang.shared.en ? f.en : f.tr) • \((AppLang.shared.en ? f.countEn : f.countTr) ?? "\(Int(grams)) \(f.unit)")").font(.hfBody.weight(.semibold)).foregroundStyle(HC.text)
                        Text("\(Int(f.kcal * r)) kcal • P \(Int(f.protein * r)) • K \(Int(f.carbs * r)) • Y \(Int(f.fat * r))").font(.hfLabel).foregroundStyle(HC.lime)
                    }
                    Spacer()
                    Button { Task { await addFood(f, grams) } } label: { Image(systemName: "plus").foregroundStyle(HC.lime).frame(width: 30, height: 30) }
                }
            }
        }
    }

    private func addFood(_ f: TrainingFood, _ grams: Double) async {
        let food = FoodSearchItem(id: "", name: f.catalogName, servingGrams: 100, calories: Int(f.kcal), protein: f.protein, carbs: f.carbs, fat: f.fat, verified: true, source: "training_day")
        await app.nutrition.addCatalogFood(food, grams: grams, meal: f.meal)
    }
}
