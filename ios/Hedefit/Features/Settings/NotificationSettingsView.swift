import SwiftUI

/// Bildirim takvimi: antrenman hatırlatmaları, tartı, challenge, gün ve saat.
struct NotificationSettingsView: View {
    @Environment(AppModel.self) private var app

    /// Calendar weekday: 1 = Pazar … 7 = Cumartesi. Pazartesi'den başlayarak gösterilir.
    private let days: [(Int, String, String)] = [(2, "Pzt", "Mon"), (3, "Sal", "Tue"), (4, "Çar", "Wed"), (5, "Per", "Thu"), (6, "Cum", "Fri"), (7, "Cmt", "Sat"), (1, "Paz", "Sun")]

    var body: some View {
        let p = app.prefs
        let time = Binding<Date>(
            get: { Calendar.current.date(bySettingHour: p.notificationHour, minute: p.notificationMinute, second: 0, of: Date()) ?? Date() },
            set: { d in let c = Calendar.current.dateComponents([.hour, .minute], from: d); update { $0.notificationHour = c.hour ?? 19; $0.notificationMinute = c.minute ?? 0 } })
        ScreenScaffold(spacing: 16) {
            HfScreenHeader(title: tr("Bildirim Takvimi", "Notification Calendar")) { if !app.path.isEmpty { app.path.removeLast() } }
            HfCard {
                HStack(spacing: 12) {
                    Image(systemName: "bell.badge.fill").foregroundStyle(HC.lime).frame(width: 44, height: 44).background(HC.lime.opacity(0.18), in: Circle())
                    VStack(alignment: .leading, spacing: 2) { Text(tr("Antrenman hatırlatmaları", "Workout reminders")).font(.hfTitleM).foregroundStyle(HC.text); Text(tr("Seçtiğin günlerde seni plana döndürür.", "Brings you back to your plan on selected days.")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                    Spacer()
                    Toggle("", isOn: Binding(get: { p.notificationsEnabled }, set: { on in Task { await setEnabled(on) } })).labelsHidden().tint(HC.lime)
                }
            }
            HfCard {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) { Text(tr("Haftalık tartı hatırlatması", "Weekly weigh-in")).font(.hfTitleM).foregroundStyle(HC.text); Text(tr("Kilonu kaydetmen için 09:00'da bildirim. Antrenman hatırlatmaları açık olmalı.", "A 09:00 nudge to log your weight. Needs workout reminders turned on.")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                        Spacer()
                        Toggle("", isOn: Binding(get: { p.weighInReminderEnabled }, set: { v in update { $0.weighInReminderEnabled = v } })).labelsHidden().tint(HC.lime)
                    }
                    if p.weighInReminderEnabled { dayRow(selected: { $0 == p.weighInReminderDay }, size: 38) { d in update { $0.weighInReminderDay = d } } }
                }
            }
            HfCard {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) { Text(tr("Challenge hatırlatmaları", "Challenge reminders")).font(.hfTitleM).foregroundStyle(HC.text); Text(tr("Günde en fazla bir kez, 18:00'de ve yalnızca bugünkü challenge görevin henüz yapılmadıysa.", "At most one nudge a day at 18:00, only if today's challenge task isn't done yet.")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                    Spacer()
                    Toggle("", isOn: Binding(get: { p.challengeRemindersEnabled }, set: { v in update { $0.challengeRemindersEnabled = v } })).labelsHidden().tint(HC.lime)
                }
            }
            Text(tr("GÜNLER", "DAYS")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
            dayRow(selected: { p.notificationDays.contains($0) }, size: 43) { d in update { if $0.notificationDays.contains(d) { $0.notificationDays.remove(d) } else { $0.notificationDays.insert(d) } } }
            Text(tr("SAAT", "TIME")).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
            HfCard {
                HStack(spacing: 12) {
                    Image(systemName: "clock.fill").foregroundStyle(HC.lime)
                    Text(tr("Hatırlatma saati", "Reminder time")).foregroundStyle(HC.text)
                    Spacer()
                    DatePicker("", selection: time, displayedComponents: .hourAndMinute).labelsHidden().tint(HC.lime)
                }
            }
        }
    }

    private func dayRow(selected: @escaping (Int) -> Bool, size: CGFloat, action: @escaping (Int) -> Void) -> some View {
        HStack {
            ForEach(days, id: \.0) { d, trLabel, enLabel in
                let on = selected(d)
                Button { action(d) } label: {
                    Text(tr(trLabel, enLabel)).font(.system(size: 12, weight: .bold)).foregroundStyle(on ? HC.onLime : HC.textSecondary).frame(width: size, height: size).background(on ? HC.lime : HC.surfaceHigh, in: Circle())
                }.buttonStyle(.plain)
                if d != days.last!.0 { Spacer(minLength: 0) }
            }
        }
    }

    private func update(_ change: (inout AppPreferences) -> Void) {
        var p = app.prefs; change(&p); app.prefs = p
        Task { await NotificationService.shared.reschedule(p) }
    }

    private func setEnabled(_ on: Bool) async {
        if on, !(await NotificationService.shared.requestAuthorization()) {
            app.notify(tr("Bildirim izni verilmedi. Ayarlar'dan izin verebilirsin.", "Notification permission denied. You can enable it in Settings."))
            return
        }
        update { $0.notificationsEnabled = on }
    }
}
