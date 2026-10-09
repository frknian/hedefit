import SwiftUI

private enum DayState { case none, planned, done, missed, deferred }

/// Antrenman takvimi: ay görünümü, seçili gün, bu hafta ve otomatik dağıtım.
struct WorkoutCalendarView: View {
    @Environment(AppModel.self) private var app
    @State private var month = Dates.startOfMonth(Date())
    @State private var selected = Dates.startOfDay()
    @State private var editing = false
    @State private var showAuto = false

    private var cal: Calendar { Dates.calendar }
    private var today: Date { Dates.startOfDay() }
    private var schedule: [WorkoutSchedule] { app.dashboard?.schedule ?? [] }
    private var programs: [WorkoutProgram] { app.dashboard?.workoutPrograms ?? [] }
    private func entry(_ d: Date) -> WorkoutSchedule? { schedule.first { String($0.date.prefix(10)) == Dates.day(d) } }
    private var completed: Set<String> { Set((app.dashboard?.sessions ?? []).compactMap { $0.date.map(Dates.day) }) }

    private func state(_ d: Date) -> DayState {
        let e = entry(d)
        if completed.contains(Dates.day(d)) || e?.status == "completed" { return .done }
        if e?.status == "deferred" { return .deferred }
        if e?.status == "planned" { return d < today ? .missed : .planned }
        return .none
    }
    @MainActor private func color(_ s: DayState) -> Color {
        switch s { case .done: return HC.lime; case .planned: return HC.water; case .missed: return HC.coral; case .deferred: return HC.warning; case .none: return HC.textSecondary }
    }

