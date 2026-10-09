import SwiftUI

// MARK: - Kart & zemin

struct HfCard<Content: View>: View {
    var padding: CGFloat = 16
    var radius: CGFloat = 20
    var fill: Color? = nil
    var onTap: (() -> Void)? = nil
    @ViewBuilder var content: Content

    var body: some View {
        let card = content.padding(padding).frame(maxWidth: .infinity, alignment: .leading)
            .background(fill ?? HC.surface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
        if let onTap { Button(action: onTap) { card }.buttonStyle(PressableStyle()) } else { card }
    }
}

struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.8 : 1).scaleEffect(configuration.isPressed ? 0.985 : 1).animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

/// Tüm ekranların ortak zemini + kaydırma.
struct ScreenScaffold<Content: View>: View {
    var spacing: CGFloat = 14
    var bottomInset: CGFloat = 24
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: spacing) { content }
                .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, bottomInset)
        }
        .scrollIndicators(.hidden)
        .background(HC.bg.ignoresSafeArea())
    }
}

// MARK: - Başlıklar

struct HfScreenHeader<Trailing: View>: View {
    let title: String
    var onBack: (() -> Void)? = nil
    @ViewBuilder var trailing: Trailing
    var body: some View {
        HStack(spacing: 12) {
            if let onBack { HfCircleButton(system: "chevron.left", label: tr("Geri", "Back"), action: onBack) }
            Text(title).font(.hfTitle).foregroundStyle(HC.text).lineLimit(1).minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            HStack(spacing: 8) { trailing }
        }
    }
}
extension HfScreenHeader where Trailing == EmptyView {
    init(title: String, onBack: (() -> Void)? = nil) { self.title = title; self.onBack = onBack; self.trailing = EmptyView() }
}

struct HfSectionHeader: View {
    let title: String
    var trailing: String? = nil
    var action: (() -> Void)? = nil
    var body: some View {
        HStack {
            Text(title).font(.hfTitleL).foregroundStyle(HC.text)
            Spacer()
            if let trailing, let action { Button(trailing, action: action).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime) }
        }.padding(.top, 4)
    }
}

struct HfCircleButton: View {
    let system: String
    var label: String = ""
    var tint: Color? = nil
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: system).font(.system(size: 16, weight: .semibold)).foregroundStyle(tint ?? HC.text)
                .frame(width: 44, height: 44).background(HC.surfaceHigh, in: Circle())
        }.buttonStyle(PressableStyle()).accessibilityLabel(label)
    }
}

// MARK: - Rozet / satır

struct HfIconBadge: View {
    let system: String
    var tint: Color
    var size: CGFloat = 38
    var radius: CGFloat = 12
    var body: some View {
        Image(systemName: system).font(.system(size: size * 0.5, weight: .semibold)).foregroundStyle(tint)
            .frame(width: size, height: size).background(tint.opacity(0.15), in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

struct HfNavRow<Trailing: View>: View {
    let icon: String
    var tint: Color
    let title: String
    var subtitle: String? = nil
    var chevron = true
    var titleColor: Color? = nil
    var action: (() -> Void)?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        let row = HStack(spacing: 12) {
            HfIconBadge(system: icon, tint: tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.hfTitleM.weight(.bold)).foregroundStyle(titleColor ?? HC.text).multilineTextAlignment(.leading)
                if let subtitle { Text(subtitle).font(.hfSmall).foregroundStyle(HC.muted).multilineTextAlignment(.leading) }
            }
            Spacer(minLength: 8)
            trailing
            if chevron && action != nil { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(HC.muted) }
        }
        HfCard(padding: 14, onTap: action) { row }
    }
}
extension HfNavRow where Trailing == EmptyView {
    init(icon: String, tint: Color, title: String, subtitle: String? = nil, chevron: Bool = true, titleColor: Color? = nil, action: (() -> Void)?) {
        self.icon = icon; self.tint = tint; self.title = title; self.subtitle = subtitle; self.chevron = chevron; self.titleColor = titleColor; self.action = action; self.trailing = EmptyView()
    }
}

/// Gruplanmış (ayarlar tarzı) kart içindeki satır.
struct HfListRow<Trailing: View>: View {
    let icon: String
    var tint: Color
    let title: String
    var subtitle: String? = nil
    var chevron = true
    var titleColor: Color? = nil
    var action: (() -> Void)?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        let row = HStack(spacing: 12) {
            HfIconBadge(system: icon, tint: tint, size: 34, radius: 11)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.hfTitleM.weight(.semibold)).foregroundStyle(titleColor ?? HC.text).multilineTextAlignment(.leading)
                if let subtitle { Text(subtitle).font(.hfSmall).foregroundStyle(HC.muted).multilineTextAlignment(.leading) }
            }
            Spacer(minLength: 8)
            trailing
            if chevron && action != nil { Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(HC.muted) }
        }.frame(minHeight: 48).contentShape(Rectangle())
        if let action { Button(action: action) { row }.buttonStyle(.plain) } else { row }
    }
}
extension HfListRow where Trailing == EmptyView {
    init(icon: String, tint: Color, title: String, subtitle: String? = nil, chevron: Bool = true, titleColor: Color? = nil, action: (() -> Void)?) {
        self.icon = icon; self.tint = tint; self.title = title; self.subtitle = subtitle; self.chevron = chevron; self.titleColor = titleColor; self.action = action; self.trailing = EmptyView()
    }
}

