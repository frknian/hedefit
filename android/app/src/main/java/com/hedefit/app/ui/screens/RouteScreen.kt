package com.hedefit.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material3.*
import com.hedefit.app.ui.components.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hedefit.app.route.RoutePoint
import com.hedefit.app.route.RouteSnapshot
import com.hedefit.app.route.RouteTrackingService
import com.hedefit.app.route.RouteTrackingStore
import com.hedefit.app.route.PlannedRoute
import com.hedefit.app.route.RoutePlanRequest
import com.hedefit.app.route.RoutePlanner
import com.hedefit.app.route.ActivitySessionStatus
import com.hedefit.app.route.AndroidTextToSpeechNavigationVoice
import com.hedefit.app.route.canSaveRoute
import com.hedefit.app.route.formatDuration
import com.hedefit.app.route.formatPace
import com.hedefit.app.route.geoDistanceMeters
import com.hedefit.app.data.model.RouteActivityData
import com.hedefit.app.ui.components.HedefitCard
import com.hedefit.app.ui.components.OutlineAction
import com.hedefit.app.ui.components.PrimaryButton
import com.hedefit.app.ui.components.ScreenContainer
import com.hedefit.app.ui.theme.HedefitColors
import com.hedefit.app.ui.components.*
import androidx.compose.material.icons.filled.Route
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.hedefit.app.ui.settings.MeasurementUnits
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteScreen(
    onBack: () -> Unit,
    onCompleted: (RouteSnapshot, String, String) -> Unit,
    routes: List<RouteActivityData> = emptyList(),
    onDeleteRoute: (RouteActivityData) -> Unit = {},
    language: String = "tr",
    unitSystem: String = "metric",
) {
    val en = language == "en"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { RouteTrackingStore(context) }
    val navigationVoice = remember { AndroidTextToSpeechNavigationVoice(context) }
    DisposableEffect(navigationVoice) { onDispose { navigationVoice.close() } }
    val initialSnapshot = remember(store) { store.read() }
    val recoveredCompleted = remember(store) { store.readCompleted().takeIf(::canSaveRoute) }
    var snapshot by remember { mutableStateOf(initialSnapshot) }
    var activityType by rememberSaveable(snapshot.id) { mutableStateOf(snapshot.activityType.ifBlank { "Koşu" }) }
    var finished by remember { mutableStateOf(recoveredCompleted) }
    var pendingStart by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var routeMessage by remember { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showFinishConfirmation by remember { mutableStateOf(false) }
    var showPlanner by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf("new") }
    var plannedRoute by remember { mutableStateOf<PlannedRoute?>(null) }
    var planningRequest by remember { mutableStateOf<RoutePlanRequest?>(null) }
    var planBusy by remember { mutableStateOf(false) }
    var lastRerouteAt by remember { mutableLongStateOf(0L) }
    var spokenNavigationCues by remember { mutableStateOf<Set<String>>(emptySet()) }
    var plannerOrigin by remember { mutableStateOf<RoutePoint?>(null) }
    var plannerDestination by remember { mutableStateOf<RoutePoint?>(null) }
    var pickerTarget by remember { mutableStateOf<LocationPickerTarget?>(null) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var activityTitle by remember { mutableStateOf(recoveredCompleted?.let { defaultActivityTitle(it.activityType, en) }.orEmpty()) }
    var sessionStatus by remember { mutableStateOf(recoveredCompleted?.let { ActivitySessionStatus.COMPLETED } ?: initialSnapshot.status) }
    val generatePlan: (RoutePlanRequest) -> Unit = { request ->
        scope.launch {
            planBusy = true
            routeMessage = null
            runCatching {
                val origin = plannerOrigin ?: currentRouteLocation(context)
                RoutePlanner.plan(origin, request, plannerDestination)
            }.onSuccess { plan ->
                plannedRoute = plan
                spokenNavigationCues = emptySet()
                activityType = request.activityType
                routeMessage = if (en) {
                    "${MeasurementUnits.formatDistance(plan.distanceMeters, unitSystem)} ${if (plan.isLoop) "loop" else "route"} was planned. Check crossings and surface conditions before starting."
                } else {
                    "${MeasurementUnits.formatDistance(plan.distanceMeters, unitSystem)} ${if (plan.isLoop) "dönüşlü rota" else "rota"} hazır. Başlamadan önce geçişleri ve zemin koşullarını kontrol et."
                }
            }.onFailure { error ->
                routeMessage = error.message ?: if (en) "A route could not be planned for this location." else "Bu konum için rota planlanamadı."
            }
            planBusy = false
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionMessage = null
            if (pendingStart) {
                sessionStatus = ActivitySessionStatus.COUNTDOWN
                countdown = 3
            }
            planningRequest?.let(generatePlan)
        } else {
            val permanentlyDenied = (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false
            permissionMessage = if (permanentlyDenied) {
                if (en) "Location permission is permanently denied. Enable precise location for Hedefit in system settings." else "Konum izni kalıcı olarak reddedildi. Sistem ayarlarından Hedefit için hassas konumu aç."
            } else if (en) "Precise location permission was denied." else "Hassas konum izni reddedildi."
        }
        pendingStart = false
        planningRequest = null
    }
    LaunchedEffect(Unit) {
        while (true) {
            val latest = store.read()
            snapshot = latest
            if (!latest.tracking && finished == null) store.readCompleted().takeIf(::canSaveRoute)?.let {
                finished = it
                activityTitle = defaultActivityTitle(it.activityType, en)
            }
            delay(1_000)
        }
    }
    LaunchedEffect(snapshot.id) {
        if (snapshot.tracking && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(context, Intent(context, RouteTrackingService::class.java))
        }
    }
    LaunchedEffect(snapshot.distanceMeters) {
        if (canSaveRoute(snapshot)) routeMessage = null
    }
    LaunchedEffect(snapshot.points.lastOrNull()?.recordedAt, plannedRoute) {
        val position = snapshot.points.lastOrNull() ?: return@LaunchedEffect
        val route = plannedRoute ?: return@LaunchedEffect
        if (!snapshot.tracking || snapshot.paused) return@LaunchedEffect
        val progress = RoutePlanner.progress(route, position)
        RoutePlanner.announcementThreshold(progress.distanceToManeuverMeters)?.let { threshold ->
            progress.nextManeuver?.let { maneuver ->
                val cue = "${maneuver.pointIndex}:$threshold"
                if (cue !in spokenNavigationCues) {
                    spokenNavigationCues = spokenNavigationCues + cue
                    val street = maneuver.streetName.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
                    navigationVoice.speak(if (en) "In $threshold meters, ${RoutePlanner.instructionFor(maneuver.type, true)}$street" else "$threshold metre sonra ${RoutePlanner.instructionFor(maneuver.type)}$street")
                }
            }
        }
        if (progress.remainingMeters <= 25.0 && geoDistanceMeters(position, route.destination ?: route.points.last()) <= 30.0) {
            val final = store.stop(); stopRoute(context)
            snapshot = final; finished = final; sessionStatus = ActivitySessionStatus.COMPLETED
            activityTitle = defaultActivityTitle(final.activityType, en)
            routeMessage = if (en) "Route completed" else "Rotayı tamamladın"
        } else if (progress.offRoute && System.currentTimeMillis() - lastRerouteAt >= 30_000L) {
            lastRerouteAt = System.currentTimeMillis()
            routeMessage = if (en) "You are off route. Recalculating…" else "Rotadan çıktın. Yeni rota hesaplanıyor…"
            val destination = route.destination ?: route.points.last()
            runCatching {
                RoutePlanner.plan(position, RoutePlanRequest(activityType, "distance", progress.remainingMeters / 1_000.0, "point_to_point"), destination)
            }.onSuccess { rerouted -> plannedRoute = rerouted; spokenNavigationCues = emptySet(); routeMessage = null }
                .onFailure { routeMessage = if (en) "Rerouting failed. Continue toward the route when safe." else "Yeni rota hesaplanamadı. Güvenliyse rotaya doğru ilerle." }
        }
    }
    LaunchedEffect(countdown) {
        val value = countdown ?: return@LaunchedEffect
        delay(1_000)
        if (value > 1) countdown = value - 1 else {
            snapshot = startRoute(context, store, activityType)
            sessionStatus = ActivitySessionStatus.ACTIVE
            finished = null
            activityTitle = ""
            countdown = null
        }
    }
    BackHandler(enabled = snapshot.tracking) { showExitConfirmation = true }
    val visibleSnapshot = if (snapshot.tracking) snapshot else finished ?: snapshot
    val activityInProgress = snapshot.tracking
    if (!activityInProgress && finished == null && section == "history") {
        RouteHistoryPage(
            routes = routes,
            en = en,
            unitSystem = unitSystem,
            onBack = onBack,
            onNewActivity = { section = "new" },
            onDeleteRoute = onDeleteRoute,
            onReuseRoute = { saved ->
                val savedPoints = saved.routePoints.map { RoutePoint(it.latitude, it.longitude, it.altitudeMeters ?: 0.0, it.recordedAt, it.accuracyMeters) }
                if (savedPoints.size >= 2) {
                    plannedRoute = PlannedRoute(savedPoints, saved.distanceMeters, saved.movingDurationSeconds, saved.distanceMeters, isLoop = geoDistanceMeters(savedPoints.first(), savedPoints.last()) < 60.0, destination = savedPoints.last(), maneuvers = RoutePlanner.geometryManeuvers(savedPoints))
                    activityType = routeActivityLabel(saved.activityType, false)
                    section = "new"
                }
            },
        )
        return
    }
    Box(Modifier.fillMaxSize().background(HedefitColors.Background)) {
        when {
            activityInProgress && plannedRoute != null -> RouteMap(requireNotNull(plannedRoute).points, Modifier.fillMaxSize(), en, snapshot.points.lastOrNull(), followCurrent = true)
            activityInProgress -> RouteMap(snapshot.points, Modifier.fillMaxSize(), en, snapshot.points.lastOrNull(), followCurrent = true)
            finished != null -> RouteMap(visibleSnapshot.points, Modifier.fillMaxSize(), en)
            plannedRoute != null && section == "new" -> RouteMap(requireNotNull(plannedRoute).points, Modifier.fillMaxSize(), en, snapshot.points.lastOrNull())
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().background(HedefitColors.Background.copy(alpha = .88f), RoundedCornerShape(22.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HfCircleButton(Icons.AutoMirrored.Filled.ArrowBack, if (en) "Back" else "Geri", if (snapshot.tracking) ({ showExitConfirmation = true }) else onBack)
                Spacer(Modifier.width(12.dp)); Column { Text(com.hedefit.app.ui.i18n.tr("Hedefit Rota", "Hedefit Route"), color = HedefitColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(when { snapshot.tracking && snapshot.points.isEmpty() -> if (en) "Acquiring precise GPS signal…" else "Hassas GPS sinyali aranıyor…"; sessionStatus == ActivitySessionStatus.PREPARING_GPS -> if (en) "Searching for GPS…" else "GPS sinyali aranıyor…"; sessionStatus == ActivitySessionStatus.PAUSED -> if (en) "Paused" else "Duraklatıldı"; sessionStatus == ActivitySessionStatus.ACTIVE -> if (en) "Recording in background" else "Arka planda kaydediliyor"; sessionStatus == ActivitySessionStatus.COMPLETED -> if (en) "Ready to save" else "Kaydetmeye hazır"; else -> if (en) "GPS activity" else "GPS aktivitesi" }, color = HedefitColors.Lime, style = MaterialTheme.typography.bodySmall) }
            }
            if (!activityInProgress && finished == null) RouteSections(section, en) { section = it }
            if (section == "new" || activityInProgress || finished != null) {
                permissionMessage?.let { Text(it, color = HedefitColors.Warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.background(Color.Black.copy(alpha = .7f), RoundedCornerShape(10.dp)).padding(10.dp)) }
                routeMessage?.let { Text(it, color = HedefitColors.Warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.background(Color.Black.copy(alpha = .7f), RoundedCornerShape(10.dp)).padding(10.dp)) }
            }
        }
        if (!activityInProgress && finished == null && section == "new" && plannedRoute == null) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp).offset(y = (-24).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HfIconBadge(Icons.Default.Route, HedefitColors.Lime, 56.dp, 28.dp, 18.dp)
                Text(if (en) "Choose an activity" else "Aktiviteni seç", color = HedefitColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Koşu" to (if (en) "Run" else "Koşu"), "Yürüyüş" to (if (en) "Walk" else "Yürüyüş"), "Trail Koşusu" to (if (en) "Trail run" else "Trail koşusu"), "Doğa Yürüyüşü" to (if (en) "Hike" else "Doğa yürüyüşü"), "Bisiklet" to (if (en) "Ride" else "Bisiklet")).forEach { (key, label) ->
                        HfChip(label, activityType == key, { activityType = key })
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlineAction(if (en) "Plan a route" else "Rota planla", onClick = { showPlanner = true }, icon = Icons.Default.Navigation)
            }
        }
        if (planBusy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = HedefitColors.Lime)
                Text(if (en) "Finding a suitable loop…" else "Uygun parkur bulunuyor…", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (section == "new" || activityInProgress || finished != null) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(HedefitColors.Surface).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(4.dp).background(HedefitColors.Divider, RoundedCornerShape(4.dp)))
            if (activityInProgress) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteMetric(if (en) "DISTANCE" else com.hedefit.app.ui.i18n.tr("MESAFE", "DISTANCE"), MeasurementUnits.formatDistance(visibleSnapshot.distanceMeters, unitSystem), Modifier.weight(1f))
                    RouteMetric(if (en) "TIME" else com.hedefit.app.ui.i18n.tr("SÜRE", "TIME"), formatDuration(visibleSnapshot.durationSeconds), Modifier.weight(1f))
                    RouteMetric(if (activityType == "Bisiklet") (if (en) "SPEED" else "HIZ") else (if (en) "PACE" else com.hedefit.app.ui.i18n.tr("TEMPO", "PACE")), if (activityType == "Bisiklet") "%.1f km/sa".format(visibleSnapshot.currentSpeedKmh) else MeasurementUnits.formatPace(visibleSnapshot.displayPaceSecondsPerKm, unitSystem), Modifier.weight(1f))
                }
                plannedRoute?.let { RouteNavigationCard(it, snapshot.points.lastOrNull(), en, unitSystem) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val plan = plannedRoute.takeIf { finished == null }
                RouteMetric(if (en) "DISTANCE" else com.hedefit.app.ui.i18n.tr("MESAFE", "DISTANCE"), MeasurementUnits.formatDistance(plan?.distanceMeters ?: visibleSnapshot.distanceMeters, unitSystem), Modifier.weight(1f))
                RouteMetric(if (en) "TIME" else com.hedefit.app.ui.i18n.tr("SÜRE", "TIME"), formatDuration(plan?.estimatedDurationSeconds ?: visibleSnapshot.durationSeconds), Modifier.weight(1f))
                RouteMetric(
                    if (plan != null) (if (en) "TARGET" else "HEDEF") else if (activityType == "Bisiklet") (if (en) "SPEED" else "HIZ") else (if (en) "PACE" else com.hedefit.app.ui.i18n.tr("TEMPO", "PACE")),
                    if (plan != null) MeasurementUnits.formatDistance(plan.requestedDistanceMeters, unitSystem) else if (activityType == "Bisiklet") "%.1f km/sa".format(visibleSnapshot.averageSpeedKmh) else MeasurementUnits.formatPace(visibleSnapshot.displayPaceSecondsPerKm, unitSystem),
                    Modifier.weight(1f),
                )
            }
            if (snapshot.tracking && !snapshot.paused) PrimaryButton(if (en) "Pause" else "Duraklat", onClick = {
                context.startService(Intent(context, RouteTrackingService::class.java).setAction(RouteTrackingService.ACTION_PAUSE))
                snapshot = store.pause()
                sessionStatus = ActivitySessionStatus.PAUSED
            }, icon = Icons.Default.Stop)
            else if (snapshot.tracking && snapshot.paused) {
                PrimaryButton(if (en) "Continue" else "Devam Et", onClick = {
                    context.startService(Intent(context, RouteTrackingService::class.java).setAction(RouteTrackingService.ACTION_RESUME))
                    snapshot = store.resume()
                    sessionStatus = ActivitySessionStatus.ACTIVE
                }, icon = Icons.Default.DirectionsRun)
                OutlineAction(if (en) "Finish activity" else "Aktiviteyi Bitir", onClick = { showFinishConfirmation = true })
            }
            else if (finished == null) PrimaryButton(if (plannedRoute != null) (if (en) "Start planned route" else "Planlı Rotayı Başlat") else (if (en) "Start GPS Recording" else "GPS Kaydını Başlat"), onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    sessionStatus = ActivitySessionStatus.COUNTDOWN
                    countdown = 3
                    routeMessage = null
                }
                else {
                    pendingStart = true
                    sessionStatus = ActivitySessionStatus.PREPARING_GPS
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }, icon = Icons.Default.DirectionsRun)
            if (!activityInProgress && finished == null && plannedRoute != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { plannedRoute = null; routeMessage = null }, modifier = Modifier.weight(1f)) { Text(if (en) "Clear plan" else "Planı temizle", color = HedefitColors.Coral) }
                TextButton(onClick = { showPlanner = true }, modifier = Modifier.weight(1f)) { Text(if (en) "Change target" else "Hedefi değiştir", color = HedefitColors.Lime) }
            }
            finished?.let { completed ->
                Text(if (en) "Route completed" else "Rotayı tamamladın", color = HedefitColors.Lime, style = MaterialTheme.typography.titleLarge)
                Text("${MeasurementUnits.formatDistance(completed.distanceMeters, unitSystem)} • ${formatDuration(completed.durationSeconds)} • ${MeasurementUnits.formatPace(completed.paceSecondsPerKm, unitSystem)} • ${estimatedRouteCalories(completed)} kcal", color = HedefitColors.TextSecondary)
                OutlinedTextField(activityTitle, { activityTitle = it.take(80) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (en) "Activity name" else "Aktivite adı") })
                PrimaryButton(if (en) "Save activity" else "Aktiviteyi Kaydet", onClick = {
                    onCompleted(completed, completed.activityType, activityTitle.ifBlank { defaultActivityTitle(completed.activityType, en) })
                    snapshot = store.reset()
                    sessionStatus = ActivitySessionStatus.IDLE
                    finished = null
                    routeMessage = null
                    plannedRoute = null
                    section = "history"
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { scope.launch { shareRoute(context, completed, activityTitle, story = false) } }, modifier = Modifier.weight(1f)) { Text("1:1 ${if (en) "Share" else "Paylaş"}") }
                    TextButton(onClick = { scope.launch { shareRoute(context, completed, activityTitle, story = true) } }, modifier = Modifier.weight(1f)) { Text("9:16 Story") }
                }
            }
        }
    }
    if (showExitConfirmation) AlertDialog(
        onDismissRequest = { showExitConfirmation = false },
        title = { Text(if (en) "Leave route recording?" else "Rota kaydından çıkılsın mı?") },
        text = { Text(if (canSaveRoute(snapshot)) (if (en) "Save this route, discard it, or keep recording." else "Bu rotayı kaydet, sil veya kayda devam et.") else (if (en) "This route is too short to save and will be discarded." else "Bu rota kaydetmek için çok kısa ve silinecek.")) },
        dismissButton = { Row {
            TextButton(onClick = { showExitConfirmation = false }) { Text(if (en) "Keep recording" else "Kayda devam et") }
            if (canSaveRoute(snapshot)) TextButton(onClick = {
                store.discard()
                stopRoute(context, discard = true)
                showExitConfirmation = false
                onBack()
            }) { Text(if (en) "Discard" else "Sil", color = HedefitColors.Coral) }
        } },
        confirmButton = { TextButton(onClick = {
            val final = if (canSaveRoute(snapshot)) store.stop() else store.discard()
            stopRoute(context, discard = !canSaveRoute(final))
            if (canSaveRoute(final)) {
                snapshot = final
                finished = final
                sessionStatus = ActivitySessionStatus.COMPLETED
                activityTitle = defaultActivityTitle(final.activityType, en)
            } else onBack()
            showExitConfirmation = false
        }) { Text(if (canSaveRoute(snapshot)) (if (en) "Finish and save" else "Bitir ve kaydet") else (if (en) "Discard route" else "Rotayı sil"), color = HedefitColors.Coral) } },
    )
    if (showFinishConfirmation) AlertDialog(
        onDismissRequest = { showFinishConfirmation = false },
        title = { Text(if (en) "Finish activity?" else "Aktiviteyi bitirmek istiyor musun?") },
        text = { Text(if (en) "The recorded route will be ready to name, save and share." else "Kaydedilen rota adlandırmaya, kaydetmeye ve paylaşmaya hazır olacak.") },
        dismissButton = { TextButton(onClick = { showFinishConfirmation = false }) { Text(if (en) "Continue" else "Devam Et") } },
        confirmButton = { TextButton(onClick = {
            sessionStatus = ActivitySessionStatus.FINISHING
            val final = store.stop()
            stopRoute(context)
            snapshot = final
            finished = final
            sessionStatus = ActivitySessionStatus.COMPLETED
            activityTitle = defaultActivityTitle(final.activityType, en)
            showFinishConfirmation = false
        }) { Text(if (en) "Finish activity" else "Aktiviteyi Bitir", color = HedefitColors.Coral) } },
    )
    if (showPlanner) RoutePlannerDialog(
        activityType = activityType,
        en = en,
        busy = planBusy,
        start = plannerOrigin,
        destination = plannerDestination,
        onDismiss = { if (!planBusy) showPlanner = false },
        onUseCurrentStart = { plannerOrigin = null },
        onPickStart = { pickerTarget = LocationPickerTarget.START; showPlanner = false },
        onPickDestination = { pickerTarget = LocationPickerTarget.DESTINATION; showPlanner = false },
        onPlan = { request ->
            showPlanner = false
            if (plannerOrigin != null || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) generatePlan(request)
            else {
                planningRequest = request
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        },
    )
    pickerTarget?.let { target ->
        LocationPickerDialog(
            initialLocation = when (target) {
                LocationPickerTarget.START -> plannerOrigin ?: plannerDestination ?: snapshot.points.lastOrNull()
                LocationPickerTarget.DESTINATION -> plannerDestination ?: plannerOrigin ?: snapshot.points.lastOrNull()
            },
            title = if (target == LocationPickerTarget.START) (if (en) "Choose starting point" else "Başlangıç noktasını seç") else (if (en) "Choose destination" else "Varış noktasını seç"),
            en = en,
            onDismiss = { pickerTarget = null; showPlanner = true },
            onConfirm = { point ->
                if (target == LocationPickerTarget.START) plannerOrigin = point else plannerDestination = point
                pickerTarget = null
                showPlanner = true
            },
        )
    }
    countdown?.let { value -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
        Text(if (value > 0) value.toString() else if (en) "GO" else "BAŞLA", color = HedefitColors.Lime, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displayLarge)
    } }
}

