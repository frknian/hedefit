import SwiftUI

private typealias Opt = (String, String)

private func environmentOptions() -> [Opt] { [("", tr("Tümü", "Any")), ("gym", tr("Spor salonu", "Gym")), ("home", tr("Ev", "Home"))] }
private func muscleRoleOptions() -> [Opt] { [("primary", tr("Ana hedef", "Main target")), ("secondary", tr("Yardımcı", "Supporting")), ("", tr("Her ikisi", "Either"))] }
private func categoryOptions() -> [Opt] { [("", tr("Tümü", "All")), ("strength", tr("Kuvvet", "Strength")), ("stretching", tr("Mobilite", "Mobility")), ("plyometrics", tr("Patlayıcı", "Explosive")), ("cardio", tr("Kardiyo", "Cardio"))] }
private func levelOptions() -> [Opt] { [("", tr("Tümü", "All")), ("beginner", tr("Kolay", "Easy")), ("intermediate", tr("Orta", "Medium")), ("expert", tr("İleri", "Advanced"))] }
private func equipmentOptions() -> [Opt] {
    [("", tr("Tümü", "All")), ("bodyweight", tr("Ekipmansız", "No equipment"))] + homeEquipmentOptions.map { ($0.0, tr($0.1, $0.2)) } + [("cable", tr("Kablo", "Cable")), ("machine", tr("Makine", "Machine"))]
}
private func forceOptions() -> [Opt] { [("", tr("Tümü", "All")), ("push", tr("İtiş", "Push")), ("pull", tr("Çekiş", "Pull")), ("static", tr("Statik", "Static"))] }
private func mechanicOptions() -> [Opt] { [("", tr("Tümü", "All")), ("compound", tr("Bileşik", "Compound")), ("isolation", tr("İzolasyon", "Isolation"))] }
private func muscleOptions() -> [Opt] {
    LangStore.english
        ? [("", "All"), ("arms", "Arms · all"), ("back", "Back · all"), ("legs", "Legs · all"), ("core", "Core · all"), ("hips", "Hips · all"), ("chest", "Chest"), ("lats", "Lats"), ("middle back", "Mid back"), ("lower back", "Lower back"), ("traps", "Traps"), ("neck", "Neck"),
           ("shoulders", "Shoulders"), ("biceps", "Front arm · biceps"), ("triceps", "Back arm · triceps"), ("forearms", "Forearm & wrist"), ("abdominals", "Abs"),
           ("glutes", "Glutes"), ("quadriceps", "Front thigh · quads"), ("hamstrings", "Back thigh · hamstrings"), ("calves", "Calves"), ("adductors", "Inner thigh · adductors"), ("abductors", "Outer hip · abductors")]
        : [("", "Tümü"), ("chest", "Göğüs"), ("back", "Sırt"), ("lats", "Kanat"), ("traps", "Trapez"), ("neck", "Boyun"), ("shoulders", "Omuz"), ("biceps", "Ön kol"), ("triceps", "Arka kol"), ("forearms", "Bilek"), ("abdominals", "Karın"), ("legs", "Bacak"), ("glutes", "Kalça"), ("calves", "Baldır"),
           ("abductors", "Dış kalça"), ("middle back", "Orta sırt"), ("lower back", "Bel"), ("quadriceps", "Ön bacak"), ("hamstrings", "Arka bacak")]
}
private func modalityOptions() -> [Opt] { [("", tr("Tüm tarzlar", "All styles")), ("pilates", "Pilates"), ("mobility", tr("Mobilite", "Mobility")), ("barre", "Barre"), ("low_impact", tr("Düşük etkili", "Low impact")), ("recovery", tr("Toparlanma", "Recovery"))] }
private func subcategoryLabel(_ key: String) -> String {
    switch key {
    case "": return tr("Tümü", "All"); case "beginner": return tr("Başlangıç", "Beginner"); case "full_body": return tr("Tüm vücut", "Full body"); case "core": return "Core"
    case "lower_body": return tr("Alt vücut", "Lower body"); case "upper_body": return tr("Üst vücut", "Upper body"); case "posture": return tr("Duruş", "Posture"); case "short": return tr("Kısa", "Short")
    case "recovery": return tr("Toparlanma", "Recovery"); case "hip": return tr("Kalça", "Hip"); case "back": return tr("Sırt", "Back"); case "shoulder": return tr("Omuz", "Shoulder")
    case "morning": return tr("Sabah", "Morning"); case "evening": return tr("Akşam", "Evening"); case "cardio": return tr("Kardiyo", "Cardio"); case "balance_posture": return tr("Denge ve duruş", "Balance & posture")
    case "breathing": return tr("Nefes", "Breathing"); default: return key
    }
}
private func subcategoryOptions(_ modality: String) -> [Opt] {
    let keys: [String]
    switch modality {
    case "pilates": keys = ["", "beginner", "full_body", "core", "lower_body", "upper_body", "posture", "short", "recovery"]
    case "mobility": keys = ["", "full_body", "hip", "back", "shoulder", "morning", "evening"]
    case "barre": keys = ["", "beginner", "lower_body", "core", "full_body", "balance_posture"]
    case "low_impact": keys = ["", "full_body", "cardio", "beginner", "recovery"]
    case "recovery": keys = ["", "full_body", "lower_body", "upper_body", "breathing"]
    default: keys = [""]
    }
    return keys.map { ($0, subcategoryLabel($0)) }
}

