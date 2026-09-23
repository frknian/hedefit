package com.hedefit.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Share
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.hedefit.app.data.model.ExerciseCatalogData
import com.hedefit.app.R
import com.hedefit.app.data.model.WorkoutExercisePerformanceData
import com.hedefit.app.equipment.EquipmentCatalog
import com.hedefit.app.equipment.EquipmentInfo
import com.hedefit.app.equipment.EquipmentRecognitionError
import com.hedefit.app.equipment.EquipmentRecognitionException
import com.hedefit.app.equipment.EquipmentRecognitionService
import com.hedefit.app.equipment.RecognitionResult
import com.hedefit.app.gym.LoadLevel
import com.hedefit.app.gym.MuscleLoad
import com.hedefit.app.gym.TrainingAnalysis
import com.hedefit.app.gym.WorkoutShareCard
import com.hedefit.app.gym.WorkoutSummary
import com.hedefit.app.gym.analyzeTraining
import com.hedefit.app.gym.compactKg
import com.hedefit.app.gym.muscleNameEn
import com.hedefit.app.gym.muscleNameTr
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.ExerciseMedia
import com.hedefit.app.ui.components.OutlineAction
import com.hedefit.app.ui.components.PrimaryButton
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.components.SectionTitle
import com.hedefit.app.ui.components.Sparkline
import com.hedefit.app.ui.theme.HedefitColors
import java.time.DayOfWeek
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun cameraPermissionMessage(granted: Boolean, permanentlyDenied: Boolean, cameraAvailable: Boolean): String? = when {
    !cameraAvailable -> "Bu cihazda kullanılabilir kamera bulunamadı."
    granted -> null
    permanentlyDenied -> "Kamera izni kalıcı olarak kapalı. Ayarlar'dan Hedefit için kamera iznini açabilirsin."
    else -> "Ekipmanı taramak için kamera izni gerekiyor."
}