struct HfDivider: View { var body: some View { Rectangle().fill(HC.divider).frame(height: 1) } }

struct HfActionTile: View {
    let icon: String, tint: Color, title: String, subtitle: String
    var action: () -> Void
    var body: some View {
        HfCard(padding: 14, onTap: action) {
            VStack(alignment: .leading, spacing: 10) {
                HfIconBadge(system: icon, tint: tint, size: 36, radius: 11)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.hfTitleM.weight(.heavy)).foregroundStyle(HC.text).lineLimit(2).multilineTextAlignment(.leading)
                    if !subtitle.isEmpty { Text(subtitle).font(.hfSmall).foregroundStyle(HC.textSecondary).lineLimit(2).multilineTextAlignment(.leading) }
                }
            }
        }
    }
}

struct HfStatTile: View {
    let label: String, value: String
    var sub: String? = nil
    var valueColor: Color? = nil
    var action: (() -> Void)? = nil
    var body: some View {
        HfCard(padding: 14, onTap: action) {
            VStack(alignment: .leading, spacing: 4) {
                Text(label.upperLocalized).font(.system(size: 11, weight: .bold)).foregroundStyle(HC.textSecondary).lineLimit(1)
                Text(value).font(.system(size: 20, weight: .heavy)).foregroundStyle(valueColor ?? HC.text).lineLimit(1).minimumScaleFactor(0.7)
                if let sub { Text(sub).font(.hfSmall).foregroundStyle(HC.muted).lineLimit(1) }
            }
        }
    }
}

// MARK: - Etiket / çip / düğme

struct HfPill: View {
    let text: String
    var color: Color? = nil
    var body: some View {
        let c = color ?? HC.lime
        Text(text).font(.hfLabel.weight(.heavy)).foregroundStyle(c).lineLimit(1).padding(.horizontal, 10).padding(.vertical, 5).background(c.opacity(0.15), in: Capsule())
    }
}

struct HfTag: View {
    let text: String
    var body: some View { Text(text).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary).padding(.horizontal, 10).padding(.vertical, 6).background(HC.surfaceHigh, in: Capsule()) }
}

struct HfChip: View {
    let text: String
    var selected: Bool
    var icon: String? = nil
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Image(systemName: icon).font(.system(size: 13, weight: .semibold)) }
                Text(text).font(.system(size: 14, weight: selected ? .heavy : .bold)).lineLimit(1)
            }
            .foregroundStyle(selected ? HC.onLime : HC.textSecondary).padding(.horizontal, 14).frame(minHeight: 40)
            .background(selected ? HC.lime : HC.surface, in: Capsule())
        }.buttonStyle(PressableStyle())
    }
}

struct HfChipRow<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View { ScrollView(.horizontal) { HStack(spacing: 8) { content }.padding(.horizontal, 1) }.scrollIndicators(.hidden) }
}

