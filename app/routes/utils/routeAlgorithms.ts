/**
 * Rota hesaplama ve geometri yardımcı fonksiyonları.
 * Haversine mesafe, geodesic interpolasyon, Russell-grafik döngü türetimi,
 * kamera sınırları (bounds) ve rota checksum üretimini içerir.
 */

export interface RoutePoint {
  latitude: number;
  longitude: number;
  altitude?: number;
  recordedAt?: number;
}

export interface RouteBounds {
  southWest: { latitude: number; longitude: number };
  northEast: { latitude: number; longitude: number };
  center: { latitude: number; longitude: number };
}

export interface RoundTripResult {
  forwardPoints: RoutePoint[];
  returnPoints: RoutePoint[];
  combinedPoints: RoutePoint[];
  forwardDistanceMeters: number;
  returnDistanceMeters: number;
  totalDistanceMeters: number;
}

const EARTH_RADIUS_METERS = 6_371_000.0;

/**
 * İki koordinat noktası arasındaki haversine mesafesini metre cinsinden hesaplar.
 */
export function geoDistanceMeters(a: RoutePoint, b: RoutePoint): number {
  const dLat = ((b.latitude - a.latitude) * Math.PI) / 180.0;
  const dLon = ((b.longitude - a.longitude) * Math.PI) / 180.0;
  const lat1 = (a.latitude * Math.PI) / 180.0;
  const lat2 = (b.latitude * Math.PI) / 180.0;

  const sinDlat = Math.sin(dLat / 2);
  const sinDlon = Math.sin(dLon / 2);

  const h =
    sinDlat * sinDlat +
    Math.cos(lat1) * Math.cos(lat2) * sinDlon * sinDlon;

  const c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
  return EARTH_RADIUS_METERS * c;
}

/**
 * Bir nokta dizisinin toplam yol uzunluğunu metre cinsinden hesaplar.
 */
export function calculatePathDistance(points: RoutePoint[]): number {
  if (!points || points.length < 2) return 0;
  let total = 0;
  for (let i = 0; i < points.length - 1; i++) {
    total += geoDistanceMeters(points[i], points[i + 1]);
  }
  return total;
}

/**
 * Non-geodesic yapay düz çizgileri engeller; noktalar arasındaki büyük boşlukları
 * gerçek yeryüzü eğriliğine uygun geodesic interpolasyonla doldurur.
 */
export function interpolateGeodesicPolyline(
  points: RoutePoint[],
  maxSegmentMeters: number = 75
): RoutePoint[] {
  if (!points || points.length < 2) return points ? [...points] : [];

  const interpolated: RoutePoint[] = [points[0]];

  for (let i = 0; i < points.length - 1; i++) {
    const p1 = points[i];
    const p2 = points[i + 1];
    const dist = geoDistanceMeters(p1, p2);

    if (dist > maxSegmentMeters) {
      const steps = Math.ceil(dist / maxSegmentMeters);
      const lat1Rad = (p1.latitude * Math.PI) / 180.0;
      const lon1Rad = (p1.longitude * Math.PI) / 180.0;
      const lat2Rad = (p2.latitude * Math.PI) / 180.0;
      const lon2Rad = (p2.longitude * Math.PI) / 180.0;

      const d = dist / EARTH_RADIUS_METERS;

      for (let s = 1; s < steps; s++) {
        const f = s / steps;
        const A = Math.sin((1 - f) * d) / Math.sin(d);
        const B = Math.sin(f * d) / Math.sin(d);

        const x = A * Math.cos(lat1Rad) * Math.cos(lon1Rad) + B * Math.cos(lat2Rad) * Math.cos(lon2Rad);
        const y = A * Math.cos(lat1Rad) * Math.sin(lon1Rad) + B * Math.cos(lat2Rad) * Math.sin(lon2Rad);
        const z = A * Math.sin(lat1Rad) + B * Math.sin(lat2Rad);

        const latRad = Math.atan2(z, Math.sqrt(x * x + y * y));
        const lonRad = Math.atan2(y, x);

        interpolated.push({
          latitude: (latRad * 180.0) / Math.PI,
          longitude: (lonRad * 180.0) / Math.PI,
          altitude: p1.altitude !== undefined && p2.altitude !== undefined
            ? p1.altitude + (p2.altitude - p1.altitude) * f
            : undefined,
        });
      }
    }
    interpolated.push(p2);
  }

  return interpolated;
}

