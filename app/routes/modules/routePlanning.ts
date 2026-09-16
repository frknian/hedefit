/**
 * Rota planlama veri tipleri, Russell-grafik tabanlı stratejiler ve
 * modüler rota planlama servisi.
 */

import {
  type RoutePoint,
  type RouteBounds,
  geoDistanceMeters,
  calculatePathDistance,
  interpolateGeodesicPolyline,
  generateRoundTripSegments,
  calculateRouteBounds,
  generateRouteChecksum,
} from '../utils/routeAlgorithms.ts';

export type PlanType = 'oneWay' | 'roundTrip' | 'point_to_point';

export type ActivityType = 'Koşu' | 'Yürüyüş' | 'Bisiklet' | 'Trail Koşusu' | 'Doğa Yürüyüşü' | string;

export const ManeuverType = {
  START: 'START',
  STRAIGHT: 'STRAIGHT',
  SLIGHT_LEFT: 'SLIGHT_LEFT',
  LEFT: 'LEFT',
  SHARP_LEFT: 'SHARP_LEFT',
  SLIGHT_RIGHT: 'SLIGHT_RIGHT',
  RIGHT: 'RIGHT',
  SHARP_RIGHT: 'SHARP_RIGHT',
  RETURN: 'RETURN',
  ARRIVE: 'ARRIVE',
} as const;

export type ManeuverType = (typeof ManeuverType)[keyof typeof ManeuverType];

export interface RouteManeuver {
  pointIndex: number;
  type: ManeuverType;
  instruction: string;
  streetName?: string;
  suffix?: 'start' | 'end' | 'return';
}

export interface RoutePlanRequest {
  activityType: ActivityType;
  planType: PlanType;
  goalType?: 'distance' | 'time';
  goalValue?: number;
  targetDistanceMeters?: number;
}

export interface PlannedRouteResult {
  id: string;
  points: RoutePoint[];
  distanceMeters: number;
  estimatedDurationSeconds: number;
  isRoundTrip: boolean;
  origin: RoutePoint;
  destination: RoutePoint;
  bounds: RouteBounds;
  checksum: string;
  maneuvers: RouteManeuver[];
  segments?: {
    forward: RoutePoint[];
    return?: RoutePoint[];
  };
}

export function speedKmh(activityType: ActivityType): Double {
  switch (activityType) {
    case 'Koşu':
      return 9.0;
    case 'Trail Koşusu':
      return 7.5;
    case 'Doğa Yürüyüşü':
      return 4.5;
    case 'Bisiklet':
      return 18.0;
    default:
      return 5.0;
  }
}

type Double = number;

export function calculateEstimatedDuration(distanceMeters: number, activityType: ActivityType): number {
  const speed = (speedKmh(activityType) * 1000.0) / 3600.0;
  return Math.max(1, Math.round(distanceMeters / speed));
}

export interface IPlanningStrategy {
  plan(
    origin: RoutePoint,
    request: RoutePlanRequest,
    destination?: RoutePoint | null,
    fetchPathFn?: (from: RoutePoint, to: RoutePoint) => Promise<RoutePoint[]>
  ): Promise<PlannedRouteResult>;
}

/**
 * Normal tek yönlü A→B rota stratejisi (Mevcut davranış korunur).
 */
export class OneWayStrategy implements IPlanningStrategy {
  async plan(
    origin: RoutePoint,
    request: RoutePlanRequest,
    destination?: RoutePoint | null,
    fetchPathFn?: (from: RoutePoint, to: RoutePoint) => Promise<RoutePoint[]>
  ): Promise<PlannedRouteResult> {
    const end = destination || {
      latitude: origin.latitude + 0.01,
      longitude: origin.longitude + 0.01,
    };

    let rawPoints: RoutePoint[];
    if (fetchPathFn) {
      rawPoints = await fetchPathFn(origin, end);
    } else {
      rawPoints = [origin, end];
    }

    const points = interpolateGeodesicPolyline(rawPoints);
    const distanceMeters = calculatePathDistance(points);
    const duration = calculateEstimatedDuration(distanceMeters, request.activityType);
    const bounds = calculateRouteBounds(points);
    const checksum = generateRouteChecksum({
      origin,
      destination: end,
      planType: 'oneWay',
      activityType: request.activityType,
      targetDistanceMeters: distanceMeters,
    });

    const maneuvers: RouteManeuver[] = [
      {
        pointIndex: 0,
        type: ManeuverType.START,
        instruction: 'Rotada ilerle',
        suffix: 'start',
      },
      {
        pointIndex: points.length - 1,
        type: ManeuverType.ARRIVE,
        instruction: 'Hedefe ulaştın',
        suffix: 'end',
      },
    ];

    return {
      id: checksum,
      points,
      distanceMeters,
      estimatedDurationSeconds: duration,
      isRoundTrip: false,
      origin,
      destination: end,
      bounds,
      checksum,
      maneuvers,
      segments: {
        forward: points,
      },
    };
  }
}