struct HfSegmented: View {
    let options: [String]
    @Binding var selection: Int
    var body: some View {
        HStack(spacing: 4) {
            ForEach(options.indices, id: \.self) { index in
                let on = index == selection
                Button { withAnimation(.easeOut(duration: 0.15)) { selection = index } } label: {
                    Text(options[index]).font(.system(size: 14, weight: on ? .heavy : .bold)).lineLimit(1).minimumScaleFactor(0.75)
                        .foregroundStyle(on ? HC.onLime : HC.textSecondary).frame(maxWidth: .infinity, minHeight: 40)
                        .background(on ? HC.lime : Color.clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }.buttonStyle(.plain)
            }
        }.padding(4).background(HC.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct HfButton: View {
    let title: String
    var icon: String? = nil
    var secondary = false
    var destructive = false
    var loading = false
    var enabled = true
    var action: () -> Void
    var body: some View {
        let bg: Color = destructive ? HC.coral : (secondary ? HC.surfaceHigh : HC.lime)
        let fg: Color = destructive ? .white : (secondary ? HC.text : HC.onLime)
        Button(action: action) {
            HStack(spacing: 8) {
                if loading { ProgressView().tint(fg) } else if let icon { Image(systemName: icon).font(.system(size: 15, weight: .bold)) }
                Text(title).font(.system(size: 15, weight: .heavy)).lineLimit(1)
            }
            .foregroundStyle(fg).padding(.horizontal, 16).frame(maxWidth: .infinity, minHeight: 52)
            .background(bg.opacity(enabled ? 1 : 0.4), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }.buttonStyle(PressableStyle()).disabled(!enabled || loading)
    }
}

struct HfProgressBar: View {
    var progress: Double
    var color: Color? = nil
    var height: CGFloat = 6
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(HC.surfaceSoft)
                Capsule().fill(color ?? HC.lime).frame(width: max(geo.size.width * min(max(progress, 0), 1), progress > 0 ? height : 0))
            }
        }.frame(height: height)
    }
}

struct HfRing: View {
    var progress: Double
    var color: Color? = nil
    var lineWidth: CGFloat = 10
    var body: some View {
        ZStack {
            Circle().stroke(HC.surfaceSoft, lineWidth: lineWidth)
            Circle().trim(from: 0, to: min(max(progress, 0), 1)).stroke(color ?? HC.lime, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)).rotationEffect(.degrees(-90))
        }
    }
}

struct HfStepRow<Trailing: View>: View {
    let index: Int
    let title: String
    let meta: String
    var done = false
    @ViewBuilder var trailing: Trailing
    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(done ? HC.lime : HC.surfaceHigh).frame(width: 28, height: 28)
                if done { Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(HC.onLime) }
                else { Text("\(index)").font(.hfLabel.weight(.heavy)).foregroundStyle(HC.textSecondary) }
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.hfTitleM.weight(.bold)).foregroundStyle(done ? HC.muted : HC.text).lineLimit(1)
                Text(meta).font(.hfSmall).foregroundStyle(HC.muted).lineLimit(1)
            }
            Spacer(minLength: 0)
            trailing
        }.frame(minHeight: 44)
    }
}
extension HfStepRow where Trailing == EmptyView {
    init(index: Int, title: String, meta: String, done: Bool = false) { self.index = index; self.title = title; self.meta = meta; self.done = done; self.trailing = EmptyView() }
}

// MARK: - Boş durum & hata

struct HfEmptyState: View {
    let icon: String, title: String
    var message: String? = nil
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 34, weight: .semibold)).foregroundStyle(HC.muted)
            Text(title).font(.hfTitleM.weight(.bold)).foregroundStyle(HC.text).multilineTextAlignment(.center)
            if let message { Text(message).font(.hfBody).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center) }
        }.frame(maxWidth: .infinity).padding(28)
    }
}

struct HfBanner: View {
    let text: String
    var color: Color? = nil
    var icon = "info.circle.fill"
    var body: some View {
        let c = color ?? HC.lime
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).foregroundStyle(c)
            Text(text).font(.hfBody).foregroundStyle(HC.text).fixedSize(horizontal: false, vertical: true)
        }.padding(14).frame(maxWidth: .infinity, alignment: .leading).background(c.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct HfField: View {
    let title: String
    @Binding var text: String
    var keyboard: UIKeyboardType = .default
    var secure = false
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.hfLabel.weight(.bold)).foregroundStyle(HC.textSecondary)
            Group { if secure { SecureField("", text: $text) } else { TextField("", text: $text).keyboardType(keyboard) } }
                .textInputAutocapitalization(keyboard == .emailAddress ? .never : nil).autocorrectionDisabled(keyboard == .emailAddress)
                .font(.hfBody).foregroundStyle(HC.text).padding(.horizontal, 14).frame(minHeight: 50)
                .background(HC.surfaceHigh, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }
}

struct HfStepper: View {
    @Binding var value: Int
    var range: ClosedRange<Int>
    var step = 1
    var body: some View {
        HStack(spacing: 4) {
            Button { value = max(range.lowerBound, value - step) } label: { Image(systemName: "minus").frame(width: 34, height: 34) }
            Text("\(value)").font(.system(size: 17, weight: .heavy)).foregroundStyle(HC.lime).frame(minWidth: 28)
            Button { value = min(range.upperBound, value + step) } label: { Image(systemName: "plus").frame(width: 34, height: 34) }
        }.foregroundStyle(HC.text).padding(.horizontal, 6).background(HC.surfaceHigh, in: Capsule())
    }
}

/// Alt sayfalarda ortak bir "tamam" üst çubuğu.
struct SheetHeader: View {
    let title: String
    var onClose: () -> Void
    var body: some View {
        HStack {
            Text(title).font(.hfHeadline.weight(.heavy)).foregroundStyle(HC.text)
            Spacer()
            Button(action: onClose) { Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(HC.textSecondary).frame(width: 32, height: 32).background(HC.surfaceHigh, in: Circle()) }
        }
    }
}