@Composable
fun EquipmentScannerScreen(
    onBack: () -> Unit,
    onAddExercise: (ExerciseCatalogData) -> Unit,
    exerciseCatalog: List<ExerciseCatalogData>,
    recognitionService: EquipmentRecognitionService,
    language: String = "tr",
) {
    val context = LocalContext.current
    val en = language == "en"
    val cameraAvailable = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var selected by remember { mutableStateOf<EquipmentInfo?>(null) }
    var recognition by remember { mutableStateOf<RecognitionResult?>(null) }
    var recognitionError by remember { mutableStateOf<String?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var exerciseChoices by remember { mutableStateOf<List<ExerciseCatalogData>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        permanentlyDenied = !it && !(context as? android.app.Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA).orFalse()
    }

    ScreenContainer { Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (en) "Back" else "Geri") }
            Column(Modifier.weight(1f)) {
                Text(if (en) "Scan equipment" else "Ekipman Tara", style = MaterialTheme.typography.titleLarge)
                Text(if (en) "Keep the whole machine in frame" else "Cihazın tamamını kadrajda tut", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        val permissionMessage = cameraPermissionMessage(permissionGranted, permanentlyDenied, cameraAvailable)
        if (permissionGranted && cameraAvailable) {
            CameraPreview(Modifier.fillMaxWidth().weight(1f), onCaptureReady = { imageCapture = it })
        } else {
            Box(Modifier.fillMaxWidth().weight(1f).background(HedefitColors.Surface), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Default.CameraAlt, null, tint = HedefitColors.TextSecondary, modifier = Modifier.size(56.dp))
                    Text(permissionMessage.orEmpty(), color = HedefitColors.TextSecondary)
                    if (cameraAvailable) PrimaryButton(if (en) "Allow camera" else "Kamera İzni Ver", { launcher.launch(Manifest.permission.CAMERA) })
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (permissionGranted) Button(
                onClick = { scope.launch {
                    if (recognizing) return@launch
                    recognizing = true; recognition = null; recognitionError = null
                    runCatching {
                        val capture = imageCapture ?: throw EquipmentRecognitionException(EquipmentRecognitionError.INVALID_IMAGE, "Kamera henüz hazır değil.")
                        recognitionService.recognize(captureEquipmentFrame(context, capture))
                    }.onSuccess { recognition = it }
                        .onFailure { recognitionError = equipmentErrorMessage(it, en) }
                    recognizing = false
                } },
                enabled = !recognizing && imageCapture != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
            ) {
                if (recognizing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = HedefitColors.OnLime)
                Text(if (recognizing) (if (en) "  Recognizing…" else "  Ekipman tanınıyor…") else (if (en) "Check recognition" else "Tanımayı Kontrol Et"))
            }
            Text(if (en) "The frame is sent securely for analysis and is not saved." else "Kare güvenli analiz için gönderilir; cihazda veya Hedefit'te saklanmaz.", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            recognitionError?.let { Text(it, color = HedefitColors.Warning, style = MaterialTheme.typography.bodySmall) }
            recognition?.let { result -> EquipmentRecognitionCard(result, en, exerciseCatalog, onDetails = { selected = it }, onAdd = { equipment ->
                val matches = EquipmentCatalog.matchingExercises(equipment, exerciseCatalog)
                when (matches.size) {
                    0 -> recognitionError = if (en) "No matching exercise was found in your Hedefit library." else "Hedefit egzersiz kütüphanesinde bu ekipmana uygun hareket bulunamadı."
                    1 -> onAddExercise(matches.first())
                    else -> exerciseChoices = matches.take(12)
                }
            }, onAlternative = { recognition = RecognitionResult.Recognized(it, .65f) }) }
            Text(if (en) "Not the right equipment? Choose manually" else "Doğru ekipman değil mi? Listeden seç", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EquipmentCatalog.items, key = { it.id }) { equipment ->
                    HedefitCard(Modifier.clickable { selected = equipment }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(equipment.name, color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    } }
    selected?.let { equipment -> EquipmentDetailDialog(equipment, EquipmentCatalog.matchingExercises(equipment, exerciseCatalog), en, { selected = null }) {
        selected = null
        val matches = EquipmentCatalog.matchingExercises(equipment, exerciseCatalog)
        if (matches.size == 1) onAddExercise(matches.first()) else if (matches.isNotEmpty()) exerciseChoices = matches.take(12)
    } }
    if (exerciseChoices.isNotEmpty()) ExerciseChoiceDialog(exerciseChoices, en, { exerciseChoices = emptyList() }) { exercise ->
        exerciseChoices = emptyList(); onAddExercise(exercise)
    }
}

@Composable
private fun CameraPreview(modifier: Modifier = Modifier, onCaptureReady: (ImageCapture?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var error by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(lifecycleOwner) { onDispose { onCaptureReady(null); cameraProvider?.unbindAll() } }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { previewContext ->
                PreviewView(previewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    val view = this
                    val providerFuture = ProcessCameraProvider.getInstance(previewContext)
                    providerFuture.addListener({
                        runCatching {
                            val provider = providerFuture.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                            onCaptureReady(capture)
                        }.onFailure { error = "Kamera başlatılamadı. Başka bir uygulamanın kamerayı kullanmadığından emin ol." }
                    }, ContextCompat.getMainExecutor(previewContext))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxWidth(.72f).aspectRatio(.78f).border(3.dp, HedefitColors.Lime, RoundedCornerShape(28.dp)))
        Text("HEDEFİT SCAN", color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).background(Color.Black.copy(alpha = .58f), CircleShape).padding(horizontal = 14.dp, vertical = 7.dp))
        error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp).background(Color.Black.copy(alpha = .78f), RoundedCornerShape(12.dp)).padding(12.dp)) }
    }
}

private suspend fun captureEquipmentFrame(context: Context, capture: ImageCapture): ByteArray {
    val (encoded, rotation) = suspendCancellableCoroutine<Pair<ByteArray, Int>> { continuation ->
        capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val buffer = image.planes.firstOrNull()?.buffer ?: throw IllegalStateException("Kamera karesi boş.")
                    val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                    if (continuation.isActive) continuation.resume(bytes to image.imageInfo.rotationDegrees)
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally { image.close() }
            }
            override fun onError(exception: ImageCaptureException) {
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        })
    }
    return withContext(Dispatchers.Default) {
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
            ?: throw EquipmentRecognitionException(EquipmentRecognitionError.INVALID_IMAGE, "Kamera karesi işlenemedi.")
        val oriented = if (rotation == 0) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true).also { decoded.recycle() }
        val longest = maxOf(oriented.width, oriented.height)
        val scaled = if (longest <= 1280) oriented else {
            val ratio = 1280f / longest
            Bitmap.createScaledBitmap(oriented, (oriented.width * ratio).toInt(), (oriented.height * ratio).toInt(), true).also { oriented.recycle() }
        }
        ByteArrayOutputStream().use { output ->
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)) throw EquipmentRecognitionException(EquipmentRecognitionError.INVALID_IMAGE, "Fotoğraf sıkıştırılamadı.")
            scaled.recycle()
            output.toByteArray()
        }
    }
}

private fun equipmentErrorMessage(error: Throwable, en: Boolean): String {
    val kind = (error as? EquipmentRecognitionException)?.kind
    return if (en) when (kind) {
        EquipmentRecognitionError.OFFLINE -> "No internet connection. Cloud recognition needs a connection."
        EquipmentRecognitionError.TIMEOUT -> "Recognition timed out. Try again."
        EquipmentRecognitionError.RATE_LIMIT -> "Recognition limit reached. Try again later."
        EquipmentRecognitionError.CONFIGURATION -> "Recognition is not published or configured on the Hedefit server."
        EquipmentRecognitionError.SERVICE_UNAVAILABLE -> "Recognition service is temporarily unavailable."
        EquipmentRecognitionError.INVALID_RESPONSE -> "The vision service returned an invalid response. Try again."
        EquipmentRecognitionError.INVALID_IMAGE -> "The camera frame could not be prepared. Reframe the equipment and retry."
        else -> "Equipment could not be recognized. Try again."
    } else when (kind) {
        EquipmentRecognitionError.OFFLINE -> "İnternet bağlantısı yok. Bulut tanıma için bağlantı gerekiyor."
        EquipmentRecognitionError.TIMEOUT -> "Tanıma isteği zaman aşımına uğradı. Tekrar dene."
        EquipmentRecognitionError.RATE_LIMIT -> "Tanıma limitine ulaşıldı. Daha sonra tekrar dene."
        EquipmentRecognitionError.CONFIGURATION -> "Tanıma servisi Hedefit sunucusunda henüz yayında değil veya yapılandırılmamış."
        EquipmentRecognitionError.SERVICE_UNAVAILABLE -> "Tanıma servisi geçici olarak kullanılamıyor."
        EquipmentRecognitionError.INVALID_RESPONSE -> "Görüntü modeli geçerli bir yanıt döndürmedi. Tekrar dene."
        EquipmentRecognitionError.INVALID_IMAGE -> "Kamera karesi hazırlanamadı. Ekipmanı yeniden kadraja al."
        else -> "Ekipman tanınamadı. Tekrar deneyebilirsin."
    }
}