/// Hareket Atlası / Kas Haritası.
struct LibraryView: View {
    @Environment(AppModel.self) private var app
    var startWithMap = false
    @State private var query = ""
    @State private var muscle = ""
    @State private var equipment = ""
    @State private var level = ""
    @State private var environment = ""
    @State private var muscleRole = "primary"
    @State private var force = ""
    @State private var mechanic = ""
    @State private var category = ""
    @State private var modality = ""
    @State private var subcategory = ""
    @State private var items: [ExerciseCatalogItem] = []
    @State private var loading = false
    @State private var showFilters = false
    @State private var showMap = false
    @State private var grid = false
    @State private var detail: ExerciseCatalogItem?
    @State private var showCustom = false
    @State private var picked: [ExerciseCatalogItem] = []
    @State private var showName = false
    @State private var programName = ""
    @State private var applied = false
    @State private var searchTask: Task<Void, Never>?

    private var activeFilterCount: Int { [muscle, equipment, level, environment, force, mechanic, category, modality, subcategory].filter { !$0.isEmpty }.count }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 12) {
                HfScreenHeader(title: startWithMap ? tr("Kas Haritası", "Muscle Map") : tr("Hareket Atlası", "Movement Atlas"), onBack: { if !app.path.isEmpty { app.path.removeLast() } }) {
                    HfCircleButton(system: "plus", label: tr("Özel hareket", "Custom exercise")) { showCustom = true }
                }
                Text(loading ? tr("Yükleniyor…", "Loading…") : tr("\(items.count) hareket", "\(items.count) movements")).font(.hfSmall).foregroundStyle(HC.muted).frame(maxWidth: .infinity, alignment: .leading).padding(.leading, 56).padding(.top, -10)
                HStack(spacing: 8) {
                    HStack { Image(systemName: "magnifyingglass").foregroundStyle(HC.muted)
                        TextField(tr("Hareket veya kas ara", "Search movement or muscle"), text: $query).submitLabel(.search).foregroundStyle(HC.text).onSubmit { search() }
                        if !query.isEmpty { Button { query = ""; search() } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(HC.muted) } }
                    }.padding(.horizontal, 14).frame(height: 52).background(HC.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    Button { showFilters = true } label: {
                        Image(systemName: "line.3.horizontal.decrease").font(.system(size: 18, weight: .semibold)).foregroundStyle(activeFilterCount > 0 ? HC.onLime : HC.text).frame(width: 52, height: 52)
                            .background(activeFilterCount > 0 ? HC.lime : HC.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(alignment: .topTrailing) { if activeFilterCount > 0 { Text("\(activeFilterCount)").font(.system(size: 10, weight: .heavy)).foregroundStyle(HC.lime).padding(.horizontal, 5).background(HC.surface, in: Capsule()).offset(x: 4, y: -4) } }
                    }.accessibilityLabel(tr("Filtreler, \(activeFilterCount) aktif", "Filters, \(activeFilterCount) active"))
                }
                HStack(spacing: 8) {
                    HfChip(text: tr("Kas haritası", "Muscle map"), selected: showMap) { withAnimation { showMap.toggle() } }
                    HfChip(text: tr("Izgara", "Grid"), selected: grid) { grid.toggle() }
                    Spacer()
                }
                HfChipRow { ForEach(muscleOptions().prefix(9), id: \.0) { v, l in HfChip(text: l, selected: muscle == v) { muscle = v; muscleRole = v.isEmpty ? "" : "primary"; search() } } }
                HfChipRow { ForEach(modalityOptions(), id: \.0) { v, l in HfChip(text: l, selected: modality == v) { modality = v; subcategory = ""; search() } } }
                if !modality.isEmpty { HfChipRow { ForEach(subcategoryOptions(modality), id: \.0) { v, l in HfChip(text: l, selected: subcategory == v) { subcategory = v; search() } } } }
                HStack {
                    Text(tr("\(items.count) sonuç", "\(items.count) results")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary); Spacer()
                    if activeFilterCount > 0 { Button(tr("Temizle", "Clear"), action: clearFilters).font(.hfBody.weight(.heavy)).foregroundStyle(HC.lime) }
                }
            }.padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 8)
            if loading { ProgressView().tint(HC.lime).progressViewStyle(.linear) }
            ScrollView {
                VStack(spacing: 12) {
                    if showMap {
                        VStack(spacing: 8) {
                            MuscleMap(selected: muscle.isEmpty ? [] : [muscle], onSelect: { id in muscle = muscle == id ? "" : id; muscleRole = muscle.isEmpty ? "" : "primary"; search() }, description: tr("Hareketlerini görmek için bir kasa dokun", "Tap a muscle to list its movements"))
                            Text(muscle.isEmpty ? tr("Bir kasa dokun, hareketleri listelensin", "Tap a muscle to see its movements") : (muscleOptions().first { $0.0 == muscle }?.1 ?? muscle)).font(.hfLabel.weight(.bold)).foregroundStyle(muscle.isEmpty ? HC.muted : HC.lime)
                        }.padding(.horizontal, 16)
                    }
                    if grid {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                            ForEach(items) { item in gridCell(item) }
                        }.padding(.horizontal, 16)
                    } else {
                        LazyVStack(spacing: 10) { ForEach(items) { item in row(item) }.padding(.horizontal, 16) }
                    }
                    if items.isEmpty && !loading { HfEmptyState(icon: "magnifyingglass", title: tr("Sonuç yok", "No results"), message: tr("Filtreleri değiştirip tekrar dene.", "Change the filters and try again.")).padding(24) }
                }.padding(.bottom, 24)
            }.scrollDismissesKeyboard(.interactively)
            if !picked.isEmpty {
                HStack(spacing: 10) {
                    Button { picked = [] } label: { Image(systemName: "xmark").foregroundStyle(HC.muted).frame(width: 36, height: 36) }.accessibilityLabel(tr("Seçimi temizle", "Clear selection"))
                    Text(tr("\(picked.count) hareket seçildi", "\(picked.count) selected")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Spacer()
                    Button(tr("Program oluştur", "Create program")) { showName = true }.font(.hfBody.weight(.bold)).foregroundStyle(HC.onLime).padding(.horizontal, 16).frame(height: 40).background(HC.lime, in: Capsule())
                }.padding(.horizontal, 16).padding(.vertical, 12).background(HC.surface)
            }
        }
        .onAppear { applyPreset() }
        .sheet(isPresented: $showFilters) { filterSheet }
        .sheet(item: $detail) { LibraryDetailSheet(item: $0) { app.workout.start(exercises: [WorkoutExercise(id: $0.id, name: $0.name, area: $0.primaryMuscles.first ?? "Tüm Vücut", sets: 3, reps: "8–12", restSeconds: 75)]) } }
        .sheet(isPresented: $showCustom) { CustomExerciseSheet() }
        .alert(tr("Programına isim ver", "Name your program"), isPresented: $showName) {
            TextField(tr("Program adı", "Program name"), text: $programName)
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Oluştur", "Create")) { let n = programName.trimmingCharacters(in: .whitespaces); guard n.count >= 2 else { return }; let list = picked; picked = []; programName = ""; Task { await app.createProgram(name: n, from: list) } }
        } message: { Text(tr("\(picked.count) hareket: ", "\(picked.count) movements: ") + picked.map(\.name).joined(separator: ", ")) }
    }

    // MARK: Satırlar

    private func locked(_ item: ExerciseCatalogItem) -> Bool { !app.limits.canUseExercise(item) || !app.limits.canUseModalityExercise(modalities: item.modalities, subcategories: item.subcategories) }
    private func showLock(_ item: ExerciseCatalogItem) {
        if !app.limits.canUseModalityExercise(modalities: item.modalities, subcategories: item.subcategories) {
            let feature: LockedFeature = item.modalities.allSatisfy { $0 == "barre" } ? .barre : .wellnessContent
            _ = app.require(feature) { $0.canUseModalityExercise(modalities: item.modalities, subcategories: item.subcategories) }
        } else { _ = app.require(.exercise) { $0.canUseExercise(item) } }
    }

    private func gridCell(_ item: ExerciseCatalogItem) -> some View {
        let isLocked = locked(item)
        return Button { if isLocked { showLock(item) } else { detail = item } } label: {
            ZStack(alignment: .bottomLeading) {
                ExerciseMedia(id: item.id, imageURLs: item.imageUrls).frame(maxWidth: .infinity).aspectRatio(0.8, contentMode: .fill)
                LinearGradient(colors: [.clear, Color.black.opacity(0.8)], startPoint: .top, endPoint: .bottom)
                Text(item.name).font(.hfLabel.weight(.bold)).foregroundStyle(.white).lineLimit(2).padding(8)
                if isLocked { Image(systemName: "lock.fill").font(.system(size: 14)).foregroundStyle(.white).padding(8).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing) }
            }.aspectRatio(0.8, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous)).opacity(isLocked ? 0.55 : 1)
        }.buttonStyle(.plain)
    }

    private func row(_ item: ExerciseCatalogItem) -> some View {
        let isLocked = locked(item), isPicked = picked.contains { $0.id == item.id }
        return HfCard(padding: 12, onTap: { if isLocked { showLock(item) } else { detail = item } }) {
            HStack(spacing: 12) {
                ExerciseMedia(id: item.id, imageURLs: item.imageUrls).frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text).lineLimit(2).multilineTextAlignment(.leading)
                    Text(([item.primaryMuscles.joined(separator: ", "), item.secondaryMuscles.prefix(2).joined(separator: ", ")]).filter { !$0.isEmpty }.joined(separator: " • ")).font(.hfSmall).foregroundStyle(HC.textSecondary).lineLimit(1)
                    Text([item.level, item.equipment.isEmpty ? tr("Ekipmansız", "No equipment") : item.equipment].filter { !$0.isEmpty }.joined(separator: " • ")).font(.hfLabel).foregroundStyle(HC.muted).lineLimit(1)
                }
                Spacer(minLength: 4)
                Button { if isLocked { showLock(item) } else if isPicked { picked.removeAll { $0.id == item.id } } else { picked.append(item) } } label: {
                    Image(systemName: isLocked ? "lock.fill" : isPicked ? "checkmark" : "plus").font(.system(size: 16, weight: .semibold)).foregroundStyle(isPicked ? HC.onLime : HC.text).frame(width: 40, height: 40).background(isPicked ? HC.lime : HC.surfaceHigh, in: Circle())
                }.buttonStyle(.plain).accessibilityLabel(isPicked ? tr("\(item.name) seçimden çıkar", "Remove \(item.name) from selection") : tr("\(item.name) yeni program için seç", "Select \(item.name) for a new program"))
            }
        }.opacity(isLocked ? 0.55 : 1)
    }

    // MARK: Filtre sayfası

    private var filterSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    section(tr("Ortam", "Place"), environmentOptions(), $environment)
                    section(tr("Hedef kas", "Target muscle"), muscleOptions(), Binding(get: { muscle }, set: { muscle = $0; muscleRole = $0.isEmpty ? "" : "primary" }))
                    if !muscle.isEmpty { section(tr("Kasın rolü", "Muscle role"), muscleRoleOptions(), $muscleRole) }
                    section(tr("Antrenman türü", "Training type"), categoryOptions(), $category)
                    HfDivider()
                    Text(tr("Daha seçici", "More precise")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime)
                    section(tr("Seviye", "Level"), levelOptions(), $level)
                    section(tr("Ekipman", "Equipment"), equipmentOptions(), $equipment)
                    section(tr("Hareket yönü", "Movement"), forceOptions(), $force)
                    section(tr("Yapı", "Structure"), mechanicOptions(), $mechanic)
                }.padding(18)
            }.background(HC.bg)
            .safeAreaInset(edge: .bottom) { HfButton(title: tr("Uygun hareketleri göster", "Show matching movements")) { search(); showFilters = false }.padding(18).background(HC.bg) }
            .navigationTitle(tr("Sonuçları daralt", "Narrow results")).navigationBarTitleDisplayMode(.inline)
            .toolbar { if activeFilterCount > 0 { ToolbarItem(placement: .confirmationAction) { Button(tr("Sıfırla", "Reset"), action: clearFilters) } } }
        }.presentationDetents([.large])
    }

    private func section(_ title: String, _ options: [Opt], _ selection: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.hfLabel.weight(.semibold)).foregroundStyle(HC.textSecondary)
            HfChipRow { ForEach(options, id: \.0) { v, l in HfChip(text: l, selected: selection.wrappedValue == v) { selection.wrappedValue = v } } }
        }
    }

    // MARK: Arama

    private func applyPreset() {
        guard !applied else { return }
        applied = true
        let p = app.libraryPreset
        environment = p.environment; equipment = p.equipment; modality = p.modality; muscle = p.muscle; muscleRole = p.muscle.isEmpty ? "" : "primary"; showMap = p.mapMode || startWithMap; grid = p.mapMode || startWithMap
        app.libraryPreset = LibraryPreset()
        search(immediately: true)
    }

    private func clearFilters() {
        muscle = ""; equipment = ""; level = ""; environment = ""; muscleRole = ""; force = ""; mechanic = ""; category = ""; modality = ""; subcategory = ""
        search()
    }

    private func search(immediately: Bool = false) {
        searchTask?.cancel()
        let q = (query, muscle, equipment, level, environment, muscleRole, force, mechanic, category, modality, subcategory)
        searchTask = Task {
            if !immediately { try? await Task.sleep(for: .milliseconds(120)) }
            guard !Task.isCancelled else { return }
            loading = true; defer { loading = false }
            do {
                let list = try await app.repo.loadExerciseCatalog(search: q.0, muscle: q.1, equipment: q.2, level: q.3, environment: q.4, muscleRole: q.5, force: q.6, mechanic: q.7, category: q.8, locale: AppLang.shared.code, modality: q.9, subcategory: q.10)
                if !Task.isCancelled { items = list }
            } catch { if !Task.isCancelled { app.report(error) } }
        }
    }
}

