import SwiftUI

private enum QuestionKind { case choices, focusMap, slider, performance, cycleOptIn }

private struct ProfileQuestion {
    var title: String
    var subtitle: String
    var choices: [String] = []
    var freeText = false
    var multiSelect = false
    var exclusiveChoice: String?
    var kind: QuestionKind = .choices
}

/// Sorular ve cevaplar sunucuya Türkçe kaydedilir; yalnız ekranda çevrilir.
private let profileQuestions: [ProfileQuestion] = [
    .init(title: "Ana hedefin ne?", subtitle: "Programın ve tahmini süren bu hedefe göre hazırlanır.", choices: ["Kilo verme", "Kilo alma", "Kas kazanma", "Formu koruma"]),
    .init(title: "Bunu neden istiyorsun?", subtitle: "Seni harekete geçiren kişisel nedenini yaz.", freeText: true),
    .init(title: "Seni en çok ne durdurdu?", subtitle: "Sürdürülebilir bir plan için gerçek engeli seç.", choices: ["Zaman", "Motivasyon", "Sakatlık", "Program eksikliği", "Beslenme"]),
    .init(title: "Daha önce düzenli spor yaptın mı?", subtitle: "Geçmiş deneyimin", choices: ["Hayır", "Kısa süre", "6–12 ay", "1 yıldan fazla"]),
    .init(title: "Kendini hangi seviyede görüyorsun?", subtitle: "Yoğunluğu buna göre ayarlarız.", choices: ["Başlangıç", "Orta", "İleri"]),
    .init(title: "Son 3 ayda haftada kaç gün spor yaptın?", subtitle: "Mevcut alışkanlığın", choices: ["0 gün", "1–2 gün", "3–4 gün", "5+ gün"]),
    .init(title: "Haftada kaç gün ayırabilirsin?", subtitle: "Gerçekçi bir tempo seç.", choices: ["2 gün", "3 gün", "4 gün", "5 gün", "6 gün", "7 gün"]),
    .init(title: "Bir antrenman için ne kadar süren var?", subtitle: "Isınma ve soğuma dahil ayırabileceğin toplam süre.", choices: ["15 dk", "30 dk", "45 dk", "60 dk", "75+ dk"]),
    .init(title: "Hangi antrenmanları seversin?", subtitle: "Birden fazla seçebilirsin.", choices: ["Ağırlık", "HIIT", "Koşu", "Bisiklet", "Vücut ağırlığı", "Pilates", "Mobilite", "Barre", "Düşük etkili", "Toparlanma"], multiSelect: true),
    .init(title: "Nerede çalışacaksın?", subtitle: "Egzersizler ortama göre seçilir.", choices: ["Evde", "Spor salonunda", "Açık havada", "Karışık"]),
    .init(title: "Hangi ekipmanların var?", subtitle: "Birden fazla seçebilirsin. “Ekipman yok” tek başına seçilir.", choices: ["Ekipman yok", "Dambıl", "Kettlebell", "Direnç bandı", "Barfiks barı", "Sehpa", "Halter", "TRX / halka", "Pilates topu", "Atlama ipi", "Tam salon", "Kardiyo aleti"], multiSelect: true, exclusiveChoice: "Ekipman yok"),
    .init(title: "Ağrı veya sakatlık var mı?", subtitle: "Birden fazla bölge seçebilirsin. “Yok” tek başına seçilir.", choices: ["Yok", "Bel", "Diz", "Omuz", "Boyun", "Diğer"], multiSelect: true, exclusiveChoice: "Yok"),
    .init(title: "Gün içinde ne kadar hareketlisin?", subtitle: "Günlük enerji hesabı", choices: ["Çoğunlukla oturuyorum", "Ara sıra hareket", "Aktif", "Çok aktif"]),
    .init(title: "Uyku düzenin nasıl?", subtitle: "Toparlanma kapasiten", choices: ["5 saatten az", "5–6 saat", "7–8 saat", "9+ saat"]),
    .init(title: "Koçunun bilmesi gereken başka bir şey?", subtitle: "Tercih, kısıt veya not ekleyebilirsin.", freeText: true),
    .init(title: "Hangi bölgelere odaklanmak istersin?", subtitle: "Birden fazla seçebilirsin. Haritadan da dokunabilirsin.", choices: ["Karın", "Göğüs", "Omuz", "Sırt", "Kol", "Kalça", "Bacak", "Genel Gelişim"], multiSelect: true, kind: .focusMap),
    .init(title: "Sağlık durumun hakkında bilgi ver.", subtitle: "Birden fazla seçebilirsin. “Yok” tek başına seçilir.", choices: ["Yok", "Diyabet", "Kalp Hastalığı", "İnsülin Direnci", "Hipotiroidi", "Hipertansiyon", "Yüksek Kolesterol", "Diğer"], multiSelect: true, exclusiveChoice: "Yok"),
    .init(title: "Besin alerjin var mı?", subtitle: "Birden fazla seçebilirsin. “Yok” tek başına seçilir.", choices: ["Yok", "Gluten", "Süt ürünleri", "Yumurta", "Kuruyemiş", "Deniz ürünleri", "Soya", "Diğer"], multiSelect: true, exclusiveChoice: "Yok"),
    .init(title: "Beslenme tercihin nedir?", subtitle: "Beslenme önerilerinde bunu dikkate alırız.", choices: ["Standart", "Vejetaryen", "Vegan", "Pesketaryen"]),
    .init(title: "Bu alışkanlıklardan hangisine sahipsin?", subtitle: "Birden fazla seçebilirsin. “Yok” seçeneği diğerlerini temizler.", choices: ["Yok", "Sigara", "Alkol", "Gece geç yatıyorum", "Fazla şeker tüketiyorum", "Çok oturuyorum", "Egzersizi erteliyorum", "Düzenli beslenemiyorum"], multiSelect: true, exclusiveChoice: "Yok"),
    .init(title: "Günlük stres seviyeni nasıl tanımlarsın?", subtitle: "Toparlanma ihtiyacını planlarken bu bilgiyi de dikkate alırız.", choices: ["Nadiren stres yaşıyorum", "Bazen stresli oluyorum", "Sık sık stres yaşıyorum", "Neredeyse her gün stres altındayım"]),
    .init(title: "Mevcut yaşam tarzından ne kadar memnunsun?", subtitle: "", kind: .slider),
    .init(title: "Fiziksel performansın nasıl?", subtitle: "Bilmiyorsan boş bırakabilirsin.", kind: .performance),
]