@Composable
private fun EquipmentRecognitionCard(
    result: RecognitionResult,
    en: Boolean,
    catalog: List<ExerciseCatalogData>,
    onDetails: (EquipmentInfo) -> Unit,
    onAdd: (EquipmentInfo) -> Unit,
    onAlternative: (EquipmentInfo) -> Unit,
) {
    HedefitCard(Modifier.fillMaxWidth()) {
        when (result) {
            is RecognitionResult.Unknown -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (en) "Equipment couldn't be identified confidently" else "Ekipman güvenle tanınamadı", color = HedefitColors.Warning, style = MaterialTheme.typography.titleMedium)
                Text(if (en) "Keep the entire machine in frame, improve lighting, and try from another angle." else "Cihazın tamamını kadraja al, ışığı iyileştir ve farklı bir açıdan tekrar dene.", color = HedefitColors.TextSecondary)
                if (result.visibleFeatures.isNotEmpty()) Text(result.visibleFeatures.joinToString(" • "), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            is RecognitionResult.Recognized -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val strong = result.confidence >= .8f
                Text(if (strong) (if (en) "Equipment identified" else "Ekipman tanındı") else (if (en) "I think this is" else "Bunun şu ekipman olduğunu düşünüyorum"), color = if (strong) HedefitColors.Lime else HedefitColors.Warning, style = MaterialTheme.typography.labelLarge)
                Text(result.equipment.name, style = MaterialTheme.typography.headlineSmall)
                Text("${result.equipment.category} • %${(result.confidence * 100).toInt()}", color = HedefitColors.TextSecondary)
                Text((if (en) "Primary muscles: " else "Ana kaslar: ") + result.equipment.primaryMuscles.joinToString { if (en) muscleNameEn(it) else muscleNameTr(it) }, color = HedefitColors.TextSecondary)
                if (result.visibleFeatures.isNotEmpty()) Text(result.visibleFeatures.joinToString(" • "), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (!strong && result.alternatives.isNotEmpty()) {
                    Text(if (en) "Other possibilities" else "Diğer olasılıklar", style = MaterialTheme.typography.labelMedium)
                    result.alternatives.forEach { alternative -> TextButton(onClick = { onAlternative(alternative.equipment) }) { Text("${alternative.equipment.name} • %${(alternative.confidence * 100).toInt()}") } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onDetails(result.equipment) }, modifier = Modifier.weight(1f)) { Text(if (en) "How to use" else "Nasıl Kullanılır?") }
                    Button(onClick = { onAdd(result.equipment) }, enabled = EquipmentCatalog.matchingExercises(result.equipment, catalog).isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Add to workout" else "Antrenmana Ekle") }
                }
            }
        }
    }
}

private fun Boolean?.orFalse() = this == true

@Composable
private fun EquipmentDetailDialog(equipment: EquipmentInfo, exercises: List<ExerciseCatalogData>, en: Boolean, onDismiss: () -> Unit, onAdd: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(equipment.name) },
        text = { LazyColumn(Modifier.height(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            exercises.firstOrNull()?.imageUrls?.firstOrNull()?.let { image -> item { ExerciseMedia(image, equipment.name, Modifier.fillMaxWidth().height(180.dp)) } }
            item { Text(equipment.description, color = HedefitColors.TextSecondary) }
            item { DetailBlock(if (en) "Primary muscles" else "Ana kaslar", equipment.primaryMuscles.joinToString { if (en) muscleNameEn(it) else muscleNameTr(it) }) }
            item { DetailBlock(if (en) "Secondary muscles" else "Yardımcı kaslar", equipment.secondaryMuscles.joinToString { if (en) muscleNameEn(it) else muscleNameTr(it) }.ifBlank { "—" }) }
            item { DetailBlock(if (en) "How to use" else "Nasıl kullanılır?", equipment.instructions.mapIndexed { index, text -> "${index + 1}. $text" }.joinToString("\n")) }
            item { DetailBlock(if (en) "Common mistakes" else "Yaygın hatalar", equipment.commonMistakes.joinToString("\n") { "• $it" }) }
            item { DetailBlock(if (en) "Safety" else "Güvenlik", equipment.safetyNotes.joinToString("\n") { "• $it" }) }
            item { DetailBlock(if (en) "Suitable exercises" else "Uygun hareketler", exercises.take(8).joinToString("\n") { "• ${it.name}" }.ifBlank { if (en) "No matching exercise in the library." else "Kütüphanede eşleşen hareket bulunamadı." }) }
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (en) "Close" else "Kapat") } },
        confirmButton = { Button(onClick = onAdd, enabled = exercises.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Add to workout" else "Antrenmana Ekle") } },
    )
}