    var body: some View {
        let range = cal.range(of: .day, in: .month, for: month) ?? 1..<31
        let days = range.compactMap { cal.date(byAdding: .day, value: $0 - 1, to: month) }
        let states = days.map(state)
        let leading = (cal.component(.weekday, from: month) + 5) % 7
        let cells: [Date?] = { var c: [Date?] = Array(repeating: nil, count: leading) + days; c += Array(repeating: nil, count: (7 - c.count % 7) % 7); return c }()
        ScreenScaffold(spacing: 14) {
            HfScreenHeader(title: tr("Takvim", "Calendar"), onBack: { if !app.path.isEmpty { app.path.removeLast() } }) {
                if !cal.isDate(month, equalTo: Date(), toGranularity: .month) || selected != today { HfChip(text: tr("Bugün", "Today"), selected: false) { month = Dates.startOfMonth(Date()); selected = today } }
            }
            HStack(spacing: 10) {
                stat(tr("Spor günü", "Workout days"), states.filter { $0 != .none }.count, HC.water)
                stat(tr("Yapıldı", "Done"), states.filter { $0 == .done }.count, HC.lime)
                stat(tr("Kalan", "Left"), states.filter { $0 == .planned }.count, HC.warning)
            }
            HfCard(padding: 12) {
                VStack(spacing: 6) {
                    HStack {
                        HfCircleButton(system: "chevron.left", label: tr("Önceki ay", "Previous month")) { month = cal.date(byAdding: .month, value: -1, to: month) ?? month }
                        Text(month.formatted(.dateTime.month(.wide).year().locale(AppLang.shared.locale)).firstUppercased).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.text).frame(maxWidth: .infinity)
                        HfCircleButton(system: "chevron.right", label: tr("Sonraki ay", "Next month")) { month = cal.date(byAdding: .month, value: 1, to: month) ?? month }
                    }
                    HStack(spacing: 0) {
                        ForEach(0..<7, id: \.self) { i in
                            Text(weekdayShort(i)).font(.system(size: 10, weight: .bold)).foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity)
                        }
                    }.padding(.top, 4)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
                        ForEach(Array(cells.enumerated()), id: \.offset) { _, date in
                            if let date { dayCell(date) } else { Color.clear.frame(height: 54) }
                        }
                    }
                }
            }
            selectedCard
            HfSectionHeader(title: tr("Bu hafta", "This week"))
            HfCard(padding: 14) {
                let start = Dates.weekStart()
                VStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { i in
                        let date = Dates.add(i, to: start), e = entry(date), st = state(date)
                        if i > 0 { HfDivider() }
                        Button { selected = date; month = Dates.startOfMonth(date) } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(date.formatted(.dateTime.weekday(.abbreviated).locale(AppLang.shared.locale)).firstUppercased).font(.hfBody.weight(.bold)).foregroundStyle(cal.isDate(date, inSameDayAs: today) ? HC.lime : HC.text)
                                    Text("\(cal.component(.day, from: date))").font(.hfLabel).foregroundStyle(HC.text)
                                }.frame(width: 52, alignment: .leading)
                                Text(e?.programName ?? (st == .done ? tr("Antrenman yapıldı", "Workout done") : tr("Dinlenme", "Rest"))).font(.hfBody.weight(e != nil || st == .done ? .semibold : .regular)).foregroundStyle(e != nil || st == .done ? HC.text : HC.textSecondary).lineLimit(1)
                                Spacer()
                                if let t = e?.time { Text(String(t.prefix(5))).font(.hfBody.weight(.bold)).foregroundStyle(color(st)) }
                                if st == .done { Image(systemName: "checkmark").foregroundStyle(HC.lime).font(.system(size: 14)) }
                            }.padding(.vertical, 10).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                    }
                }
            }
            HfButton(title: tr("Programları otomatik dağıt", "Auto-distribute programs"), icon: "sparkles", enabled: !programs.isEmpty) { showAuto = true }
        }
        .sheet(isPresented: $editing) { ScheduleDaySheet(date: selected, entry: entry(selected)) }
        .sheet(isPresented: $showAuto) { AutoDistributeSheet(month: month) }
    }

    private func weekdayShort(_ i: Int) -> String {
        let f = DateFormatter(); f.locale = AppLang.shared.locale
        return String(f.shortWeekdaySymbols[(i + 1) % 7].prefix(3)).firstUppercased
    }

    private func stat(_ label: String, _ value: Int, _ c: Color) -> some View {
        HfCard(padding: 10) { VStack(spacing: 0) { Text("\(value)").font(.system(size: 24, weight: .heavy)).foregroundStyle(c); Text(label).font(.hfLabel.weight(.bold)).foregroundStyle(HC.text) }.frame(maxWidth: .infinity) }
    }

    private func dayCell(_ date: Date) -> some View {
        let st = state(date), isToday = cal.isDate(date, inSameDayAs: today), isSel = cal.isDate(date, inSameDayAs: selected), e = entry(date)
        let c = color(st)
        return Button { selected = date } label: {
            VStack(spacing: 2) {
                Text("\(cal.component(.day, from: date))").font(.hfBody.weight(isToday || isSel ? .heavy : .semibold)).foregroundStyle(isSel ? HC.onLime : HC.text)
                if st == .done { Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(isSel ? HC.onLime : HC.lime) }
                else if let t = e?.time, st != .none { Text(String(t.prefix(5))).font(.system(size: 9, weight: .bold)).foregroundStyle(isSel ? HC.onLime : c).lineLimit(1) }
                else { Spacer().frame(height: 12) }
            }
            .frame(maxWidth: .infinity).frame(height: 54)
            .background(isSel ? HC.lime : st == .done ? HC.lime.opacity(0.18) : st != .none ? c.opacity(0.12) : HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay { if isToday && !isSel { RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(HC.lime, lineWidth: 1.5) } }
        }.buttonStyle(.plain)
    }

    private var selectedCard: some View {
        let st = state(selected), e = entry(selected)
        let canPlan = selected >= today && !programs.isEmpty
        return HfCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    HfIconBadge(system: st == .done ? "calendar.badge.checkmark" : "dumbbell.fill", tint: st == .none ? HC.sleep : color(st), size: 42, radius: 21)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(selected.formatted(.dateTime.day().month(.wide).weekday(.wide).locale(AppLang.shared.locale)).firstUppercased).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text)
                        Text({ switch st { case .done: return tr("Tamamlandı", "Completed"); case .planned: return tr("Planlandı", "Planned"); case .missed: return tr("Kaçırıldı", "Missed"); case .deferred: return tr("Ertelendi", "Deferred"); case .none: return tr("Dinlenme günü", "Rest day") } }()).font(.hfBody.weight(.bold)).foregroundStyle(color(st))
                    }
                    Spacer()
                }
                if let e {
                    HStack(spacing: 10) {
                        HfStatTile(label: tr("Program", "Program"), value: e.programName ?? "—")
                        HfStatTile(label: tr("Saat", "Time"), value: String(e.time.prefix(5)), valueColor: HC.lime)
                    }
                }
                if canPlan { HfButton(title: e == nil ? tr("Antrenman planla", "Plan a workout") : tr("Program veya saati değiştir", "Change program or time"), icon: e == nil ? "clock" : "pencil", secondary: e != nil) { editing = true } }
            }
        }
    }
}

private extension Array {
    func chunked(_ n: Int) -> [[Element]] { stride(from: 0, to: count, by: n).map { Array(self[$0..<Swift.min($0 + n, count)]) } }
}

