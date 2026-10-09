import SwiftUI
import AVFoundation

/// Canlı kamera önizlemesi + fotoğraf çekimi.
@MainActor @Observable
final class CameraSession: NSObject, AVCapturePhotoCaptureDelegate {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private var continuation: CheckedContinuation<Data, Error>?
    var ready = false
    var failed = false

    func start() {
        guard !session.isRunning else { return }
        session.beginConfiguration()
        session.sessionPreset = .photo
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back), let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) { session.addInput(input) } else { failed = true }
        if session.canAddOutput(output) { session.addOutput(output) }
        session.commitConfiguration()
        let s = session
        DispatchQueue.global(qos: .userInitiated).async { s.startRunning() }
        ready = !failed
    }

    func stop() { let s = session; DispatchQueue.global(qos: .userInitiated).async { s.stopRunning() }; ready = false }

    func capture() async throws -> Data {
        try await withCheckedThrowingContinuation { cont in
            continuation = cont
            output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }
    }

    nonisolated func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let data = photo.fileDataRepresentation()
        Task { @MainActor in
            if let data, let image = UIImage(data: data)?.jpegForUpload(maxDimension: 1280, limit: 4_500_000) { self.continuation?.resume(returning: image) }
            else { self.continuation?.resume(throwing: error ?? AppError.message(trNow("Kamera karesi işlenemedi.", "Couldn't process the camera frame."))) }
            self.continuation = nil
        }
    }
}

struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> PreviewUIView { let v = PreviewUIView(); v.previewLayer.session = session; v.previewLayer.videoGravity = .resizeAspectFill; return v }
    func updateUIView(_ uiView: PreviewUIView, context: Context) {}
    final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

struct EquipmentScannerView: View {
    @Environment(AppModel.self) private var app
    @State private var camera = CameraSession()
    @State private var allowed = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
    @State private var denied = AVCaptureDevice.authorizationStatus(for: .video) == .denied || AVCaptureDevice.authorizationStatus(for: .video) == .restricted
    @State private var recognizing = false
    @State private var result: RecognitionResult?
    @State private var error: String?
    @State private var detail: EquipmentInfo?
    @State private var choices: [ExerciseCatalogItem] = []

    private var catalog: [ExerciseCatalogItem] { app.dashboard?.exerciseCatalog ?? [] }