@Composable
private fun RoutePlannerDialog(
    activityType: String,
    en: Boolean,
    busy: Boolean,
    start: RoutePoint?,
    destination: RoutePoint?,
    onDismiss: () -> Unit,
    onUseCurrentStart: () -> Unit,
    onPickStart: () -> Unit,
    onPickDestination: () -> Unit,
    onPlan: (RoutePlanRequest) -> Unit,
) {
    var planType by remember { mutableStateOf("loop") }
    var goalType by remember { mutableStateOf("time") }
    var value by remember(goalType) { mutableStateOf(if (goalType == "time") "30" else "5") }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    val validGoal = parsed != null && if (goalType == "time") parsed in 10.0..240.0 else parsed in .5..50.0
    val valid = if (planType == "point_to_point") destination != null else validGoal
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (en) "Plan your route" else "Rotanı planla") },
        text = { Column(
            // Küçük ekranlarda taşmaya karşı kaydırılabilir; yüksekliği diyalogun
            // kendi sınırı belirler (sabit bir tavan, içeriği gereksiz yere kesiyordu).
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(routeActivityLabel(activityType, en), color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutePlanModeTile(
                    icon = Icons.Default.Autorenew,
                    title = if (en) "Loop" else "Dönüşlü",
                    subtitle = if (en) "Ends where you start" else "Başladığın yerde biter",
                    selected = planType == "loop",
                    modifier = Modifier.weight(1f),
                ) { planType = "loop" }
                RoutePlanModeTile(
                    icon = Icons.AutoMirrored.Filled.TrendingFlat,
                    title = "A → B",
                    subtitle = if (en) "Ends elsewhere" else "Başka noktada biter",
                    selected = planType == "point_to_point",
                    modifier = Modifier.weight(1f),
                ) { planType = "point_to_point" }
            }
            RoutePointRow(
                icon = Icons.Default.MyLocation,
                label = if (en) "Start" else "Başlangıç",
                value = start?.let { coordinateLabel(it) } ?: if (en) "My current location" else "Mevcut konumum",
                isPlaceholder = start == null,
            ) {
                TextButton(onClick = onUseCurrentStart, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(if (en) "Use current" else "Konumum") }
                TextButton(onClick = onPickStart, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(if (en) "On map" else "Haritadan") }
            }
            if (planType == "point_to_point") {
                RoutePointRow(
                    icon = Icons.Default.Flag,
                    label = if (en) "Destination" else "Varış",
                    value = destination?.let { coordinateLabel(it) } ?: if (en) "Not selected yet" else "Henüz seçilmedi",
                    isPlaceholder = destination == null,
                ) {
                    TextButton(onClick = onPickDestination, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(if (en) "Choose on map" else "Haritadan seç") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutePlanModeTile(
                        icon = Icons.Default.Schedule,
                        title = if (en) "Duration" else "Süre",
                        subtitle = if (en) "10-240 min" else "10-240 dk",
                        selected = goalType == "time",
                        modifier = Modifier.weight(1f),
                    ) { goalType = "time" }
                    RoutePlanModeTile(
                        icon = Icons.Default.Straighten,
                        title = if (en) "Distance" else "Mesafe",
                        subtitle = if (en) "0.5-50 km" else "0,5-50 km",
                        selected = goalType == "distance",
                        modifier = Modifier.weight(1f),
                    ) { goalType = "distance" }
                }
                // Hızlı seçim: en sık kullanılan hedefler, yazmadan tek dokunuşla.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presets = if (goalType == "time") listOf("20", "30", "45", "60") else listOf("3", "5", "10", "21")
                    val unit = if (goalType == "time") (if (en) "dk" else "dk") else "km"
                    presets.forEach { preset ->
                        val selected = value == preset
                        Box(
                            Modifier.weight(1f)
                                .background(if (selected) HedefitColors.Lime.copy(alpha = .18f) else HedefitColors.SurfaceHigh, RoundedCornerShape(10.dp))
                                .clickable { value = preset }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$preset $unit",
                                color = if (selected) HedefitColors.Lime else HedefitColors.TextSecondary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (goalType == "time") (if (en) "Minutes" else "Dakika") else (if (en) "Kilometers" else "Kilometre")) },
                    suffix = { Text(if (goalType == "time") (if (en) "min" else "dk") else "km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = parsed != null && !valid,
                    supportingText = if (parsed != null && !valid) ({
                        Text(
                            if (goalType == "time") (if (en) "Enter a value between 10 and 240 minutes." else "10 ile 240 dakika arasında bir değer gir.")
                            else (if (en) "Enter a value between 0.5 and 50 km." else "0,5 ile 50 km arasında bir değer gir."),
                            color = HedefitColors.Coral,
                        )
                    }) else null,
                )
            }
            RoutePlannerHint(
                if (planType == "loop") {
                    if (en) "Loops back to your start; distance may vary." else "Başlangıcına dönen parkur; mesafe biraz değişebilir."
                } else if (en) "A route between your two points." else "İki nokta arasında rota oluşturulur.",
                if (en) "Points go to the open BRouter service." else "Noktalar açık BRouter servisine gönderilir.",
            )
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = { Button(
            onClick = { onPlan(RoutePlanRequest(activityType, goalType, if (planType == "loop") requireNotNull(parsed) else 0.0, planType)) },
            enabled = valid && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime),
        ) { Text(if (busy) (if (en) "Planning…" else "Planlanıyor…") else (if (en) "Create route" else "Rota oluştur")) } },
    )
}

/** İkon + başlık + tek satır açıklamadan oluşan seçilebilir kutu; kullanıcının
 *  seçeneğin ne yaptığını denemeden anlamasını sağlar. */
@Composable
private fun RoutePlanModeTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(if (selected) HedefitColors.Lime else HedefitColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (selected) HedefitColors.OnLime else HedefitColors.Lime)
        Text(title, color = if (selected) HedefitColors.OnLime else HedefitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            color = if (selected) HedefitColors.OnLime.copy(alpha = .75f) else HedefitColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoutePointRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isPlaceholder: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    HedefitCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).background(HedefitColors.Lime.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(17.dp), tint = HedefitColors.Lime)
                }
                Column(Modifier.weight(1f)) {
                    Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(
                        value,
                        color = if (isPlaceholder) HedefitColors.TextSecondary else HedefitColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Butonlar ayrı satırda: aynı satırda olduklarında etiket/konum metnini
            // harf harf kırılacak kadar daraltıyorlardı.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
        }
    }
}