// MARK: - Ayrıntı & özel hareket

struct LibraryDetailSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let item: ExerciseCatalogItem
    var onStart: (ExerciseCatalogItem) -> Void
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ExerciseMotionPlayer(id: item.id, imageURLs: item.imageUrls).frame(height: 230).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    Text(tr("Çalışan kas grupları", "Muscles involved")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                    Text(tr("Ana hedef · ", "Main target · ") + item.primaryMuscles.joined(separator: ", ")).font(.hfBody).foregroundStyle(HC.lime)
                    Text(item.secondaryMuscles.isEmpty ? tr("Yardımcı kaslar · Ek grup belirtilmemiş", "Supporting muscles · No additional group listed") : tr("Yardımcı kaslar · ", "Supporting muscles · ") + item.secondaryMuscles.joined(separator: ", ")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    Text(item.equipment.isEmpty ? tr("Ekipmansız", "No equipment") : item.equipment).font(.hfLabel).foregroundStyle(HC.textSecondary)
                    if !item.instructions.isEmpty {
                        Text(tr("Nasıl yapılır?", "How to perform")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text).padding(.top, 6)
                        ForEach(Array(item.instructions.enumerated()), id: \.offset) { i, s in Text("\(i + 1). \(s)").font(.hfBody).foregroundStyle(HC.textSecondary) }
                    }
                    HfButton(title: tr("Hemen çalış", "Train now"), icon: "play.fill") { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { onStart(item) } }.padding(.top, 8)
                    HfButton(title: tr("Programa ekle", "Add to program"), icon: "plus", secondary: true) { Task { await app.useExerciseFromLibrary(item); dismiss() } }
                }.padding(18)
            }.background(HC.bg).navigationTitle(item.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.large])
    }
}

