import SwiftUI
import MapKit
import PhotosUI
import Charts
import UIKit

struct HomeView: View {
    @Environment(AppStore.self) private var store; @State private var showGuide = false
    private var todayCalories: Int { store.dashboard.nutritionLogs.reduce(0) { $0 + $1.calories } }
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HomeHeader()
                HomeEnergyCard(calories: todayCalories)
                HomeWorkoutGrid()
                HomeFlow()
                NavigationLink { GoalJourneyView() } label: { HomeGoalCard() }.buttonStyle(.plain)
            }.padding(.horizontal, 16).padding(.bottom, 20)
        }
        .background(Color.hedefitBackground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showGuide) { UserGuideView() }
        .refreshable { await store.refresh() }
    }

    @ViewBuilder private func HomeHeader() -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(Date.now.formatted(.dateTime.weekday(.abbreviated).day()).uppercased()).font(.system(size: 11, weight: .medium, design: .monospaced)).foregroundStyle(Color.hedefitMuted)
                Spacer()
                Button { showGuide = true } label: { Image(systemName: "bell").frame(width: 36, height: 36).background(Color.hedefitSurface, in: Circle()) }
                NavigationLink { ProfileSettingsView() } label: { Text(store.dashboard.profile.displayName.prefix(1).uppercased()).font(.caption.bold()).frame(width: 36, height: 36).background(Color.hedefitGreen.opacity(0.14), in: Circle()).overlay(Circle().stroke(Color.hedefitGreen, lineWidth: 1.5)) }
            }
            HStack(spacing: 10) {
                Text("Günaydın, \(store.dashboard.profile.displayName)").font(.system(size: 29, weight: .heavy, design: .rounded)).tracking(-0.7)
                Spacer(minLength: 0)
                HStack(spacing: 5) { Text("🔥"); Text("\(store.dashboard.streakDays)").fontWeight(.heavy) }.font(.subheadline).foregroundStyle(Color.hedefitGreen).padding(.horizontal, 10).padding(.vertical, 7).background(Color.hedefitGreen.opacity(0.14), in: Capsule()).overlay(Capsule().stroke(Color.hedefitGreen, lineWidth: 1))
            }
            Text("Planın hazır. Bugünün ritmini sade ve istikrarlı tut.").font(.subheadline).foregroundStyle(.secondary)
        }.padding(.top, 8)
    }

    @ViewBuilder private func HomeEnergyCard(calories: Int) -> some View {
        let target = max(store.dashboard.nutritionGoal.calories, 1)
        VStack(spacing: 18) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 7) { Text("KALAN ENERJİ").font(.caption2.weight(.semibold)).foregroundStyle(.secondary); HStack(alignment: .firstTextBaseline, spacing: 7) { Text("\(max(target - calories, 0))").font(.system(size: 34, weight: .heavy, design: .rounded)); Text("kcal").font(.caption.weight(.semibold)).foregroundStyle(.secondary) } }; Spacer(); Text("Dengeyi gör").font(.caption.bold()).padding(.horizontal, 14).padding(.vertical, 10).background(Color.hedefitSurfaceHigh, in: Capsule())
            }
            HomeMeter(label: "Kalori", value: "\(calories) / \(target)", progress: Double(calories) / Double(target), color: .hedefitGreen)
            HomeMeter(label: "Adım", value: "\(store.dashboard.steps) / \(store.settings.stepGoal)", progress: Double(store.dashboard.steps) / Double(max(store.settings.stepGoal, 1)), color: .hedefitBlue)
            HomeMeter(label: "Su", value: "\(store.dashboard.waterMl) / \(store.settings.waterGoal) ml", progress: Double(store.dashboard.waterMl) / Double(max(store.settings.waterGoal, 1)), color: .hedefitBlue)
        }.padding(20).background(Color.hedefitSurface, in: RoundedRectangle(cornerRadius: 24)).overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    @ViewBuilder private func HomeWorkoutGrid() -> some View {
        HStack(spacing: 10) {
            Button { store.selectedTab = .workout } label: {
                VStack(alignment: .leading) {
                    Text("BUGÜNÜN ANTRENMANI").font(.caption2.bold()).opacity(0.68)
                    Text(store.dashboard.workouts.first?.area ?? "Programını oluştur").font(.title3.bold()).padding(.top, 8)
                    Text("\(store.dashboard.workouts.count) hareket · ~38 dk").font(.caption.weight(.medium)).opacity(0.68)
                    Spacer(); Text("Başlat ▶").font(.subheadline.bold())
                }.foregroundStyle(Color.black.opacity(0.84)).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading).padding(18).background(Color.hedefitGreen, in: RoundedRectangle(cornerRadius: 22))
            }.buttonStyle(.plain)
            VStack(spacing: 10) {
                NavigationLink { RouteView() } label: { QuickAction(icon: "location.fill", title: "Rota", subtitle: "GPS ile kaydet") }
                Button { store.selectedTab = .nutrition } label: { QuickAction(icon: "fork.knife", title: "Besin", subtitle: "Öğün kaydet") }.buttonStyle(.plain)
            }.frame(width: 132)
        }.frame(height: 150)
    }

    @ViewBuilder private func HomeFlow() -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Bugünün akışı").font(.headline.bold()).padding(.bottom, 4)
            FlowRow(time: "08:00", title: "Kahvaltı", subtitle: "İlk öğününü ekle", active: false)
            FlowRow(time: "18:30", title: store.dashboard.workoutPrograms.first(where: \.isActive)?.name ?? "Bugünün antrenmanı", subtitle: "\(store.dashboard.workouts.count) hareket", active: true)
            FlowRow(time: "21:00", title: "Gün sonu kontrolü", subtitle: "Enerji dengesini gözden geçir", active: false)
        }
    }

    @ViewBuilder private func HomeGoalCard() -> some View {
        let current = store.dashboard.measurements.last?.weightKg ?? store.dashboard.profile.weightKg
        let target = store.dashboard.profile.targetWeightKg
        VStack(spacing: 14) {
            HStack { Text(target.map { "Hedefe \(abs((current ?? $0) - $0), specifier: "%.0f") kg" } ?? "Hedef yolculuğum").font(.subheadline.bold()); Spacer(); Text("Detaylar →").font(.caption.weight(.semibold)).foregroundStyle(Color.hedefitGreen) }
            ProgressView(value: target == nil ? 0.22 : 0.35).tint(Color.hedefitGreen)
            HStack { Text(current.map { "\($0, specifier: "%.1f") kg" } ?? "Başlangıç"); Spacer(); Text(target.map { "hedef \($0, specifier: "%.1f") kg" } ?? "Hedef") }.font(.system(size: 11, design: .monospaced)).foregroundStyle(Color.hedefitMuted)
        }.padding(18).background(Color.hedefitSurface, in: RoundedRectangle(cornerRadius: 22)).overlay(RoundedRectangle(cornerRadius: 22).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }
}
struct HomeMeter: View { let label, value: String; let progress: Double; let color: Color; var body: some View { VStack(spacing: 8) { HStack { Text(label).foregroundStyle(.secondary); Spacer(); Text(value).fontWeight(.bold) }.font(.caption); ProgressView(value: min(max(progress, 0), 1)).tint(color) } } }
struct FlowRow: View { let time, title, subtitle: String; let active: Bool; var body: some View { HStack(spacing: 13) { Text(time).font(.system(size: 11, design: .monospaced)).foregroundStyle(Color.hedefitMuted).frame(width: 43, alignment: .leading); Circle().fill(active ? Color.hedefitGreen : Color.hedefitSurfaceHigh).frame(width: 10, height: 10).overlay(Circle().stroke(active ? Color.hedefitGreen : Color.white.opacity(0.08))); VStack(alignment: .leading, spacing: 3) { Text(title).font(.subheadline.bold()); Text(subtitle).font(.caption).foregroundStyle(active ? Color.hedefitGreen : .secondary) }; Spacer() }.padding(.vertical, 8) } }
struct QuickAction: View { let icon, title: String; var subtitle = ""; var body: some View { VStack(alignment: .leading, spacing: 4) { Image(systemName: icon).font(.caption).foregroundStyle(Color.hedefitGreen); Spacer(); Text(title).font(.subheadline.bold()); if !subtitle.isEmpty { Text(subtitle).font(.caption2).foregroundStyle(.secondary) } }.foregroundStyle(.primary).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading).padding(15).background(Color.hedefitSurface, in: RoundedRectangle(cornerRadius: 22)).overlay(RoundedRectangle(cornerRadius: 22).stroke(Color.white.opacity(0.08), lineWidth: 1)) } }
struct ExerciseRow: View { let exercise: WorkoutExercise; var body: some View { HStack { Image(systemName: "dumbbell.fill").frame(width: 38, height: 38).background(Color.hedefitGreen.opacity(0.15), in: Circle()).foregroundStyle(Color.hedefitGreen); VStack(alignment: .leading) { Text(exercise.name).fontWeight(.semibold); Text("\(exercise.sets) set × \(exercise.reps) • \(exercise.area)").font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("\(exercise.restSeconds) sn").font(.caption) } } }

struct WorkoutView: View {
    @Environment(AppStore.self) private var store; @State private var showActive = false; @State private var showLibrary = false; @State private var showManual = false
    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 18) {
                    HStack {
                        Button { showLibrary = true } label: { Label("Egzersiz kütüphanesi", systemImage: "books.vertical.fill") }
                        Spacer()
                        Button { showManual = true } label: { Label("Aktivite ekle", systemImage: "plus.circle") }
                    }.font(.subheadline).padding()

                    if !store.dashboard.workoutPrograms.isEmpty {
                        SectionHeader(title: "Programlarım")
                        ForEach(store.dashboard.workoutPrograms) { program in
                            Button {
                                Task {
                                    await store.activateProgram(program)
                                    withAnimation(.snappy) { proxy.scrollTo("active-program-details", anchor: .top) }
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: program.isActive ? "checkmark.circle.fill" : "circle")
                                        .font(.title3).foregroundStyle(program.isActive ? Color.hedefitGreen : Color.hedefitMuted)
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(program.name).fontWeight(.semibold).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                                        Text("\(program.exercises.count) hareket\(program.focusArea.isEmpty ? "" : " • \(program.focusArea)")").font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.down").foregroundStyle(Color.hedefitMuted)
                                }.padding().background(Color.panel, in: RoundedRectangle(cornerRadius: 18))
                            }.buttonStyle(.plain)
                        }
                    }

