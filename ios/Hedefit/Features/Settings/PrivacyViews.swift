import SwiftUI

// MARK: - Koç hafızası

struct AiMemoryView: View {
    @Environment(AppModel.self) private var app
    @State private var memories: [AiMemoryItem]?
    @State private var busy = false
    @State private var error: String?
    @State private var confirmAll = false

    var body: some View {
        ScreenScaffold(spacing: 12) {
            HfScreenHeader(title: tr("Koç hafızası", "Coach memory"), onBack: { if !app.path.isEmpty { app.path.removeLast() } }) {
                if busy { ProgressView().tint(HC.lime) } else { HfCircleButton(system: "arrow.clockwise", label: tr("Yenile", "Refresh")) { Task { await load() } } }
            }
            HfCard {
                HStack(alignment: .top, spacing: 12) {
                    HfIconBadge(system: "brain.head.profile", tint: HC.lime, size: 40, radius: 20)
                    Text(tr("FitKoç, sana daha uygun yanıt verebilmek için hakkında kısa notlar (tercih, hedef, kısıt, alışkanlık) tutar. Notlar hesabına bağlı olarak sunucuda saklanır, bağlam olarak yapay zekâ sağlayıcısına gönderilir ve en fazla 60 tanedir. Buradan istediğini kaldırabilirsin.",
                            "Fit Coach keeps short notes about you (preferences, goals, constraints, habits) to answer more fittingly. They're stored on our server linked to your account, sent to the AI provider as context, and capped at 60 notes. You can remove any of them here.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                }
            }
            if let error { Text(error).font(.hfSmall).foregroundStyle(HC.coral) }
            if let memories {
                if memories.isEmpty { HfCard(padding: 20) { Text(tr("Henüz kayıtlı not yok.", "No notes saved yet.")).foregroundStyle(HC.textSecondary).frame(maxWidth: .infinity, alignment: .leading) } }
                else {
                    ForEach(memories) { m in
                        HfCard(padding: 14) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(aiMemoryTypeLabel(m.type)).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
                                    Text("\(m.key): \(m.value)").font(.hfBody.weight(.semibold)).foregroundStyle(HC.text)
                                    Text((m.userExplicit ? tr("Koça sen söyledin", "You told the coach") : tr("Sohbetlerinden çıkarıldı", "Inferred from your chats")) + (m.updatedAt.map { " · \($0.prefix(10))" } ?? "")).font(.hfLabel).foregroundStyle(HC.muted)
                                }
                                Spacer()
                                Button { Task { await delete(m.id) } } label: { Image(systemName: "trash").foregroundStyle(HC.coral) }.disabled(busy).accessibilityLabel(tr("Notu sil", "Delete note"))
                            }
                        }
                    }
                    Button(tr("Tüm notları sil", "Delete all notes")) { confirmAll = true }.font(.hfBody.weight(.bold)).foregroundStyle(HC.coral).disabled(busy).frame(maxWidth: .infinity)
                }
            }
        }
        .task { await load() }
        .alert(tr("Tüm notlar silinsin mi?", "Delete all notes?"), isPresented: $confirmAll) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Hepsini sil", "Delete all"), role: .destructive) { Task { await deleteAll() } }
        } message: { Text(tr("FitKoç kayıtlı tercihlerini, hedeflerini ve kısıtlarını unutur. Bu işlem geri alınamaz.", "Fit Coach will forget your saved preferences, goals and constraints. This can't be undone.")) }
    }

    private func load() async {
        busy = true; defer { busy = false }
        do { memories = try await app.repo.aiMemories(); error = nil } catch { self.error = error.friendly }
    }
    private func delete(_ id: String) async {
        busy = true; defer { busy = false }
        do { try await app.repo.deleteAiMemory(id); memories?.removeAll { $0.id == id } } catch { self.error = error.friendly }
    }
    private func deleteAll() async {
        busy = true; defer { busy = false }
        do { for m in memories ?? [] { try await app.repo.deleteAiMemory(m.id) }; memories = [] } catch { self.error = error.friendly }
    }
}

// MARK: - Gizlilik ve rızalar

struct ConsentSettingsView: View {
    @Environment(AppModel.self) private var app
    @State private var status: ConsentStatus?
    @State private var busy = false
    @State private var error: String?
    @State private var withdrawing: (health: Bool, cross: Bool)?
    @State private var document: LegalDocument?