/**
 * Russell-grafik ve döngü tabanlı A→B→A dönüşlü rota stratejisi.
 * A→B gidiş segmenti ile B→A dönüş segmentini entegre eder,
 * toplam mesafe = mesafe(AB) + mesafe(BA) hesaplar,
 * tüm rotayı kapsayan bounds ve tekil Başlangıç/Bitiş işaretlerini üretir.
 */
export class RoundTripStrategy implements IPlanningStrategy {
  async plan(
    origin: RoutePoint,
    request: RoutePlanRequest,
    destination?: RoutePoint | null,
    fetchPathFn?: (from: RoutePoint, to: RoutePoint) => Promise<RoutePoint[]>
  ): Promise<PlannedRouteResult> {
    const targetEnd = destination || origin;

    let forwardWaypoints: RoutePoint[] = [];

    if (fetchPathFn) {
      const intermediate = await fetchPathFn(origin, targetEnd);
      forwardWaypoints = intermediate.slice(1, -1);
    } else if (geoDistanceMeters(origin, targetEnd) < 10.0) {
      // Başlangıç ve bitiş aynı olduğunda dairesel rota için ara waypointler
      const targetMeters = request.targetDistanceMeters || 3000;
      const radius = targetMeters / (2 * Math.PI);
      const latDelta = (radius / 6371000.0) * (180 / Math.PI);
      const lngDelta = (radius / (6371000.0 * Math.cos((origin.latitude * Math.PI) / 180))) * (180 / Math.PI);

      forwardWaypoints = [
        { latitude: origin.latitude + latDelta, longitude: origin.longitude, altitude: origin.altitude },
        { latitude: origin.latitude, longitude: origin.longitude + lngDelta, altitude: origin.altitude },
        { latitude: origin.latitude - latDelta, longitude: origin.longitude, altitude: origin.altitude },
      ];
    }

    const segments = generateRoundTripSegments(origin, targetEnd, forwardWaypoints);

    // Geodesic interpolasyon ile düz çizgileri tamamen engelle
    const interpolatedPoints = interpolateGeodesicPolyline(segments.combinedPoints);
    const bounds = calculateRouteBounds(interpolatedPoints);
    const duration = calculateEstimatedDuration(segments.totalDistanceMeters, request.activityType);

    const checksum = generateRouteChecksum({
      origin,
      destination: targetEnd,
      planType: 'roundTrip',
      activityType: request.activityType,
      targetDistanceMeters: segments.totalDistanceMeters,
    });

    const returnStartIndex = segments.forwardPoints.length - 1;
    const maneuvers: RouteManeuver[] = [
      {
        pointIndex: 0,
        type: ManeuverType.START,
        instruction: 'Dönüşlü rotaya başla',
        suffix: 'start',
      },
      {
        pointIndex: Math.min(returnStartIndex, interpolatedPoints.length - 1),
        type: ManeuverType.RETURN,
        instruction: 'Geri dönüş rotasına gir',
        suffix: 'return',
      },
      {
        pointIndex: interpolatedPoints.length - 1,
        type: ManeuverType.ARRIVE,
        instruction: 'Başlangıç noktasına döndün',
        suffix: 'end',
      },
    ];

    return {
      id: checksum,
      points: interpolatedPoints,
      distanceMeters: segments.totalDistanceMeters,
      estimatedDurationSeconds: duration,
      isRoundTrip: true,
      origin,
      destination: targetEnd,
      bounds,
      checksum,
      maneuvers,
      segments: {
        forward: segments.forwardPoints,
        return: segments.returnPoints,
      },
    };
  }
}

/**
 * Rota planlama servisi factory'si
 */
export function getPlanningStrategy(planType: PlanType): IPlanningStrategy {
  if (planType === 'roundTrip') {
    return new RoundTripStrategy();
  }
  return new OneWayStrategy();
}