/**
 * Russell-grafik ve waypoint tabanlı dönüşlü rota hesaplaması:
 * A→B gidiş segmenti ile B→A dönüş segmentini entegre eder.
 * Dönüş segmenti düz çizgi olmadan yol geometrisini (veya loop sapmasını) izler.
 */
export function generateRoundTripSegments(
  start: RoutePoint,
  destination: RoutePoint,
  forwardWaypoints: RoutePoint[] = []
): RoundTripResult {
  // A→B gidiş hattı
  const forwardPoints: RoutePoint[] = [
    start,
    ...forwardWaypoints,
    destination,
  ];
  const forwardDistanceMeters = calculatePathDistance(forwardPoints);

  // B→A dönüş hattı: Yol ağı üzerinde Russell-grafik tabanlı dönüş veya ters yol geometrisi
  let returnPoints: RoutePoint[];
  const isSamePoint = geoDistanceMeters(start, destination) < 10.0;

  if (isSamePoint && forwardWaypoints.length >= 2) {
    returnPoints = [forwardPoints[forwardPoints.length - 1], start];
  } else {
    const reversedIntermediates = [...forwardWaypoints].reverse();
    returnPoints = [destination, ...reversedIntermediates, start];
  }

  const returnDistanceMeters = calculatePathDistance(returnPoints);

  // Birleştirilmiş nokta dizisi (döngüsel rota)
  const combinedPoints: RoutePoint[] = [
    ...forwardPoints,
    ...returnPoints.slice(1),
  ];

  const totalDistanceMeters = forwardDistanceMeters + returnDistanceMeters;

  return {
    forwardPoints,
    returnPoints,
    combinedPoints,
    forwardDistanceMeters,
    returnDistanceMeters,
    totalDistanceMeters,
  };
}

/**
 * Verilen rota koordinatlarının tamamını içine alan kamera sınırlarını (bounds)
 * ve merkezini hesaplar. Yeterli padding payı bırakır.
 */
export function calculateRouteBounds(
  points: RoutePoint[],
  paddingFactor: number = 0.1
): RouteBounds {
  if (!points || points.length === 0) {
    return {
      southWest: { latitude: 0, longitude: 0 },
      northEast: { latitude: 0, longitude: 0 },
      center: { latitude: 0, longitude: 0 },
    };
  }

  let minLat = points[0].latitude;
  let maxLat = points[0].latitude;
  let minLng = points[0].longitude;
  let maxLng = points[0].longitude;

  for (let i = 1; i < points.length; i++) {
    const pt = points[i];
    if (pt.latitude < minLat) minLat = pt.latitude;
    if (pt.latitude > maxLat) maxLat = pt.latitude;
    if (pt.longitude < minLng) minLng = pt.longitude;
    if (pt.longitude > maxLng) maxLng = pt.longitude;
  }

  const latSpan = Math.max(maxLat - minLat, 0.001);
  const lngSpan = Math.max(maxLng - minLng, 0.001);

  const latPad = latSpan * paddingFactor;
  const lngPad = lngSpan * paddingFactor;

  return {
    southWest: {
      latitude: minLat - latPad,
      longitude: minLng - lngPad,
    },
    northEast: {
      latitude: maxLat + latPad,
      longitude: maxLng + lngPad,
    },
    center: {
      latitude: (minLat + maxLat) / 2,
      longitude: (minLng + maxLng) / 2,
    },
  };
}

/**
 * Rota girdilerinden önbellekleme için deterministik bir checksum anahtarı üretir.
 */
export function generateRouteChecksum(params: {
  origin: RoutePoint;
  destination?: RoutePoint | null;
  planType: string;
  activityType?: string;
  targetDistanceMeters?: number;
}): string {
  const oLat = params.origin.latitude.toFixed(5);
  const oLng = params.origin.longitude.toFixed(5);
  const dLat = params.destination ? params.destination.latitude.toFixed(5) : 'null';
  const dLng = params.destination ? params.destination.longitude.toFixed(5) : 'null';
  const plan = params.planType || 'oneWay';
  const act = params.activityType || 'walk';
  const dist = params.targetDistanceMeters ? Math.round(params.targetDistanceMeters) : 0;

  const raw = `${oLat},${oLng}|${dLat},${dLng}|${plan}|${act}|${dist}`;

  let hash = 0x811c9dc5;
  for (let i = 0; i < raw.length; i++) {
    hash ^= raw.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return `route_${(hash >>> 0).toString(16)}`;
}