                    VStack(spacing: 14) {
                        if store.dashboard.workouts.isEmpty {
                            ContentUnavailableView("Henüz program yok", systemImage: "figure.strengthtraining.traditional", description: Text("Profil değerlendirmene göre program oluşturabilir veya kütüphaneden hareket seçebilirsin."))
                            Button("AI programı oluştur") { store.message = "Program oluşturma isteği hazırlandı." }.buttonStyle(HedefitButtonStyle())
                        } else {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text("Aktif program").font(.caption).foregroundStyle(.secondary)
                                    Text(store.dashboard.workoutPrograms.first(where: \.isActive)?.name ?? "Kişisel programım").font(.title2.bold()).fixedSize(horizontal: false, vertical: true)
                                }
                                Spacer()
                                Image(systemName: "figure.strengthtraining.traditional").font(.largeTitle).foregroundStyle(Color.hedefitGreen)
                            }.padding().background(Color.panel, in: RoundedRectangle(cornerRadius: 20))
                            ForEach(store.dashboard.workouts) { ExerciseRow(exercise: $0).padding().background(Color.panel, in: RoundedRectangle(cornerRadius: 16)) }
                            Button("Antrenmanı başlat") { showActive = true }.buttonStyle(HedefitButtonStyle()).frame(maxWidth: .infinity)
                        }
                    }.id("active-program-details")
                }.padding()
            }
            .background(Color.hedefitBackground)
        }
        .navigationTitle("Antrenman")
        .toolbar { NavigationLink { EquipmentScannerView() } label: { Label("Ekipman tara", systemImage: "camera.viewfinder") } }
        .fullScreenCover(isPresented: $showActive) { ActiveWorkoutView(exercises: store.dashboard.workouts) }
        .sheet(isPresented: $showLibrary) { NavigationStack { ExerciseLibraryView() } }
        .sheet(isPresented: $showManual) { NavigationStack { ManualActivityView() } }
    }
}

