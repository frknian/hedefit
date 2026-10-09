import SwiftUI
import PhotosUI

/// "Ne yedin?" sayfası: yaz (doğal dil), ara (katalog) ya da fotoğraf.
struct MealEntrySheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let initialMeal: String
    @State private var mode = 0
    @State private var meal = "Kahvaltı"
    @State private var text = ""
    @State private var amount = "100"
    @State private var query = ""
    @State private var picked: FoodSearchItem?
    @State private var pickedGrams = "100"
    @State private var showCamera = false
    @State private var photoItem: PhotosPickerItem?
    @State private var cameraDenied = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    FlowLayout(spacing: 8) { ForEach(NutritionModel.mealTypes, id: \.self) { m in HfChip(text: mealName(m), selected: meal == m) { meal = m } } }
                    HfSegmented(options: [tr("Yaz", "Type"), tr("Ara", "Search"), tr("Fotoğraf", "Photo")], selection: $mode)
                    switch mode {
                    case 0: typeMode
                    case 1: searchMode
                    default: photoMode
                    }
                    shortcuts
                }.padding(20)
            }
            .background(HC.bg).navigationTitle(tr("Ne yedin?", "What did you eat?")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }
        .onAppear { meal = initialMeal }
        .sheet(isPresented: $showCamera) { CameraPicker { image in analyze(image) }.ignoresSafeArea() }
        .onChange(of: photoItem) { _, item in Task { if let image = await PhotoLoader.image(from: item) { analyze(image) } } }
        .alert(tr("Kamera kapalı", "Camera off"), isPresented: $cameraDenied) { Button("OK", role: .cancel) {} } message: { Text(CameraAccess.deniedMessage) }
        .sheet(item: $picked) { food in FoodAmountSheet(food: food, meal: meal) { dismiss() } }
        .presentationDetents([.large])
    }

    private func mealName(_ m: String) -> String { switch m { case "Kahvaltı": return tr("Kahvaltı", "Breakfast"); case "Öğle yemeği": return tr("Öğle yemeği", "Lunch"); case "Akşam yemeği": return tr("Akşam yemeği", "Dinner"); default: return tr("Atıştırmalık", "Snack") } }

    private func analyze(_ image: UIImage) {
        guard let data = image.jpegForUpload() else { return }
        dismiss()
        Task { await app.nutrition.analyzePhoto(data) }
    }

    private var typeMode: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField(tr("örn. 2 yumurta, 1 dilim ekmek, domates", "e.g. 2 eggs, 1 slice of bread, tomato"), text: $text, axis: .vertical).lineLimit(1...4).padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous)).foregroundStyle(HC.text)
            if !NutritionModel.looksLikeWholeMeal(text) && !text.isEmpty {
                HfField(title: tr("Miktar (g)", "Amount (g)"), text: $amount, keyboard: .decimalPad)
            }
            Text(tr("Tüm öğünü tek cümleyle yazabilirsin; her besin ayrı porsiyonlanır ve kaydetmeden önce gözden geçirirsin.", "Type the whole meal in one sentence; each food is portioned separately and you review it before saving.")).font(.hfSmall).foregroundStyle(HC.muted)
            HfButton(title: app.nutrition.busy ? tr("Hesaplanıyor…", "Calculating…") : tr("Ekle", "Add"), icon: "sparkles", loading: app.nutrition.busy, enabled: text.trimmingCharacters(in: .whitespaces).count >= 2) {
                let grams = min(max(Double(amount.replacingOccurrences(of: ",", with: ".")) ?? 100, 1), 5000)
                Task { await app.nutrition.addWithAI(food: text, grams: grams, meal: meal); if app.nutrition.reviewItems.isEmpty { dismiss() } else { dismiss() } }
            }
        }
    }

    private var searchMode: some View {
        let n = app.nutrition
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                TextField(tr("Besin ara…", "Search foods…"), text: $query).submitLabel(.search).onSubmit { Task { await n.searchFoods(query) } }.textInputAutocapitalization(.never).foregroundStyle(HC.text)
                Button { Task { await n.searchFoods(query) } } label: { Image(systemName: "magnifyingglass").foregroundStyle(HC.lime) }
            }.padding(14).background(HC.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            if n.foodSearchBusy { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding(20) }
            if let q = n.foodQuery, n.foodResults.isEmpty { Text(tr("“\(q)” için sonuç bulunamadı.", "No results for “\(q)”.")).font(.hfBody).foregroundStyle(HC.textSecondary) }
            ForEach(n.foodResults) { f in
                Button { picked = f; pickedGrams = String(Int(f.servingGrams)) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(f.name).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text).multilineTextAlignment(.leading)
                            Text("\([f.brand].compactMap { $0 }.joined()) 100 g: \(f.calories) kcal • P \(Int(f.protein)) • K \(Int(f.carbs)) • Y \(Int(f.fat))").font(.hfSmall).foregroundStyle(HC.muted)
                        }
                        Spacer(); if f.verified { Image(systemName: "checkmark.seal.fill").foregroundStyle(HC.lime) }
                    }.padding(12).background(HC.surface, in: RoundedRectangle(cornerRadius: 14))
                }.buttonStyle(.plain)
            }
        }
    }

    private var photoMode: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(tr("Tabağın tamamını ve mümkünse ölçek için çatal veya kartı kadraja al. Porsiyonlar yine tahminidir.", "Include the whole plate and, if possible, a fork or card for scale. Portions remain estimates.")).font(.hfBody).foregroundStyle(HC.textSecondary)
            HfButton(title: tr("Kamera", "Camera"), icon: "camera.fill") {
                Task { if await CameraAccess.ensure() { showCamera = true } else { cameraDenied = true } }
            }
            PhotosPicker(selection: $photoItem, matching: .images) {
                Label(tr("Galeri", "Gallery"), systemImage: "photo.on.rectangle").font(.system(size: 15, weight: .heavy)).foregroundStyle(HC.text).frame(maxWidth: .infinity, minHeight: 52).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            Text(tr("Bugün kalan fotoğraf analizi hakkın: \(max(app.limits.dailyPhotoMeals, 0) == unlimited ? "∞" : "\(app.limits.dailyPhotoMeals)")", "Photo analyses per day on your plan: \(app.limits.dailyPhotoMeals == unlimited ? "∞" : "\(app.limits.dailyPhotoMeals)")")).font(.hfSmall).foregroundStyle(HC.muted)
        }
    }

    @ViewBuilder private var shortcuts: some View {
        let favorites = app.dashboard?.favoriteMeals ?? []
        let recent = Array(NSOrderedSet(array: (app.nutrition.history + (app.dashboard?.nutritionLogs ?? [])).map(\.name))).compactMap { $0 as? String }.prefix(8)
        if !favorites.isEmpty {
            Text(tr("Favoriler", "Favorites")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
            HfChipRow { ForEach(favorites.prefix(10)) { f in HfChip(text: f.name, selected: false, icon: "star.fill") { Task { await app.nutrition.repeatFavorite(f); dismiss() } } } }
        }
        if !recent.isEmpty {
            Text(tr("Son yediklerin", "Recent")).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
            HfChipRow { ForEach(Array(recent), id: \.self) { name in HfChip(text: name, selected: false) { text = name; mode = 0 } } }
        }
    }
}

