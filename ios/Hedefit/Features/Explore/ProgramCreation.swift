import SwiftUI

/// Alt sayfa şablonu: geri düğmeli başlık + kaydırılabilir içerik.
struct SubPage<Content: View>: View {
    @Environment(AppModel.self) private var app
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var content: Content
    var body: some View {
        ScreenScaffold(spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                HfScreenHeader(title: title) { if !app.path.isEmpty { app.path.removeLast() } }
                if let subtitle { Text(subtitle).font(.hfSmall).foregroundStyle(HC.muted).padding(.leading, 56) }
            }
            content
        }
    }
}

private struct ModuleCard: View {
    let icon: String, tint: Color, title: String, desc: String, bullets: [String]
    var tag: String? = nil
    var action: () -> Void
    var body: some View {
        HfCard(padding: 18, onTap: action) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    HfIconBadge(system: icon, tint: tint, size: 44, radius: 14)
                    VStack(alignment: .leading, spacing: 2) { Text(title).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text); Text(desc).font(.hfSmall).foregroundStyle(HC.muted).multilineTextAlignment(.leading) }
                    Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.muted)
                }
                if let tag { HfPill(text: tag, color: tint) }
                ForEach(bullets, id: \.self) { b in HStack(spacing: 8) { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(tint); Text(b).font(.hfSmall).foregroundStyle(HC.textSecondary) } }
            }
        }
    }
}

struct ProgramHubView: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        SubPage(title: tr("Program oluştur", "Create a program"), subtitle: tr("Sana uygun yolu seç", "Choose the way that suits you")) {
            ModuleCard(icon: "bolt.fill", tint: HC.coral, title: tr("Hızlı antrenman", "Quick workout"), desc: tr("Şu an, süren ve yorgunluğuna göre", "Right now, based on your time and energy"),
                       bullets: [tr("Süre, yorgunluk ve bölge seç", "Set duration, fatigue and target area"), tr("Program saniyeler içinde hazır", "Program is ready in seconds")], tag: tr("EN HIZLI", "FASTEST")) { app.push(.quickWorkout) }
            ModuleCard(icon: "sparkles", tint: HC.lime, title: tr("Hedefit AI ile oluştur", "Create with Hedefit AI"), desc: tr("Profiline göre kişisel program", "A personal plan from your profile"),
                       bullets: [tr("Hedef, seviye ve ekipmanını dikkate alır", "Uses your goal, level and equipment"), tr("İlerlemene göre uyarlanır", "Adapts as you progress")], tag: tr("ÖNERİLEN", "RECOMMENDED")) { app.push(.aiProgram) }
            ModuleCard(icon: "square.grid.2x2.fill", tint: HC.water, title: tr("Hazır programlar", "Ready programs"), desc: tr("Kanıtlanmış bir şablon seç", "Pick a proven template"),
                       bullets: [tr("İtiş / Çekiş / Bacak ve Tüm Vücut", "Push / Pull / Legs and Full Body"), tr("Tüm hareketler sonradan düzenlenebilir", "Every movement stays editable")]) { app.push(.readyPrograms) }
            ModuleCard(icon: "pencil", tint: HC.warning, title: tr("Program yap", "Build a program"), desc: tr("Antrenmanlarını kendin adlandır ve sırala", "Name and order your own sessions"),
                       bullets: [tr("Hareket Atlası'ndan hareket seç", "Pick exercises from the Movement Atlas"), tr("Set, tekrar ve dinlenmeyi sen belirle", "Set sets, reps and rest yourself")]) { if app.canCreateCustomProgram() { app.push(.customProgram) } }
            HfDivider()
            HfNavRow(icon: "figure.arms.open", tint: HC.coral, title: tr("Bölgesel programlar", "Body-part programs"), subtitle: tr("Tek bir kas grubuna odaklan", "Focus on a single muscle group")) { app.push(.regionalPrograms) }
        }
    }
}

// MARK: - Hızlı antrenman