struct CustomExerciseSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var muscle = ""
    @State private var equipment = ""
    @State private var note = ""
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    HfField(title: tr("Hareket adı", "Exercise name"), text: $name)
                    HfField(title: tr("Kas grubu", "Muscle group"), text: $muscle)
                    HfField(title: tr("Ekipman", "Equipment"), text: $equipment)
                    HfField(title: tr("Uygulama notu", "Instruction note"), text: $note)
                    HfButton(title: tr("Programa ekle", "Add to program"), enabled: name.trimmingCharacters(in: .whitespaces).count >= 2) {
                        var json: [String: JSON] = ["id": JSON("custom-\(UUID().uuidString.lowercased())"), "name": JSON(name.trimmingCharacters(in: .whitespaces)), "level": JSON("custom"), "equipment": JSON(equipment.trimmingCharacters(in: .whitespaces)), "category": JSON("custom")]
                        json["primaryMuscles"] = [JSON(muscle.trimmingCharacters(in: .whitespaces).isEmpty ? "Tüm Vücut" : muscle.trimmingCharacters(in: .whitespaces))]
                        json["instructions"] = note.trimmingCharacters(in: .whitespaces).isEmpty ? [] : [JSON(note.trimmingCharacters(in: .whitespaces))]
                        let item = ExerciseCatalogItem(json: .object(json))
                        Task { await app.useExerciseFromLibrary(item); dismiss() }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Özel hareket", "Custom exercise")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } } }
        }.presentationDetents([.medium, .large])
    }
}
