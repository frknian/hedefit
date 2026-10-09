import SwiftUI
import Observation

// GEÇİCİ: gerçek ekranlar yazıldıkça buradan silinir.
struct Placeholder: View {
    @Environment(AppModel.self) private var app
    let title: String
    var body: some View {
        VStack(spacing: 16) { HfScreenHeader(title: title) { if !app.path.isEmpty { app.path.removeLast() } }; Spacer() }.padding(16).frame(maxWidth: .infinity, maxHeight: .infinity).background(HC.bg)
    }
}