@Composable
private fun ExerciseChoiceDialog(exercises: List<ExerciseCatalogData>, en: Boolean, onDismiss: () -> Unit, onSelect: (ExerciseCatalogData) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Choose an exercise" else "Hareket seç") },
        text = { LazyColumn(Modifier.height(380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(exercises, key = ExerciseCatalogData::id) { exercise ->
                HedefitCard(Modifier.fillMaxWidth().clickable { onSelect(exercise) }, contentPadding = PaddingValues(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExerciseMedia(exercise.imageUrls.firstOrNull(), exercise.name, Modifier.size(54.dp))
                        Column { Text(exercise.name); Text(exercise.primaryMuscles.joinToString(), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") } },
    )
}

@Composable private fun DetailBlock(title: String, value: String) { Column { Text(title, color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge); Text(value, color = HedefitColors.TextSecondary) } }

@Composable
fun WorkoutSummaryScreen(summary: WorkoutSummary, onDone: () -> Unit, language: String = "tr") {
    val context = LocalContext.current
    val en = language == "en"
    val shareText = "${summary.title} • ${summary.setCount} set • ${summary.volumeKg.compactKg()} • ${formatGymDuration(summary.durationSeconds)}"
    var previewTemplate by remember { mutableStateOf<WorkoutShareCard.Template?>(null) }
    val previewBitmap = remember(previewTemplate) { previewTemplate?.let { WorkoutShareCard.render(summary, it) } }
    ScreenContainer { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (en) "Workout complete" else "Antrenman Tamamlandı", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(onClick = onDone) { Icon(Icons.Default.Check, if (en) "Done" else "Tamam", tint = HedefitColors.Lime) } } }
        item { HedefitCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(summary.title.uppercase(), color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge); Text(java.time.LocalDate.now().toString(), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall); Text(formatGymDuration(summary.durationSeconds), style = MaterialTheme.typography.displaySmall); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { SummaryMetric("${summary.exerciseCount}", if (en) "exercises" else "hareket"); SummaryMetric("${summary.setCount}", "set"); SummaryMetric("${summary.repetitions}", if (en) "reps" else "tekrar") }; Text("${summary.volumeKg.compactKg()} • ${summary.calories} kcal", style = MaterialTheme.typography.headlineSmall); summary.strongestExercise?.let { Text((if (en) "Strongest movement: " else "En güçlü hareket: ") + it, color = HedefitColors.TextSecondary) }; if (summary.muscleGroups.isNotEmpty()) Text((if (en) "Muscles: " else "Çalışan kaslar: ") + summary.muscleGroups.joinToString { if (en) muscleNameEn(it) else muscleNameTr(it) }, color = HedefitColors.TextSecondary) } } }
        if (summary.personalRecords.isNotEmpty()) item { HedefitCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(if (en) "NEW PR" else "YENİ PR", color = HedefitColors.Lime, style = MaterialTheme.typography.headlineSmall); summary.personalRecords.forEach { Text("${it.exerciseName} • ${if (en) "Estimated 1RM" else "Tahmini 1RM"} %.1f kg".format(it.estimatedOneRepMax)) } } } }
        item { SectionTitle(if (en) "Share cards" else "Paylaşım Kartları") }
        item { PrimaryButton(if (en) "Workout summary • 9:16" else "Antrenman Özeti • 9:16", { previewTemplate = WorkoutShareCard.Template.WORKOUT_SUMMARY }, icon = Icons.Default.Share) }
        if (summary.personalRecords.isNotEmpty()) item { OutlineAction(if (en) "New PR card" else "Yeni PR Kartı", { previewTemplate = WorkoutShareCard.Template.PERSONAL_RECORD }) }
        item { OutlineAction(if (en) "Muscle map card" else "Kas Haritası Kartı", { previewTemplate = WorkoutShareCard.Template.MUSCLE_MAP }) }
        item { OutlineAction(if (en) "Copy summary" else "Özeti Kopyala", onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Hedefit", shareText)) }) }
        item { TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (en) "Done" else "Bitti") } }
    } }
    val template = previewTemplate
    val bitmap = previewBitmap
    if (template != null && bitmap != null) AlertDialog(
        onDismissRequest = { previewTemplate = null },
        title = { Text(if (en) "Card preview" else "Kart Önizlemesi") },
        text = {
            Image(
                bitmap.asImageBitmap(),
                if (en) "Share card preview" else "Paylaşım kartı önizlemesi",
                Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp)),
            )
        },
        dismissButton = { TextButton(onClick = { previewTemplate = null }) { Text(if (en) "Close" else "Kapat") } },
        confirmButton = { TextButton(onClick = { WorkoutShareCard.shareBitmap(context, bitmap, summary, template); previewTemplate = null }) { Text(if (en) "Share" else "Paylaş", color = HedefitColors.Lime) } },
    )
}