@Composable
private fun RoutePlannerHint(text: String, privacyNote: String) {
    Row(
        Modifier.fillMaxWidth().background(HedefitColors.SurfaceHigh, RoundedCornerShape(10.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Info, null, Modifier.size(15.dp), tint = HedefitColors.TextSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(privacyNote, color = HedefitColors.TextSecondary.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private suspend fun currentRouteLocation(context: Context): RoutePoint {
    val locationManager = context.getSystemService(LocationManager::class.java)
    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        error("GPS kapalı. Konum servislerini açıp tekrar dene.")
    }
    return suspendCancellableCoroutine { continuation ->
    val cancellation = CancellationTokenSource()
    continuation.invokeOnCancellation { cancellation.cancel() }
    @Suppress("MissingPermission")
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
        .addOnSuccessListener { location ->
            if (!continuation.isActive) return@addOnSuccessListener
            if (location == null) continuation.resumeWithException(IllegalStateException("Konum alınamadı; GPS'i açıp tekrar dene."))
            else if (System.currentTimeMillis() - location.time > 15_000L) continuation.resumeWithException(IllegalStateException("Konum güncel değil; açık alanda tekrar dene."))
            else if (!location.hasAccuracy() || location.accuracy > 25f) continuation.resumeWithException(IllegalStateException(com.hedefit.app.ui.i18n.tr("GPS doğruluğu düşük (${location.accuracy.roundToInt()} m). Açık alanda tekrar dene.", "GPS accuracy is low (${location.accuracy.roundToInt()} m). Try again in an open area.")))
            else continuation.resume(RoutePoint(location.latitude, location.longitude, location.altitude, location.time, location.accuracy.toDouble(), location.speed.takeIf { location.hasSpeed() }?.toDouble(), location.bearing.takeIf { location.hasBearing() }?.toDouble()))
        }
        .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }
}

private enum class LocationPickerTarget { START, DESTINATION }

private fun coordinateLabel(point: RoutePoint): String = point.displayName.ifBlank { "%.5f, %.5f".format(Locale.US, point.latitude, point.longitude) }

@Composable
private fun LocationPickerDialog(
    initialLocation: RoutePoint?,
    title: String,
    en: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RoutePoint) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(initialLocation) }
    var selected by remember { mutableStateOf(initialLocation) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialLocation) {
        if (initialLocation == null && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runCatching { currentRouteLocation(context) }.onSuccess { center = it; selected = it }.onFailure { error = it.message }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().heightIn(max = 690.dp), shape = RoundedCornerShape(24.dp), color = HedefitColors.Surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text(if (en) "Place or address" else "Yer veya adres") })
                    Button(onClick = {
                        if (query.isBlank() || searching) return@Button
                        scope.launch {
                            searching = true; error = null
                            runCatching { geocodeRouteLocation(context, query) }.onSuccess { center = it; selected = it }.onFailure { error = it.message }
                            searching = false
                        }
                    }, enabled = query.isNotBlank() && !searching) { Text(if (searching) "…" else if (en) "Search" else "Ara") }
                }
                if (center != null && selected != null) LocationPickerMap(requireNotNull(center), requireNotNull(selected), Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(16.dp))) { selected = it }
                else Box(Modifier.fillMaxWidth().height(180.dp).background(HedefitColors.SurfaceHigh, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text(if (en) "Search for an address to place the map." else "Haritayı açmak için bir adres ara.", color = HedefitColors.TextSecondary) }
                selected?.let { Text(if (en) "Selected: ${coordinateLabel(it)}" else "Seçilen konum: ${coordinateLabel(it)}", color = HedefitColors.Lime, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = HedefitColors.Warning, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (en) "Cancel" else "Vazgeç") }
                    Button(onClick = { selected?.let(onConfirm) }, enabled = selected != null, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Lime, contentColor = HedefitColors.OnLime)) { Text(if (en) "Use this point" else "Bu noktayı kullan") }
                }
            }
        }
    }
}

