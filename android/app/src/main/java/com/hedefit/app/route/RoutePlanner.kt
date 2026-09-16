package com.hedefit.app.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.roundToInt

data class RoutePlanRequest(
    val activityType: String,
    val goalType: String,
    val goalValue: Double,
    val planType: String = "loop",
)

data class PlannedRoute(
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val estimatedDurationSeconds: Int,
    val requestedDistanceMeters: Double,
    val isLoop: Boolean = true,
    val destination: RoutePoint? = null,
    val maneuvers: List<RouteManeuver> = emptyList(),
)

enum class ManeuverType { START, STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, ARRIVE }

data class RouteManeuver(
    val pointIndex: Int,
    val type: ManeuverType,
    val instruction: String,
    val streetName: String = "",
)

data class RouteProgress(
    val nearestPointIndex: Int,
    val traveledMeters: Double,
    val remainingMeters: Double,
    val completionPercent: Int,
    val distanceFromRouteMeters: Double,
    val offRoute: Boolean,
    val nextManeuver: RouteManeuver?,
    val distanceToManeuverMeters: Double,
)

object RoutePlanner {
    private const val BASE_URL = "https://brouter.de/brouter"
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun speedKmh(activityType: String): Double = when (activityType) {
        "Koşu" -> 9.0
        "Trail Koşusu" -> 7.5
        "Doğa Yürüyüşü" -> 4.5
        "Bisiklet" -> 18.0
        else -> 5.0
    }

    fun targetDistanceMeters(request: RoutePlanRequest): Double {
        val distance = if (request.goalType == "time") {
            speedKmh(request.activityType) * request.goalValue / 60.0 * 1_000.0
        } else request.goalValue * 1_000.0
        return distance.coerceIn(500.0, 50_000.0)
    }

    suspend fun plan(origin: RoutePoint, request: RoutePlanRequest, destination: RoutePoint? = null): PlannedRoute = withContext(Dispatchers.IO) {
        if (request.planType == "point_to_point") {
            val end = requireNotNull(destination) { "Varış noktası seçilmedi." }
            return@withContext fetchRoute(listOf(origin, end), request.activityType, 0.0, false, end)
        }
        val target = targetDistanceMeters(request)
        val orientation = ((origin.latitude * 1_000 + origin.longitude * 1_000).toInt().mod(360)).toDouble()
        val initialRadius = target / 3.0
        val first = fetchLoop(origin, initialRadius, orientation, request.activityType, target)
        if (abs(first.distanceMeters - target) / target <= .12) return@withContext first

        val correctedRadius = initialRadius * (target / first.distanceMeters.coerceAtLeast(100.0)).coerceIn(.45, 1.8)
        val second = fetchLoop(origin, correctedRadius, orientation, request.activityType, target)
        if (abs(second.distanceMeters - target) < abs(first.distanceMeters - target)) second else first
    }

    private fun fetchLoop(origin: RoutePoint, radius: Double, bearing: Double, activityType: String, target: Double): PlannedRoute {
        val firstCorner = destination(origin, radius, bearing)
        val secondCorner = destination(origin, radius, bearing + 60.0)
        return fetchRoute(listOf(origin, firstCorner, secondCorner, origin), activityType, target, true, origin)
    }

