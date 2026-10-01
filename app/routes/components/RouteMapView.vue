<template>
  <div ref="mapContainerRef" class="route-map-container">
    <div id="route-map-viewport" class="map-viewport">
      <!-- SVG harita projeksiyon katmanı (Web/Mobil canvas uyumlu) -->
      <svg
        v-if="projectedPoints.length > 0"
        class="map-svg-layer"
        :viewBox="`0 0 ${viewportWidth} ${viewportHeight}`"
      >
        <!-- Geodesic Polyline: Yol geometrisini tam izler, yapay düz çizgi içermez -->
        <path
          :d="svgPathData"
          fill="none"
          stroke="#0ea5e9"
          stroke-width="5"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="route-polyline"
        />

        <!-- Başlangıç / Bitiş Marker Görselleştirmesi -->
        <!-- Dönüşlü modda (veya başlangıç === bitiş ise): Tek 'Başlangıç/Bitiş' Marker'ı -->
        <g v-if="isSingleMarker" :transform="`translate(${startPos.x}, ${startPos.y})`">
          <circle r="12" fill="#10b981" stroke="#ffffff" stroke-width="3" />
          <text y="-18" text-anchor="middle" fill="#10b981" font-size="12" font-weight="bold">
            Başlangıç / Bitiş
          </text>
        </g>

        <!-- Normal A→B Modu: Ayrı Başlangıç (A) ve Hedef (B) Marker'ları -->
        <g v-else>
          <g :transform="`translate(${startPos.x}, ${startPos.y})`">
            <circle r="10" fill="#10b981" stroke="#ffffff" stroke-width="2.5" />
            <text y="-16" text-anchor="middle" fill="#10b981" font-size="11" font-weight="bold">
              Başlangıç (A)
            </text>
          </g>
          <g :transform="`translate(${endPos.x}, ${endPos.y})`">
            <circle r="10" fill="#ef4444" stroke="#ffffff" stroke-width="2.5" />
            <text y="-16" text-anchor="middle" fill="#ef4444" font-size="11" font-weight="bold">
              Hedef (B)
            </text>
          </g>
        </g>
      </svg>

      <div v-else class="map-empty-state">
        <span>Rota yükleniyor veya seçilmedi</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { PlannedRouteResult } from '../modules/routePlanning.ts';
import { geoDistanceMeters, calculateRouteBounds, RouteBounds } from '../utils/routeAlgorithms.ts';
import { mapManager } from '../plugins/map.ts';

const props = defineProps<{
  route: PlannedRouteResult | null;
  isNavigating?: boolean;
}>();

const mapContainerRef = ref<HTMLElement | null>(null);
const viewportWidth = ref<number>(600);
const viewportHeight = ref<number>(800);

// Kamera Bounds'u: Yalnızca merkeze odaklanmak yerine tüm rotayı kapsayan bounds
const computedBounds = computed<RouteBounds>(() => {
  if (!props.route || props.route.points.length === 0) {
    return {
      southWest: { latitude: 0, longitude: 0 },
      northEast: { latitude: 0, longitude: 0 },
      center: { latitude: 0, longitude: 0 },
    };
  }
  return props.route.bounds || calculateRouteBounds(props.route.points, 0.15);
});

// Başlangıç ve bitiş tekilleştirme kontrolü
const isSingleMarker = computed(() => {
  if (!props.route) return false;
  if (props.route.isRoundTrip) return true;
  return geoDistanceMeters(props.route.origin, props.route.destination) < 15.0;
});

// Koordinatları SVG piksel projeksiyonuna dönüştürme (Mercator/Equirectangular uyarlaması)
const projectCoordinate = (lat: number, lng: number) => {
  const bounds = computedBounds.value;
  const latSpan = Math.max(bounds.northEast.latitude - bounds.southWest.latitude, 0.0001);
  const lngSpan = Math.max(bounds.northEast.longitude - bounds.southWest.longitude, 0.0001);

  const x = ((lng - bounds.southWest.longitude) / lngSpan) * (viewportWidth.value - 60) + 30;
  const y = (1 - (lat - bounds.southWest.latitude) / latSpan) * (viewportHeight.value - 80) + 40;

  return { x, y };
};

const projectedPoints = computed(() => {
  if (!props.route || !props.route.points) return [];
  return props.route.points.map((p) => projectCoordinate(p.latitude, p.longitude));
});

const startPos = computed(() => {
  if (projectedPoints.value.length === 0) return { x: 0, y: 0 };
  return projectedPoints.value[0];
});

const endPos = computed(() => {
  if (projectedPoints.value.length === 0) return { x: 0, y: 0 };
  return projectedPoints.value[projectedPoints.value.length - 1];
});

const svgPathData = computed(() => {
  const pts = projectedPoints.value;
  if (pts.length < 2) return '';
  let d = `M ${pts[0].x.toFixed(1)} ${pts[0].y.toFixed(1)}`;
  for (let i = 1; i < pts.length; i++) {
    d += ` L ${pts[i].x.toFixed(1)} ${pts[i].y.toFixed(1)}`;
  }
  return d;
});

watch(
  () => props.route,
  (newRoute) => {
    if (newRoute) {
      const map = mapManager.getInstance();
      if (map) {
        map.fitBounds({
          southWest: computedBounds.value.southWest,
          northEast: computedBounds.value.northEast,
        });
      }
    }
  },
  { immediate: true }
);

onMounted(() => {
  if (mapContainerRef.value) {
    viewportWidth.value = mapContainerRef.value.clientWidth || 600;
    viewportHeight.value = mapContainerRef.value.clientHeight || 800;
    mapManager.initializeMap(mapContainerRef.value);
  }
});
</script>

<style scoped>
.route-map-container {
  width: 100%;
  height: 100%;
  position: relative;
  background-color: #0b0f19;
}

.map-viewport {
  width: 100%;
  height: 100%;
  position: relative;
}

.map-svg-layer {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
}

.route-polyline {
  filter: drop-shadow(0 2px 6px rgba(14, 165, 233, 0.4));
}

.map-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #64748b;
  font-size: 14px;
}
</style>