let homeEquipmentOptions: [(String, String, String)] = [("dumbbell", "Dambıl", "Dumbbell"), ("kettlebell", "Kettlebell", "Kettlebell"), ("band", "Direnç bandı", "Resistance band"), ("pull_up_bar", "Barfiks barı", "Pull-up bar"),
                                                         ("bench", "Sehpa", "Bench"), ("barbell", "Halter", "Barbell"), ("suspension", "TRX / halka", "TRX / rings"), ("stability_ball", "Pilates topu", "Stability ball"), ("jump_rope", "Atlama ipi", "Jump rope"), ("ab_wheel", "Karın tekerleği", "Ab wheel")]

@MainActor func regionalRegions() -> [(String, String)] {
    let en = AppLang.shared.en
    return en ? [("chest", "Chest"), ("back", "Back"), ("lats", "Lats"), ("traps", "Traps"), ("neck", "Neck"), ("shoulders", "Shoulders"), ("biceps", "Front arm"), ("triceps", "Back arm"), ("forearms", "Wrist"), ("abdominals", "Abs"), ("legs", "Leg"), ("glutes", "Hip"), ("calves", "Calf"), ("abductors", "Outer hip")]
        : [("chest", "Göğüs"), ("back", "Sırt"), ("lats", "Kanat"), ("traps", "Trapez"), ("neck", "Boyun"), ("shoulders", "Omuz"), ("biceps", "Ön kol"), ("triceps", "Arka kol"), ("forearms", "Bilek"), ("abdominals", "Karın"), ("legs", "Bacak"), ("glutes", "Kalça"), ("calves", "Baldır"), ("abductors", "Dış kalça")]
}

struct QuickWorkoutView: View {
    @Environment(AppModel.self) private var app
    @State private var duration = 30
    @State private var fatigue = "normal"
    @State private var environment = "home"
    @State private var owned: Set<String> = []
    @State private var level = "intermediate"
    @State private var selected: Set<String> = ["full_body"]

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        HfCard(padding: 18) { VStack(alignment: .leading, spacing: 10) { Text(title).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text); content() } }
    }

    var body: some View {
        let allRegions = [("full_body", tr("Tüm vücut", "Full body"))] + regionalRegions()
        let fatigueOptions = [("dinc", tr("Dinç", "Fresh")), ("normal", "Normal"), ("yorgun", tr("Yorgun", "Tired"))]
        let levels = [("beginner", tr("Başlangıç", "Beginner")), ("intermediate", tr("Orta", "Intermediate")), ("advanced", tr("İleri", "Advanced"))]
        SubPage(title: tr("Hızlı antrenman", "Quick workout")) {
            section(tr("Ne kadar süren var?", "How much time do you have?")) { HfChipRow { ForEach([15, 20, 30, 45, 60], id: \.self) { v in HfChip(text: tr("\(v) dk", "\(v) min"), selected: duration == v) { duration = v } } } }
            section(tr("Yorgunluk durumun?", "How's your energy?")) { HfSegmented(options: fatigueOptions.map(\.1), selection: Binding(get: { fatigueOptions.firstIndex { $0.0 == fatigue } ?? 1 }, set: { fatigue = fatigueOptions[$0].0 })) }
            section(tr("Seviye", "Level")) { HfSegmented(options: levels.map(\.1), selection: Binding(get: { levels.firstIndex { $0.0 == level } ?? 1 }, set: { level = levels[$0].0 })) }
            section(tr("Nerede antrenman yapıyorsun?", "Where are you training?")) {
                HfSegmented(options: [tr("Ev", "Home"), tr("Spor salonu", "Gym")], selection: Binding(get: { environment == "gym" ? 1 : 0 }, set: { environment = $0 == 1 ? "gym" : "home" }))
                if environment == "home" {
                    Text(tr("Elinde ne var? (hiçbiri = sadece vücut ağırlığı)", "What do you have? (nothing = bodyweight only)")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                    FlowLayout(spacing: 8) { ForEach(homeEquipmentOptions, id: \.0) { k, t, e in HfChip(text: tr(t, e), selected: owned.contains(k)) { if owned.contains(k) { owned.remove(k) } else { owned.insert(k) } } } }
                }
            }
            section(tr("Hangi bölgeler? (en fazla 3)", "Which areas? (up to 3)")) {
                FlowLayout(spacing: 8) {
                    ForEach(allRegions, id: \.0) { k, l in
                        HfChip(text: l, selected: selected.contains(k)) {
                            if k == "full_body" { selected = ["full_body"] }
                            else if selected.contains(k) { selected.remove(k) }
                            else { var s = selected; s.remove("full_body"); if s.count < 3 { s.insert(k); selected = s } }
                        }
                    }
                }
            }
            HfButton(title: app.planGenerating ? tr("Hazırlanıyor…", "Preparing…") : tr("Antrenmanımı oluştur", "Create my workout"), icon: "bolt.fill", loading: app.planGenerating, enabled: !selected.isEmpty) {
                let chosen = allRegions.filter { selected.contains($0.0) }
                Task { await app.generateQuickWorkout(regions: chosen, durationMinutes: duration, fatigue: fatigue, environment: environment, owned: Array(owned), level: level); app.path.removeAll() }
            }
        }
    }
}