struct ScheduleDaySheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let date: Date
    let entry: WorkoutSchedule?
    @State private var time = Calendar.current.date(bySettingHour: 19, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var programId: String?

    var body: some View {
        let programs = app.dashboard?.workoutPrograms ?? []
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HfCard { HStack { Text(tr("Saat", "Time")).foregroundStyle(HC.textSecondary); Spacer(); DatePicker("", selection: $time, displayedComponents: .hourAndMinute).labelsHidden().tint(HC.lime) } }
                    ForEach(programs) { p in
                        Button { programId = p.id } label: {
                            HStack { Image(systemName: programId == p.id ? "largecircle.fill.circle" : "circle").foregroundStyle(programId == p.id ? HC.lime : HC.textSecondary); Text(localizedProgramName(p.name, source: p.source)).foregroundStyle(HC.text).lineLimit(1); Spacer() }.padding(.vertical, 8)
                        }.buttonStyle(.plain)
                    }
                    if let entry { Button(tr("Takvimden kaldır", "Remove from calendar")) { Task { await app.removeSchedule(entry); dismiss() } }.foregroundStyle(HC.coral).padding(.top, 6) }
                }.padding(20)
            }.background(HC.bg)
            .navigationTitle(date.formatted(.dateTime.day().month(.wide).weekday(.wide).locale(AppLang.shared.locale)).firstUppercased).navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tr("Kaydet", "Save")) {
                        guard let p = programs.first(where: { $0.id == programId }) else { return }
                        let c = Calendar.current.dateComponents([.hour, .minute], from: time)
                        let t = String(format: "%02d:%02d", c.hour ?? 19, c.minute ?? 0)
                        Task { await app.scheduleWorkout(date: date, time: t, originalDate: entry?.originalDate, programId: p.id, programName: p.name); dismiss() }
                    }.disabled(programId == nil)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear {
            let programs = app.dashboard?.workoutPrograms ?? []
            programId = entry?.programId ?? programs.first(where: { $0.isActive })?.id ?? programs.first?.id
            if let t = entry?.time, t.count >= 5, let h = Int(t.prefix(2)), let m = Int(t.dropFirst(3).prefix(2)) { time = Calendar.current.date(bySettingHour: h, minute: m, second: 0, of: Date()) ?? time }
        }
    }
}

struct AutoDistributeSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let month: Date
    @State private var selectedIds: Set<String> = []
    /// Calendar weekday (1 = Pazar … 7 = Cumartesi) → "HH:mm"
    @State private var dayTimes: [Int: String] = [2: "19:00", 4: "19:00", 6: "19:00"]
    private let days: [Int] = [2, 3, 4, 5, 6, 7, 1]

    var body: some View {
        let programs = app.dashboard?.workoutPrograms ?? []
        let all = !programs.isEmpty && selectedIds.count == programs.count
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(tr("Programlar", "Programs")).font(.hfBody.weight(.heavy)).foregroundStyle(HC.text)
                    toggleRow(tr("Tümünü seç", "Select all"), all) { selectedIds = all ? [] : Set(programs.map(\.id)) }
                    HfDivider()
                    ForEach(programs) { p in toggleRow(localizedProgramName(p.name, source: p.source), selectedIds.contains(p.id)) { if selectedIds.contains(p.id) { selectedIds.remove(p.id) } else { selectedIds.insert(p.id) } } }
                    Text(tr("Günler ve saatler", "Days and times")).font(.hfBody.weight(.heavy)).foregroundStyle(HC.text).padding(.top, 8)
                    ForEach(days, id: \.self) { d in
                        HStack {
                            Button { if dayTimes[d] != nil { dayTimes[d] = nil } else { dayTimes[d] = dayTimes.values.first ?? "19:00" } } label: {
                                Image(systemName: dayTimes[d] != nil ? "checkmark.square.fill" : "square").foregroundStyle(dayTimes[d] != nil ? HC.lime : HC.textSecondary)
                            }.buttonStyle(.plain)
                            Text(weekdayName(d)).font(.hfBody.weight(dayTimes[d] != nil ? .bold : .regular)).foregroundStyle(HC.text); Spacer()
                            if let t = dayTimes[d] {
                                DatePicker("", selection: Binding(get: { date(from: t) }, set: { v in let c = Calendar.current.dateComponents([.hour, .minute], from: v); dayTimes[d] = String(format: "%02d:%02d", c.hour ?? 19, c.minute ?? 0) }), displayedComponents: .hourAndMinute).labelsHidden().tint(HC.lime)
                            }
                        }
                    }
                    Text(month.formatted(.dateTime.month(.wide).locale(AppLang.shared.locale)).firstUppercased + tr(" • kalan günler doldurulur", " • remaining days are filled")).font(.hfBody.weight(.semibold)).foregroundStyle(HC.text)
                    HfButton(title: tr("Dağıt", "Distribute"), enabled: !selectedIds.isEmpty && !dayTimes.isEmpty) {
                        let chosen = programs.filter { selectedIds.contains($0.id) }.map { ($0.id, $0.name) }
                        Task { await app.autoDistribute(month: month, dayTimes: dayTimes, programs: chosen); dismiss() }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Otomatik dağıt", "Auto-distribute")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } } }
        }.presentationDetents([.large])
        .onAppear { if selectedIds.isEmpty, let id = programs.first(where: { $0.isActive })?.id ?? programs.first?.id { selectedIds = [id] } }
    }

    private func toggleRow(_ title: String, _ on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { HStack { Image(systemName: on ? "checkmark.square.fill" : "square").foregroundStyle(on ? HC.lime : HC.textSecondary); Text(title).foregroundStyle(HC.text).lineLimit(1); Spacer() }.padding(.vertical, 4) }.buttonStyle(.plain)
    }
    private func weekdayName(_ d: Int) -> String {
        let f = DateFormatter(); f.locale = AppLang.shared.locale
        return f.weekdaySymbols[d - 1].firstUppercased
    }
    private func date(from t: String) -> Date {
        Calendar.current.date(bySettingHour: Int(t.prefix(2)) ?? 19, minute: Int(t.dropFirst(3).prefix(2)) ?? 0, second: 0, of: Date()) ?? Date()
    }
}
