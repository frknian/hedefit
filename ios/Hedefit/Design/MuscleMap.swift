import SwiftUI

/// Tıklanabilir kas haritası (ön + arka). Kimlikler egzersiz kataloğunun kas filtresi değerleriyle aynıdır
/// ("chest", "lats", "abdominals"…). Şekiller 100×220'lik birim uzayında SVG yol verisiyle tanımlıdır;
/// yalnız sağ yarı yazılır, sol yarı yansıtılır.
struct MuscleMap: View {
    var selected: Set<String>
    var onSelect: (String) -> Void
    var description = ""

    var body: some View {
        HStack(spacing: 0) {
            BodyCanvas(shapes: MuscleShapes.front, details: MuscleShapes.frontDetails, selected: selected, onSelect: onSelect)
            BodyCanvas(shapes: MuscleShapes.back, details: MuscleShapes.backDetails, selected: selected, onSelect: onSelect)
        }
        .frame(maxWidth: 300).aspectRatio(2 * MuscleShapes.w / MuscleShapes.h, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore).accessibilityLabel(description)
    }
}

private struct BodyCanvas: View {
    let shapes: [MuscleShapes.Shape]
    let details: [Path]
    let selected: Set<String>
    let onSelect: (String) -> Void

    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / MuscleShapes.w
            let ordered = shapes.sorted { a, _ in !(a.id.map(selected.contains) ?? false) }
            let accent = HC.lime
            Canvas { ctx, _ in
                ctx.scaleBy(x: scale, y: scale)
                let line = HC.bg.opacity(0.75)
                for shape in ordered {
                    let active = shape.id.map(selected.contains) ?? false
                    let b = shape.path.boundingRect
                    let fill: GraphicsContext.Shading
                    if shape.id == nil { fill = .color(HC.surfaceHigh) }
                    else if active { fill = .linearGradient(Gradient(colors: [accent, accent.opacity(0.72)]), startPoint: CGPoint(x: 0, y: b.minY), endPoint: CGPoint(x: 0, y: b.maxY)) }
                    else { fill = .linearGradient(Gradient(colors: [HC.muted.opacity(0.5), HC.muted.opacity(0.26)]), startPoint: CGPoint(x: 0, y: b.minY), endPoint: CGPoint(x: 0, y: b.maxY)) }
                    ctx.fill(shape.path, with: fill)
                    ctx.stroke(shape.path, with: .color(active ? accent : line), style: StrokeStyle(lineWidth: 0.55, lineJoin: .round))
                }
                for d in details { ctx.stroke(d, with: .color(HC.bg.opacity(0.75)), style: StrokeStyle(lineWidth: 0.45, lineCap: .round, lineJoin: .round)) }
            }
            .contentShape(Rectangle())
            .onTapGesture { location in
                let p = CGPoint(x: location.x / scale, y: location.y / scale)
                // Üstte çizilen (listede sonra gelen) bölge önce yakalansın.
                if let hit = ordered.last(where: { $0.id != nil && $0.path.contains(p) }), let id = hit.id { onSelect(id) }
            }
        }
        .aspectRatio(MuscleShapes.w / MuscleShapes.h, contentMode: .fit)
    }
}

enum MuscleShapes {
    static let w: CGFloat = 100, h: CGFloat = 220

    struct Shape { let id: String?; let path: Path }