private let questionCount = 23
private let fullFlowOrder = [0, 4, 12, 6, 7, 8, 9, 10, 11] + Array(15..<questionCount)
private let quickStartOrder = [0, 4, 6, 7, 11]
private let cycleStepIndex = 100
private let cycleQuestion = ProfileQuestion(title: "Hedefit'in antrenmanlarını adet döngüsü bilgilerine göre uyarlamasını ister misin?",
                                            subtitle: "İsteğe bağlı. Tek başına antrenman kararı vermez; günlük check-in cevapların her zaman önceliklidir. İstediğin zaman Ayarlar'dan kapatabilir ve verini silebilirsin.", kind: .cycleOptIn)
/// Tamamlanma ölçütü (Android CORE_QUESTION_INDICES ile aynı).
let coreQuestionIndices = [0, 4, 6, 7, 9, 10, 11]

func coreQuestionsAnswered(_ answers: [String]) -> Bool {
    coreQuestionIndices.allSatisfy { i in i < answers.count && !answers[i].trimmingCharacters(in: .whitespaces).isEmpty }
}

private let focusAreaByMuscle: [String: String] = [
    "abdominals": "Karın", "chest": "Göğüs", "shoulders": "Omuz", "lats": "Sırt", "traps": "Sırt", "middle back": "Sırt", "lower back": "Sırt",
    "biceps": "Kol", "triceps": "Kol", "forearms": "Kol", "glutes": "Kalça", "quadriceps": "Bacak", "hamstrings": "Bacak", "calves": "Bacak",
]

@MainActor private func qt(_ text: String) -> String { tr(text, questionEnglish[text] ?? text) }