struct FoodAmountSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let food: FoodSearchItem
    let meal: String
    var onAdded: () -> Void
    @State private var grams = ""
    var body: some View {
        let g = Double(grams.replacingOccurrences(of: ",", with: ".")) ?? 0
        let r = g / 100
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text(food.name).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                HfField(title: tr("Miktar (g)", "Amount (g)"), text: $grams, keyboard: .decimalPad)
                HStack(spacing: 10) {
                    HfStatTile(label: "kcal", value: "\(Int(Double(food.calories) * r))"); HfStatTile(label: "Protein", value: "\(Int(food.protein * r)) g"); HfStatTile(label: tr("Karb.", "Carbs"), value: "\(Int(food.carbs * r)) g"); HfStatTile(label: tr("Yağ", "Fat"), value: "\(Int(food.fat * r)) g")
                }
                HfButton(title: tr("Ekle", "Add"), icon: "plus", loading: app.nutrition.busy, enabled: g > 0) { Task { await app.nutrition.addCatalogFood(food, grams: min(g, 5000), meal: meal); dismiss(); onAdded() } }
                Spacer()
            }.padding(20).background(HC.bg).navigationTitle(tr("Miktar", "Amount")).navigationBarTitleDisplayMode(.inline)
        }.onAppear { grams = String(Int(food.servingGrams)) }.presentationDetents([.medium])
    }
}

