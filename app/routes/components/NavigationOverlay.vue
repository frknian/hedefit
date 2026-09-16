<template>
  <div class="navigation-overlay">
    <!-- Üst Navigasyon Banner (Aktif Manevra / Dönüş Yönlendirmesi) -->
    <div class="turn-banner" :class="[activeSuffixClass]">
      <div class="turn-icon">
        <span v-if="activeManeuver?.suffix === 'return'">🔄</span>
        <span v-else-if="activeManeuver?.suffix === 'start'">🏁</span>
        <span v-else-if="activeManeuver?.suffix === 'end'">🎯</span>
        <span v-else>⬆️</span>
      </div>
      <div class="turn-details">
        <div class="turn-instruction">
          {{ activeManeuver?.instruction || 'Rotayı takip et' }}
        </div>
        <div v-if="activeManeuver?.suffix" class="turn-suffix-badge">
          {{ getSuffixBadgeText(activeManeuver.suffix) }}
        </div>
      </div>
    </div>

    <!-- Marker Bilgi Kartı: Başlangıç/Bitiş tekilleştirilmiş UI -->
    <div class="marker-info-card">
      <div v-if="isSingleMarkerMode" class="marker-pill single-marker">
        <span class="marker-icon">📍</span>
        <div class="marker-labels">
          <span class="main-label">Başlangıç / Bitiş</span>
          <span class="sub-label">Dönüşlü rota başlangıç konumunda sonlanacaktır</span>
        </div>
      </div>
      <div v-else class="marker-dual">
        <div class="marker-pill start-pill">
          <span class="marker-icon">🟢</span>
          <span class="main-label">Başlangıç (A)</span>
        </div>
        <div class="marker-pill end-pill">
          <span class="marker-icon">🔴</span>
          <span class="main-label">Hedef (B)</span>
        </div>
      </div>
    </div>

    <!-- Alt İlerleme ve Bitir Çubuğu -->
    <div class="bottom-controls">
      <div class="stats-row">
        <div class="stat-item">
          <span class="stat-label">Kalan Mesafe</span>
          <span class="stat-value">{{ (progress.remainingMeters / 1000).toFixed(2) }} km</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">Geçen Yol</span>
          <span class="stat-value">{{ (progress.traveledMeters / 1000).toFixed(2) }} km</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">Rota Tipi</span>
          <span class="stat-value highlight">{{ route.isRoundTrip ? 'Dönüşlü (A→B→A)' : 'Tek Yön (A→B)' }}</span>
        </div>
      </div>

      <button class="stop-nav-btn" @click="$emit('close')">
        Navigasyonu Bitir
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue';
import { PlannedRouteResult, RouteManeuver } from '../modules/routePlanning.ts';
import { geoDistanceMeters } from '../utils/routeAlgorithms.ts';
import { prepareNavigation, updateNavigationProgress, NavigationState } from '../modules/navigation.ts';

const props = defineProps<{
  route: PlannedRouteResult;
}>();

defineEmits<{
  (e: 'close'): void;
}>();

const navigationState = ref<NavigationState>(prepareNavigation(props.route));

// Başlangıç ve bitiş aynı mı kontrolü (Tek marker kuralı)
const isSingleMarkerMode = computed(() => {
  if (props.route.isRoundTrip) return true;
  const dist = geoDistanceMeters(props.route.origin, props.route.destination);
  return dist < 15.0;
});

const activeManeuver = computed<RouteManeuver | null>(() => {
  return navigationState.value.activeManeuver;
});

const activeSuffixClass = computed(() => {
  const suffix = activeManeuver.value?.suffix;
  if (suffix === 'return') return 'banner-return';
  if (suffix === 'start') return 'banner-start';
  if (suffix === 'end') return 'banner-end';
  return 'banner-default';
});

const getSuffixBadgeText = (suffix: string) => {
  switch (suffix) {
    case 'return':
      return 'Dönüş Segmenti';
    case 'start':
      return 'Başlangıç';
    case 'end':
      return 'Varış';
    default:
      return suffix;
  }
};

const progress = computed(() => ({
  remainingMeters: navigationState.value.remainingMeters,
  traveledMeters: navigationState.value.traveledMeters,
}));

let intervalId: any = null;

onMounted(() => {
  // Simüle edilmiş geodesic ilerleme (gerçek cihazda GPS listener ile beslenir)
  let simulatedIndex = 0;
  intervalId = setInterval(() => {
    if (simulatedIndex < props.route.points.length - 1) {
      simulatedIndex++;
      const currentPoint = props.route.points[simulatedIndex];
      navigationState.value = updateNavigationProgress(
        navigationState.value,
        props.route,
        currentPoint
      );
    }
  }, 2000);
});

onUnmounted(() => {
  if (intervalId) clearInterval(intervalId);
});
</script>

<style scoped>
.navigation-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px;
  z-index: 20;
}

.turn-banner {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  background-color: rgba(17, 24, 39, 0.95);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #374151;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.banner-return {
  border-left: 5px solid #06b6d4;
}

.banner-start {
  border-left: 5px solid #10b981;
}

.banner-end {
  border-left: 5px solid #ef4444;
}

.turn-icon {
  font-size: 28px;
}

.turn-instruction {
  font-size: 16px;
  font-weight: 700;
  color: #f9fafb;
}

.turn-suffix-badge {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  background: #1e293b;
  color: #38bdf8;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.marker-info-card {
  pointer-events: auto;
  align-self: center;
}

.marker-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: rgba(15, 23, 42, 0.9);
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid #334155;
}

.single-marker {
  border-color: #10b981;
}

.main-label {
  font-size: 13px;
  font-weight: 700;
  color: #ffffff;
}

.sub-label {
  display: block;
  font-size: 11px;
  color: #94a3b8;
}

.marker-dual {
  display: flex;
  gap: 8px;
}

.start-pill {
  border-color: #10b981;
}

.end-pill {
  border-color: #ef4444;
}

.bottom-controls {
  pointer-events: auto;
  background-color: rgba(17, 24, 39, 0.95);
  border-radius: 16px;
  padding: 16px;
  border: 1px solid #374151;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.5);
}

.stats-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-label {
  font-size: 11px;
  color: #9ca3af;
}

.stat-value {
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
}

.stat-value.highlight {
  color: #38bdf8;
}

.stop-nav-btn {
  width: 100%;
  padding: 12px;
  background-color: #dc2626;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-weight: 700;
  font-size: 15px;
  cursor: pointer;
}
</style>