private let answerSeparator = " • "
private func selectedChoices(_ answer: String) -> Set<String> {
    Set(answer.replacingOccurrences(of: ",", with: answerSeparator).components(separatedBy: answerSeparator).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
}
private func toggledChoice(_ answer: String, _ choice: String, _ choices: [String], _ exclusive: String?) -> String {
    var selected = selectedChoices(answer)
    if choice == exclusive { return choice }
    if let exclusive { selected.remove(exclusive) }
    if selected.contains(choice) { selected.remove(choice) } else { selected.insert(choice) }
    return choices.filter(selected.contains).joined(separator: answerSeparator)
}

/// 7 belirleyici soru + sağlık/yaşam tarzı soruları; sonunda program yenilenir.
struct QuestionnaireView: View {
    @Environment(AppModel.self) private var app
    var quickStart = false
    var onClose: () -> Void

    @State private var answers: [String] = Array(repeating: "", count: questionCount)
    @State private var step = 0
    @State private var forward = true
    @State private var targetText = ""
    @State private var cycleDraft = CycleDraft()
    @State private var loaded = false
    @State private var firstRun = false

    private var profile: Profile? { app.dashboard?.profile }
    private var cycleStep: Bool { !quickStart && cycleOptInInOnboarding(profile?.gender ?? "") }
    private var order: [Int] { quickStart ? quickStartOrder : fullFlowOrder + (cycleStep ? [cycleStepIndex] : []) }

    var body: some View {
        Group {
            if app.profileSaving { PlanBuildingScene() }
            else if let profile, loaded { content(profile) }
            else { HC.bg.ignoresSafeArea() }
        }
        .background(HC.bg.ignoresSafeArea())
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded, let p = profile else { return }
        answers = (p.historyAnswers + Array(repeating: "", count: questionCount)).prefix(questionCount).map { $0 == "Kas alma" ? "Kas kazanma" : $0 }
        targetText = p.targetWeightKg.map { String($0) } ?? String(format: "%.0f", suggestedTarget(p))
        firstRun = coreQuestionIndices.contains { p.historyAnswers.indices.contains($0) ? p.historyAnswers[$0].trimmingCharacters(in: .whitespaces).isEmpty : true }
        loaded = true
    }

    private func suggestedTarget(_ p: Profile) -> Double {
        let w = p.weightKg ?? 75
        return max(p.goal.localizedCaseInsensitiveContains("ver") ? w - 6 : w + 4, 40)
    }

    @ViewBuilder private func content(_ profile: Profile) -> some View {
        let index = order[min(step, order.count - 1)]
        let question = index == cycleStepIndex ? cycleQuestion : profileQuestions[index]
        let isLast = step == order.count - 1
        let target = Double(targetText.replacingOccurrences(of: ",", with: "."))
        let weeks = GoalScience.weeks(current: profile.weightKg, target: target)
        let canContinue = index == cycleStepIndex ? cycleDraft.enabled != nil : (!(answers[safe: index] ?? "").isEmpty || question.freeText || question.kind == .performance)
        let fraction = Double(step + 1) / Double(order.count)
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button { goBack() } label: { Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold)).foregroundStyle(HC.text).frame(width: 44, height: 44) }.accessibilityLabel(tr("Geri", "Back"))
                ProgressView(value: fraction).tint(HC.lime).scaleEffect(y: 1.6).animation(.easeOut(duration: 0.45), value: step)
                Text("\(step + 1)/\(order.count)").font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary).padding(.horizontal, 8)
            }.padding(.horizontal, 8)
            Text(stepMessage(fraction, isLast)).font(.hfBody).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center).padding(.horizontal, 22)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if quickStart && step == 0 { Text(tr("Bu \(order.count) yanıtla ilk programını hemen hazırlayacağız.", "With these \(order.count) answers we will build your first program right away.")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime) }
                    Text(qt(question.title)).font(.system(size: 28, weight: .black)).foregroundStyle(HC.text)
                    if !question.subtitle.isEmpty { Text(qt(question.subtitle)).foregroundStyle(HC.textSecondary) }
                    Spacer().frame(height: 6)
                    questionBody(question, index, profile, target: target, weeks: weeks)
                }.padding(.horizontal, 22).padding(.vertical, 12).frame(maxWidth: .infinity, alignment: .leading)
                .id(step).transition(.asymmetric(insertion: .move(edge: forward ? .trailing : .leading).combined(with: .opacity), removal: .opacity))
            }.scrollDismissesKeyboard(.interactively)
            if canContinue || question.multiSelect || isLast {
                HfButton(title: isLast ? (quickStart || firstRun ? tr("Planımı oluştur", "Build my plan") : tr("Kaydet ve planı yenile", "Save & refresh plan")) : tr("Devam", "Continue"), enabled: canContinue) {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred(); next(profile, target: target, weeks: weeks)
                }.padding(.horizontal, 20).padding(.vertical, 14)
            }
        }
    }

    @ViewBuilder private func questionBody(_ q: ProfileQuestion, _ index: Int, _ profile: Profile, target: Double?, weeks: Int?) -> some View {
        if q.freeText {
            TextEditor(text: Binding(get: { answers[index] }, set: { answers[index] = $0 })).scrollContentBackground(.hidden).foregroundStyle(HC.text).frame(minHeight: 150).padding(12)
                .background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(alignment: .topLeading) { if answers[index].isEmpty { Text(tr("Yanıtını yaz… (boş bırakabilirsin)", "Type your answer… (optional)")).foregroundStyle(HC.textSecondary).padding(18).allowsHitTesting(false) } }
        } else if q.kind == .focusMap {
            let chosen = selectedChoices(answers[index])
            let toggle = { (area: String) in answers[index] = toggledChoice(answers[index], area, q.choices, nil) }
            MuscleMap(selected: Set(focusAreaByMuscle.filter { chosen.contains($0.value) }.keys), onSelect: { id in if let area = focusAreaByMuscle[id] { toggle(area) } }, description: tr("Odaklanmak istediğin bölgelere dokun", "Tap the areas you want to focus on")).padding(.vertical, 4)
            FlowLayout(spacing: 8) { ForEach(q.choices, id: \.self) { a in HfChip(text: qt(a), selected: chosen.contains(a)) { toggle(a) } } }
        } else if q.kind == .slider {
            let value = Int(answers[index]) ?? 5
            VStack(spacing: 8) {
                Text("\(value)").font(.system(size: 56, weight: .black)).foregroundStyle(HC.lime)
                Slider(value: Binding(get: { Double(value) }, set: { answers[index] = String(Int($0)) }), in: 0...10, step: 1).tint(HC.lime)
                HStack { Text(tr("Hiç memnun değilim", "Not satisfied at all")); Spacer(); Text(tr("Çok memnunum", "Very satisfied")) }.font(.hfSmall).foregroundStyle(HC.textSecondary)
            }.padding(.top, 16).onAppear { if answers[index].isEmpty { answers[index] = "5" } }
        } else if q.kind == .performance {
            let squat = firstMatch("Squat: (\\d+)", answers[index]), pull = firstMatch("Barfiks: (\\d+)", answers[index])
            let write = { (s: String, p: String) in answers[index] = [s.isEmpty ? nil : "Squat: \(s)", p.isEmpty ? nil : "Barfiks: \(p)"].compactMap { $0 }.joined(separator: ", ") }
            HfField(title: tr("Squat (tekrar)", "Squat (reps)"), text: Binding(get: { squat }, set: { write(String($0.filter(\.isNumber).prefix(3)), pull) }), keyboard: .numberPad)
            HfField(title: tr("Barfiks (tekrar)", "Pull-ups (reps)"), text: Binding(get: { pull }, set: { write(squat, String($0.filter(\.isNumber).prefix(3))) }), keyboard: .numberPad)
            Text(tr("Bu bilgi, programının ve koçunun seviyeni daha iyi anlamasına yardım eder.", "This helps your program and coach understand your level.")).font(.hfBody).foregroundStyle(HC.textSecondary).padding(16).frame(maxWidth: .infinity, alignment: .leading).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16))
        } else if q.kind == .cycleOptIn {
            CycleOptInStep(draft: Binding(get: { cycleDraft }, set: { v in
                let wasEnabled = cycleDraft.enabled
                cycleDraft = v
                if v.enabled == false, wasEnabled != false, step < order.count - 1 { autoAdvance(from: step, profile: profile) }
            }))
        } else {
            ForEach(q.choices, id: \.self) { choice in
                let selected = q.multiSelect ? selectedChoices(answers[index]).contains(choice) : answers[index] == choice
                Button {
                    UISelectionFeedbackGenerator().selectionChanged()
                    answers[index] = q.multiSelect ? toggledChoice(answers[index], choice, q.choices, q.exclusiveChoice) : choice
                    if !q.multiSelect && index != 0 && step < order.count - 1 { autoAdvance(from: step, profile: profile) }
                } label: {
                    HStack {
                        Text(qt(choice)).font(.hfTitleM.weight(selected ? .bold : .medium)).foregroundStyle(HC.text).multilineTextAlignment(.leading); Spacer()
                        if selected { Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(HC.onLime).frame(width: 26, height: 26).background(HC.lime, in: Circle()).transition(.scale) }
                    }
                    .padding(18).background(selected ? HC.lime.opacity(0.16) : HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(selected ? HC.lime : HC.divider, lineWidth: selected ? 2 : 1))
                }.buttonStyle(.plain).animation(.spring(duration: 0.35, bounce: 0.4), value: selected)
            }
            if index == 0, !answers[0].isEmpty, answers[0] != "Formu koruma" {
                VStack(alignment: .leading, spacing: 10) {
                    HfField(title: tr("Hedef kilo (kg)", "Target weight (kg)"), text: $targetText, keyboard: .decimalPad)
                    if target != nil, let weeks {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(tr("Tahmini \(weeks) hafta", "About \(weeks) weeks")).font(.hfTitleL).foregroundStyle(HC.lime)
                            Text(tr("Sağlıklı ve sürdürülebilir haftalık değişim hızına göre hesaplandı.", "Based on a healthy, sustainable weekly rate of change.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 16))
                    }
                }.padding(.top, 6)
            }
        }
    }

    private func firstMatch(_ pattern: String, _ s: String) -> String {
        guard let r = s.range(of: pattern, options: .regularExpression) else { return "" }
        return String(s[r].filter(\.isNumber))
    }

    private func stepMessage(_ fraction: Double, _ last: Bool) -> String {
        if step == 0 { return tr("Isınma turu — rahat ol, birlikte başlıyoruz 👊", "Warm-up round — relax, we start together 👊") }
        if fraction < 0.5 { return tr("Seni tanıdıkça plan netleşiyor, devam 🔥", "The plan sharpens as we get to know you 🔥") }
        if fraction < 0.75 { return tr("Yarıyı geçtin, tam formdasın 💪", "Past halfway — you're in great form 💪") }
        return last ? tr("Son soru! Planın hazırlanıyor 🏁", "Final question! Your plan is next 🏁") : tr("Son cevaplar — bırakma, neredeyse bitti 🏁", "Last answers — almost there 🏁")
    }

    private func goBack() { if step > 0 { forward = false; withAnimation(.easeInOut(duration: 0.3)) { step -= 1 } } else { onClose() } }

    private func autoAdvance(from shown: Int, profile: Profile) {
        Task {
            try? await Task.sleep(for: .milliseconds(320))
            if step == shown, shown < order.count - 1 { forward = true; withAnimation(.easeInOut(duration: 0.3)) { step += 1 } }
        }
    }

    private func next(_ profile: Profile, target: Double?, weeks: Int?) {
        if step < order.count - 1 { forward = true; withAnimation(.easeInOut(duration: 0.3)) { step += 1 }; return }
        if cycleStep, cycleDraft.enabled == true { Task { _ = await app.adaptive.saveCycleProfile(cycleDraft.profile) } }
        let stored = answers.enumerated().map { i, a in a.isEmpty && profileQuestions[i].freeText ? "Yok" : a }
        let update = ProfileUpdate(displayName: profile.displayName, age: profile.age, gender: profile.gender, heightCm: profile.heightCm, weightKg: profile.weightKg,
                                   goalType: answers[0].isEmpty ? profile.goal : answers[0], targetWeightKg: target, targetWeeks: weeks,
                                   environment: answers[9].isEmpty ? profile.environment : answers[9], equipment: answers[10].isEmpty ? profile.equipment : answers[10], historyAnswers: stored)
        Task { if await app.saveProfile(update, regeneratePlan: true) { onClose() } }
    }
}

private extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}

/// Pushed (Ayarlar'dan) sürüm.
struct QuestionnaireRoute: View {
    @Environment(AppModel.self) private var app
    var body: some View { QuestionnaireView { if !app.path.isEmpty { app.path.removeLast() } } }
}

// MARK: - Plan hazırlanıyor

struct PlanBuildingScene: View {
    @State private var step = 0
    @State private var rotation = 0.0
    var body: some View {
        let steps = [tr("Hedefin analiz ediliyor…", "Analysing your goal…"), tr("Ekipmanına uygun hareketler seçiliyor…", "Picking moves for your equipment…"), tr("Haftalık tempon ayarlanıyor…", "Setting your weekly pace…"), tr("Kalori hedefin hesaplanıyor…", "Calculating your calorie target…")]
        VStack(spacing: 28) {
            ZStack {
                Circle().stroke(HC.divider, lineWidth: 10)
                Circle().trim(from: 0, to: 0.3).stroke(HC.lime, style: StrokeStyle(lineWidth: 10, lineCap: .round)).rotationEffect(.degrees(rotation))
            }.frame(width: 120, height: 120)
            VStack(spacing: 8) {
                Text(tr("Programın hazırlanıyor", "Building your program")).font(.hfHeadline.weight(.bold)).foregroundStyle(HC.text)
                Text(steps[step % steps.count]).foregroundStyle(HC.textSecondary).id(step).transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).background(HC.bg)
        .onAppear { withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) { rotation = 360 } }
        .task { while !Task.isCancelled { try? await Task.sleep(for: .milliseconds(900)); withAnimation { step += 1 } } }
    }
}

