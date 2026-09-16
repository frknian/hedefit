<template>
  <div class="route-plan-screen">
    <!-- Üst Sekmeler: Dönüşlü modda (roundTrip) gizlenir, tek yönlü modda görünür -->
    <header v-if="isHeaderTabsVisible" class="header-tabs">
      <button
        :class="['tab-btn', { active: activeTab === 'plan' }]"
        @click="activeTab = 'plan'"
      >
        Rota Planla
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'history' }]"
        @click="activeTab = 'history'"
      >
        Geçmiş Rotalar
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'favorites' }]"
        @click="activeTab = 'favorites'"
      >
        Favoriler
      </button>
    </header>

    <!-- Rota Planlama Kontrolleri -->
    <div class="planning-card">
      <div class="mode-selector">
        <button
          :class="['mode-btn', { active: routeMode === 'roundTrip' }]"
          @click="setMode('roundTrip')"
        >
          🔄 Dönüşlü Rota (A→B→A)
        </button>
        <button
          :class="['mode-btn', { active: routeMode === 'oneWay' }]"
          @click="setMode('oneWay')"
        >
          ➡️ Tek Yön (A→B)
        </button>
      </div>

      <div class="inputs-section">
        <div class="input-row">
          <span class="dot origin-dot"></span>
          <span class="label">Başlangıç:</span>
          <span class="value">{{ originLabel }}</span>
        </div>

        <div v-if="routeMode === 'oneWay'" class="input-row">
          <span class="dot dest-dot"></span>
          <span class="label">Hedef:</span>
          <span class="value">{{ destinationLabel }}</span>
        </div>
        <div v-else class="input-row roundtrip-note">
          <span class="dot loop-dot"></span>
          <span class="label">Bitiş:</span>
          <span class="value">Başlangıç ile aynı noktaya dönüş</span>
        </div>

        <div class="activity-selector">
          <label>Aktivite: </label>
          <select v-model="selectedActivity" @change="recalculate">
            <option value="Koşu">Koşu</option>
            <option value="Yürüyüş">Yürüyüş</option>
            <option value="Bisiklet">Bisiklet</option>
            <option value="Trail Koşusu">Trail Koşusu</option>
          </select>
        </div>
      </div>

      <div v-if="plannedRoute" class="route-summary">
        <div class="metric">
          <span class="metric-title">Toplam Mesafe</span>
          <span class="metric-val">{{ (plannedRoute.distanceMeters / 1000).toFixed(2) }} km</span>
        </div>
        <div class="metric">
          <span class="metric-title">Tahmini Süre</span>
          <span class="metric-val">{{ Math.round(plannedRoute.estimatedDurationSeconds / 60) }} dk</span>
        </div>
      </div>

      <div class="actions">
        <button
          class="start-route-btn"
          :disabled="isPlanning || !plannedRoute"
          @click="handleStartPlannedRoute"
        >
          <span v-if="isPlanning">Hesaplanıyor...</span>
          <span v-else>Planlı Rotayı Başlat</span>
        </button>
      </div>
    </div>

    <!-- Harita Görünümü -->
    <div class="map-wrapper">
      <RouteMapView
        :route="plannedRoute"
        :is-navigating="isNavigating"
      />
      <NavigationOverlay
        v-if="isNavigating && plannedRoute"
        :route="plannedRoute"
        @close="stopNavigation"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { routeStore } from '../stores/routeState.ts';
import { useRouteEngine } from '../composables/useRouteEngine.ts';
import { mapService } from '../services/MapService.ts';
import RouteMapView from './RouteMapView.vue';
import NavigationOverlay from './NavigationOverlay.vue';
import { PlanType, PlannedRouteResult, RoutePoint } from '../modules/routePlanning.ts';

const activeTab = ref<'plan' | 'history' | 'favorites'>('plan');
const routeMode = ref<PlanType>('roundTrip');
const selectedActivity = ref<string>('Koşu');
const isPlanning = ref<boolean>(false);
const plannedRoute = ref<PlannedRouteResult | null>(null);
const isNavigating = ref<boolean>(false);

const origin = ref<RoutePoint>({ latitude: 41.0082, longitude: 28.9784 });
const destination = ref<RoutePoint | null>(null);

const { calculateRoute } = useRouteEngine();

/**
 * Dönüşlü rota modunda (roundTrip) üst sekmelerin gizlenmesini sağlayan computed property.
 * Tek yönlü A→B modunda ise sekmeler görünür kalır.
 */
const isHeaderTabsVisible = computed(() => {
  return routeMode.value !== 'roundTrip';
});

const originLabel = computed(() => {
  return `${origin.value.latitude.toFixed(4)}, ${origin.value.longitude.toFixed(4)}`;
});

const destinationLabel = computed(() => {
  if (!destination.value) return 'Seçilmedi (Varsayılan hedef)';
  return `${destination.value.latitude.toFixed(4)}, ${destination.value.longitude.toFixed(4)}`;
});

const setMode = (mode: PlanType) => {
  routeMode.value = mode;
  routeStore.setRouteMode(mode);
  recalculate();
};

const recalculate = async () => {
  isPlanning.value = true;
  try {
    const dest = routeMode.value === 'oneWay'
      ? (destination.value || { latitude: origin.value.latitude + 0.015, longitude: origin.value.longitude + 0.015 })
      : origin.value;

    const result = await calculateRoute(origin.value, {
      activityType: selectedActivity.value,
      planType: routeMode.value,
      targetDistanceMeters: 4000,
    }, dest);

    plannedRoute.value = result;
  } finally {
    isPlanning.value = false;
  }
};

const handleStartPlannedRoute = async () => {
  if (!plannedRoute.value) return;

  // MapService üzerinden early-return destekli önbellekli harita başlatma
  await mapService.startRoute(plannedRoute.value);
  isNavigating.value = true;
};

const stopNavigation = () => {
  mapService.stopRoute();
  isNavigating.value = false;
};

onMounted(() => {
  recalculate();
});
</script>

<style scoped>
.route-plan-screen {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  position: relative;
  background-color: #090d16;
  color: #ffffff;
}

.header-tabs {
  display: flex;
  background-color: #111827;
  border-bottom: 1px solid #1f2937;
  padding: 8px 16px;
  gap: 12px;
}

.tab-btn {
  background: transparent;
  border: none;
  color: #9ca3af;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
}

.tab-btn.active {
  background-color: #1f2937;
  color: #38bdf8;
}

.planning-card {
  padding: 16px;
  background-color: #111827;
  border-bottom: 1px solid #1f2937;
  z-index: 10;
}

.mode-selector {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.mode-btn {
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #374151;
  background-color: #1f2937;
  color: #d1d5db;
  font-size: 14px;
  cursor: pointer;
}

.mode-btn.active {
  border-color: #38bdf8;
  background-color: #0369a1;
  color: #ffffff;
  font-weight: bold;
}

.inputs-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.origin-dot { background-color: #10b981; }
.dest-dot { background-color: #ef4444; }
.loop-dot { background-color: #06b6d4; }

.route-summary {
  display: flex;
  justify-content: space-around;
  padding: 12px;
  background-color: #1e293b;
  border-radius: 8px;
  margin-bottom: 12px;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.metric-title {
  font-size: 11px;
  color: #94a3b8;
}

.metric-val {
  font-size: 16px;
  font-weight: bold;
  color: #38bdf8;
}

.start-route-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #0284c7, #0369a1);
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
}

.start-route-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.map-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
}
</style>