// MARK: - Fotoğraf / metin incelemesi

struct MealReviewSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var rows: [Row] = []
    @State private var meal = "Öğle yemeği"
    struct Row: Identifiable { let id: UUID; var original: NutritionEstimate; var name: String; var grams: String; var included = true }

    var body: some View {
        let n = app.nutrition
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(n.reviewSource == "text" ? tr("Metinden çıkarılan besinler. Adı ve miktarı düzeltebilirsin.", "Foods read from your text. You can correct the name and amount.") : tr("Fotoğraftaki besinler tahmindir. Kaydetmeden önce düzelt.", "Foods in the photo are estimates. Correct them before saving.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                    FlowLayout(spacing: 8) { ForEach(NutritionModel.mealTypes, id: \.self) { m in HfChip(text: m, selected: meal == m) { meal = m } } }
                    ForEach($rows) { $row in
                        HfCard(padding: 14) {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack { Toggle("", isOn: $row.included).labelsHidden().tint(HC.lime); TextField(tr("Besin adı", "Food name"), text: $row.name).foregroundStyle(HC.text).font(.hfBody.weight(.semibold)) }
                                HStack {
                                    TextField("g", text: $row.grams).keyboardType(.decimalPad).padding(10).frame(width: 90).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 10)).foregroundStyle(HC.text); Text("g").foregroundStyle(HC.textSecondary)
                                    Spacer()
                                    let g = Double(row.grams.replacingOccurrences(of: ",", with: ".")) ?? row.original.grams, k = row.original.grams > 0 ? g / row.original.grams : 1
                                    Text("\(Int(Double(row.original.calories) * k)) kcal • P \(Int(row.original.protein * k)) • K \(Int(row.original.carbs * k)) • Y \(Int(row.original.fat * k))").font(.hfSmall).foregroundStyle(HC.lime)
                                }
                                if let w = NutritionWarning.text(for: row.original) { Label(w, systemImage: "exclamationmark.triangle.fill").font(.hfSmall).foregroundStyle(HC.warning).labelStyle(.titleAndIcon) }
                            }
                        }
                    }
                    HfButton(title: tr("Kaydet", "Save"), loading: n.busy, enabled: rows.contains { $0.included }) {
                        let items = rows.filter(\.included).map { r -> NutritionEstimate in
                            var e = r.original; let g = min(max(Double(r.grams.replacingOccurrences(of: ",", with: ".")) ?? e.grams, 1), 5000)
                            let k = e.grams > 0 ? g / e.grams : 1
                            e.name = r.name.isEmpty ? e.name : r.name; e.grams = g; e.calories = Int(Double(e.calories) * k); e.protein *= k; e.carbs *= k; e.fat *= k; e.fiber *= k; e.sugar *= k; e.sodiumMg *= k; e.potassiumMg *= k; e.calciumMg *= k; e.ironMg *= k; e.vitaminCMg *= k
                            return e
                        }
                        Task { await n.saveReview(items, meal: meal); dismiss() }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Öğünü gözden geçir", "Review meal")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { n.clearReview(); dismiss() } } }
        }
        .onAppear {
            meal = n.reviewMeal ?? smartMealForCurrentTime()
            rows = n.reviewItems.map { Row(id: $0.id, original: $0, name: $0.name, grams: String(Int($0.grams.rounded()))) }
        }
        .presentationDetents([.large])
    }
}