struct ActiveWorkoutView: View {
    @Environment(AppStore.self) private var store; @Environment(\.dismiss) private var dismiss; let exercises: [WorkoutExercise]
    @State private var index = 0; @State private var elapsed = 0; @State private var sets: [WorkoutSet] = []; @State private var feedback = WorkoutFeedback(); @State private var saving = false
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    var body: some View { NavigationStack { VStack(spacing: 18) {
        HStack { Text(String(format: "%02d:%02d", elapsed / 60, elapsed % 60)).font(.title.monospacedDigit().bold()); Spacer(); Text("\(index + 1)/\(exercises.count)").foregroundStyle(.secondary) }
        ProgressView(value: Double(index + 1), total: Double(max(exercises.count, 1))).tint(Color.hedefitGreen)
        if exercises.indices.contains(index) { let exercise = exercises[index]; VStack(spacing: 10) { Image(systemName: "figure.strengthtraining.traditional").font(.system(size: 70)).foregroundStyle(Color.hedefitGreen); Text(exercise.name).font(.largeTitle.bold()); Text("\(exercise.area) • Dinlenme \(exercise.restSeconds) sn").foregroundStyle(.secondary) }.frame(maxHeight: .infinity); List(0..<exercise.sets, id: \.self) { setIndex in SetInputRow(setNumber: setIndex + 1, exercise: exercise, sets: $sets, order: index) }.listStyle(.plain); HStack { Button("Önceki") { index = max(0, index - 1) }.disabled(index == 0); Spacer(); if index + 1 < exercises.count { Button("Sonraki") { index += 1 }.buttonStyle(HedefitButtonStyle()) } else { Button("Bitir") { finish() }.buttonStyle(HedefitButtonStyle()) } } }
    }.padding().navigationTitle("Aktif antrenman").navigationBarTitleDisplayMode(.inline).toolbar { ToolbarItem(placement: .cancellationAction) { Button("Kapat") { dismiss() } } }.onReceive(timer) { _ in elapsed += 1 }.interactiveDismissDisabled(saving) } }
    private func finish() { saving = true; if sets.isEmpty { for (order, exercise) in exercises.enumerated() { for number in 1...exercise.sets { sets.append(.init(exerciseID: exercise.id, exerciseName: exercise.name, exerciseOrder: order, setNumber: number, reps: Int(exercise.reps.filter(\.isNumber)))) } } }; Task { do { try await store.saveWorkout(sets: sets, seconds: elapsed, calories: max(1, elapsed / 8), feedback: feedback); dismiss() } catch { store.error = error.localizedDescription }; saving = false } }
}

