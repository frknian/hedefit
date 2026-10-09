import SwiftUI

struct CoachView: View {
    @Environment(AppModel.self) private var app
    @State private var input = ""
    @State private var renaming = false
    @State private var draftName = ""
    @State private var clearing = false

    private var name: String { app.prefs.coachName.isEmpty ? tr("Fit Koç", "Fit Coach") : app.prefs.coachName }

    var body: some View {
        VStack(spacing: 8) {
            header.padding(.horizontal, 18).padding(.top, 8)
            if app.dashboard != nil {
                Button { app.push(.coachChallenge) } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "trophy.fill").foregroundStyle(HC.lime)
                        Text(tr("Bana challenge oluştur", "Create a challenge for me")).font(.system(size: 14, weight: .bold)).foregroundStyle(HC.text)
                        Spacer(); Text(tr("Kişisel plan", "Personal plan")).font(.hfSmall).foregroundStyle(HC.muted); Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(HC.muted)
                    }.padding(.horizontal, 14).padding(.vertical, 11).background(HC.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }.buttonStyle(.plain).padding(.horizontal, 18)
            }
            CoachChatBody(input: $input, context: nil, quick: [tr("Bugünkü antrenmanım?", "What's my workout today?"), tr("Akşam ne yesem?", "What should I eat tonight?"), tr("Toparlanma kontrolü", "Recovery check"),
                                                              tr("Beslenmemi değerlendir", "Review my nutrition"), tr("Antrenman programımı değerlendir", "Review my workout plan")], showAvatar: true)
        }
        .onAppear { app.coach.greetIfNeeded(); app.markTried("coach") }
        .alert(tr("Koçunun adı", "Coach name"), isPresented: $renaming) {
            TextField(tr("İsim", "Name"), text: $draftName)
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Kaydet", "Save")) { let n = String(draftName.trimmingCharacters(in: .whitespaces).prefix(24)); if n.count >= 2 { app.prefs.coachName = n } }
        }
        .alert(tr("Sohbet temizlensin mi?", "Clear this chat?"), isPresented: $clearing) {
            Button(tr("Vazgeç", "Cancel"), role: .cancel) {}
            Button(tr("Temizle", "Clear"), role: .destructive) { app.coach.clear() }
        } message: { Text(tr("Bu cihazdaki sohbet mesajları kaldırılacak.", "Messages on this device will be removed.")) }
    }

    private var header: some View {
        let c = app.coach
        let usage: String = {
            if let limit = c.usageLimit { return c.usageUsed.map { tr("Bugün \(max(limit - $0, 0)) soru hakkın kaldı", "\(max(limit - $0, 0)) questions left today") } ?? tr("Günlük \(limit) soru hakkı", "\(limit) questions daily") }
            return tr("Bulut AI", "Cloud AI")
        }()
        return HStack(spacing: 12) {
            CoachAvatar(size: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.hfTitle).foregroundStyle(HC.text).lineLimit(1)
                Text(usage).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime)
            }
            Spacer()
            Menu {
                Button(tr("Koçun adını değiştir", "Rename coach")) { draftName = name; renaming = true }
                Button(tr("Hafızayı yönet", "Manage memory")) { app.push(.aiMemory) }
                Button(tr("Sohbeti temizle", "Clear chat"), role: .destructive) { clearing = true }
            } label: { Image(systemName: "ellipsis").font(.system(size: 16, weight: .bold)).foregroundStyle(HC.text).frame(width: 44, height: 44).background(HC.surfaceHigh, in: Circle()) }
        }
    }
}

struct CoachAvatar: View {
    var size: CGFloat = 36
    var body: some View { Image("FitCoach").resizable().scaledToFit().padding(size * 0.08).frame(width: size, height: size).background(HC.surfaceHigh, in: Circle()).overlay(Circle().stroke(HC.lime.opacity(0.5), lineWidth: 1.5)) }
}

/// Mesaj listesi + hızlı sorular + yazma alanı. Koç sekmesi ve antrenman içi sayfa ortak kullanır.
struct CoachChatBody: View {
    @Environment(AppModel.self) private var app
    @Binding var input: String
    var context: WorkoutCoachContext?
    var quick: [String]
    var showAvatar = false
    @FocusState private var focused: Bool