// MARK: - Takvim

struct NutritionCalendarSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var month = Date()
    var body: some View {
        let n = app.nutrition
        let daily = Dictionary(grouping: n.history + (app.dashboard?.nutritionLogs ?? []), by: { String($0.date.prefix(10)) }).mapValues { $0.reduce(0) { $0 + $1.calories } }
        let goal = app.dashboard?.nutritionGoal.calories ?? 2250
        let start = Dates.calendar.date(from: Dates.calendar.dateComponents([.year, .month], from: month)) ?? month
        let days = Dates.calendar.range(of: .day, in: .month, for: start)?.count ?? 30
        let offset = (Dates.calendar.component(.weekday, from: start) + 5) % 7
        return NavigationStack {
            VStack(spacing: 14) {
                HStack {
                    Button { month = Dates.calendar.date(byAdding: .month, value: -1, to: month) ?? month; Task { await n.loadMonth(month) } } label: { Image(systemName: "chevron.left") }
                    Spacer(); Text(start.formatted(.dateTime.month(.wide).year().locale(AppLang.shared.locale))).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text); Spacer()
                    Button { month = min(Dates.calendar.date(byAdding: .month, value: 1, to: month) ?? month, Date()); Task { await n.loadMonth(month) } } label: { Image(systemName: "chevron.right") }.disabled(Dates.calendar.isDate(month, equalTo: Date(), toGranularity: .month))
                }.foregroundStyle(HC.lime).padding(.horizontal)
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 6) {
                    ForEach(["P", "S", "Ç", "P", "C", "C", "P"].indices, id: \.self) { i in Text(AppLang.shared.en ? ["M", "T", "W", "T", "F", "S", "S"][i] : ["P", "S", "Ç", "P", "C", "C", "P"][i]).font(.hfLabel).foregroundStyle(HC.muted) }
                    ForEach(0..<offset, id: \.self) { _ in Color.clear.frame(height: 44) }
                    ForEach(1...days, id: \.self) { d in
                        let date = Dates.add(d - 1, to: start), key = Dates.day(date), kcal = daily[key] ?? 0, future = date > Date()
                        Button { n.selectDate(date); dismiss() } label: {
                            VStack(spacing: 2) {
                                Text("\(d)").font(.system(size: 14, weight: Dates.isSameDay(date, Date()) ? .heavy : .medium)).foregroundStyle(future ? HC.muted.opacity(0.4) : HC.text)
                                Text(kcal > 0 ? "\(kcal)" : "").font(.system(size: 9)).foregroundStyle(kcal > goal ? HC.coral : HC.lime)
                            }.frame(maxWidth: .infinity, minHeight: 44).background(kcal > 0 ? (kcal > goal ? HC.coral : HC.lime).opacity(0.14) : HC.surface, in: RoundedRectangle(cornerRadius: 10))
                        }.buttonStyle(.plain).disabled(future)
                    }
                }.padding(.horizontal)
                Spacer()
            }.padding(.top).background(HC.bg).navigationTitle(tr("Kalori takvimi", "Calorie calendar")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.task { await n.loadMonth(month) }.presentationDetents([.large])
    }
}

// MARK: - Haftalık plan

struct WeeklyMealPlanner: View {
    @Environment(AppModel.self) private var app
    @State private var day = Date()
    @State private var meal = "lunch"
    @State private var query = ""
    @State private var picked: FoodSearchItem?
    @State private var grams = "100"
    private let types = [("breakfast", "Kahvaltı", "Breakfast"), ("lunch", "Öğle", "Lunch"), ("dinner", "Akşam", "Dinner"), ("snack", "Atıştırmalık", "Snack")]