struct EquipmentScannerView: View {
    @Environment(AppStore.self) private var store
    @State private var image: UIImage?
    @State private var result: EquipmentRecognitionResult?
    @State private var showingCamera = false
    @State private var photoItem: PhotosPickerItem?
    @State private var analyzing = false
    @State private var localError: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                VStack(alignment: .leading, spacing: 7) {
                    Label("Canlı ekipman analizi", systemImage: "sparkles.rectangle.stack")
                        .font(.title2.bold()).foregroundStyle(Color.hedefitGreen)
                    Text("Makinenin tamamını, logosunu ve hareketli parçalarını tek kadraja al. Fotoğraf yalnızca ekipmanı tanımak için güvenli sunucuya gönderilir.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity, alignment: .leading)

                ZStack {
                    RoundedRectangle(cornerRadius: 24).fill(Color.hedefitSurface)
                    if let image {
                        Image(uiImage: image).resizable().scaledToFill()
                    } else {
                        VStack(spacing: 12) {
                            Image(systemName: "camera.viewfinder").font(.system(size: 52)).foregroundStyle(Color.hedefitGreen)
                            Text("Ekipmanı çerçevenin ortasına getir").font(.headline)
                            Text("İyi ışık ve tam görünüm doğruluğu artırır.").font(.caption).foregroundStyle(.secondary)
                        }.padding()
                    }
                    if analyzing {
                        Color.black.opacity(0.55)
                        VStack(spacing: 10) { ProgressView(); Text("Ekipman analiz ediliyor…").font(.subheadline.bold()) }
                    }
                }
                .frame(height: 330).clipShape(RoundedRectangle(cornerRadius: 24))
                .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.08)))

                HStack(spacing: 12) {
                    Button { showingCamera = true } label: { Label("Kamerayı aç", systemImage: "camera.fill").frame(maxWidth: .infinity) }
                        .buttonStyle(HedefitButtonStyle()).disabled(analyzing || !UIImagePickerController.isSourceTypeAvailable(.camera))
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label("Galeriden seç", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity).padding(.vertical, 13)
                            .background(Color.hedefitSurfaceHigh, in: RoundedRectangle(cornerRadius: 14))
                    }.disabled(analyzing)
                }

                if let localError {
                    Label(localError, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                        .frame(maxWidth: .infinity, alignment: .leading).padding().background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
                }
                if let result { resultCard(result) }
            }.padding()
        }
        .background(Color.hedefitBackground.ignoresSafeArea())
        .navigationTitle("Ekipman Tara")
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(isPresented: $showingCamera) {
            EquipmentCameraPicker { captured in
                showingCamera = false
                guard let captured else { return }
                image = captured
                Task { await analyze(captured) }
            }
            .ignoresSafeArea()
        }
        .onChange(of: photoItem) { _, newItem in
            guard let newItem else { return }
            Task {
                guard let data = try? await newItem.loadTransferable(type: Data.self), let selected = UIImage(data: data) else {
                    localError = "Fotoğraf açılamadı. Başka bir görsel seç."
                    return
                }
                image = selected
                await analyze(selected)
            }
        }
    }

    @ViewBuilder private func resultCard(_ value: EquipmentRecognitionResult) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if value.recognized, let name = value.localizedName ?? value.equipmentName {
                Label("Ekipman tanındı", systemImage: "checkmark.seal.fill").font(.headline).foregroundStyle(Color.hedefitGreen)
                Text(name).font(.title.bold())
                HStack {
                    if let category = value.category { Text(category.capitalized) }
                    Spacer()
                    Text("%\(Int((value.confidence * 100).rounded())) güven").fontWeight(.bold).foregroundStyle(Color.hedefitGreen)
                }.font(.caption).foregroundStyle(.secondary)
            } else {
                Label("Ekipman net tanınamadı", systemImage: "viewfinder.circle").font(.headline).foregroundStyle(.orange)
                Text("Makinenin tamamı görünecek şekilde yeniden çek. Emin olmadığımızda yanlış isim göstermiyoruz.").font(.subheadline).foregroundStyle(.secondary)
            }
            if !value.visibleFeatures.isEmpty {
                Divider()
                Text("Görünen özellikler").font(.caption.bold()).foregroundStyle(.secondary)
                Text(value.visibleFeatures.joined(separator: " • ")).font(.subheadline)
            }
            if value.alternatives.isEmpty == false {
                Text("Diğer olasılıklar: " + value.alternatives.map { "\($0.localizedName) %\(Int(($0.confidence * 100).rounded()))" }.joined(separator: ", "))
                    .font(.caption).foregroundStyle(.secondary)
            }
        }.frame(maxWidth: .infinity, alignment: .leading).padding(18).background(Color.hedefitSurface, in: RoundedRectangle(cornerRadius: 20))
    }

    @MainActor private func analyze(_ source: UIImage) async {
        analyzing = true; result = nil; localError = nil
        defer { analyzing = false }
        guard let jpeg = source.hedefitJPEG(maxDimension: 1280, quality: 0.82) else { localError = "Fotoğraf hazırlanamadı. Tekrar çek."; return }
        do { result = try await store.recognizeEquipment(jpeg) }
        catch { localError = error.localizedDescription }
    }
}