@Composable private fun SummaryMetric(value: String, label: String) { Column { Text(value, style = MaterialTheme.typography.headlineSmall); Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) } }
private fun formatGymDuration(seconds: Int) = if (seconds >= 3600) "%d sa %02d dk".format(seconds / 3600, seconds / 60 % 60) else "%d dk %02d sn".format(seconds / 60, seconds % 60)

@Composable
fun TrainingAnalysisSection(
    performances: List<WorkoutExercisePerformanceData>,
    catalog: List<ExerciseCatalogData> = emptyList(),
    language: String = "tr",
) {
    val en = language == "en"
    var rangeDays by remember { mutableStateOf(7) }
    var selectedMuscle by remember { mutableStateOf<MuscleLoad?>(null) }
    val analysis = remember(performances, catalog, rangeDays) { analyzeTraining(performances, catalog, rangeDays = rangeDays) }
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (en) "Estimated training stimulus from completed sets; this is not a physical muscle-size measurement."
                else "Tamamlanan setlerden tahmini antrenman yükünü gösterir; fiziksel kas büyüklüğü ölçümü değildir.",
                color = HedefitColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            val ranges = listOf(7 to (if (en) "This week" else "Bu hafta"), 30 to (if (en) "30 days" else "Son 30 gün"), 90 to (if (en) "3 months" else "Son 3 ay"))
            com.hedefit.app.ui.components.HfSegmented(ranges.map { it.second }, ranges.indexOfFirst { it.first == rangeDays }, { rangeDays = ranges[it].first })
            if (analysis.muscleLoads.all { it.level == LoadLevel.NONE }) {
                Text(if (en) "There is not enough workout data yet. Complete your first set to unlock the map." else "Henüz yeterli antrenman verin yok. Kas haritasını açmak için ilk setini tamamla.", color = HedefitColors.TextSecondary)
            }
            MuscleFigure(
                analysis,
                Modifier.fillMaxWidth(),
                selected = selectedMuscle?.muscle,
                onSelect = { muscle -> selectedMuscle = analysis.muscleLoads.firstOrNull { it.muscle == muscle } },
            )
            Text(if (en) "Tap a muscle to see its sets, volume, exercises and trend." else "Set, hacim, hareket ve trend ayrıntısı için bir kasa dokun.", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            analysis.muscleLoads.filter { it.level != LoadLevel.NONE }.take(5).forEach { load ->
                Row(Modifier.fillMaxWidth().clickable { selectedMuscle = load }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(loadColor(load.level), CircleShape))
                    Text(if (en) muscleNameEn(load.muscle) else muscleNameTr(load.muscle).replaceFirstChar(Char::uppercase), Modifier.padding(start = 8.dp).weight(1f))
                    Text("%.1f set • %s".format(load.setEquivalent, load.totalVolumeKg.compactKg()), color = HedefitColors.TextSecondary)
                }
            }
            Text(if (en) analysis.balanceInsightEn else analysis.balanceInsightTr, color = HedefitColors.TextSecondary)
        }
    }
    Spacer(Modifier.height(14.dp))
    WeeklyVolumeCard(analysis, en)
    selectedMuscle?.let { load -> MuscleDetailSheet(load, rangeDays, en) { selectedMuscle = null } }
}

/**
 * Bölge merkezleri ve yarıçapları, `muscle_anatomy.png` (1254x1254, kare)
 * üzerinde gövde siluetinin piksel piksel ölçülmesiyle türetildi; değerler
 * TAM görselin 0..1 normalize koordinatlarıdır. Görsel kare olduğu ve
 * `MuscleFigure` hem görseli hem Canvas'ı `aspectRatio(1f)` ile aynı kare
 * kutuya sabitlediği için bu koordinatlar birebir hizalanır — eski sürümde
 * Canvas tüm kutuyu, görsel ise `ContentScale.Fit` ile letterbox'lanmış daha
 * küçük bir alanı kullandığından işaretler gövdenin dışına düşüyordu.
 */
private data class AnatomicalRegion(val muscle: String, val x: Float, val y: Float, val rx: Float, val ry: Float)

