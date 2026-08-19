// Harita döşemelerinin (tile) TEK kaynağı.
//
// Adres iki yerde birden gerekiyor: haritayı çizen istemci (GpsMapView) ve
// güvenlik başlıkları (lib/security-headers.ts). İkisi ayrı ayrı yazıldığında
// biri değişip diğeri unutuluyor ve sonuç sessiz bir hata oluyor: MapLibre
// döşemeleri bir web worker'ından fetch ile çeker, CSP'nin connect-src'i o
// adrese izin vermezse istekler engellenir ve harita bomboş kalır — konsolu
// açmayan kimse sebebini göremez. Bu yüzden host buradan türetiliyor.
export const MAP_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

/** OSM döşemeleri a/b/c alt alanlarından da servis edilebiliyor. */
export const MAP_TILE_CSP_ORIGINS = [
  "https://tile.openstreetmap.org",
  "https://*.tile.openstreetmap.org",
];

export const MAP_TILE_ATTRIBUTION = "© OpenStreetMap katkıda bulunanlar";