// MARK: - AI program

struct AIProgramView: View {
    @Environment(AppModel.self) private var app
    var body: some View {
        let p = app.profile
        SubPage(title: "Hedefit AI", subtitle: tr("Profiline göre kişisel program", "Personal program from your profile")) {
            HfCard(padding: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(tr("AI'IN KULLANACAĞI BİLGİLER", "WHAT THE AI USES")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.muted)
                    HfListRow(icon: "flag.fill", tint: HC.lime, title: tr("Hedef", "Goal"), subtitle: p?.goal.nilIfEmpty ?? "—", chevron: false, action: nil)
                    HfDivider()
                    HfListRow(icon: "mappin.circle.fill", tint: HC.water, title: tr("Antrenman ortamı", "Where you train"), subtitle: p?.environment.nilIfEmpty ?? "—", chevron: false, action: nil)
                    HfDivider()
                    HfListRow(icon: "dumbbell.fill", tint: HC.warning, title: tr("Ekipman", "Equipment"), subtitle: p?.equipment.nilIfEmpty ?? tr("Vücut ağırlığı", "Bodyweight"), chevron: false, action: nil)
                    HfDivider()
                    HfListRow(icon: "scalemass.fill", tint: HC.sleep, title: tr("Vücut", "Body"), subtitle: [p?.age.map { tr("\($0) yaş", "\($0) y") }, p?.heightCm.map { "\(Int($0)) cm" }, p?.weightKg.map { String(format: "%.1f kg", $0) }].compactMap { $0 }.joined(separator: " • ").nilIfEmpty ?? "—", chevron: false, action: nil)
                }
            }
            HfButton(title: app.planGenerating ? tr("Oluşturuluyor…", "Creating…") : tr("Programımı oluştur", "Create my program"), icon: "sparkles", loading: app.planGenerating) { Task { await app.generateAIPlan(); app.path.removeAll() } }
            HfButton(title: tr("Tercihlerimi güncelle", "Update my preferences"), icon: "pencil", secondary: true) { app.push(.questionnaire) }
        }
    }
}

// MARK: - Hazır programlar

struct ReadyProgramsView: View {
    @Environment(AppModel.self) private var app
    @State private var filter = "all"
    @State private var preview: String?
    private let groups: [String: String] = ["push_a": "push", "push_b": "push", "pull_a": "pull", "pull_b": "pull", "leg_a": "legs", "leg_b": "legs", "full_a": "full", "full_b": "full"]

    private func minutes(_ list: [WorkoutExercise]) -> Int { list.reduce(0) { $0 + $1.sets * (45 + $1.restSeconds) } / 60 + 8 }