    var body: some View {
        let n = app.nutrition
        let weekStart = Dates.weekStart(Date())
        let items = (app.dashboard?.mealPlanItems ?? []).filter { $0.plannedDate.prefix(10) == Dates.day(day) }
        HfCard(padding: 14) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 4) {
                    ForEach(0..<7, id: \.self) { i in
                        let d = Dates.add(i, to: weekStart), sel = Dates.isSameDay(d, day)
                        Button { day = d } label: {
                            VStack(spacing: 2) { Text(String(d.formatted(.dateTime.weekday(.abbreviated).locale(AppLang.shared.locale)).prefix(3))).font(.system(size: 10, weight: .bold)); Text("\(Dates.calendar.component(.day, from: d))").font(.system(size: 15, weight: .bold)) }
                                .foregroundStyle(sel ? HC.onLime : HC.textSecondary).frame(maxWidth: .infinity).padding(.vertical, 8).background(sel ? HC.lime : HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 12))
                        }.buttonStyle(.plain)
                    }
                }
                ForEach(types, id: \.0) { key, trName, enName in
                    let list = items.filter { $0.mealType == key }
                    if !list.isEmpty {
                        Text(tr(trName, enName)).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime)
                        ForEach(list) { item in
                            HStack(spacing: 10) {
                                Button { Task { await n.togglePlanItem(item, completed: !item.completed) } } label: { Image(systemName: item.completed ? "checkmark.circle.fill" : "circle").font(.system(size: 22)).foregroundStyle(item.completed ? HC.lime : HC.muted) }
                                VStack(alignment: .leading, spacing: 1) { Text(item.name).font(.hfBody.weight(.semibold)).foregroundStyle(item.completed ? HC.muted : HC.text).strikethrough(item.completed); Text("\(Int(item.grams)) g • \(item.calories) kcal").font(.hfSmall).foregroundStyle(HC.muted) }
                                Spacer(); Button { Task { await n.removePlanItem(item) } } label: { Image(systemName: "trash").foregroundStyle(HC.muted) }
                            }
                        }
                    }
                }
                if items.isEmpty { Text(tr("Bu gün için plan yok.", "Nothing planned for this day.")).font(.hfSmall).foregroundStyle(HC.muted) }
                HfDivider()
                HfChipRow { ForEach(types, id: \.0) { k, t, e in HfChip(text: tr(t, e), selected: meal == k) { meal = k } } }
                HStack {
                    TextField(tr("Plana besin ekle…", "Add a food to the plan…"), text: $query).submitLabel(.search).onSubmit { Task { await n.searchFoods(query) } }.foregroundStyle(HC.text).textInputAutocapitalization(.never)
                    Button { Task { await n.searchFoods(query) } } label: { Image(systemName: "magnifyingglass").foregroundStyle(HC.lime) }
                }.padding(12).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 12))
                ForEach(n.foodResults.prefix(5)) { f in
                    Button { picked = f; grams = String(Int(f.servingGrams)) } label: { HStack { Text(f.name).foregroundStyle(HC.text).font(.hfBody); Spacer(); Text("\(f.calories) kcal/100 g").font(.hfSmall).foregroundStyle(HC.muted) } }.buttonStyle(.plain)
                }
            }
        }
        .sheet(item: $picked) { food in
            NavigationStack {
                VStack(alignment: .leading, spacing: 14) {
                    Text(food.name).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text)
                    HfField(title: tr("Miktar (g)", "Amount (g)"), text: $grams, keyboard: .decimalPad)
                    HfButton(title: tr("Plana ekle", "Add to plan"), icon: "plus", enabled: (Double(grams) ?? 0) > 0) { Task { await n.addPlanItem(food, grams: Double(grams) ?? 100, date: day, mealType: meal); picked = nil } }
                    Spacer()
                }.padding(20).background(HC.bg).navigationTitle(tr("Plana ekle", "Add to plan")).navigationBarTitleDisplayMode(.inline)
            }.presentationDetents([.medium])
        }
    }
}
