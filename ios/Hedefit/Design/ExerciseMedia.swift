import SwiftUI
import UIKit

private let legacyWorkoutImageIds: [String: String] = [
    "barbell-bench-press": "Barbell_Bench_Press_-_Medium_Grip", "incline-dumbbell-press": "Incline_Dumbbell_Press", "seated-dumbbell-press": "Seated_Dumbbell_Press",
    "dumbbell-lateral-raise": "Side_Lateral_Raise", "dumbbell-lateral-raise-b": "Side_Lateral_Raise", "cable-rope-triceps-pushdown": "Triceps_Pushdown_-_Rope_Attachment",
    "barbell-shoulder-press": "Barbell_Shoulder_Press", "dumbbell-bench-press": "Dumbbell_Bench_Press", "cable-crossover": "Cable_Crossover",
    "overhead-cable-triceps-extension": "Cable_Rope_Overhead_Triceps_Extension", "pullups": "Pullups", "wide-grip-lat-pulldown": "Wide-Grip_Lat_Pulldown",
    "seated-cable-row": "Seated_Cable_Rows", "face-pull": "Face_Pull", "incline-dumbbell-curl": "Incline_Dumbbell_Curl", "barbell-row": "Bent_Over_Barbell_Row",
    "close-grip-lat-pulldown": "Close-Grip_Front_Lat_Pulldown", "chest-supported-dumbbell-row": "Incline_Bench_Pull", "reverse-pec-deck": "Reverse_Machine_Flyes", "hammer-curl": "Hammer_Curls",
]

/// RepDB görselleri `/exercise-images/<id>/{start,main,peak}.webp`; hangisinin var olduğu istemciden bilinmediği için sırayla denenir.
enum ExerciseImages {
    static func repdbCandidates(_ id: String) -> [String] { ["/exercise-images/\(id)/start.webp", "/exercise-images/\(id)/main.webp", "/exercise-images/\(id)/peak.webp"] }
    static func legacyCandidates(_ id: String) -> [String] {
        guard let legacy = legacyWorkoutImageIds[id.lowercased()] else { return [] }
        return ["/exercise-images/\(legacy)/0.jpg", "/exercise-images/\(legacy)/1.jpg"]
    }
    static func url(_ path: String) -> URL? {
        path.hasPrefix("http") ? URL(string: path) : URL(string: AppConfiguration.apiBase + "/" + path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }
}

actor ImageLoader {
    static let shared = ImageLoader()
    private let cache = NSCache<NSString, UIImage>()
    private var failed = Set<String>()

    func image(_ url: URL, maxDimension: CGFloat = 768) async -> UIImage? {
        let key = "\(url.absoluteString)@\(Int(maxDimension))" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        if failed.contains(url.absoluteString) { return nil }
        var request = URLRequest(url: url, timeoutInterval: 10); request.cachePolicy = .returnCacheDataElseLoad
        guard let (data, response) = try? await URLSession.shared.data(for: request), (response as? HTTPURLResponse)?.statusCode == 200,
              let image = UIImage(data: data) else { failed.insert(url.absoluteString); return nil }
        let scaled = image.downscaled(maxDimension)
        cache.setObject(scaled, forKey: key)
        return scaled
    }
}

extension UIImage {
    func downscaled(_ maxDimension: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxDimension else { return self }
        let scale = maxDimension / longest
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        return UIGraphicsImageRenderer(size: target).image { _ in draw(in: CGRect(origin: .zero, size: target)) }
    }
}

/// Aday yollar sırayla denenir; ilki yüklenen gösterilir.
struct ExerciseMedia: View {
    var paths: [String]
    var contentMode: ContentMode = .fill
    var maxDimension: CGFloat = 256
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color(hex: 0xDDEFFB).opacity(Theme.shared.dark ? 0.92 : 1)
            if let image { Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode == .fill ? .fill : .fit) }
            else { Image(systemName: "dumbbell.fill").foregroundStyle(HC.lime.opacity(0.7)) }
        }.clipped().task(id: paths) {
            image = nil
            for path in paths where !path.isEmpty {
                if let url = ExerciseImages.url(path), let loaded = await ImageLoader.shared.image(url, maxDimension: maxDimension) { image = loaded; return }
            }
        }
    }
}

extension ExerciseMedia {
    init(id: String, imageURLs: [String] = [], maxDimension: CGFloat = 256) {
        self.init(paths: (imageURLs.isEmpty ? ExerciseImages.repdbCandidates(id) : imageURLs) + ExerciseImages.legacyCandidates(id), maxDimension: maxDimension)
    }
}

/// Başlangıç/bitiş karelerini döngüyle gösterir; tek kareliyse nefes alma animasyonu.
struct ExerciseMotionPlayer: View {
    let id: String
    var imageURLs: [String] = []
    @State private var frames: [UIImage] = []
    @State private var index = 0
    @State private var playing = true
    @State private var pulse = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Color(hex: 0xDDEFFB)
            if frames.isEmpty { Image(systemName: "dumbbell.fill").font(.system(size: 36)).foregroundStyle(HC.lime) }
            else {
                Image(uiImage: frames[min(index, frames.count - 1)]).resizable().scaledToFit().padding(12)
                    .scaleEffect(frames.count == 1 && pulse ? 1.035 : 1).animation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true), value: pulse)
                    .id(index).transition(.opacity)
            }
            if frames.count > 1 {
                Button { playing.toggle() } label: { Image(systemName: playing ? "pause.fill" : "play.fill").foregroundStyle(HC.lime).frame(width: 40, height: 40).background(.black.opacity(0.68), in: Circle()) }.padding(10)
            }
        }
        .task(id: id) {
            frames = []; index = 0
            let primaryPaths = imageURLs.isEmpty ? ExerciseImages.repdbCandidates(id) : imageURLs
            var loaded: [UIImage] = []
            for p in primaryPaths { if let u = ExerciseImages.url(p), let img = await ImageLoader.shared.image(u) { loaded.append(img) } }
            if loaded.isEmpty { for p in ExerciseImages.legacyCandidates(id) { if let u = ExerciseImages.url(p), let img = await ImageLoader.shared.image(u) { loaded.append(img) } } }
            frames = loaded; pulse = true
        }
        .task(id: "\(frames.count)-\(playing)") {
            while playing && frames.count > 1 && !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(850))
                withAnimation(.easeInOut(duration: 0.3)) { index = (index + 1) % max(frames.count, 1) }
            }
        }
    }
}