    var body: some View {
        let filters = [("all", tr("Tümü", "All")), ("push", tr("İtiş", "Push")), ("pull", tr("Çekiş", "Pull")), ("legs", tr("Bacak", "Legs")), ("full", tr("Tüm vücut", "Full body"))]
        SubPage(title: tr("Hazır programlar", "Ready programs"), subtitle: tr("\(readyProgramKeys.count) şablon • önizlemek için dokun", "\(readyProgramKeys.count) templates • tap to preview")) {
            HfChipRow { ForEach(filters, id: \.0) { k, l in HfChip(text: l, selected: filter == k) { filter = k } } }
            ForEach(readyProgramKeys.filter { filter == "all" || groups[$0] == filter }, id: \.self) { key in
                let list = app.readyProgram(key)?.1 ?? []
                let tint: Color = { switch groups[key] { case "push": return HC.lime; case "pull": return HC.water; case "legs": return HC.warning; default: return HC.sleep } }()
                HfCard(padding: 14, onTap: { preview = key }) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 12) {
                            ZStack { Circle().fill(tint.opacity(0.16)).frame(width: 40, height: 40); Image(systemName: "dumbbell.fill").foregroundStyle(tint) }
                            VStack(alignment: .leading, spacing: 2) { Text(readyProgramTitle(key)).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text); Text(tr("\(list.count) hareket • ~\(minutes(list)) dk", "\(list.count) exercises • ~\(minutes(list)) min")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                            Spacer(); Image(systemName: "chevron.right").foregroundStyle(HC.muted)
                        }
                        HStack(spacing: 6) { ForEach(list.prefix(5)) { ex in ExerciseMedia(id: ex.id).frame(width: 46, height: 46).clipShape(RoundedRectangle(cornerRadius: 10)) } }
                        Text(Array(NSOrderedSet(array: list.map(\.area))).compactMap { $0 as? String }.joined(separator: " • ")).font(.hfLabel.weight(.bold)).foregroundStyle(tint)
                    }
                }
            }
        }
        .sheet(item: Binding(get: { preview.map { StringID(id: $0) } }, set: { preview = $0?.id })) { item in ReadyPreviewSheet(key: item.id, minutes: minutes) }
    }
}

struct StringID: Identifiable { let id: String }

private struct ReadyPreviewSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let key: String
    let minutes: ([WorkoutExercise]) -> Int
    var body: some View {
        let data = app.readyProgram(key)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(data?.0 ?? key).font(.system(size: 24, weight: .black)).foregroundStyle(HC.text)
                    if let list = data?.1 { Text(tr("\(list.count) hareket • ~\(minutes(list)) dk • \(list.reduce(0) { $0 + $1.sets }) set", "\(list.count) exercises • ~\(minutes(list)) min • \(list.reduce(0) { $0 + $1.sets }) sets")).font(.hfBody).foregroundStyle(HC.textSecondary) }
                    ForEach(data?.1 ?? []) { ex in
                        HStack(spacing: 12) {
                            ExerciseMedia(id: ex.id).frame(width: 58, height: 58).clipShape(RoundedRectangle(cornerRadius: 12))
                            VStack(alignment: .leading) { Text(ex.name).font(.hfBody.weight(.bold)).foregroundStyle(HC.text).lineLimit(2); Text(ex.area).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                            Spacer()
                            VStack(alignment: .trailing) { Text("\(ex.sets) × \(ex.reps)").font(.system(size: 15, weight: .heavy)).foregroundStyle(HC.lime); Text(tr("\(ex.restSeconds) sn dinlenme", "\(ex.restSeconds)s rest")).font(.hfSmall).foregroundStyle(HC.muted) }
                        }.padding(10).background(HC.surface, in: RoundedRectangle(cornerRadius: 16))
                    }
                    HfButton(title: app.planGenerating ? tr("Ekleniyor…", "Adding…") : tr("Programlarıma ekle", "Add to my programs"), icon: "plus", loading: app.planGenerating) { Task { await app.addReadyProgram(key); dismiss(); app.path.removeAll() } }
                }.padding(18)
            }.background(HC.bg).navigationTitle("").navigationBarTitleDisplayMode(.inline).toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.large])
    }
}

// MARK: - Kendi programın