private data class MapCoordinate(val x: Double, val y: Double)

@Composable
private fun LocationPickerMap(center: RoutePoint, selected: RoutePoint, modifier: Modifier, onPick: (RoutePoint) -> Unit) {
    BoxWithConstraints(modifier.background(Color(0xFF172018))) {
        val density = LocalDensity.current
        val tileSize = with(density) { 256.dp.toPx() }
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val zoom = 14
        val tileCount = 1 shl zoom
        val centerPosition = mapCoordinate(center, zoom)
        val centerTileX = floor(centerPosition.x).toInt()
        val centerTileY = floor(centerPosition.y).toInt()
        val horizontalTiles = (width / tileSize).toInt() / 2 + 2
        val verticalTiles = (height / tileSize).toInt() / 2 + 2
        for (tileY in (centerTileY - verticalTiles)..(centerTileY + verticalTiles)) {
            if (tileY !in 0 until tileCount) continue
            for (rawTileX in (centerTileX - horizontalTiles)..(centerTileX + horizontalTiles)) {
                val left = width / 2f + (rawTileX - centerPosition.x).toFloat() * tileSize
                val top = height / 2f + (tileY - centerPosition.y).toFloat() * tileSize
                OpenStreetMapTile(zoom, rawTileX.mod(tileCount), tileY, Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(256.dp))
            }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(center) {
            detectTapGestures { tap ->
                val x = centerPosition.x + (tap.x - width / 2f) / tileSize
                val y = centerPosition.y + (tap.y - height / 2f) / tileSize
                val longitude = x / tileCount * 360.0 - 180.0
                val latitude = Math.toDegrees(Math.atan(Math.sinh(PI * (1.0 - 2.0 * y / tileCount))))
                onPick(RoutePoint(latitude, longitude, 0.0, System.currentTimeMillis()))
            }
        }) {
            val coordinate = mapCoordinate(selected, zoom)
            val marker = Offset(size.width / 2f + (coordinate.x - centerPosition.x).toFloat() * tileSize, size.height / 2f + (coordinate.y - centerPosition.y).toFloat() * tileSize)
            drawCircle(Color.White, 15f, marker)
            drawCircle(HedefitColors.Coral, 10f, marker)
        }
    }
}