    var body: some View {
        VStack(spacing: 0) {
            HStack { HfCircleButton(system: "chevron.left", label: tr("Geri", "Back")) { if !app.path.isEmpty { app.path.removeLast() } }; Text(tr("Ekipman Tara", "Scan equipment")).font(.hfTitleL.weight(.bold)).foregroundStyle(HC.text); Spacer() }.padding(.horizontal, 16).padding(.vertical, 8)
            ZStack {
                if allowed {
                    CameraPreviewView(session: camera.session)
                    RoundedRectangle(cornerRadius: 28).stroke(HC.lime, lineWidth: 3).frame(maxWidth: .infinity).padding(.horizontal, 60).aspectRatio(0.78, contentMode: .fit)
                    VStack { Text("HEDEFİT SCAN").font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime).padding(.horizontal, 14).padding(.vertical, 7).background(.black.opacity(0.58), in: Capsule()).padding(.top, 18); Spacer() }
                } else {
                    VStack(spacing: 14) {
                        Image(systemName: "camera.fill").font(.system(size: 52)).foregroundStyle(HC.textSecondary)
                        Text(denied ? tr("Kamera izni kapalı. Ayarlar'dan Hedefit için kamera iznini açabilirsin.", "Camera permission is off. You can enable it for Hedefit in Settings.") : tr("Ekipmanı taramak için kamera izni gerekiyor.", "Camera permission is needed to scan equipment.")).foregroundStyle(HC.textSecondary).multilineTextAlignment(.center)
                        if denied { HfButton(title: tr("Ayarları aç", "Open Settings")) { if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) } } }
                        else { HfButton(title: tr("Kamera İzni Ver", "Allow camera")) { Task { allowed = await CameraAccess.ensure(); denied = !allowed } } }
                    }.padding(28).frame(maxWidth: .infinity, maxHeight: .infinity).background(HC.surface)
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity)
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if allowed { HfButton(title: recognizing ? tr("Ekipman tanınıyor…", "Recognizing…") : tr("Tanımayı Kontrol Et", "Check recognition"), enabled: !recognizing && camera.ready) { Task { await recognize() } } }
                    if let error { Text(error).font(.hfSmall).foregroundStyle(HC.warning) }
                    if let result { resultCard(result) }
                    Text(tr("Doğru ekipman değil mi? Listeden seç", "Not the right equipment? Choose manually")).font(.hfTitleM).foregroundStyle(HC.text)
                    HfChipRow { ForEach(EquipmentCatalog.items) { e in Button { detail = e } label: { Text(e.name).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime).padding(.horizontal, 14).padding(.vertical, 10).background(HC.surface, in: RoundedRectangle(cornerRadius: 14)) }.buttonStyle(.plain) } }
                }.padding(16)
            }.frame(maxHeight: 330)
        }
        .background(HC.bg.ignoresSafeArea())
        .onAppear { if allowed { camera.start() } }
        .onDisappear { camera.stop() }
        .onChange(of: allowed) { _, ok in if ok { camera.start() } }
        .sheet(item: $detail) { e in EquipmentDetailSheet(equipment: e, exercises: EquipmentCatalog.matchingExercises(e, in: catalog)) { add(e) } }
        .sheet(isPresented: Binding(get: { !choices.isEmpty }, set: { if !$0 { choices = [] } })) {
            NavigationStack {
                List(choices) { ex in
                    Button { choices = []; Task { await app.useExerciseFromLibrary(ex); if !app.path.isEmpty { app.path.removeLast() } } } label: {
                        HStack(spacing: 10) { ExerciseMedia(id: ex.id, imageURLs: ex.imageUrls).frame(width: 54, height: 54).clipShape(RoundedRectangle(cornerRadius: 10)); VStack(alignment: .leading) { Text(ex.name).foregroundStyle(HC.text); Text(ex.primaryMuscles.joined(separator: ", ")).font(.hfSmall).foregroundStyle(HC.textSecondary) } }
                    }.listRowBackground(HC.surface)
                }.scrollContentBackground(.hidden).background(HC.bg).navigationTitle(tr("Hareket seç", "Choose an exercise")).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Vazgeç", "Cancel")) { choices = [] } } }
            }.presentationDetents([.large])
        }
    }

    private func add(_ e: EquipmentInfo) {
        let matches = EquipmentCatalog.matchingExercises(e, in: catalog)
        switch matches.count {
        case 0: error = tr("Hedefit egzersiz kütüphanesinde bu ekipmana uygun hareket bulunamadı.", "No matching exercise was found in your Hedefit library.")
        case 1: Task { await app.useExerciseFromLibrary(matches[0]); if !app.path.isEmpty { app.path.removeLast() } }
        default: choices = Array(matches.prefix(12))
        }
    }

    @ViewBuilder private func resultCard(_ r: RecognitionResult) -> some View {
        HfCard {
            switch r {
            case .unknown(_, let features):
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("Ekipman güvenle tanınamadı", "Equipment couldn't be identified confidently")).font(.hfTitleM).foregroundStyle(HC.warning)
                    Text(tr("Cihazın tamamını kadraja al, ışığı iyileştir ve farklı bir açıdan tekrar dene.", "Keep the entire machine in frame, improve lighting, and try from another angle.")).foregroundStyle(HC.textSecondary)
                    if !features.isEmpty { Text(features.joined(separator: " • ")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                }.frame(maxWidth: .infinity, alignment: .leading)
            case .recognized(let e, let confidence, let alternatives, let features):
                let strong = confidence >= 0.8
                VStack(alignment: .leading, spacing: 9) {
                    Text(strong ? tr("Ekipman tanındı", "Equipment identified") : tr("Bunun şu ekipman olduğunu düşünüyorum", "I think this is")).font(.hfLabel.weight(.bold)).foregroundStyle(strong ? HC.lime : HC.warning)
                    Text(e.name).font(.hfHeadline).foregroundStyle(HC.text)
                    Text("\(e.category) • %\(Int(confidence * 100))").foregroundStyle(HC.textSecondary)
                    Text(tr("Ana kaslar: ", "Primary muscles: ") + e.primaryMuscles.map(muscleName).joined(separator: ", ")).foregroundStyle(HC.textSecondary)
                    if !features.isEmpty { Text(features.joined(separator: " • ")).font(.hfSmall).foregroundStyle(HC.textSecondary) }
                    if !strong && !alternatives.isEmpty {
                        Text(tr("Diğer olasılıklar", "Other possibilities")).font(.hfLabel).foregroundStyle(HC.textSecondary)
                        ForEach(alternatives, id: \.equipment.id) { a in Button("\(a.equipment.name) • %\(Int(a.confidence * 100))") { result = .recognized(a.equipment, confidence: 0.65, alternatives: [], features: []) }.foregroundStyle(HC.lime) }
                    }
                    HStack(spacing: 8) {
                        Button(tr("Nasıl Kullanılır?", "How to use")) { detail = e }.font(.hfBody.weight(.bold)).foregroundStyle(HC.lime).frame(maxWidth: .infinity, minHeight: 46)
                        Button(tr("Antrenmana Ekle", "Add to workout")) { add(e) }.font(.hfBody.weight(.bold)).foregroundStyle(HC.onLime).frame(maxWidth: .infinity, minHeight: 46).background(HC.lime, in: RoundedRectangle(cornerRadius: 14)).disabled(EquipmentCatalog.matchingExercises(e, in: catalog).isEmpty).opacity(EquipmentCatalog.matchingExercises(e, in: catalog).isEmpty ? 0.4 : 1)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func recognize() async {
        guard !recognizing else { return }
        recognizing = true; result = nil; error = nil; defer { recognizing = false }
        do {
            let data = try await camera.capture()
            guard !data.isEmpty, data.count <= 5 * 1024 * 1024 else { throw AppError.message(tr("Fotoğraf hazırlanamadı.", "Couldn't prepare the photo.")) }
            let response = try await app.repo.recognizeEquipmentRaw(data)
            if !(200..<300).contains(response.status) {
                let body = response.json
                switch response.status {
                case 400, 413, 422: throw AppError.message(tr("Kamera karesi hazırlanamadı. Ekipmanı yeniden kadraja al.", "The camera frame could not be prepared. Reframe the equipment and retry."))
                case 404: throw AppError.message(tr("Tanıma servisi Hedefit sunucusunda henüz yayında değil veya yapılandırılmamış.", "Recognition is not published or configured on the Hedefit server."))
                case 429: throw AppError.message(tr("Tanıma limitine ulaşıldı. Daha sonra tekrar dene.", "Recognition limit reached. Try again later."))
                case 502: throw AppError.message(tr("Görüntü modeli geçerli bir yanıt döndürmedi. Tekrar dene.", "The vision service returned an invalid response. Try again."))
                case 503: throw AppError.message(body.string("code") == "VISION_NOT_CONFIGURED" ? tr("Tanıma servisi Hedefit sunucusunda henüz yayında değil veya yapılandırılmamış.", "Recognition is not published or configured on the Hedefit server.") : tr("Tanıma servisi geçici olarak kullanılamıyor.", "Recognition service is temporarily unavailable."))
                default: throw AppError.message(body.nonEmptyString("error") ?? tr("Ekipman tanınamadı. Tekrar deneyebilirsin.", "Equipment could not be recognized. Try again."))
                }
            }
            let json = response.json
            let confidence = min(max(json.double("confidence"), 0), 1)
            let features = Array(json["visibleFeatures"].items.compactMap(\.stringValue).filter { !$0.isEmpty }.prefix(8))
            guard json.bool("recognized"), confidence >= 0.55, let e = json.nonEmptyString("equipmentName").flatMap(EquipmentCatalog.find(byLabel:)) else { result = .unknown(confidence: confidence, features: features); return }
            let alts: [RecognitionAlternative] = json["alternatives"].items.compactMap { item in
                guard let m = item.nonEmptyString("equipmentName").flatMap(EquipmentCatalog.find(byLabel:)), m.id != e.id else { return nil }
                return RecognitionAlternative(equipment: m, confidence: min(max(item.double("confidence"), 0), 1))
            }
            result = .recognized(e, confidence: confidence, alternatives: alts, features: features)
        } catch { self.error = error.friendly }
    }
}

struct EquipmentDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    let equipment: EquipmentInfo
    let exercises: [ExerciseCatalogItem]
    var onAdd: () -> Void
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if let ex = exercises.first { ExerciseMedia(id: ex.id, imageURLs: ex.imageUrls).frame(height: 180).clipShape(RoundedRectangle(cornerRadius: 16)) }
                    Text(equipment.description).foregroundStyle(HC.textSecondary)
                    block(tr("Ana kaslar", "Primary muscles"), equipment.primaryMuscles.map(muscleName).joined(separator: ", "))
                    block(tr("Yardımcı kaslar", "Secondary muscles"), equipment.secondaryMuscles.map(muscleName).joined(separator: ", ").isEmpty ? "—" : equipment.secondaryMuscles.map(muscleName).joined(separator: ", "))
                    block(tr("Nasıl kullanılır?", "How to use"), equipment.instructions.enumerated().map { "\($0.offset + 1). \($0.element)" }.joined(separator: "\n"))
                    block(tr("Yaygın hatalar", "Common mistakes"), equipment.commonMistakes.map { "• \($0)" }.joined(separator: "\n"))
                    block(tr("Güvenlik", "Safety"), equipment.safetyNotes.map { "• \($0)" }.joined(separator: "\n"))
                    block(tr("Uygun hareketler", "Suitable exercises"), exercises.isEmpty ? tr("Kütüphanede eşleşen hareket bulunamadı.", "No matching exercise in the library.") : exercises.prefix(8).map { "• \($0.name)" }.joined(separator: "\n"))
                    HfButton(title: tr("Antrenmana Ekle", "Add to workout"), enabled: !exercises.isEmpty) { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { onAdd() } }
                }.padding(18)
            }.background(HC.bg).navigationTitle(equipment.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Kapat", "Close")) { dismiss() } } }
        }.presentationDetents([.large])
    }
    private func block(_ t: String, _ v: String) -> some View { VStack(alignment: .leading, spacing: 2) { Text(t).font(.hfLabel.weight(.bold)).foregroundStyle(HC.lime); Text(v).foregroundStyle(HC.textSecondary) } }
}