struct CustomProgramView: View {
    @Environment(AppModel.self) private var app
    @State private var name = ""
    @State private var titles = ["Push", "Pull", "Legs"]
    var body: some View {
        let valid = name.trimmingCharacters(in: .whitespaces).count >= 2 && !titles.isEmpty && titles.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        SubPage(title: tr("Program yap", "Build a program"), subtitle: tr("Antrenmanlarını adlandır ve sırala", "Name and order your sessions")) {
            HfCard { HfField(title: tr("Program adı", "Program name"), text: $name) }
            HfSectionHeader(title: tr("Antrenmanlar", "Sessions"), trailing: nil)
            Text("\(titles.count) / 7").font(.hfSmall).foregroundStyle(HC.muted).padding(.top, -10)
            HfCard(padding: 10) {
                VStack(spacing: 4) {
                    ForEach(titles.indices, id: \.self) { i in
                        HStack(spacing: 6) {
                            Text("\(i + 1)").font(.hfLabel.weight(.heavy)).foregroundStyle(HC.textSecondary).frame(width: 28, height: 28).background(HC.surfaceHigh, in: Circle())
                            TextField(tr("Antrenman adı", "Workout name"), text: Binding(get: { titles[i] }, set: { titles[i] = String($0.prefix(40)) })).padding(10).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 10)).foregroundStyle(HC.text)
                            Button { titles.swapAt(i, i - 1) } label: { Image(systemName: "chevron.up") }.disabled(i == 0)
                            Button { titles.swapAt(i, i + 1) } label: { Image(systemName: "chevron.down") }.disabled(i == titles.count - 1)
                            Button { titles.remove(at: i) } label: { Image(systemName: "trash").foregroundStyle(HC.coral) }.disabled(titles.count <= 1)
                        }.foregroundStyle(HC.text).buttonStyle(.plain).padding(.vertical, 2)
                    }
                    if titles.count < 7 { Button { titles.append("") } label: { Label(tr("Antrenman ekle", "Add session"), systemImage: "plus").font(.system(size: 14, weight: .bold)).foregroundStyle(HC.lime).frame(maxWidth: .infinity, minHeight: 40) } }
                }
            }
            HfButton(title: tr("Programı kaydet", "Save program"), icon: "checkmark", enabled: valid) {
                let days = titles.enumerated().map { WorkoutProgramDay(weekday: $0.offset + 1, title: $0.element.trimmingCharacters(in: .whitespaces)) }
                Task { if await app.createCustomProgram(name: name.trimmingCharacters(in: .whitespaces), trainingDays: days) { app.path.removeAll(); app.openLibrary() } }
            }
        }
    }
}

// MARK: - Bölgesel programlar

struct RegionalProgramsView: View {
    @Environment(AppModel.self) private var app
    @State private var region: (String, String)?
    @State private var items: [ExerciseCatalogItem] = []
    @State private var loading = false
    @State private var includeConnected = false
    @State private var detail: ExerciseCatalogItem?
    private let connected: [String: String] = ["chest": "triceps", "triceps": "chest", "back": "biceps", "lats": "biceps", "biceps": "back", "shoulders": "triceps", "legs": "glutes", "glutes": "legs", "calves": "legs", "abductors": "glutes", "forearms": "biceps", "traps": "neck", "neck": "traps"]

