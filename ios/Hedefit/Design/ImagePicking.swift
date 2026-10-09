import SwiftUI
import UIKit
import PhotosUI
import AVFoundation

/// Kamera çekimi (UIImagePickerController).
struct CameraPicker: UIViewControllerRepresentable {
    var onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onImage(image) }
            parent.dismiss()
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

extension UIImage {
    /// Uzun kenarı `maxDimension`'a indirip JPEG'e çevirir; 5 MB altına inene dek kaliteyi düşürür.
    func jpegForUpload(maxDimension: CGFloat = 1280, limit: Int = 4_500_000) -> Data? {
        let scaled = downscaled(maxDimension)
        for quality in stride(from: 0.82, through: 0.3, by: -0.12) {
            if let data = scaled.jpegData(compressionQuality: quality), data.count <= limit { return data }
        }
        return scaled.jpegData(compressionQuality: 0.3)
    }
}

enum PhotoLoader {
    static func image(from item: PhotosPickerItem?) async -> UIImage? {
        guard let item, let data = try? await item.loadTransferable(type: Data.self) else { return nil }
        return UIImage(data: data)
    }
}

/// Kamera izni durumu → kullanıcıya gösterilecek mesaj.
enum CameraAccess {
    static func ensure() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return true
        case .notDetermined: return await AVCaptureDevice.requestAccess(for: .video)
        default: return false
        }
    }
    @MainActor static var deniedMessage: String { tr("Kamera izni verilmedi. Ayarlar'dan izin verebilirsin.", "Camera permission denied. You can enable it in Settings.") }
}