    var body: some View {
        ScreenScaffold(spacing: 12) {
            HfScreenHeader(title: tr("Gizlilik ve rızalar", "Privacy and consents"), onBack: { if !app.path.isEmpty { app.path.removeLast() } }) { if busy { ProgressView().tint(HC.lime) } }
            if let error { Text(error).font(.hfSmall).foregroundStyle(HC.coral) }
            consentCard(title: tr("Sağlık verilerinin işlenmesi", "Health data processing"),
                        body: tr("Boy, kilo, ölçüler, antrenman, beslenme, uyku, adım ve bildirdiğin ağrı/sakatlık bilgileri.", "Height, weight, measurements, workouts, nutrition, sleep, steps and any pain/injury info you report."),
                        givenAt: status?.healthDataConsentAt) { withdrawing = (true, false) }
            consentCard(title: tr("Yurt dışına aktarım", "Transfer abroad"),
                        body: tr("Hizmetin sunulması için yurt dışındaki sunuculara ve yapay zekâ sağlayıcılarına (Supabase, Cloudflare, OpenAI, Google) aktarım.", "Transfer to servers and AI providers abroad (Supabase, Cloudflare, OpenAI, Google) to provide the service."),
                        givenAt: status?.crossBorderConsentAt) { withdrawing = (false, true) }
            HfCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("Onaylanan metin sürümü: ", "Accepted text version: ") + (status?.textVersion ?? "—")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    Button(tr("KVKK Aydınlatma Metni'ni oku", "Read the KVKK Privacy Notice")) { document = .kvkk }.foregroundStyle(HC.lime)
                    Button(tr("Gizlilik Politikası'nı oku", "Read the Privacy Policy")) { document = .privacy }.foregroundStyle(HC.lime)
                    Text(tr("Veri talepleri (erişim, düzeltme, silme) için \(legalContact) adresine yaz.", "For data requests (access, correction, deletion) write to \(legalContact).")).font(.hfLabel).foregroundStyle(HC.muted)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .task { await load() }
        .sheet(item: $document) { LegalSheet(document: $0) }
        .alert(tr("Rıza geri çekilsin mi?", "Withdraw consent?"), isPresented: Binding(get: { withdrawing != nil }, set: { if !$0 { withdrawing = nil } })) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) { withdrawing = nil }
            Button(tr("Geri çek ve çıkış yap", "Withdraw and sign out"), role: .destructive) { if let w = withdrawing { Task { await withdraw(w.health, w.cross) } }; withdrawing = nil }
        } message: { Text(tr("Hedefit bu rıza olmadan çalışamaz; bu yüzden oturumun kapanır ve bir sonraki girişte rızan yeniden istenir. Verilerin SİLİNMEZ; silmek için ayarlardan \"Hesabı kalıcı sil\"i kullan ya da \(legalContact) adresine yaz.", "Hedefit can't work without this consent, so you'll be signed out and asked again next time you sign in. Your data is NOT deleted; to delete it, use \"Delete account permanently\" in settings, or write to \(legalContact).")) }
    }

    private func consentCard(title: String, body: String, givenAt: String?, onWithdraw: @escaping () -> Void) -> some View {
        HfCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    HfIconBadge(system: "checkmark.shield.fill", tint: givenAt != nil ? HC.lime : HC.textSecondary, size: 38, radius: 19)
                    VStack(alignment: .leading) {
                        Text(title).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                        Text(status == nil ? tr("Yükleniyor…", "Loading…") : givenAt != nil ? tr("Verildi: ", "Given on ") + givenAt!.prefix(10) : tr("Verilmedi", "Not given")).font(.hfLabel).foregroundStyle(HC.textSecondary)
                    }
                }
                Text(body).font(.hfSmall).foregroundStyle(HC.textSecondary)
                if givenAt != nil { Button(tr("Rızayı geri çek", "Withdraw consent"), action: onWithdraw).font(.hfBody.weight(.bold)).foregroundStyle(HC.coral).disabled(busy) }
            }.frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func load() async {
        busy = true; defer { busy = false }
        do { status = try await AuthService.shared.consentStatus(); error = nil } catch { self.error = error.friendly }
    }
    private func withdraw(_ health: Bool, _ cross: Bool) async {
        busy = true; defer { busy = false }
        do { try await AuthService.shared.withdrawConsents(health: health, crossBorder: cross); await app.signOut() } catch { self.error = error.friendly }
    }
}

// MARK: - Sağlık verisi ve kişiselleştirme

struct HealthPrivacyView: View {
    @Environment(AppModel.self) private var app
    @State private var editingCycle = false
    @State private var confirmCycle = false
    @State private var confirmAll = false