private suspend fun geocodeRouteLocation(context: Context, query: String): RoutePoint = withContext(Dispatchers.IO) {
    val result = Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1)?.firstOrNull()
        ?: error("Konum bulunamadı. Adresi veya yer adını kontrol et.")
    RoutePoint(result.latitude, result.longitude, 0.0, System.currentTimeMillis(), displayName = result.getAddressLine(0) ?: result.featureName.orEmpty())
}

@Composable
private fun RouteSections(selected: String, en: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .68f), RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("new" to (if (en) "New activity" else "Yeni aktivite"), "history" to (if (en) "Completed" else "Yapılanlar")).forEach { (key, label) ->
            val active = selected == key
            Box(
                Modifier.weight(1f).background(if (active) HedefitColors.Lime else Color.Transparent, RoundedCornerShape(11.dp))
                    .clickable { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) HedefitColors.OnLime else Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RouteHistoryPage(
    routes: List<RouteActivityData>,
    en: Boolean,
    unitSystem: String,
    onBack: () -> Unit,
    onNewActivity: () -> Unit,
    onDeleteRoute: (RouteActivityData) -> Unit,
    onReuseRoute: (RouteActivityData) -> Unit,
) {
    ScreenContainer {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.background(HedefitColors.SurfaceHigh, CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (en) "Back" else "Geri") }
                Spacer(Modifier.width(10.dp))
                Column { Text(if (en) "Completed activities" else "Yapılanlar", style = MaterialTheme.typography.headlineSmall) }
            }
            RouteSections("history", en) { if (it == "new") onNewActivity() }
            RouteHistory(routes, en, unitSystem, onDeleteRoute, onReuseRoute, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RouteHistory(
    routes: List<RouteActivityData>,
    en: Boolean,
    unitSystem: String,
    onDeleteRoute: (RouteActivityData) -> Unit,
    onReuseRoute: (RouteActivityData) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRoute by remember { mutableStateOf<RouteActivityData?>(null) }
    var deleteCandidate by remember { mutableStateOf<RouteActivityData?>(null) }
    val completedRoutes = remember(routes) { routes.sortedByDescending(RouteActivityData::startedAt) }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (completedRoutes.isEmpty()) item {
            HedefitCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DirectionsRun, null, tint = HedefitColors.Lime, modifier = Modifier.size(36.dp))
                    Text(if (en) "No completed routes yet" else "Henüz tamamlanan rota yok", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(completedRoutes, key = RouteActivityData::id) { route ->
            HedefitCard(Modifier.fillMaxWidth(), onClick = { selectedRoute = route }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).background(HedefitColors.Lime.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(routeActivityEmoji(route.activityType), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(route.title.ifBlank { routeActivityLabel(route.activityType, en) }, style = MaterialTheme.typography.titleMedium)
                        Text("${routeActivityLabel(route.activityType, en)} • ${routeHistoryDate(route.startedAt, en)}", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(MeasurementUnits.formatDistance(route.distanceMeters, unitSystem), color = HedefitColors.Lime, style = MaterialTheme.typography.labelLarge)
                        Text(formatDuration(route.movingDurationSeconds), color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    selectedRoute?.let { route -> AlertDialog(
        onDismissRequest = { selectedRoute = null },
        title = { Text(route.title.ifBlank { routeActivityLabel(route.activityType, en) }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RouteHistoryPreview(route, Modifier.fillMaxWidth().height(210.dp), en)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RouteHistoryMetric(if (en) "Distance" else "Mesafe", MeasurementUnits.formatDistance(route.distanceMeters, unitSystem))
                RouteHistoryMetric(if (en) "Time" else "Süre", formatDuration(route.movingDurationSeconds))
                RouteHistoryMetric(if (en) "Pace" else "Tempo", MeasurementUnits.formatPace(route.averagePaceSecondsPerKm, unitSystem))
            }
            Text("${routeHistoryDate(route.startedAt, en)} • ${route.calories} kcal", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            OutlineAction(if (en) "Use this saved route" else "Bu kayıtlı rotayı kullan", { onReuseRoute(route); selectedRoute = null })
        } },
        dismissButton = { TextButton(onClick = { selectedRoute = null; deleteCandidate = route }) { Text(if (en) "Delete" else "Sil", color = HedefitColors.Coral) } },
        confirmButton = { TextButton(onClick = { selectedRoute = null }) { Text(if (en) "Done" else "Tamam") } },
    ) }
    deleteCandidate?.let { route -> AlertDialog(
        onDismissRequest = { deleteCandidate = null },
        title = { Text(if (en) "Delete this route?" else "Bu rota silinsin mi?") },
        text = { Text(if (en) "The route and its activity details will be permanently deleted." else "Rota ve aktivite ayrıntıları kalıcı olarak silinecek.") },
        dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(if (en) "Cancel" else "Vazgeç") } },
        confirmButton = { Button(onClick = { onDeleteRoute(route); deleteCandidate = null }, colors = ButtonDefaults.buttonColors(containerColor = HedefitColors.Coral, contentColor = Color.White)) { Text(if (en) "Delete" else "Sil") } },
    ) }
}

@Composable
private fun RouteHistoryPreview(route: RouteActivityData, modifier: Modifier, en: Boolean) {
    val points = route.routePoints.map { RoutePoint(it.latitude, it.longitude, it.altitudeMeters ?: 0.0, it.recordedAt, it.accuracyMeters) }
    RouteMap(points, modifier.clip(RoundedCornerShape(18.dp)), en)
}

@Composable
private fun RouteHistoryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium)
        Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

private fun routeActivityLabel(type: String, en: Boolean): String = when (type.lowercase(Locale.ROOT)) {
    "run", "running", "koşu" -> if (en) "Run" else "Koşu"
    "trail run", "trail_running", "trail koşusu" -> if (en) "Trail run" else "Trail Koşusu"
    "hike", "hiking", "doğa yürüyüşü" -> if (en) "Hike" else "Doğa Yürüyüşü"
    "ride", "cycling", "bisiklet" -> if (en) "Ride" else "Bisiklet"
    else -> if (en) "Walk" else "Yürüyüş"
}

private fun routeActivityEmoji(type: String): String = when (type.lowercase(Locale.ROOT)) {
    "run", "running", "koşu" -> "🏃"
    "trail run", "trail_running", "trail koşusu" -> "⛰️"
    "hike", "hiking", "doğa yürüyüşü" -> "🥾"
    "ride", "cycling", "bisiklet" -> "🚴"
    else -> "🚶"
}

private fun routeHistoryDate(value: String, en: Boolean): String = runCatching {
    val date = Instant.parse(value).atZone(ZoneId.systemDefault())
    date.format(DateTimeFormatter.ofPattern(if (en) "MMM d, yyyy • HH:mm" else "d MMM yyyy • HH:mm", if (en) Locale.ENGLISH else Locale("tr")))
}.getOrElse { value.take(16).replace('T', ' ') }

@Composable
private fun RouteMap(points: List<RoutePoint>, modifier: Modifier, en: Boolean = false, currentPosition: RoutePoint? = null, followCurrent: Boolean = false) {
    var following by remember(followCurrent) { mutableStateOf(followCurrent) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val center = if (following && currentPosition != null) currentPosition else points.takeIf { it.isNotEmpty() }?.let { route ->
        RoutePoint((route.minOf { it.latitude } + route.maxOf { it.latitude }) / 2.0, (route.minOf { it.longitude } + route.maxOf { it.longitude }) / 2.0, 0.0, 0L)
    } ?: currentPosition
    BoxWithConstraints(modifier.background(Color(0xFF0B0D0C)).pointerInput(followCurrent) {
        if (followCurrent) detectDragGestures { change, amount -> change.consume(); following = false; panOffset += amount }
    }, contentAlignment = Alignment.TopStart) {
        if (center == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).background(HedefitColors.Lime.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsRun, null, tint = HedefitColors.Lime, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(10.dp)); Text(if (en) "GPS ready • Your route will appear here after you start" else "GPS hazır • Başladığında rotan burada çizilecek", color = HedefitColors.TextSecondary)
            }
        } else {
            val density = LocalDensity.current
            val tileSize = with(density) { 256.dp.toPx() }
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val zoom = if (following && currentPosition != null) 17 else fittedMapZoom(points, width, height, tileSize)
            val tileCount = 1 shl zoom
            val centerPosition = mapCoordinate(center, zoom)
            val centerTileX = floor(centerPosition.x).toInt()
            val centerTileY = floor(centerPosition.y).toInt()
            val horizontalTiles = (width / tileSize).toInt() / 2 + 2
            val verticalTiles = (height / tileSize).toInt() / 2 + 2
            for (tileY in (centerTileY - verticalTiles)..(centerTileY + verticalTiles)) {
                if (tileY !in 0 until tileCount) continue
                for (rawTileX in (centerTileX - horizontalTiles)..(centerTileX + horizontalTiles)) {
                    val left = width / 2f + (rawTileX - centerPosition.x).toFloat() * tileSize + panOffset.x
                    val top = height / 2f + (tileY - centerPosition.y).toFloat() * tileSize + panOffset.y
                    OpenStreetMapTile(zoom, rawTileX.mod(tileCount), tileY, Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(256.dp))
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                fixedScaleRoutePath(points, centerPosition, size.width, size.height, tileSize, zoom, panOffset)?.let {
                    drawPath(it, Color(0xFF0A2711), style = Stroke(14f, cap = StrokeCap.Round))
                    drawPath(it, HedefitColors.Lime, style = Stroke(8f, cap = StrokeCap.Round))
                }
                fun pointOffset(point: RoutePoint): Offset { val coordinate = mapCoordinate(point, zoom); return Offset(size.width / 2f + (coordinate.x - centerPosition.x).toFloat() * tileSize + panOffset.x, size.height / 2f + (coordinate.y - centerPosition.y).toFloat() * tileSize + panOffset.y) }
                drawCircle(Color.White, radius = 10f, center = pointOffset(points.first()))
                drawCircle(HedefitColors.Lime, radius = 12f, center = pointOffset(points.last()))
                currentPosition?.let {
                    val position = pointOffset(it)
                    drawCircle(Color.White, radius = 13f, center = position)
                    drawCircle(Color(0xFF4C9AFF), radius = 9f, center = position)
                    it.bearingDegrees?.let { bearing ->
                        val radians = Math.toRadians(bearing)
                        drawLine(Color.White, position, Offset(position.x + kotlin.math.sin(radians).toFloat() * 23f, position.y - kotlin.math.cos(radians).toFloat() * 23f), 5f, StrokeCap.Round)
                    }
                }
            }
            if (followCurrent && !following) TextButton(
                onClick = { following = true; panOffset = Offset.Zero },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).background(Color(0xEE111411), RoundedCornerShape(22.dp)),
            ) { Text(if (en) "Recenter" else "Konuma dön", color = HedefitColors.Lime) }
        }
    }
}

@Composable
private fun RouteNavigationCard(route: PlannedRoute, position: RoutePoint?, en: Boolean, unitSystem: String) {
    val startDistance = position?.let { geoDistanceMeters(it, route.points.first()) }
    val joiningRoute = startDistance != null && startDistance > 80.0
    val progress = RoutePlanner.progress(route, position)
    val remaining = when {
        position == null -> route.distanceMeters
        joiningRoute -> startDistance
        else -> progress.remainingMeters
    }
    val instruction = when {
        position == null -> if (en) "Getting your precise GPS location…" else "Hassas GPS konumu alınıyor…"
        joiningRoute -> if (en) "Head to the planned route start" else "Planlanan rota başlangıcına ilerle"
        progress.offRoute -> if (en) "Off route • recalculating" else "Rotadan çıktın • yeniden hesaplanıyor"
        else -> progress.nextManeuver?.let { RoutePlanner.instructionFor(it.type, en) } ?: if (en) "Continue on the route" else "Rotada devam et"
    }
    HedefitCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).background(Color(0xFF4C9AFF).copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsRun, null, tint = Color(0xFF75B3FF)) }
            Column(Modifier.weight(1f)) {
                Text(instruction, style = MaterialTheme.typography.titleSmall)
                Text(progress.nextManeuver?.streetName?.takeIf(String::isNotBlank) ?: if (en) "Planned route" else "Planlanan rota", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(MeasurementUnits.formatDistance(if (joiningRoute) remaining else progress.distanceToManeuverMeters, unitSystem), color = HedefitColors.Lime, style = MaterialTheme.typography.titleMedium)
        }
        if (!joiningRoute) {
            Spacer(Modifier.height(8.dp))
            Text(if (en) "${MeasurementUnits.formatDistance(progress.traveledMeters, unitSystem)} completed • ${MeasurementUnits.formatDistance(remaining, unitSystem)} remaining • ${progress.completionPercent}%" else "${MeasurementUnits.formatDistance(progress.traveledMeters, unitSystem)} tamamlandı • ${MeasurementUnits.formatDistance(remaining, unitSystem)} kaldı • %${progress.completionPercent}", color = HedefitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { progress.completionPercent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = HedefitColors.Lime, trackColor = HedefitColors.SurfaceSoft)
        }
    }
}

@Composable private fun RouteMetric(label: String, value: String, modifier: Modifier) = HedefitCard(modifier) { Column { Text(label, color = HedefitColors.TextSecondary, style = MaterialTheme.typography.labelSmall); Text(value, color = HedefitColors.Lime, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) } }

private fun mapCoordinate(point: RoutePoint, zoom: Int = 16): MapCoordinate {
    val tileCount = 1 shl zoom
    val latitude = point.latitude.coerceIn(-85.05112878, 85.05112878)
    val x = (point.longitude + 180.0) / 360.0 * tileCount
    val y = (1.0 - ln(tan(Math.toRadians(latitude)) + 1.0 / cos(Math.toRadians(latitude))) / PI) / 2.0 * tileCount
    return MapCoordinate(x, y)
}

private fun fittedMapZoom(points: List<RoutePoint>, width: Float, height: Float, tileSize: Float): Int {
    if (points.size < 2) return 16
    for (zoom in 18 downTo 3) {
        val coordinates = points.map { mapCoordinate(it, zoom) }
        val spanX = (coordinates.maxOf { it.x } - coordinates.minOf { it.x }) * tileSize
        val spanY = (coordinates.maxOf { it.y } - coordinates.minOf { it.y }) * tileSize
        if (spanX <= width * .76f && spanY <= height * .58f) return zoom
    }
    return 3
}

@Composable
private fun OpenStreetMapTile(zoom: Int, x: Int, y: Int, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, zoom, x, y) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                URL("https://tile.openstreetmap.org/$zoom/$x/$y.png").openConnection().apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("User-Agent", "Hedefit/0.2 Android route map")
                }.getInputStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.FillBounds) }
        ?: Box(modifier.background(Color(0xFF172018)))
}