    private fun fetchRoute(waypoints: List<RoutePoint>, activityType: String, target: Double, isLoop: Boolean, destination: RoutePoint): PlannedRoute {
        val coordinates = waypoints.joinToString("%7C") { "${it.longitude},${it.latitude}" }
        val profile = if (activityType == "Bisiklet") "fastbike" else "hiking-mountain"
        // BRouter defaults `timode` to 0, which omits turn instructions. Asking
        // for its automatic mode makes a planned route usable as navigation.
        val connection = URL("$BASE_URL?lonlats=$coordinates&profile=$profile&alternativeidx=0&format=geojson&timode=1").openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.setRequestProperty("Accept", "application/geo+json, application/json")
        connection.setRequestProperty("User-Agent", "Hedefit/0.2 Android route planner")
        return try {
            if (connection.responseCode !in 200..299) error("Rota servisi şu anda yanıt vermiyor.")
            val root = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val feature = root.optJSONArray("features")?.optJSONObject(0) ?: error("Bu konum için uygun rota bulunamadı.")
            val rawCoordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: error("Rota çizgisi alınamadı.")
            val now = System.currentTimeMillis()
            val allPoints = buildList {
                for (index in 0 until rawCoordinates.length()) {
                    val coordinate = rawCoordinates.optJSONArray(index) ?: continue
                    if (coordinate.length() < 2) continue
                    add(RoutePoint(
                        latitude = coordinate.optDouble(1),
                        longitude = coordinate.optDouble(0),
                        altitude = coordinate.optDouble(2, 0.0),
                        recordedAt = now + index,
                    ))
                }
            }
            if (allPoints.size < 2) error("Rota çizgisi alınamadı.")
            val serverDistance = feature.optJSONObject("properties")?.optString("track-length")?.toDoubleOrNull()
            val distance = serverDistance ?: allPoints.zipWithNext().sumOf { (a, b) -> geoDistanceMeters(a, b) }
            val points = if (allPoints.size <= 2_000) allPoints else {
                val step = allPoints.size / 2_000 + 1
                allPoints.filterIndexed { index, _ -> index % step == 0 } + allPoints.last()
            }
            PlannedRoute(
                points = points,
                distanceMeters = distance,
                estimatedDurationSeconds = (distance / (speedKmh(activityType) * 1_000.0 / 3_600.0)).toInt().coerceAtLeast(1),
                requestedDistanceMeters = target.takeIf { it > 0 } ?: distance,
                isLoop = isLoop,
                destination = destination,
                maneuvers = parseManeuvers(feature, points),
            )
        } finally {
            connection.disconnect()
        }
    }

    internal fun destination(origin: RoutePoint, distanceMeters: Double, bearingDegrees: Double): RoutePoint {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearing = bearingDegrees * PI / 180.0
        val latitude = origin.latitude * PI / 180.0
        val longitude = origin.longitude * PI / 180.0
        val destinationLatitude = asin(sin(latitude) * cos(angularDistance) + cos(latitude) * sin(angularDistance) * cos(bearing))
        val destinationLongitude = longitude + atan2(
            sin(bearing) * sin(angularDistance) * cos(latitude),
            cos(angularDistance) - sin(latitude) * sin(destinationLatitude),
        )
        return RoutePoint(destinationLatitude * 180.0 / PI, destinationLongitude * 180.0 / PI, origin.altitude, System.currentTimeMillis())
    }

    fun remainingDistanceMeters(route: PlannedRoute, position: RoutePoint?): Double {
        return progress(route, position).remainingMeters
    }

    fun progress(route: PlannedRoute, position: RoutePoint?, offRouteThresholdMeters: Double = 45.0): RouteProgress {
        if (position == null || route.points.isEmpty()) return RouteProgress(0, 0.0, route.distanceMeters, 0, Double.POSITIVE_INFINITY, false, route.maneuvers.firstOrNull(), route.distanceMeters)
        if (route.points.size == 1) {
            val distance = geoDistanceMeters(position, route.points.first())
            return RouteProgress(0, 0.0, route.distanceMeters, 0, distance, distance > offRouteThresholdMeters, route.maneuvers.firstOrNull(), route.distanceMeters)
        }
        val segmentLengths = route.points.zipWithNext(::geoDistanceMeters)
        val cumulative = MutableList(route.points.size) { 0.0 }
        segmentLengths.forEachIndexed { index, length -> cumulative[index + 1] = cumulative[index] + length }
        val geometryDistance = cumulative.last().coerceAtLeast(1.0)
        val best = segmentLengths.indices.minByOrNull { segment -> projectToSegment(position, route.points[segment], route.points[segment + 1]).distanceMeters } ?: 0
        val projection = projectToSegment(position, route.points[best], route.points[best + 1])
        val traveledOnGeometry = cumulative[best] + segmentLengths[best] * projection.fraction
        val scale = route.distanceMeters / geometryDistance
        val traveled = (traveledOnGeometry * scale).coerceIn(0.0, route.distanceMeters)
        val remaining = (route.distanceMeters - traveled).coerceAtLeast(0.0)
        val nearestIndex = best + if (projection.fraction >= .5) 1 else 0
        val next = route.maneuvers.firstOrNull { it.pointIndex > best } ?: route.maneuvers.lastOrNull()
        val distanceToTurn = next?.let { maneuver ->
            ((cumulative[maneuver.pointIndex.coerceIn(0, cumulative.lastIndex)] - traveledOnGeometry).coerceAtLeast(0.0) * scale)
        } ?: remaining
        return RouteProgress(nearestIndex, traveled, remaining, if (route.distanceMeters > 0) (traveled / route.distanceMeters * 100).roundToInt().coerceIn(0, 100) else 0, projection.distanceMeters, projection.distanceMeters > offRouteThresholdMeters, next, distanceToTurn)
    }