    var body: some View {
        let m = app.adaptive, state = m.health
        let showCycle = cycleSettingsVisible(app.dashboard?.profile.gender ?? "")
        let tracking = state.cycle?.profile.trackingEnabled == true
        ScreenScaffold(spacing: 12) {
            HfScreenHeader(title: tr("Sağlık verisi ve kişiselleştirme", "Health data and personalization"), onBack: { if !app.path.isEmpty { app.path.removeLast() } }) { if state.busy { ProgressView().tint(HC.lime) } }
            if state.loaded && !state.available { Text(tr("Kişiselleştirme ayarları henüz kullanılamıyor. Kullanılabilir olana kadar hiçbir veri toplanmaz.", "Personalization settings aren't available yet. Nothing is collected until they are.")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
            HfCard {
                VStack(spacing: 14) {
                    toggleRow(tr("Planımı günlük check-in'e göre uyarla", "Adapt my plan to my daily check-in"), tr("Kapalıyken planın olduğu gibi kalır.", "When off, your plan stays exactly as written."), state.personalization.adaptiveEnabled, state.available) { v in Task { await m.updatePersonalization(adaptive: v) } }
                    if showCycle { toggleRow(tr("Fit Koç döngü ve check-in bağlamımı kullanabilsin", "Let Fit Coach use my cycle and check-in context"), tr("Yalnızca Premium. Varsayılan kapalı; kapalıyken koç bu bilgileri hiç görmez.", "Premium only. Off by default; when off, the coach never sees this information."), state.personalization.aiHealthContextEnabled, state.available) { v in Task { await m.updatePersonalization(aiHealthContext: v) } }
                    }
                }
            }
            if showCycle {
                HfCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(tr("Döngü takibi (isteğe bağlı)", "Cycle tracking (optional)")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                        Text(tracking ? tr("Açık. Planını yalnızca küçük ölçüde etkiler; günlük check-in cevapların her zaman önceliklidir.", "On. It only nudges your plan slightly, and your daily check-in always comes first.") : tr("Kapalı. Döngünle ilgili hiçbir şey saklanmaz.", "Off. Nothing about your cycle is stored.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                        HfButton(title: tracking ? tr("Döngü bilgilerini düzenle", "Edit cycle details") : tr("Döngü takibini aç", "Turn on cycle tracking"), enabled: state.available) { editingCycle = true }
                        if tracking {
                            Button(tr("Kapat (verim kalsın)", "Turn off (keep my data)")) { Task { await m.updatePersonalization(cycleOff: true); await m.loadHealthPrivacy() } }.foregroundStyle(HC.textSecondary)
                            Button(tr("Döngü verimi sil", "Delete my cycle data")) { confirmCycle = true }.foregroundStyle(HC.coral)
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            HfCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("Tüm sağlık verisini sil", "Delete all health data")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                    Text(tr("Döngü bilgilerini, günlük check-in'leri ve bu ayarları sunucularımızdan kalıcı olarak kaldırır.", "Removes your cycle information, daily check-ins and these settings from our servers for good.")).font(.hfSmall).foregroundStyle(HC.textSecondary)
                    Button(tr("Tüm sağlık verisini sil", "Delete all health data")) { confirmAll = true }.foregroundStyle(HC.coral).disabled(!state.available)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
            Text(tr("Bunların hiçbiri tıbbi tavsiye değildir.", "None of this is medical advice.")).font(.hfLabel).foregroundStyle(HC.muted)
        }
        .task { await m.loadHealthPrivacy() }
        .sheet(isPresented: $editingCycle) { CycleEditSheet(current: state.cycle?.profile) { profile in Task { if await m.saveCycleProfile(profile) { await m.updatePersonalization(); await m.loadHealthPrivacy() } } } }
        .alert(tr("Döngü verisi silinsin mi?", "Delete cycle data?"), isPresented: $confirmCycle) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Sil", "Delete"), role: .destructive) { Task { await m.deleteCycleData() } }
        } message: { Text(tr("Döngü bilgilerin sunucularımızdan kalıcı olarak silinir ve döngüye göre uyarlama kapanır.", "Your cycle information is permanently deleted from our servers and cycle-based adaptation turns off.")) }
        .alert(tr("Tüm sağlık verisi silinsin mi?", "Delete all health data?"), isPresented: $confirmAll) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Hepsini sil", "Delete everything"), role: .destructive) { Task { await m.deleteAllHealthData() } }
        } message: { Text(tr("Döngü bilgilerin, tüm günlük check-in'ler ve bu kişiselleştirme ayarları kalıcı olarak silinir. Hesabın, antrenmanların ve öğünlerin etkilenmez.", "Your cycle information, all daily check-ins and these personalization settings are permanently deleted. Your account, workouts and meals are not affected.")) }
    }

    private func toggleRow(_ title: String, _ body: String, _ on: Bool, _ enabled: Bool, _ change: @escaping (Bool) -> Void) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) { Text(title).font(.hfBody.weight(.bold)).foregroundStyle(HC.text); Text(body).font(.hfLabel).foregroundStyle(HC.textSecondary) }
            Spacer()
            Toggle("", isOn: Binding(get: { on }, set: change)).labelsHidden().tint(HC.lime).disabled(!enabled)
        }
    }
}

// MARK: - Döngü formu (ayarlar + anket)

struct CycleDraft: Equatable {
    var enabled: Bool?
    var lastPeriod: Date?
    var cycleLength = 28
    var periodLength = 5
    var regularity = "unknown"

    init(enabled: Bool? = nil, profile: CycleProfile? = nil) {
        self.enabled = enabled
        if let profile {
            lastPeriod = profile.lastPeriodStart.flatMap(Dates.parse)
            cycleLength = profile.cycleLengthDays ?? 28; periodLength = profile.periodLengthDays ?? 5; regularity = profile.regularity
        }
    }
    var profile: CycleProfile { CycleProfile(trackingEnabled: enabled == true, lastPeriodStart: lastPeriod.map(Dates.day), cycleLengthDays: cycleLength, periodLengthDays: periodLength, regularity: regularity) }
}

struct CycleOptInStep: View {
    @Binding var draft: CycleDraft
    var body: some View {
        VStack(spacing: 12) {
            choice(draft.enabled == true, tr("Aktifleştir", "Enable")) { draft.enabled = true }
            choice(draft.enabled == false, tr("Şimdi değil", "Not now")) { draft.enabled = false }
            if draft.enabled == true {
                VStack(alignment: .leading, spacing: 14) {
                    Text(tr("Son adetin ne zaman başladı?", "When did your last period start?")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                    HStack {
                        DatePicker("", selection: Binding(get: { draft.lastPeriod ?? Date() }, set: { draft.lastPeriod = $0 }), in: ...Date(), displayedComponents: .date).labelsHidden().tint(HC.lime)
                            .opacity(draft.lastPeriod == nil ? 0.5 : 1)
                        if draft.lastPeriod != nil { Button(tr("Bilmiyorum", "I don't know")) { draft.lastPeriod = nil }.foregroundStyle(HC.textSecondary) }
                        else { Text(tr("Tarih seç", "Pick a date")).font(.hfSmall).foregroundStyle(HC.muted) }
                    }
                    stepper(tr("Döngü uzunluğu (gün)", "Cycle length (days)"), draft.cycleLength, 21...45) { draft.cycleLength = $0; if draft.periodLength >= $0 { draft.periodLength = $0 - 1 } }
                    stepper(tr("Adet süresi (gün)", "Period length (days)"), draft.periodLength, 1...min(10, draft.cycleLength - 1)) { draft.periodLength = $0 }
                    Text(tr("Döngün ne kadar düzenli?", "How regular is your cycle?")).font(.hfBody.weight(.bold)).foregroundStyle(HC.text)
                    FlowLayout(spacing: 8) {
                        ForEach([("regular", tr("Düzenli", "Regular")), ("somewhat_irregular", tr("Biraz düzensiz", "Somewhat irregular")), ("irregular", tr("Düzensiz", "Irregular")), ("unknown", tr("Bilmiyorum", "Not sure"))], id: \.0) { v, l in
                            HfChip(text: l, selected: draft.regularity == v) { draft.regularity = v }
                        }
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func choice(_ selected: Bool, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack { Text(label).font(.hfTitleM.weight(selected ? .bold : .medium)).foregroundStyle(HC.text); Spacer(); if selected { Image(systemName: "checkmark").foregroundStyle(HC.lime) } }
                .padding(18).background(selected ? HC.lime.opacity(0.16) : HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(selected ? HC.lime : HC.divider, lineWidth: selected ? 2 : 1))
        }.buttonStyle(.plain)
    }

    private func stepper(_ label: String, _ value: Int, _ range: ClosedRange<Int>, _ change: @escaping (Int) -> Void) -> some View {
        HStack(spacing: 10) {
            Text(label).foregroundStyle(HC.text); Spacer()
            Button { if value > range.lowerBound { change(value - 1) } } label: { Text("−").frame(width: 40, height: 40).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 12)) }
            Text("\(value)").font(.system(size: 16, weight: .black)).foregroundStyle(HC.lime).frame(width: 40)
            Button { if value < range.upperBound { change(value + 1) } } label: { Text("+").frame(width: 40, height: 40).background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 12)) }
        }.foregroundStyle(HC.text)
    }
}

struct CycleEditSheet: View {
    @Environment(\.dismiss) private var dismiss
    let current: CycleProfile?
    var onSave: (CycleProfile) -> Void
    @State private var draft = CycleDraft()
    var body: some View {
        NavigationStack {
            ScrollView { CycleOptInStep(draft: $draft).padding(20) }.background(HC.bg)
                .navigationTitle(tr("Döngü bilgileri", "Cycle details")).navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button(tr("Kaydet", "Save")) { onSave(draft.profile); dismiss() } }
                }
        }.onAppear { draft = CycleDraft(enabled: true, profile: current) }
    }
}
