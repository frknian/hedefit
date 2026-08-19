export type LatLng = { lat: number; lng: number };

// Google'ın klasik polyline encoding algoritması (5 ondalık hassasiyet).
// Saf fonksiyonlardır; hiçbir plugin/DB bağımlılığı yoktur, bu yüzden
// bağımsız olarak test edilebilir. Bkz. db/migrations/20260819_gps_activity_routes.sql
// (rota depolama kararının gerekçesi).
const PRECISION = 1e5;

function encodeSignedNumber(num: number): string {
  let sgn_num = num << 1;
  if (num < 0) sgn_num = ~sgn_num;
  let result = "";
  while (sgn_num >= 0x20) {
    result += String.fromCharCode((0x20 | (sgn_num & 0x1f)) + 63);
    sgn_num >>= 5;
  }
  result += String.fromCharCode(sgn_num + 63);
  return result;
}

export function encodePolyline(points: LatLng[]): string {
  let result = "";
  let prevLat = 0;
  let prevLng = 0;
  for (const point of points) {
    const lat = Math.round(point.lat * PRECISION);
    const lng = Math.round(point.lng * PRECISION);
    result += encodeSignedNumber(lat - prevLat);
    result += encodeSignedNumber(lng - prevLng);
    prevLat = lat;
    prevLng = lng;
  }
  return result;
}

export function decodePolyline(encoded: string): LatLng[] {
  const points: LatLng[] = [];
  let index = 0;
  let lat = 0;
  let lng = 0;
  const length = encoded.length;

  while (index < length) {
    let result = 0;
    let shift = 0;
    let byte: number;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    lat += result & 1 ? ~(result >> 1) : result >> 1;

    result = 0;
    shift = 0;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    lng += result & 1 ? ~(result >> 1) : result >> 1;

    points.push({ lat: lat / PRECISION, lng: lng / PRECISION });
  }

  return points;
}

const EARTH_RADIUS_M = 6371000;

export function haversineDistanceMeters(a: LatLng, b: LatLng): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

export function routeDistanceKm(points: LatLng[]): number {
  let meters = 0;
  for (let i = 1; i < points.length; i++) {
    meters += haversineDistanceMeters(points[i - 1], points[i]);
  }
  return meters / 1000;
}