    private data class SegmentProjection(val fraction: Double, val distanceMeters: Double)

    /** Distance to the road segment, rather than merely its sampled vertices. */
    private fun projectToSegment(point: RoutePoint, start: RoutePoint, end: RoutePoint): SegmentProjection {
        val referenceLatitude = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
        fun x(longitude: Double) = Math.toRadians(longitude - start.longitude) * EARTH_RADIUS_METERS * cos(referenceLatitude)
        fun y(latitude: Double) = Math.toRadians(latitude - start.latitude) * EARTH_RADIUS_METERS
        val endX = x(end.longitude); val endY = y(end.latitude)
        val pointX = x(point.longitude); val pointY = y(point.latitude)
        val squaredLength = endX * endX + endY * endY
        val fraction = if (squaredLength <= 0.0) 0.0 else ((pointX * endX + pointY * endY) / squaredLength).coerceIn(0.0, 1.0)
        return SegmentProjection(fraction, hypot(pointX - endX * fraction, pointY - endY * fraction))
    }

    internal fun parseManeuvers(feature: JSONObject, points: List<RoutePoint>): List<RouteManeuver> {
        val properties = feature.optJSONObject("properties") ?: JSONObject()
        val voiceHints = properties.optJSONArray("voicehints") ?: properties.optJSONArray("voiceHints")
        val fromService = buildList {
            if (voiceHints != null) for (index in 0 until voiceHints.length()) {
                when (val hint = voiceHints.opt(index)) {
                    is JSONObject -> {
                        val pointIndex = hint.optInt("i", hint.optInt("index", -1)).takeIf { it in points.indices } ?: continue
                        val command = hint.optString("cmd", hint.optString("command"))
                        val street = hint.optString("streetname", hint.optString("street", hint.optString("name")))
                        val type = maneuverType(command)
                        val message = hint.optString("message").ifBlank { instructionFor(type) }
                        add(RouteManeuver(pointIndex, type, message, street))
                    }
                    is JSONArray -> {
                        // GeoJSON timode=1: [pointIndex, commandIndex, exit, distance, angle]
                        addAll(arrayVoiceManeuvers(listOf(listOf(hint.optInt(0, -1), hint.optInt(1, 1))), points.size))
                    }
                }
            }
        }
        if (fromService.isNotEmpty()) return buildList {
            add(RouteManeuver(0, ManeuverType.START, instructionFor(ManeuverType.START)))
            addAll(fromService.distinctBy { it.pointIndex }.sortedBy { it.pointIndex }.filter { it.pointIndex != 0 && it.pointIndex != points.lastIndex })
            add(RouteManeuver(points.lastIndex, ManeuverType.ARRIVE, instructionFor(ManeuverType.ARRIVE)))
        }
        return geometryManeuvers(points)
    }

    internal fun arrayVoiceManeuvers(hints: List<List<Int>>, pointCount: Int): List<RouteManeuver> = hints.mapNotNull { hint ->
        val pointIndex = hint.getOrNull(0)?.takeIf { it in 0 until pointCount } ?: return@mapNotNull null
        val type = maneuverType(hint.getOrNull(1) ?: 1)
        RouteManeuver(pointIndex, type, instructionFor(type))
    }

