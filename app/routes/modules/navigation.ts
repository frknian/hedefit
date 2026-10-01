/**
 * Navigasyon modülü (navigation.ts).
 * Kamera bounds ayarlaması, tüm rotayı sığdırma ve navigasyon pra-hazırlık işlemleri.
 */

import type { RoutePoint, PlannedRouteResult, RouteManeuver } from './routePlanning.ts';
import { type RouteBounds, geoDistanceMeters, calculateRouteBounds } from '../utils/routeAlgorithms.ts';
import type { MapInstance } from '../plugins/map.ts';

export interface NavigationState {
  currentPosition: RoutePoint | null;
  nearestPointIndex: number;
  remainingMeters: number;
  traveledMeters: number;
  offRoute: boolean;
  activeManeuver: RouteManeuver | null;
  distanceToNextManeuverMeters: number;
}

/**
 * Harita kamerasını tüm rotayı (ve dönüş segmentini) içine alacak şekilde ayarlar.
 * Yalnızca merkez noktaya zoom yapma hatasını engeller.
 */
export function fitCameraToRoute(
  map: MapInstance,
  route: PlannedRouteResult,
  paddingFactor: number = 0.15
): RouteBounds {
  // Eğer route.bounds zaten hesaplanmışsa onu kullan, yoksa yeniden hesapla
  const bounds = route.bounds || calculateRouteBounds(route.points, paddingFactor);

  map.fitBounds({
    southWest: bounds.southWest,
    northEast: bounds.northEast,
  });

  return bounds;
}

/**
 * Navigasyon pra-hazırlık fonksiyonu:
 * Rotanın başlangıç durumunu ve ilk manevralarını hazırlar.
 */
export function prepareNavigation(route: PlannedRouteResult): NavigationState {
  return {
    currentPosition: route.origin,
    nearestPointIndex: 0,
    remainingMeters: route.distanceMeters,
    traveledMeters: 0,
    offRoute: false,
    activeManeuver: route.maneuvers.length > 0 ? route.maneuvers[0] : null,
    distanceToNextManeuverMeters: route.maneuvers.length > 1
      ? geoDistanceMeters(route.points[0], route.points[route.maneuvers[1].pointIndex] || route.points[0])
      : route.distanceMeters,
  };
}

/**
 * Kullanıcının anlık konumuna göre navigasyon ilerlemesini günceller.
 */
export function updateNavigationProgress(
  currentState: NavigationState,
  route: PlannedRouteResult,
  newPosition: RoutePoint,
  offRouteThresholdMeters: number = 40.0
): NavigationState {
  if (!route.points || route.points.length === 0) {
    return currentState;
  }

  // En yakın rota noktasını bul
  let nearestIndex = 0;
  let minDistance = Number.POSITIVE_INFINITY;

  for (let i = 0; i < route.points.length; i++) {
    const dist = geoDistanceMeters(newPosition, route.points[i]);
    if (dist < minDistance) {
      minDistance = dist;
      nearestIndex = i;
    }
  }

  const isOffRoute = minDistance > offRouteThresholdMeters;

  // İlerleme ve kalan mesafe
  let traveledMeters = 0;
  for (let i = 0; i < nearestIndex; i++) {
    traveledMeters += geoDistanceMeters(route.points[i], route.points[i + 1]);
  }
  const remainingMeters = Math.max(0, route.distanceMeters - traveledMeters);

  // Sıradaki manevra
  const nextManeuver = route.maneuvers.find((m) => m.pointIndex > nearestIndex) || null;
  const distToManeuver = nextManeuver && route.points[nextManeuver.pointIndex]
    ? geoDistanceMeters(newPosition, route.points[nextManeuver.pointIndex])
    : 0;

  return {
    currentPosition: newPosition,
    nearestPointIndex: nearestIndex,
    remainingMeters,
    traveledMeters,
    offRoute: isOffRoute,
    activeManeuver: nextManeuver,
    distanceToNextManeuverMeters: distToManeuver,
  };
}