// MARK: - Kişisel bilgiler

/// Yaş / cinsiyet / boy / kilo eksikse ilk girişte (kapatılamaz) gösterilir.
struct PersonalDetailsView: View {
    @Environment(AppModel.self) private var app
    @State private var age = ""
    @State private var gender = "Erkek"
    @State private var height = ""
    @State private var weight = ""
    @State private var error: String?
    @State private var saving = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(tr("Profilini Tamamla", "Complete Your Profile")).font(.hfHeadline.weight(.bold)).foregroundStyle(HC.text)
                Text(tr("Sana özel kalori, su ve antrenman hedeflerini doğru hesaplayabilmemiz için temel bilgilerini girmen gerekiyor.", "To accurately calculate your personal calorie, water, and workout plans, we need a few details.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                HfDivider()
                HfField(title: tr("Yaş (Örn: 25)", "Age (e.g. 25)"), text: Binding(get: { age }, set: { age = String($0.filter(\.isNumber).prefix(3)); error = nil }), keyboard: .numberPad)
                Text(tr("Cinsiyet", "Gender")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                HStack(spacing: 8) {
                    ForEach([("Erkek", tr("Erkek", "Male")), ("Kadın", tr("Kadın", "Female")), ("Diğer", tr("Diğer", "Other"))], id: \.0) { code, label in
                        let on = gender.caseInsensitiveCompare(code) == .orderedSame
                        Button { gender = code } label: {
                            Text(label).font(.hfBody.weight(.semibold)).foregroundStyle(on ? HC.lime : HC.textSecondary).frame(maxWidth: .infinity, minHeight: 44)
                                .background(on ? HC.lime.opacity(0.2) : HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 10)).overlay(RoundedRectangle(cornerRadius: 10).stroke(on ? HC.lime : .clear))
                        }.buttonStyle(.plain)
                    }
                }
                HfField(title: tr("Boy (cm, Örn: 178)", "Height (cm, e.g. 178)"), text: Binding(get: { height }, set: { height = String($0.filter(\.isNumber).prefix(3)); error = nil }), keyboard: .numberPad)
                HfField(title: tr("Kilo (kg, Örn: 74.5)", "Weight (kg, e.g. 74.5)"), text: Binding(get: { weight }, set: { weight = String($0.filter { $0.isNumber || $0 == "." || $0 == "," }.prefix(6)); error = nil }), keyboard: .decimalPad)
                if let error { Text(error).font(.hfSmall).foregroundStyle(HC.coral) }
                HfButton(title: saving ? tr("Kaydediliyor…", "Saving…") : tr("Kaydet ve devam et", "Save and continue"), enabled: !saving, action: save)
            }.padding(22)
        }
        .background(HC.bg).scrollDismissesKeyboard(.interactively)
        .onAppear {
            guard let p = app.dashboard?.profile else { return }
            age = p.age.map(String.init) ?? ""; gender = p.gender.isEmpty ? "Erkek" : p.gender
            height = p.heightCm.map { String(Int($0)) } ?? ""; weight = p.weightKg.map { String(format: "%.1f", $0) } ?? ""
        }
    }

    private func save() {
        guard let p = app.dashboard?.profile else { return }
        guard let a = Int(age), (13...100).contains(a) else { error = tr("Lütfen geçerli bir yaş girin (13–100).", "Please enter a valid age (13-100)."); return }
        guard let h = Double(height), (100...250).contains(h) else { error = tr("Lütfen geçerli bir boy girin (100–250 cm).", "Please enter a valid height in cm (100-250)."); return }
        guard let w = Double(weight.replacingOccurrences(of: ",", with: ".")), (30...300).contains(w) else { error = tr("Lütfen geçerli bir kilo girin (30–300 kg).", "Please enter a valid weight in kg (30-300)."); return }
        saving = true
        let update = ProfileUpdate(displayName: p.displayName, age: a, gender: gender, heightCm: h, weightKg: w, goalType: p.goal, targetWeightKg: p.targetWeightKg, targetWeeks: p.targetWeeks, environment: p.environment, equipment: p.equipment, historyAnswers: p.historyAnswers)
        Task { _ = await app.saveProfile(update); saving = false }
    }
}