private fun fixedScaleRoutePath(points: List<RoutePoint>, center: MapCoordinate, width: Float, height: Float, tileSize: Float, zoom: Int, panOffset: Offset = Offset.Zero): Path? {
    if (points.size < 2) return null
    return Path().apply {
        points.forEachIndexed { index, point ->
            val coordinate = mapCoordinate(point, zoom)
            val x = width / 2f + (coordinate.x - center.x).toFloat() * tileSize + panOffset.x
            val y = height / 2f + (coordinate.y - center.y).toFloat() * tileSize + panOffset.y
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
}

private fun startRoute(context: Context, store: RouteTrackingStore, activityType: String): RouteSnapshot {
    // Start the visible timer at the tap, rather than waiting for the service and
    // the next one-second UI refresh to create the route session.
    val started = store.start(activityType)
    ContextCompat.startForegroundService(context, Intent(context, RouteTrackingService::class.java))
    return started
}
private fun stopRoute(context: Context, discard: Boolean = false) = context.startService(
    Intent(context, RouteTrackingService::class.java).setAction(if (discard) RouteTrackingService.ACTION_DISCARD else RouteTrackingService.ACTION_STOP),
)

private suspend fun shareRoute(context: Context, snapshot: RouteSnapshot, title: String, story: Boolean) {
    val width = 1080
    val height = if (story) 1920 else 1080
    val file = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(11, 13, 12))
        val mapBottom = (height - 390).coerceAtMost(1120).toFloat()
        drawShareMap(canvas, snapshot.points, width.toFloat(), mapBottom)
        canvas.drawRect(0f, mapBottom, width.toFloat(), height.toFloat(), Paint().apply { color = AndroidColor.rgb(11, 13, 12) })
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(126, 225, 80)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        textPaint.textSize = 58f
        canvas.drawText(title.ifBlank { defaultActivityTitle(snapshot.activityType, false) }, 72f, height - 330f, textPaint)
        textPaint.color = AndroidColor.rgb(166, 174, 169)
        textPaint.textSize = 34f
        canvas.drawText(com.hedefit.app.ui.i18n.tr("MESAFE", "DISTANCE"), 72f, height - 220f, textPaint); canvas.drawText(com.hedefit.app.ui.i18n.tr("SÜRE", "TIME"), 410f, height - 220f, textPaint); canvas.drawText(com.hedefit.app.ui.i18n.tr("TEMPO", "PACE"), 730f, height - 220f, textPaint)
        textPaint.color = AndroidColor.WHITE
        textPaint.textSize = 46f
        canvas.drawText("%.2f km".format(snapshot.distanceMeters / 1_000.0), 72f, height - 155f, textPaint); canvas.drawText(formatDuration(snapshot.durationSeconds), 410f, height - 155f, textPaint); canvas.drawText(formatPace(snapshot.paceSecondsPerKm), 730f, height - 155f, textPaint)
        textPaint.color = AndroidColor.rgb(126, 225, 80)
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textPaint.textSize = 42f
        canvas.drawText(com.hedefit.app.ui.i18n.tr("HEDEFİT ROTA", "HEDEFIT ROUTE"), 72f, height - 70f, textPaint)
        val directory = File(context.cacheDir, "shared-routes").apply { mkdirs() }
        File(directory, "hedefit-rota-${snapshot.id}.png").also { output ->
            FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Rotanı paylaş"))
}

private fun drawShareMap(canvas: AndroidCanvas, points: List<RoutePoint>, width: Float, height: Float) {
    if (points.isEmpty()) return
    val center = RoutePoint((points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2.0, (points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2.0, 0.0, 0L)
    val tileSize = 360f
    val zoom = fittedMapZoom(points, width, height, tileSize)
    val tileCount = 1 shl zoom
    val centerPosition = mapCoordinate(center, zoom)
    val centerTileX = floor(centerPosition.x).toInt()
    val centerTileY = floor(centerPosition.y).toInt()
    val horizontalTiles = ceil(width / tileSize / 2).toInt() + 1
    val verticalTiles = ceil(height / tileSize / 2).toInt() + 1
    for (tileY in (centerTileY - verticalTiles)..(centerTileY + verticalTiles)) {
        if (tileY !in 0 until tileCount) continue
        for (rawTileX in (centerTileX - horizontalTiles)..(centerTileX + horizontalTiles)) {
            val left = width / 2f + (rawTileX - centerPosition.x).toFloat() * tileSize
            val top = height / 2f + (tileY - centerPosition.y).toFloat() * tileSize
            if (left > width || top > height || left + tileSize < 0 || top + tileSize < 0) continue
            runCatching {
                URL("https://tile.openstreetmap.org/$zoom/${rawTileX.mod(tileCount)}/$tileY.png").openConnection().apply {
                    connectTimeout = 1_500
                    readTimeout = 1_500
                    setRequestProperty("User-Agent", "Hedefit/0.2 Android route share")
                }.getInputStream().use(BitmapFactory::decodeStream)
            }.getOrNull()?.let { tile -> canvas.drawBitmap(tile, null, RectF(left, top, left + tileSize, top + tileSize), null) }
        }
    }
    val path = AndroidPath()
    points.forEachIndexed { index, point ->
        val coordinate = mapCoordinate(point, zoom)
        val x = width / 2f + (coordinate.x - centerPosition.x).toFloat() * tileSize
        val y = height / 2f + (coordinate.y - centerPosition.y).toFloat() * tileSize
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    routePaint.color = AndroidColor.rgb(10, 39, 17); routePaint.strokeWidth = 28f; canvas.drawPath(path, routePaint)
    routePaint.color = AndroidColor.rgb(126, 225, 80); routePaint.strokeWidth = 15f; canvas.drawPath(path, routePaint)
    val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun drawPoint(point: RoutePoint, color: Int) { val coordinate = mapCoordinate(point, zoom); pointPaint.color = color; canvas.drawCircle(width / 2f + (coordinate.x - centerPosition.x).toFloat() * tileSize, height / 2f + (coordinate.y - centerPosition.y).toFloat() * tileSize, 18f, pointPaint) }
    drawPoint(points.first(), AndroidColor.WHITE)
    drawPoint(points.last(), AndroidColor.rgb(126, 225, 80))
    val attribution = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE; textSize = 22f; setShadowLayer(3f, 0f, 1f, AndroidColor.BLACK) }
    canvas.drawText("© OpenStreetMap contributors", 20f, height - 18f, attribution)
}

private fun drawMinimalRoute(canvas: AndroidCanvas, points: List<RoutePoint>, paint: Paint, width: Float, height: Float) {
    if (points.size < 2) return
    val renderPoints = if (points.size <= 1_000) points else points.filterIndexed { index, _ -> index % (points.size / 1_000 + 1) == 0 } + points.last()
    val minLat = renderPoints.minOf { it.latitude }; val maxLat = renderPoints.maxOf { it.latitude }
    val minLng = renderPoints.minOf { it.longitude }; val maxLng = renderPoints.maxOf { it.longitude }
    val latRange = (maxLat - minLat).coerceAtLeast(.000001); val lngRange = (maxLng - minLng).coerceAtLeast(.000001)
    val left = 90f; val right = width - 90f; val top = 110f; val bottom = height * .65f
    val path = AndroidPath()
    renderPoints.forEachIndexed { index, point ->
        val x = (left + (point.longitude - minLng) / lngRange * (right - left)).toFloat()
        val y = (bottom - (point.latitude - minLat) / latRange * (bottom - top)).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)
}

private fun defaultActivityTitle(activityType: String, en: Boolean): String {
    val hour = java.time.LocalTime.now().hour
    val period = when { hour < 12 -> if (en) "Morning" else "Sabah"; hour < 18 -> if (en) "Afternoon" else "Öğleden Sonra"; else -> if (en) "Evening" else "Akşam" }
    val type = when (activityType) { "Koşu" -> if (en) "Run" else "Koşusu"; "Trail Koşusu" -> if (en) "Trail Run" else "Trail Koşusu"; "Bisiklet" -> if (en) "Ride" else "Bisikleti"; "Doğa Yürüyüşü" -> if (en) "Hike" else "Doğa Yürüyüşü"; else -> if (en) "Walk" else "Yürüyüşü" }
    return "$period $type"
}

private fun estimatedRouteCalories(snapshot: RouteSnapshot): Int =
    ((snapshot.distanceMeters / 1_000.0) * when (snapshot.activityType) { "Bisiklet" -> 28.0; "Koşu", "Trail Koşusu" -> 62.0; else -> 45.0 }).roundToInt().coerceAtLeast(0)