    var body: some View {
        let c = app.coach
        VStack(spacing: 8) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(c.messages) { message in MessageBubble(message: message) { action in Task { await app.executeCoachAction(action) } }.id(message.id) }
                        if c.busy { ThinkingIndicator().id("thinking") }
                    }.padding(.horizontal, 18).padding(.vertical, 14)
                }
                .scrollDismissesKeyboard(.interactively).scrollIndicators(.hidden)
                .onChange(of: c.messages.count) { _, _ in withAnimation { if c.busy { proxy.scrollTo("thinking", anchor: .bottom) } else if let id = c.messages.last?.id { proxy.scrollTo(id, anchor: .bottom) } } }
                .onChange(of: c.busy) { _, busy in if busy { withAnimation { proxy.scrollTo("thinking", anchor: .bottom) } } }
            }
            HfChipRow { ForEach(quick, id: \.self) { q in HfChip(text: q, selected: false) { send(q) } } }.padding(.horizontal, 18).disabled(c.busy)
            HStack(spacing: 8) {
                TextField(tr("Bir şey sor...", "Ask something..."), text: $input, axis: .vertical).lineLimit(1...5).focused($focused).submitLabel(.send).onSubmit { send(input) }
                    .padding(.horizontal, 16).padding(.vertical, 12).background(HC.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous)).foregroundStyle(HC.text)
                let enabled = !input.trimmingCharacters(in: .whitespaces).isEmpty && !c.busy
                Button { send(input) } label: { Image(systemName: "arrow.up").font(.system(size: 17, weight: .bold)).foregroundStyle(enabled ? HC.onLime : HC.textSecondary).frame(width: 44, height: 44).background(enabled ? HC.lime : HC.divider, in: Circle()) }.disabled(!enabled)
            }.padding(.horizontal, 18).padding(.bottom, 6)
        }
    }

    private func send(_ text: String) {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        input = ""; focused = false
        Task { await app.coach.send(clean, context: context) }
    }
}

struct MessageBubble: View {
    let message: ChatMessage
    var onAction: (CoachAction) -> Void
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            if message.fromUser { Spacer(minLength: 40) } else { CoachAvatar(size: 34) }
            VStack(alignment: message.fromUser ? .trailing : .leading, spacing: 8) {
                Text(message.text).font(.hfBody.weight(message.fromUser ? .semibold : .regular)).foregroundStyle(message.fromUser ? HC.onLime : HC.text).textSelection(.enabled)
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(message.fromUser ? HC.lime : HC.surface, in: UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: message.fromUser ? 18 : 6, bottomTrailingRadius: message.fromUser ? 6 : 18, topTrailingRadius: 18, style: .continuous))
                if !message.fromUser && !message.actions.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 10) { HfIconBadge(system: "bolt.fill", tint: HC.lime, size: 30, radius: 9); Text(tr("ÖNERİLEN EYLEM", "SUGGESTED ACTION")).font(.hfLabel.weight(.heavy)).foregroundStyle(HC.lime) }
                        ForEach(message.actions) { action in
                            Text(label(action)).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text)
                            HfButton(title: tr("Uygula", "Apply"), icon: "checkmark") { onAction(action) }
                        }
                    }.padding(14).frame(maxWidth: .infinity, alignment: .leading).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous)).overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(HC.lime.opacity(0.35)))
                }
            }
            if !message.fromUser { Spacer(minLength: 20) }
        }
    }

    private func label(_ a: CoachAction) -> String {
        switch a.type {
        case "replace_exercise": return tr("Bu hareketle değiştir: \(a.replacementName ?? "Alternatif")", "Replace with: \(a.replacementName ?? "Alternative")")
        case "reduce_intensity": return tr("Yoğunluğu %\(a.percent ?? 25) hafiflet", "Reduce intensity by \(a.percent ?? 25)%")
        case "shorten_workout": return tr("Antrenmanı \(a.targetMinutes ?? 20) dakikaya uyarla", "Shorten workout to \(a.targetMinutes ?? 20) min")
        case "start_recovery_check": return tr("Hazırlık ve toparlanma kontrolü yap", "Start readiness & recovery check")
        case "modify_sets": return tr("Set sayısını \(a.sets ?? 3) yap", "Set count: \(a.sets ?? 3)")
        case "modify_rest_time": return tr("Dinlenmeyi \(a.restSeconds ?? 60)s yap", "Rest time: \(a.restSeconds ?? 60)s")
        case "openWorkout": return tr("Bugünkü antrenmanı aç", "Open today's workout")
        case "createWorkout": return tr("\(a.region ?? "Hızlı") programı oluştur", "Create a \(a.region ?? "quick") workout")
        case "startOutdoor": return tr("Açık hava aktivitesi başlat", "Start an outdoor activity")
        case "suggestMeal": return tr("Öğün önerilerini aç", "Open meal suggestions")
        case "remind": return tr("Hatırlatma ayarlarını aç", "Open reminder settings")
        case "changeGoal": return tr("Hedef planımı incele", "Review my goal plan")
        case "createChallenge": return tr("Challenge'ımı oluştur", "Create my challenge")
        default: return tr("Önerilen eylemi uygula", "Apply recommendation")
        }
    }
}

struct ThinkingIndicator: View {
    @State private var phase = false
    var body: some View {
        HStack(spacing: 8) {
            CoachAvatar(size: 34)
            HStack(spacing: 5) {
                ForEach(0..<3) { i in Circle().fill(HC.lime).frame(width: 7, height: 7).opacity(phase ? 1 : 0.28).animation(.easeInOut(duration: 0.52).repeatForever(autoreverses: true).delay(Double(i) * 0.13), value: phase) }
                Text(tr("Düşünüyor", "Thinking")).font(.hfSmall).foregroundStyle(HC.textSecondary).padding(.leading, 3)
            }.padding(.horizontal, 14).padding(.vertical, 11).background(HC.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            Spacer()
        }.onAppear { phase = true }
    }
}