private struct EquipmentCameraPicker: UIViewControllerRepresentable {
    let onFinish: (UIImage?) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController(); picker.sourceType = .camera; picker.cameraCaptureMode = .photo
        picker.allowsEditing = false; picker.delegate = context.coordinator; return picker
    }
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let onFinish: (UIImage?) -> Void
        init(onFinish: @escaping (UIImage?) -> Void) { self.onFinish = onFinish }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { onFinish(nil) }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) { onFinish(info[.originalImage] as? UIImage) }
    }
}

private extension UIImage {
    func hedefitJPEG(maxDimension: CGFloat, quality: CGFloat) -> Data? {
        let longest = max(size.width, size.height)
        guard longest > 0 else { return nil }
        let scale = min(1, maxDimension / longest)
        let target = CGSize(width: max(1, size.width * scale), height: max(1, size.height * scale))
        let renderer = UIGraphicsImageRenderer(size: target)
        let resized = renderer.image { _ in draw(in: CGRect(origin: .zero, size: target)) }
        return resized.jpegData(compressionQuality: quality)
    }
}
struct SetInputRow: View { let setNumber: Int; let exercise: WorkoutExercise; @Binding var sets: [WorkoutSet]; let order: Int; @State private var kg = ""; @State private var reps = ""; var body: some View { HStack { Text("\(setNumber)").font(.headline).frame(width: 26); TextField("kg", text: $kg).keyboardType(.decimalPad).textFieldStyle(.roundedBorder); TextField("tekrar", text: $reps).keyboardType(.numberPad).textFieldStyle(.roundedBorder); Button { let row = WorkoutSet(exerciseID: exercise.id, exerciseName: exercise.name, exerciseOrder: order, setNumber: setNumber, weightKg: Double(kg), reps: Int(reps)); sets.removeAll { $0.exerciseID == exercise.id && $0.setNumber == setNumber }; sets.append(row) } label: { Image(systemName: sets.contains(where: { $0.exerciseID == exercise.id && $0.setNumber == setNumber }) ? "checkmark.circle.fill" : "circle") }.foregroundStyle(Color.hedefitGreen) } } }