    /** Uses the routed road geometry, never a timer or fabricated path, when the provider omits voice hints. */
    internal fun geometryManeuvers(points: List<RoutePoint>): List<RouteManeuver> {
        if (points.size < 2) return emptyList()
        val result = mutableListOf(RouteManeuver(0, ManeuverType.START, instructionFor(ManeuverType.START)))
        var lastTurn = 0
        for (index in 2 until points.lastIndex) {
            if (index - lastTurn < 3) continue
            val incoming = bearing(points[index - 2], points[index])
            val outgoing = bearing(points[index], points[(index + 2).coerceAtMost(points.lastIndex)])
            val delta = ((outgoing - incoming + 540.0) % 360.0) - 180.0
            val type = when {
                delta <= -100 -> ManeuverType.SHARP_LEFT
                delta <= -45 -> ManeuverType.LEFT
                delta <= -22 -> ManeuverType.SLIGHT_LEFT
                delta >= 100 -> ManeuverType.SHARP_RIGHT
                delta >= 45 -> ManeuverType.RIGHT
                delta >= 22 -> ManeuverType.SLIGHT_RIGHT
                else -> null
            }
            if (type != null) { result += RouteManeuver(index, type, instructionFor(type)); lastTurn = index }
        }
        result += RouteManeuver(points.lastIndex, ManeuverType.ARRIVE, instructionFor(ManeuverType.ARRIVE))
        return result
    }

    private fun maneuverType(command: String): ManeuverType = when (command.lowercase()) {
        "tl", "left" -> ManeuverType.LEFT
        "tsll", "slight_left" -> ManeuverType.SLIGHT_LEFT
        "tshl", "sharp_left" -> ManeuverType.SHARP_LEFT
        "tr", "right" -> ManeuverType.RIGHT
        "tslr", "slight_right" -> ManeuverType.SLIGHT_RIGHT
        "tshr", "sharp_right" -> ManeuverType.SHARP_RIGHT
        "finish", "arrive" -> ManeuverType.ARRIVE
        else -> ManeuverType.STRAIGHT
    }

    private fun maneuverType(command: Int): ManeuverType = when (command) {
        2 -> ManeuverType.LEFT
        3 -> ManeuverType.SLIGHT_LEFT
        4 -> ManeuverType.SHARP_LEFT
        5 -> ManeuverType.RIGHT
        6 -> ManeuverType.SLIGHT_RIGHT
        7 -> ManeuverType.SHARP_RIGHT
        else -> ManeuverType.STRAIGHT
    }

    /** Navigation voice thresholds; each threshold is announced only once by the UI. */
    fun announcementThreshold(distanceMeters: Double): Int? = when {
        distanceMeters <= 25.0 -> 25
        distanceMeters <= 80.0 -> 80
        distanceMeters <= 200.0 -> 200
        else -> null
    }

    fun instructionFor(type: ManeuverType, english: Boolean = false): String = if (english) when (type) {
        ManeuverType.START -> "Start on the route"; ManeuverType.STRAIGHT -> "Continue straight"; ManeuverType.SLIGHT_LEFT -> "Bear left"; ManeuverType.LEFT -> "Turn left"; ManeuverType.SHARP_LEFT -> "Make a sharp left"; ManeuverType.SLIGHT_RIGHT -> "Bear right"; ManeuverType.RIGHT -> "Turn right"; ManeuverType.SHARP_RIGHT -> "Make a sharp right"; ManeuverType.ARRIVE -> "You have reached your destination"
    } else when (type) {
        ManeuverType.START -> "Rotada ilerle"; ManeuverType.STRAIGHT -> "Düz devam et"; ManeuverType.SLIGHT_LEFT -> "Hafif sola yönel"; ManeuverType.LEFT -> "Sola dön"; ManeuverType.SHARP_LEFT -> "Keskin sola dön"; ManeuverType.SLIGHT_RIGHT -> "Hafif sağa yönel"; ManeuverType.RIGHT -> "Sağa dön"; ManeuverType.SHARP_RIGHT -> "Keskin sağa dön"; ManeuverType.ARRIVE -> "Hedefine ulaştın"
    }

    private fun bearing(from: RoutePoint, to: RoutePoint): Double {
        val firstLat = Math.toRadians(from.latitude); val secondLat = Math.toRadians(to.latitude)
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val y = sin(longitudeDelta) * cos(secondLat)
        val x = cos(firstLat) * sin(secondLat) - sin(firstLat) * cos(secondLat) * cos(longitudeDelta)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