    var body: some View {
        let regions = regionalRegions()
        let connectedKey = region.flatMap { connected[$0.0] }
        let connectedLabel = connectedKey.flatMap { k in regions.first { $0.0 == k }?.1 }
        SubPage(title: region?.1 ?? tr("Bölgesel programlar", "Body-part programs"), subtitle: region == nil ? tr("Bir kas grubuna odaklan", "Focus on a muscle group") : (loading ? tr("Hareketler yükleniyor…", "Loading movements…") : tr("\(items.count) hareket", "\(items.count) movements"))) {
            if region == nil {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                    ForEach(regions, id: \.0) { r in
                        Button { region = r; Task { await load(r.0) } } label: { Text(r.1).font(.system(size: 14, weight: .heavy)).foregroundStyle(HC.text).frame(maxWidth: .infinity, minHeight: 64).padding(8).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous)) }.buttonStyle(PressableStyle())
                    }
                }
            } else {
                Button { region = nil; items = [] } label: { Label(tr("Bölgeleri göster", "All regions"), systemImage: "chevron.left").font(.hfBody.weight(.semibold)).foregroundStyle(HC.lime) }
                if loading { ProgressView().tint(HC.lime).frame(maxWidth: .infinity).padding() }
                if !loading && items.isEmpty { Text(tr("Bu bölge için hareket bulunamadı.", "No movement was found for this region.")).foregroundStyle(HC.textSecondary) }
                if !items.isEmpty {
                    HfCard(padding: 8) {
                        VStack(spacing: 0) {
                            ForEach(Array(items.enumerated()), id: \.element.id) { i, ex in
                                if i > 0 { HfDivider() }
                                Button { detail = ex } label: {
                                    HStack(spacing: 12) {
                                        ExerciseMedia(id: ex.id, imageURLs: ex.imageUrls).frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 14))
                                        VStack(alignment: .leading) { Text(ex.name).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text).lineLimit(1); Text([ex.primaryMuscles.joined(separator: ", "), ex.equipment].filter { !$0.isEmpty }.joined(separator: " • ")).font(.hfSmall).foregroundStyle(HC.muted).lineLimit(1) }
                                        Spacer()
                                    }.padding(.vertical, 8).padding(.horizontal, 8)
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                    if let connectedLabel {
                        HfCard(padding: 14, onTap: { includeConnected.toggle() }) {
                            HStack(spacing: 12) { HfIconBadge(system: "link", tint: HC.sleep, size: 34, radius: 11); Text(tr("Bağlı bölgeyi de ekle: \(connectedLabel)", "Also add connected area: \(connectedLabel)")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Spacer(); Toggle("", isOn: $includeConnected).labelsHidden().tint(HC.lime) }
                        }
                    }
                    HfButton(title: includeConnected && connectedLabel != nil ? tr("2 program oluştur", "Create 2 programs") : tr("\(region!.1) programı oluştur", "Create \(region!.1) program"), icon: "plus", loading: app.planGenerating) {
                        let r = region!
                        Task { await app.generateRegionalPlan(muscle: r.0, label: r.1, connected: includeConnected ? connectedKey.flatMap { k in connectedLabel.map { (k, $0) } } : nil); app.path.removeAll() }
                    }
                }
            }
        }
        .sheet(item: $detail) { ExerciseDetailSheet(item: $0) }
    }

    private func load(_ muscle: String) async {
        loading = true; includeConnected = false; defer { loading = false }
        items = ((try? await app.repo.loadExerciseCatalog(muscle: muscle, muscleRole: "primary", category: "strength", locale: AppLang.shared.code)) ?? []).sorted { ($0.mechanic == "compound" ? 0 : 1, $0.name) < ($1.mechanic == "compound" ? 0 : 1, $1.name) }
    }
}

/// Hareket detayı: animasyon + açıklama + adımlar.
struct ExerciseDetailSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let item: ExerciseCatalogItem
    var canAdd = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ExerciseMotionPlayer(id: item.id, imageURLs: item.imageUrls).frame(height: 240).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    FlowLayout(spacing: 6) {
                        if !item.level.isEmpty { HfTag(text: item.level) }; if !item.equipment.isEmpty { HfTag(text: item.equipment) }
                        ForEach(item.primaryMuscles, id: \.self) { HfPill(text: $0) }
                    }
                    if !item.description.isEmpty { Text(item.description).font(.hfBody).foregroundStyle(HC.textSecondary) }
                    if !item.instructions.isEmpty {
                        Text(tr("Nasıl yapılır?", "How to perform")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                        ForEach(Array(item.instructions.enumerated()), id: \.offset) { i, s in HStack(alignment: .top, spacing: 8) { Text("\(i + 1).").font(.hfBody.weight(.bold)).foregroundStyle(HC.lime); Text(s).font(.hfBody).foregroundStyle(HC.textSecondary) } }
                    }
                    if !item.tips.isEmpty { Text(tr("İpuçları", "Tips")).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text); ForEach(item.tips, id: \.self) { Text("• \($0)").font(.hfBody).foregroundStyle(HC.textSecondary) } }
                    if canAdd { HfButton(title: tr("Programa ekle", "Add to program"), icon: "plus") { Task { await app.useExerciseFromLibrary(item); dismiss() } } }
                }.padding(18)
            }.background(HC.bg).navigationTitle(item.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.large])
    }
}