struct NutritionView: View {
    @Environment(AppStore.self) private var store; @State private var showAdd = false
    private var totals: (Int, Double, Double, Double) { store.dashboard.nutritionLogs.reduce((0, 0, 0, 0)) { ($0.0 + $1.calories, $0.1 + $1.protein, $0.2 + $1.carbs, $0.3 + $1.fat) } }
    var body: some View { ScrollView { VStack(spacing: 18) {
        HStack { ProgressRing(value: Double(totals.0) / Double(store.dashboard.nutritionGoal.calories), color: Color.hedefitOrange, title: "Kalori", center: "\(totals.0)"); ProgressRing(value: totals.1 / Double(store.dashboard.nutritionGoal.protein), color: .pink, title: "Protein", center: "\(Int(totals.1))g"); ProgressRing(value: totals.2 / Double(store.dashboard.nutritionGoal.carbs), color: .blue, title: "Karb.", center: "\(Int(totals.2))g"); ProgressRing(value: totals.3 / Double(store.dashboard.nutritionGoal.fat), color: .yellow, title: "Yağ", center: "\(Int(totals.3))g") }.padding().background(Color.panel, in: RoundedRectangle(cornerRadius: 22))
        HStack { VStack(alignment: .leading) { Text("Su takibi").font(.headline); Text("\(store.dashboard.waterMl) / \(store.settings.waterGoal) ml").foregroundStyle(.secondary) }; Spacer(); Button("−250") { Task { await store.addWater(-250) } }; Button("+250") { Task { await store.addWater(250) } }.buttonStyle(.borderedProminent) }.padding().background(.blue.opacity(0.1), in: RoundedRectangle(cornerRadius: 18))
        ForEach(["Kahvaltı", "Öğle", "Akşam", "Ara Öğün"], id: \.self) { meal in VStack(alignment: .leading, spacing: 10) { SectionHeader(title: meal); let logs = store.dashboard.nutritionLogs.filter { $0.meal.localizedCaseInsensitiveContains(meal) || (meal == "Ara Öğün" && $0.meal.lowercased().contains("ara")) }; if logs.isEmpty { Text("Henüz kayıt yok").font(.caption).foregroundStyle(.secondary) } else { ForEach(logs) { log in HStack { VStack(alignment: .leading) { Text(log.name); Text("\(Int(log.grams ?? 0)) g • P \(Int(log.protein))g  K \(Int(log.carbs))g  Y \(Int(log.fat))g").font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("\(log.calories) kcal"); Menu { Button("Sil", role: .destructive) { Task { await store.deleteFood(log) } } } label: { Image(systemName: "ellipsis") } } } } }.padding().background(Color.panel, in: RoundedRectangle(cornerRadius: 18)) }
    }.padding() }.navigationTitle("Beslenme").toolbar { Button { showAdd = true } label: { Image(systemName: "plus") } }.sheet(isPresented: $showAdd) { AddFoodView() } }
}
struct AddFoodView: View { @Environment(AppStore.self) private var store; @Environment(\.dismiss) private var dismiss; @State private var text = ""; @State private var grams = 100.0; @State private var meal = "Kahvaltı"; var body: some View { NavigationStack { Form { Section("Yazarak ekle") { TextField("Örn. 2 yumurta ve peynir", text: $text, axis: .vertical); TextField("Gram", value: $grams, format: .number).keyboardType(.decimalPad); Picker("Öğün", selection: $meal) { ForEach(["Kahvaltı", "Öğle", "Akşam", "Ara Öğün"], id: \.self) { Text($0) } } }; Section { Text("Hedefit AI yazdığın öğünün porsiyonunu ve besin değerlerini tahmin eder. Kaydetmeden sonra günlüğünden düzenleyebilirsin.").font(.caption).foregroundStyle(.secondary) } }.navigationTitle("Öğün ekle").toolbar { ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Hesapla ve kaydet") { Task { await store.addFood(text, grams: grams, meal: meal); if store.error == nil { dismiss() } } }.disabled(text.isEmpty) } } } } }

struct CoachView: View {
    @Environment(AppStore.self) private var store; @State private var input = ""
    var body: some View { VStack { ScrollViewReader { proxy in ScrollView { LazyVStack(spacing: 12) { ForEach(store.chat) { message in HStack { if message.fromUser { Spacer(minLength: 45) }; Text(message.text).padding(12).background(message.fromUser ? Color.hedefitGreen : Color.panel, in: RoundedRectangle(cornerRadius: 17)).foregroundStyle(message.fromUser ? .black : .primary); if !message.fromUser { Spacer(minLength: 45) } }.id(message.id) } }.padding() }.onChange(of: store.chat.count) { _, _ in if let id = store.chat.last?.id { withAnimation { proxy.scrollTo(id, anchor: .bottom) } } } }; HStack { TextField("Koçuna bir şey sor…", text: $input, axis: .vertical).hedefitField(); Button { let text = input; input = ""; Task { await store.sendChat(text) } } label: { Image(systemName: "arrow.up.circle.fill").font(.title).foregroundStyle(Color.hedefitGreen) }.disabled(input.trimmingCharacters(in: .whitespaces).isEmpty || store.loading) }.padding() }.navigationTitle("Hedefit Koç")
}
}

struct ProgressDashboardView: View {
    @Environment(AppStore.self) private var store; @State private var showMeasurement = false
    var body: some View { ScrollView { VStack(spacing: 18) {
        LazyVGrid(columns: [.init(.flexible()), .init(.flexible())]) { MetricCard(title: "Toplam antrenman", value: "\(store.dashboard.sessions.count)", icon: "dumbbell.fill"); MetricCard(title: "Toplam XP", value: "\(store.dashboard.gamificationTotalXp ?? 0)", icon: "star.fill", color: .yellow); MetricCard(title: "Rota mesafesi", value: String(format: "%.1f km", store.dashboard.routeActivities.reduce(0) { $0 + $1.distanceMeters } / 1000), icon: "map.fill", color: .blue); MetricCard(title: "Seri", value: "\(store.dashboard.streakDays) gün", icon: "flame.fill", color: .orange) }
        MuscleDevelopmentMap()
        if !store.dashboard.measurements.isEmpty { VStack(alignment: .leading) { SectionHeader(title: "Kilo değişimi"); Chart(store.dashboard.measurements.compactMap { m in m.weightKg.map { (m.date, $0) } }, id: \.0) { item in LineMark(x: .value("Tarih", item.0), y: .value("Kilo", item.1)).foregroundStyle(Color.hedefitGreen); PointMark(x: .value("Tarih", item.0), y: .value("Kilo", item.1)).foregroundStyle(Color.hedefitGreen) }.frame(height: 190) }.padding().background(Color.panel, in: RoundedRectangle(cornerRadius: 20)) }
        VStack(alignment: .leading, spacing: 10) { SectionHeader(title: "Son aktiviteler"); ForEach(store.dashboard.sessions.prefix(10)) { session in HStack { Image(systemName: session.manualActivityKey == nil ? "dumbbell.fill" : "figure.mixed.cardio").foregroundStyle(Color.hedefitGreen); VStack(alignment: .leading) { Text(session.exerciseNames.first ?? "Aktivite"); Text(session.completedAt.prefix(10)).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("\(session.calories) kcal") } } }.padding().background(Color.panel, in: RoundedRectangle(cornerRadius: 20))
        NavigationLink { GoalJourneyView() } label: { Label("Hedef yolculuğu", systemImage: "flag.checkered").frame(maxWidth: .infinity) }.buttonStyle(HedefitButtonStyle())
    }.padding() }.background(Color.hedefitBackground).navigationTitle("İlerleme").toolbar { Button { showMeasurement = true } label: { Image(systemName: "plus") } }.sheet(isPresented: $showMeasurement) { MeasurementView() } }
}

struct MuscleDevelopmentMap: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Kas Gelişim Haritası")
            Image("MuscleAnatomy")
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity)
                .accessibilityLabel("Ön ve arka kas anatomisi")
                .clipShape(RoundedRectangle(cornerRadius: 18))
            HStack(spacing: 12) {
                legend(.hedefitMuted, "Veri yok")
                legend(.hedefitGreen, "Dengeli")
                legend(.yellow, "Yüksek")
                legend(.red, "Aşırı")
            }.font(.caption2)
        }
        .padding()
        .background(Color.panel, in: RoundedRectangle(cornerRadius: 22))
    }

    private func legend(_ color: Color, _ title: String) -> some View {
        HStack(spacing: 4) { Circle().fill(color).frame(width: 7, height: 7); Text(title).lineLimit(1).minimumScaleFactor(0.75) }
    }
}
