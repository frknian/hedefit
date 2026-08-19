"use client";

import { useEffect, useRef } from "react";
import type * as maplibregl from "maplibre-gl";
import type { LatLng } from "@/lib/polyline";

// maplibre-gl yalnızca tip olarak statik içe aktarılır (yukarıdaki `import
// type`, derleme sonrası tamamen silinir). Gerçek modül aşağıda useEffect
// içinde dinamik import() ile yüklenir; aksi halde Next'in RSC/SSR modül
// grafiği bu istemci-yalnızca kütüphaneyi sunucu tarafında statik olarak
// tarayıp değerlendirmeye çalışıyor ve `window`/WebGL'e bağımlı kodu
// çalıştırınca dev sunucusunu (workerd) sonsuza dek askıda bırakıyordu.
let maplibreModulePromise: Promise<typeof maplibregl> | null = null;
function loadMaplibre() {
  if (!maplibreModulePromise) {
    maplibreModulePromise = Promise.all([
      import("maplibre-gl"),
      import("maplibre-gl/dist/maplibre-gl.css"),
    ]).then(([mod]) => mod);
  }
  return maplibreModulePromise;
}

// Ücretsiz, API anahtarı gerektirmeyen OSM raster tile'ları. Vektör
// stil/glyph barındırma karmaşıklığına şimdilik gerek yok — bkz. plan
// dokümanındaki gerekçe (Faz 1: raster, ihtiyaç olursa sonra vektöre geçilir).
const RASTER_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: "raster",
      tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      tileSize: 256,
      attribution: "© OpenStreetMap katkıda bulunanlar",
    },
  },
  layers: [{ id: "osm", type: "raster", source: "osm" }],
};

const ROUTE_SOURCE_ID = "hedefit-rota-route";
const ROUTE_LAYER_ID = "hedefit-rota-route-line";
const POSITION_SOURCE_ID = "hedefit-rota-position";
const POSITION_LAYER_ID = "hedefit-rota-position-dot";

function toGeoJsonLine(points: LatLng[]): GeoJSON.Feature<GeoJSON.LineString> {
  return {
    type: "Feature",
    properties: {},
    geometry: { type: "LineString", coordinates: points.map((p) => [p.lng, p.lat]) },
  };
}

function toGeoJsonPoint(point: LatLng | null): GeoJSON.FeatureCollection<GeoJSON.Point> {
  return {
    type: "FeatureCollection",
    features: point ? [{ type: "Feature", properties: {}, geometry: { type: "Point", coordinates: [point.lng, point.lat] } }] : [],
  };
}

export type GpsMapViewProps = {
  route: LatLng[];
  currentPosition?: LatLng | null;
  interactive?: boolean;
  className?: string;
};

/** Canlı takip, rota detayı ve önizlemede paylaşılan MapLibre yaşam döngüsü sarmalayıcısı. */
export function GpsMapView({ route, currentPosition = null, interactive = true, className }: GpsMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const readyRef = useRef(false);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    let cancelled = false;
    let map: maplibregl.Map | null = null;
    const initialCenter: [number, number] = route.length ? [route[route.length - 1].lng, route[route.length - 1].lat] : [29.0, 41.0];

    void loadMaplibre().then((maplibregl) => {
      if (cancelled || !containerRef.current) return;
      map = new maplibregl.Map({
        container: containerRef.current,
        style: RASTER_STYLE,
        center: initialCenter,
        zoom: route.length ? 15 : 5,
        interactive,
        attributionControl: interactive ? {} : false,
      });
      mapRef.current = map;
      map.on("load", () => {
        if (!map) return;
        readyRef.current = true;
        map.addSource(ROUTE_SOURCE_ID, { type: "geojson", data: toGeoJsonLine(route) });
        map.addLayer({ id: ROUTE_LAYER_ID, type: "line", source: ROUTE_SOURCE_ID, paint: { "line-color": "#7d9a2c", "line-width": 4 }, layout: { "line-cap": "round", "line-join": "round" } });
        map.addSource(POSITION_SOURCE_ID, { type: "geojson", data: toGeoJsonPoint(currentPosition) });
        map.addLayer({ id: POSITION_LAYER_ID, type: "circle", source: POSITION_SOURCE_ID, paint: { "circle-radius": 7, "circle-color": "#bfe94a", "circle-stroke-width": 2, "circle-stroke-color": "#41501a" } });
        if (route.length > 1) {
          const bounds = route.reduce((box, p) => box.extend([p.lng, p.lat]), new maplibregl.LngLatBounds(route[0] ? [route[0].lng, route[0].lat] : initialCenter, route[0] ? [route[0].lng, route[0].lat] : initialCenter));
          map.fitBounds(bounds, { padding: 32, maxZoom: 17, duration: 0 });
        }
      });
    });

    return () => {
      cancelled = true;
      map?.remove();
      mapRef.current = null;
      readyRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !readyRef.current) return;
    const routeSource = map.getSource(ROUTE_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    routeSource?.setData(toGeoJsonLine(route));
    const positionSource = map.getSource(POSITION_SOURCE_ID) as maplibregl.GeoJSONSource | undefined;
    positionSource?.setData(toGeoJsonPoint(currentPosition));
    if (currentPosition) map.easeTo({ center: [currentPosition.lng, currentPosition.lat], duration: 300 });
  }, [route, currentPosition]);

  return <div ref={containerRef} className={className || "gps-map-view"} />;
}
