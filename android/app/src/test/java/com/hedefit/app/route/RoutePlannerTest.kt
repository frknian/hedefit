package com.hedefit.app.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    @Test fun `thirty minute walk becomes two and a half kilometer target`() {
        val request = RoutePlanRequest("Yürüyüş", "time", 30.0)
        assertEquals(2_500.0, RoutePlanner.targetDistanceMeters(request), 0.1)
    }

    @Test fun `distance goal is preserved in meters`() {
        val request = RoutePlanRequest("Koşu", "distance", 5.0)
        assertEquals(5_000.0, RoutePlanner.targetDistanceMeters(request), 0.1)
    }

    @Test fun `generated waypoint is requested distance from origin`() {
        val origin = RoutePoint(41.045, 29.035, 0.0, 1L)
        val waypoint = RoutePlanner.destination(origin, 1_500.0, 80.0)
        assertTrue(geoDistanceMeters(origin, waypoint) in 1_495.0..1_505.0)
    }

    @Test fun `remaining distance drops as position moves along planned route`() {
        val start = RoutePoint(41.0, 29.0, 0.0, 1L)
        val middle = RoutePlanner.destination(start, 1_000.0, 90.0)
        val end = RoutePlanner.destination(middle, 1_000.0, 90.0)
        val route = PlannedRoute(listOf(start, middle, end), 2_000.0, 1_200, 2_000.0, isLoop = false, destination = end)
        assertTrue(RoutePlanner.remainingDistanceMeters(route, middle) in 990.0..1_010.0)
    }

    @Test fun `progress reports traveled remaining percentage and off route state`() {
        val start = RoutePoint(41.0, 29.0, 0.0, 1L)
        val middle = RoutePlanner.destination(start, 1_000.0, 90.0)
        val end = RoutePlanner.destination(middle, 1_000.0, 90.0)
        val route = PlannedRoute(listOf(start, middle, end), 2_000.0, 1_200, 2_000.0, false, end)
        val halfway = RoutePlanner.progress(route, middle)
        assertTrue(halfway.traveledMeters in 990.0..1_010.0)
        assertTrue(halfway.remainingMeters in 990.0..1_010.0)
        assertTrue(halfway.completionPercent in 49..51)
        assertTrue(!halfway.offRoute)
        val displaced = RoutePlanner.destination(middle, 100.0, 0.0)
        assertTrue(RoutePlanner.progress(route, displaced).offRoute)
    }

    @Test fun `real routed geometry produces turn instructions`() {
        val start = RoutePoint(41.0, 29.0, 0.0, 1L)
        val east = RoutePlanner.destination(start, 300.0, 90.0)
        val north = RoutePlanner.destination(east, 300.0, 0.0)
        val points = listOf(start, RoutePlanner.destination(start, 100.0, 90.0), RoutePlanner.destination(start, 200.0, 90.0), east, RoutePlanner.destination(east, 100.0, 0.0), RoutePlanner.destination(east, 200.0, 0.0), north)
        val maneuvers = RoutePlanner.geometryManeuvers(points)
        assertTrue(maneuvers.any { it.type == ManeuverType.LEFT })
        assertEquals(ManeuverType.ARRIVE, maneuvers.last().type)
    }

    @Test fun `position on a long road segment is not falsely treated as off route`() {
        val start = RoutePoint(41.0, 29.0, 0.0, 1L)
        val end = RoutePlanner.destination(start, 1_000.0, 90.0)
        val middle = RoutePlanner.destination(start, 500.0, 90.0)
        val route = PlannedRoute(listOf(start, end), 1_000.0, 600, 1_000.0, false, end)
        val progress = RoutePlanner.progress(route, middle)
        assertTrue(!progress.offRoute)
        assertTrue(progress.distanceFromRouteMeters < 2.0)
        assertTrue(progress.completionPercent in 49..51)
    }

    @Test fun `brouter geojson array voice hints become navigation maneuvers`() {
        val maneuvers = RoutePlanner.arrayVoiceManeuvers(listOf(listOf(2, 5, 0, 54, 88), listOf(6, 3, 0, 12, -33)), 9)
        assertEquals(ManeuverType.RIGHT, maneuvers.first { it.pointIndex == 2 }.type)
        assertEquals(ManeuverType.SLIGHT_LEFT, maneuvers.first { it.pointIndex == 6 }.type)
    }

    @Test fun `voice prompt thresholds are stable navigation milestones`() {
        assertEquals(200, RoutePlanner.announcementThreshold(199.0))
        assertEquals(80, RoutePlanner.announcementThreshold(79.0))
        assertEquals(25, RoutePlanner.announcementThreshold(24.0))
        assertEquals(null, RoutePlanner.announcementThreshold(250.0))
    }
}
