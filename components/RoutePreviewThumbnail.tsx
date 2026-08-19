"use client";

import { useMemo } from "react";
import type { LatLng } from "@/lib/polyline";

// Liste satırları için hafif SVG mini-rota önizlemesi. Aktivite günlüğünde
// aynı anda onlarca satır gösterilebileceğinden, her satıra ayrı bir
// MapLibre/WebGL örneği açmak yerine (tile isteği yok, GL context yok) saf
// bir vektör çizim tercih edildi — bkz. plan dokümanı.
export function RoutePreviewThumbnail({ route, width = 88, height = 88 }: { route: LatLng[]; width?: number; height?: number }) {
  const path = useMemo(() => {
    if (route.length < 2) return null;
    const lats = route.map((p) => p.lat);
    const lngs = route.map((p) => p.lng);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const minLng = Math.min(...lngs);
    const maxLng = Math.max(...lngs);
    const padding = 6;
    const spanLat = Math.max(maxLat - minLat, 1e-6);
    const spanLng = Math.max(maxLng - minLng, 1e-6);
    const scaleX = (width - padding * 2) / spanLng;
    const scaleY = (height - padding * 2) / spanLat;
    const scale = Math.min(scaleX, scaleY);
    const offsetX = (width - spanLng * scale) / 2;
    const offsetY = (height - spanLat * scale) / 2;
    return route
      .map((p, index) => {
        const x = offsetX + (p.lng - minLng) * scale;
        const y = height - (offsetY + (p.lat - minLat) * scale);
        return `${index === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(" ");
  }, [route, width, height]);

  return (
    <svg className="route-preview-thumb" width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Rota önizlemesi">
      <rect x={0} y={0} width={width} height={height} rx={10} className="route-preview-thumb-bg" />
      {path && <path d={path} className="route-preview-thumb-line" />}
      {!path && <circle cx={width / 2} cy={height / 2} r={3} className="route-preview-thumb-dot" />}
    </svg>
  );
}