private val muscleRegionsFront = listOf(
    AnatomicalRegion("neck", .312f, .152f, .028f, .020f),
    AnatomicalRegion("front_delts", .258f, .192f, .030f, .023f), AnatomicalRegion("front_delts", .360f, .192f, .032f, .023f),
    AnatomicalRegion("side_delts", .207f, .208f, .026f, .024f), AnatomicalRegion("side_delts", .421f, .208f, .026f, .024f),
    AnatomicalRegion("chest", .312f, .245f, .072f, .032f),
    AnatomicalRegion("biceps", .183f, .285f, .024f, .038f), AnatomicalRegion("biceps", .438f, .285f, .024f, .038f),
    AnatomicalRegion("abs", .315f, .345f, .048f, .054f),
    AnatomicalRegion("forearms", .150f, .400f, .026f, .042f), AnatomicalRegion("forearms", .462f, .400f, .026f, .042f),
    AnatomicalRegion("abductors", .228f, .512f, .022f, .030f), AnatomicalRegion("abductors", .396f, .512f, .022f, .030f),
    AnatomicalRegion("adductors", .285f, .548f, .022f, .038f), AnatomicalRegion("adductors", .340f, .548f, .022f, .038f),
    AnatomicalRegion("quads", .252f, .578f, .042f, .052f), AnatomicalRegion("quads", .373f, .578f, .042f, .052f),
    AnatomicalRegion("calves", .245f, .748f, .031f, .040f), AnatomicalRegion("calves", .377f, .748f, .031f, .040f),
)

private val muscleRegionsBack = listOf(
    AnatomicalRegion("neck", .687f, .152f, .026f, .020f),
    AnatomicalRegion("traps", .688f, .196f, .052f, .026f),
    AnatomicalRegion("rear_delts", .584f, .212f, .030f, .024f), AnatomicalRegion("rear_delts", .790f, .212f, .030f, .024f),
    AnatomicalRegion("upper_back", .689f, .272f, .068f, .030f),
    AnatomicalRegion("triceps", .568f, .292f, .024f, .038f), AnatomicalRegion("triceps", .813f, .292f, .026f, .038f),
    AnatomicalRegion("lats", .688f, .345f, .076f, .042f),
    AnatomicalRegion("forearms", .542f, .400f, .026f, .042f), AnatomicalRegion("forearms", .844f, .400f, .026f, .042f),
    AnatomicalRegion("lower_back", .687f, .437f, .048f, .030f),
    AnatomicalRegion("glutes", .687f, .503f, .078f, .038f),
    AnatomicalRegion("abductors", .604f, .532f, .022f, .030f), AnatomicalRegion("abductors", .772f, .532f, .022f, .030f),
    AnatomicalRegion("adductors", .652f, .578f, .021f, .036f), AnatomicalRegion("adductors", .722f, .578f, .021f, .036f),
    AnatomicalRegion("hamstrings", .628f, .605f, .040f, .048f), AnatomicalRegion("hamstrings", .746f, .605f, .040f, .048f),
    AnatomicalRegion("calves", .621f, .735f, .031f, .040f), AnatomicalRegion("calves", .754f, .735f, .031f, .040f),
)

private val muscleRegions = muscleRegionsFront + muscleRegionsBack

@Composable
private fun MuscleFigure(analysis: TrainingAnalysis, modifier: Modifier, selected: String?, onSelect: (String) -> Unit) {
    val score = analysis.muscleLoads.associateBy { it.muscle }
    HedefitCard(modifier, contentPadding = PaddingValues(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Image(
                    painter = painterResource(R.drawable.muscle_anatomy),
                    contentDescription = if (selected != null) "Kas anatomisi, seçili bölge: ${muscleNameTr(selected)}" else "Ön ve arka kas anatomisi",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.12f) }),
                    alpha = .62f,
                )
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { tap ->
                            // Elips içi testi; üst üste binen bölgelerde merkeze en yakın olan kazanır.
                            muscleRegions.mapNotNull { region ->
                                val dx = (tap.x / size.width - region.x) / region.rx
                                val dy = (tap.y / size.height - region.y) / region.ry
                                val d = dx * dx + dy * dy
                                if (d <= 1f) region to d else null
                            }.minByOrNull { it.second }?.let { onSelect(it.first.muscle) }
                        }
                    },
                ) {
                    muscleRegions.forEach { region ->
                        val level = score[region.muscle]?.level ?: LoadLevel.NONE
                        val center = Offset(region.x * size.width, region.y * size.height)
                        val rx = region.rx * size.width
                        val ry = region.ry * size.height
                        if (level == LoadLevel.NONE) {
                            // Çalışılmamış kas: dokunulabilir olduğunu belli eden küçük bir nokta.
                            // (Tam elips çerçevesi, üst üste binen bölgelerde gövdeyi okunmaz
                            // hale getiren bir çizgi yumağına dönüşüyordu.)
                            drawCircle(
                                color = HedefitColors.TextSecondary.copy(alpha = .30f),
                                radius = 1.6.dp.toPx(),
                                center = center,
                            )
                            return@forEach
                        }
                        val color = loadColor(level)
                        // Elips şeklinde yumuşak ısı lekesi: daire + Y ekseninde ölçekleme.
                        withTransform({ scale(1f, ry / rx, center) }) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to color.copy(alpha = .72f),
                                        .45f to color.copy(alpha = .46f),
                                        .78f to color.copy(alpha = .18f),
                                        1f to Color.Transparent,
                                    ),
                                    center = center,
                                    radius = rx * 1.35f,
                                ),
                                radius = rx * 1.35f,
                                center = center,
                            )
                        }
                        if (region.muscle == selected) drawOval(
                            color = Color.White.copy(alpha = .85f),
                            topLeft = Offset(center.x - rx * 1.2f, center.y - ry * 1.2f),
                            size = Size(rx * 2.4f, ry * 2.4f),
                            style = Stroke(1.6.dp.toPx()),
                        )
                    }
                }
                Text(
                    "ÖN",
                    color = HedefitColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 4.dp),
                )
                Text(
                    "ARKA",
                    color = HedefitColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp, top = 4.dp),
                )
            }
            MuscleLoadLegend()
        }
    }
}