    /// SVG yol alt kümesi: M, L, C, Z (mutlak).
    static func parse(_ d: String) -> Path {
        var path = Path()
        var tokens: [String] = []
        var current = ""
        for ch in d {
            if ch.isLetter { if !current.isEmpty { tokens.append(current); current = "" }; tokens.append(String(ch)) }
            else if ch == " " || ch == "," { if !current.isEmpty { tokens.append(current); current = "" } }
            else { current.append(ch) }
        }
        if !current.isEmpty { tokens.append(current) }
        var i = 0
        func num() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i]) ?? 0) }
        func pt() -> CGPoint { let x = num(); let y = num(); return CGPoint(x: x, y: y) }
        var cmd = ""
        while i < tokens.count {
            if tokens[i].first?.isLetter == true { cmd = tokens[i]; i += 1; if cmd == "Z" { path.closeSubpath(); continue } }
            switch cmd {
            case "M": path.move(to: pt()); cmd = "L"
            case "L": path.addLine(to: pt())
            case "C": let c1 = pt(), c2 = pt(), e = pt(); path.addCurve(to: e, control1: c1, control2: c2)
            default: i += 1
            }
        }
        return path
    }

    static func mirrored(_ p: Path) -> Path { p.applying(CGAffineTransform(scaleX: -1, y: 1).concatenating(CGAffineTransform(translationX: w, y: 0))) }
    static func sym(_ id: String?, _ ds: String...) -> [Shape] { ds.flatMap { s -> [Shape] in let p = parse(s); return [Shape(id: id, path: p), Shape(id: id, path: mirrored(p))] } }
    static func center(_ id: String?, _ d: String) -> [Shape] { [Shape(id: id, path: parse(d))] }
    static func detail(_ ds: String...) -> [Path] { ds.flatMap { s -> [Path] in let p = parse(s); return [p, mirrored(p)] } }

    static let head = "M50 5.5 C55.5 5.5 59 9.5 59 15.5 C59 21.5 55.5 26.5 50 26.5 C44.5 26.5 41 21.5 41 15.5 C41 9.5 44.5 5.5 50 5.5 Z"
    static let neck = "M45 25 L55 25 L56.5 36 C54 38 46 38 43.5 36 Z"
    static let torso = "M50 35 L58 35.5 C64 37 68 40 69.5 46 C70.5 56 69 70 66 84 C64.5 92 64 98 67 104 L50 108 Z"
    static let hip = "M50 100 L68 100 C71.5 104 72 111 70 117 L50 119 Z"
    static let armGap = "M69 44 L82 46 C84 54 83 60 81 64 L72 62 Z"
    static let hand = "M82 123 C85 123.5 89 123 91.5 122.5 C93.5 128 94 134 92 139 C89 140.5 85.5 139.5 83.5 135 Z"
    static let foot = "M55 209 C58.5 210 62.5 210 64.5 209 L68 216.5 C64 219 57.5 219 53.5 217.5 Z"
    static let knee = "M54 155.5 C58 157 63 157 66.5 155.5 C66.5 159.5 65.5 162.5 64.5 165 L55 165 C54.5 162 54 159 54 155.5 Z"
    static let deltoid = "M62 36.5 C69 34.5 78 37 81.5 45 C83.5 51 83 57 80 62 L72 60.5 C70.5 54 67 47 62 42.5 Z"
    static let armUpper = "M72 62.5 C76 62 80 61.5 82.5 62.5 C86 70 87 80 85.5 89.5 C82 91.5 78 91.5 75 90.5 C72.5 80 71.5 70 72 62.5 Z"
    static let forearm = "M75 92.5 C79 92.5 83 91.5 85.5 91.5 C88.5 100 90.5 112 91.5 122 C88.5 123.5 85 124 82 123 C79 113 76 102 75 92.5 Z"

    static let front: [Shape] =
        center(nil, head) + sym(nil, torso, hip) +
        sym(nil, armGap, hand, foot, knee) +
        center("neck", neck) +
        sym("chest", "M50.5 40 C56 38 61.5 38 65.5 41 C69.5 45 70.5 52 67.5 57.5 C63.5 61.5 56 62 50.5 60 Z") +
        sym("shoulders", deltoid) +
        sym("abdominals", "M58 64 C61.5 64 64.5 63 66 62.5 C67 75 65 90 60.5 100 C59.5 92 58.5 80 58 64 Z") +
        center("abdominals", "M43 63 C46.5 62 53.5 62 57 63 L58 80 C58 92 56 98 50 102 C44 98 42 92 42 80 Z") +
        sym("biceps", armUpper) +
        sym("forearms", forearm) +
        sym("quadriceps", "M53 110 C59.5 108 65.5 109 68.5 113 C69 127 67 143 64.5 156 C60.5 158 57 158 55 156 C53.5 142 52 126 53 110 Z") +
        sym("calves", "M55 165.5 C59 164.5 63 164.5 65 165.5 C66.5 178 64.5 196 62.5 208.5 C60.5 209.5 58 209.5 56 208.5 C54.5 196 53.5 178 55 165.5 Z")

    static let frontDetails: [Path] =
        [parse("M44.5 70 L55.5 70"), parse("M44.5 77.5 L55.5 77.5"), parse("M45 85 L55 85"), parse("M50 63 L50 100")] + detail("M58 64 C59 74 59 86 57 96")

    static let back: [Shape] =
        center(nil, head) + sym(nil, torso, hip) +
        sym(nil, armGap, hand, foot, knee) +
        center("neck", neck) +
        sym("shoulders", deltoid) +
        sym("lats", "M52 66 C57 58 62 53 67.5 52 C70.5 60 69.5 72 66.5 82 C63.5 88 58.5 91.5 52 92.5 Z") +
        sym("traps", "M50 31 C56 32 61 34 66 38 C69.5 41 70.5 43.5 68.5 46.5 C62 48.5 56 54.5 50 64.5 Z") +
        sym("middle back", "M50 64.5 C54 60.5 58 57.5 61.5 56.5 C61.5 62 60.5 68 57.5 74 C54.5 78 52 80 50 82 Z") +
        sym("lower back", "M50 84 C54 84 58 86 60.5 88 C60.5 94 59.5 99 57.5 103 C54.5 104 52 104 50 104 Z") +
        sym("triceps", armUpper) +
        sym("forearms", forearm) +
        sym("glutes", "M50 104.5 C57.5 102.5 65.5 103.5 69.5 107.5 C71.5 113.5 69.5 121.5 63.5 125.5 C57.5 127.5 52 124.5 50 120.5 Z") +
        sym("hamstrings", "M52 126.5 C58 127.5 64 126.5 68.5 122.5 C69.5 135 67.5 150 65.5 160.5 C61.5 162.5 57.5 162.5 54.5 160.5 C52.5 148 51 136 52 126.5 Z") +
        sym("calves", "M55 165.5 C59 164.5 63.5 164.5 65.5 165.5 C68 177 65.5 194 62.5 208.5 C60.5 209.5 58 209.5 56 208.5 C53 194 52.5 177 55 165.5 Z")

    static let backDetails: [Path] = [parse("M50 33 L50 106")] + detail("M53 70 C57 68 60 69 61 72")
}
