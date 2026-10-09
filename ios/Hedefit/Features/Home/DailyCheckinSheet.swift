import SwiftUI

/// Kısa günlük check-in: enerji, uyku, kas ağrısı, ağrı ve müsait süre (hepsi tek dokunuş).
struct DailyCheckinSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var energy: Int?
    @State private var sleep: Int?
    @State private var soreness = 0
    @State private var pain = 0
    @State private var minutes = 30

    var body: some View {
        let a = app.adaptive
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(tr("5 dokunuş. Antrenmanını gününe göre uyarlamamıza yardım eder.", "Five taps. Helps us adapt your workout to your day.")).font(.hfBody).foregroundStyle(HC.textSecondary)
                    if a.checkinSaved {
                        Text(tr("Kaydedildi ✓", "Saved ✓")).font(.hfTitleL.weight(.heavy)).foregroundStyle(HC.lime)
                        if let c = a.checkinCycle { Text(tr("Döngü: gün \(c.cycleDay)", "Cycle: day \(c.cycleDay)") + (c.periodLikely ? tr(" • adet dönemi", " • period") : "") + tr(". Bu yalnızca bilgi; antrenman kararında senin cevapların öncelikli.", ". This is only context; your answers always come first.")).font(.hfBody).foregroundStyle(HC.textSecondary) }
                        HfButton(title: tr("Tamam", "Done")) { a.checkinSaved = false; dismiss() }
                    } else {
                        let energyLabels = [tr("Bitkin", "Drained"), tr("Düşük", "Low"), tr("Orta", "Okay"), tr("İyi", "Good"), tr("Zinde", "Great")]
                        let levels = [tr("Yok", "None"), tr("Hafif", "Mild"), tr("Orta", "Moderate"), tr("Yüksek", "High")]
                        let sleepLabels = [tr("5 sa altı", "Under 5 h"), tr("5–6 sa", "5–6 h"), tr("7–8 sa", "7–8 h"), tr("9 sa+", "9 h+")]
                        group(tr("Enerji", "Energy")) { ForEach(Array(CheckinChoices.energy.enumerated()), id: \.offset) { i, v in HfChip(text: energyLabels[i], selected: energy == v) { energy = v } } }
                        group(tr("Uyku", "Sleep")) { ForEach(sleepLabels.indices, id: \.self) { i in HfChip(text: sleepLabels[i], selected: sleep == i) { sleep = i } } }
                        group(tr("Kas ağrısı", "Soreness")) { ForEach(Array(CheckinChoices.soreness.enumerated()), id: \.offset) { i, v in HfChip(text: levels[i], selected: soreness == v) { soreness = v } } }
                        group(tr("Ağrı (eklem/sakatlık)", "Pain (joint/injury)")) { ForEach(Array(CheckinChoices.pain.enumerated()), id: \.offset) { i, v in HfChip(text: levels[i], selected: pain == v) { pain = v } } }
                        group(tr("Müsait süre", "Time available")) { ForEach(CheckinChoices.minutes, id: \.self) { v in HfChip(text: v == 60 ? tr("60+ dk", "60+ min") : tr("\(v) dk", "\(v) min"), selected: minutes == v) { minutes = v } } }
                        HfButton(title: tr("Kaydet", "Save"), loading: a.busy, enabled: energy != nil && sleep != nil) {
                            let (hours, quality) = CheckinChoices.sleep[sleep ?? 2]
                            Task { _ = await a.save(Checkin(day: Dates.day(), energy: energy ?? 6, sleepQuality: quality, sleepHours: hours, soreness: soreness, pain: pain, availableMinutes: minutes)) }
                        }
                    }
                }.padding(20)
            }.background(HC.bg).navigationTitle(tr("Bugün nasılsın?", "How are you today?")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Kapat", "Close")) { a.checkinSaved = false; dismiss() } } }
        }
        .onAppear { a.checkinSaved = false
            if let c = a.checkinToday { energy = c.energy; sleep = CheckinChoices.sleep.firstIndex { $0.quality == c.sleepQuality }; soreness = c.soreness; pain = c.pain; minutes = c.availableMinutes ?? 30 } }
        .presentationDetents([.large])
    }

    private func group<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) { Text(title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text); FlowLayout(spacing: 8) { content() } }
    }
}