@Composable
private fun MuscleLoadLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(
            LoadLevel.NONE to "Çalışılmadı",
            LoadLevel.LOW to "Az",
            LoadLevel.BALANCED to "Dengeli",
            LoadLevel.HIGH to "Yoğun",
            LoadLevel.OVERLOAD to "Aşırı",
        ).forEach { (level, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).background(loadColor(level), CircleShape))
                Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun DrawScope.drawAnatomicalBody(centerX: Float, figureWidth: Float, front: Boolean) {
    fun x(value: Float) = centerX + value * figureWidth
    fun y(value: Float) = value * size.height
    val base = HedefitColors.SurfaceSoft
    val contour = HedefitColors.TextSecondary.copy(alpha = .58f)

    drawOval(base, Offset(x(-.145f), y(.025f)), Size(figureWidth * .29f, size.height * .14f))
    drawRect(base, Offset(x(-.075f), y(.145f)), Size(figureWidth * .15f, size.height * .075f))
    val torso = Path().apply {
        moveTo(x(-.31f), y(.20f)); cubicTo(x(-.42f), y(.25f), x(-.31f), y(.43f), x(-.20f), y(.57f))
        cubicTo(x(-.25f), y(.63f), x(-.22f), y(.68f), x(-.13f), y(.69f))
        lineTo(x(.13f), y(.69f)); cubicTo(x(.22f), y(.68f), x(.25f), y(.63f), x(.20f), y(.57f))
        cubicTo(x(.31f), y(.43f), x(.42f), y(.25f), x(.31f), y(.20f)); close()
    }
    drawPath(torso, base)
    drawLine(base, Offset(x(-.31f), y(.25f)), Offset(x(-.52f), y(.58f)), figureWidth * .15f, StrokeCap.Round)
    drawLine(base, Offset(x(.31f), y(.25f)), Offset(x(.52f), y(.58f)), figureWidth * .15f, StrokeCap.Round)
    drawLine(base, Offset(x(-.52f), y(.56f)), Offset(x(-.58f), y(.72f)), figureWidth * .105f, StrokeCap.Round)
    drawLine(base, Offset(x(.52f), y(.56f)), Offset(x(.58f), y(.72f)), figureWidth * .105f, StrokeCap.Round)
    drawOval(base, Offset(x(-.635f), y(.69f)), Size(figureWidth * .12f, size.height * .07f))
    drawOval(base, Offset(x(.515f), y(.69f)), Size(figureWidth * .12f, size.height * .07f))
    val leftLeg = Path().apply { moveTo(x(-.19f), y(.66f)); lineTo(x(-.02f), y(.66f)); lineTo(x(-.08f), y(.95f)); lineTo(x(-.27f), y(.95f)); close() }
    val rightLeg = Path().apply { moveTo(x(.02f), y(.66f)); lineTo(x(.19f), y(.66f)); lineTo(x(.27f), y(.95f)); lineTo(x(.08f), y(.95f)); close() }
    drawPath(leftLeg, base); drawPath(rightLeg, base)
    drawOval(base, Offset(x(-.32f), y(.93f)), Size(figureWidth * .24f, size.height * .045f))
    drawOval(base, Offset(x(.08f), y(.93f)), Size(figureWidth * .24f, size.height * .045f))
    drawPath(torso, contour, style = Stroke(width = 1.4.dp.toPx()))
    drawLine(contour, Offset(centerX, y(.20f)), Offset(centerX, y(.67f)), 1.dp.toPx())
    if (front) {
        drawLine(contour, Offset(x(-.27f), y(.35f)), Offset(x(.27f), y(.35f)), 1.dp.toPx())
        listOf(.43f, .50f, .57f).forEach { level -> drawLine(contour, Offset(x(-.13f), y(level)), Offset(x(.13f), y(level)), 1.dp.toPx()) }
    } else {
        drawLine(contour, Offset(x(-.30f), y(.31f)), Offset(centerX, y(.45f)), 1.dp.toPx())
        drawLine(contour, Offset(x(.30f), y(.31f)), Offset(centerX, y(.45f)), 1.dp.toPx())
        drawLine(contour, Offset(x(-.22f), y(.57f)), Offset(centerX, y(.66f)), 1.dp.toPx())
        drawLine(contour, Offset(x(.22f), y(.57f)), Offset(centerX, y(.66f)), 1.dp.toPx())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MuscleDetailSheet(load: MuscleLoad, rangeDays: Int, en: Boolean, onDismiss: () -> Unit) {
    val name = if (en) muscleNameEn(load.muscle) else muscleNameTr(load.muscle).replaceFirstChar(Char::uppercase)
    val status = loadStatus(load.level, en)
    val coach = when (load.level) {
        LoadLevel.NONE -> if (en) "$name has no recorded work in this period." else "$name için bu dönemde kayıtlı çalışma yok."
        LoadLevel.LOW -> if (en) "$name volume remained low. Add a few quality sets if recovery allows." else "$name hacmin bu dönem düşük kaldı. Toparlanman uygunsa birkaç kaliteli set ekleyebilirsin."
        LoadLevel.BALANCED -> if (en) "$name volume is balanced. Keep the current progression." else "$name hacmin dengeli. Mevcut ilerlemeyi koruyabilirsin."
        LoadLevel.HIGH -> if (en) "$name volume is high; prioritize recovery before adding more." else "$name hacmin yüksek; artırmadan önce toparlanmaya öncelik ver."
        LoadLevel.OVERLOAD -> if (en) "$name is overloaded. Consider reducing sets and watch pain or fatigue." else "$name aşırı yük altında. Set azaltmayı düşün; ağrı ve yorgunluğu izle."
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HedefitColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailBlock(if (en) "SETS" else "SET", "%.1f".format(load.setEquivalent))
                DetailBlock(if (en) "VOLUME" else "HACİM", load.totalVolumeKg.compactKg())
                DetailBlock(if (en) "STATUS" else "DURUM", status)
            }
            Text(if (en) "Last trained: ${load.lastTrainedAt?.take(10) ?: "—"}" else "Son çalıştırılma: ${load.lastTrainedAt?.take(10) ?: "—"}", color = HedefitColors.TextSecondary)
            Text(if (en) "30-day trend" else "Son 30 günlük trend", style = MaterialTheme.typography.titleMedium)
            Sparkline(load.trendVolumes.map(Double::toFloat).ifEmpty { listOf(0f, 0f) }, Modifier.fillMaxWidth().height(90.dp), showGrid = true)
            Text(if (en) "Volume sources" else "Hacmin geldiği hareketler", style = MaterialTheme.typography.titleMedium)
            if (load.exercises.isEmpty()) Text("—", color = HedefitColors.TextSecondary)
            load.exercises.take(6).forEach { contribution ->
                Row(Modifier.fillMaxWidth()) {
                    Text(contribution.exerciseName, Modifier.weight(1f))
                    Text("%.1f set • %s".format(contribution.setEquivalent, contribution.volumeKg.compactKg()), color = HedefitColors.TextSecondary)
                }
            }
            HedefitCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("AI COACH", color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge); Text(coach) } }
            Text(if (en) "Values reflect the selected ${rangeDays}-day period; secondary muscles count at 0.5×." else "Değerler seçilen $rangeDays günlük dönemi gösterir; yardımcı kaslar 0,5× ağırlıkla hesaplanır.", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WeeklyVolumeCard(analysis: TrainingAnalysis, en: Boolean) {
    HedefitCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(if (en) "Weekly training volume" else "Haftalık Antrenman Hacmi")
            val max = analysis.dailyVolume.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                DayOfWeek.entries.forEach { day ->
                    val value = analysis.dailyVolume[day] ?: 0.0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.fillMaxWidth(.62f).height((112 * (value / max)).coerceAtLeast(3.0).dp).background(if (value > 0) HedefitColors.Lime else HedefitColors.SurfaceSoft, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        Text(dayLabel(day, en), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text((if (en) "Total: " else "Toplam: ") + analysis.totalVolumeKg.compactKg(), color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun dayLabel(day: DayOfWeek, en: Boolean) = if (en) day.name.take(3).lowercase().replaceFirstChar(Char::uppercase) else mapOf(DayOfWeek.MONDAY to "Pzt", DayOfWeek.TUESDAY to "Sal", DayOfWeek.WEDNESDAY to "Çar", DayOfWeek.THURSDAY to "Per", DayOfWeek.FRIDAY to "Cum", DayOfWeek.SATURDAY to "Cmt", DayOfWeek.SUNDAY to "Paz").getValue(day)
private fun loadStatus(level: LoadLevel, en: Boolean) = if (en) when (level) { LoadLevel.NONE -> "No data"; LoadLevel.LOW -> "Low"; LoadLevel.BALANCED -> "Balanced"; LoadLevel.HIGH -> "High"; LoadLevel.OVERLOAD -> "Overload" } else when (level) { LoadLevel.NONE -> "Veri yok"; LoadLevel.LOW -> "Düşük"; LoadLevel.BALANCED -> "Dengeli"; LoadLevel.HIGH -> "Yüksek"; LoadLevel.OVERLOAD -> "Aşırı yük" }
private fun loadColor(level: LoadLevel) = when (level) {
    LoadLevel.NONE -> Color(0xFF303532)
    LoadLevel.LOW -> Color(0xFF9ACE83)
    LoadLevel.BALANCED -> HedefitColors.Lime
    LoadLevel.HIGH -> Color(0xFF36C95A)
    LoadLevel.OVERLOAD -> HedefitColors.Coral
}
