import SwiftUI

extension Color {
    static let hedefitGreen = Color(red: 0.133, green: 0.773, blue: 0.369)
    static let hedefitWater = Color(red: 0.22, green: 0.74, blue: 0.97)
    static let hedefitCoral = Color(red: 1.0, green: 0.42, blue: 0.42)
    static let hedefitAmber = Color(red: 0.98, green: 0.75, blue: 0.14)
    static let hedefitSurface = Color(red: 0.10, green: 0.12, blue: 0.15)
}

func clock(_ seconds: Int) -> String {
    let s = max(0, seconds)
    return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
}
