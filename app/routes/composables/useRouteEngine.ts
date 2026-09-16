/**
 * Rota Motoru Composable (useRouteEngine).
 * Rota tipine göre ('oneWay' vs 'roundTrip') dinamik yönlendirme,
 * B→A dönüş hesaplaması, toplam mesafe entegrasyonu ve checksum tabanlı önbellek yönetimi.
 */

import {
  type RoutePlanRequest,
  type PlannedRouteResult,
  getPlanningStrategy,
  type RoutePoint,
} from '../modules/routePlanning.ts';
import { routeStore } from '../stores/routeState.ts';
import { generateRouteChecksum } from '../utils/routeAlgorithms.ts';

export function useRouteEngine() {
  const store = routeStore;

  const calculateRoute = async (
    origin: RoutePoint,
    request: RoutePlanRequest,
    destination?: RoutePoint | null,
    fetchPathFn?: (from: RoutePoint, to: RoutePoint) => Promise<RoutePoint[]>
  ): Promise<PlannedRouteResult> => {
    // 1. Checksum hesaplayarak önbellek kontrolü yap
    const checksum = generateRouteChecksum({
      origin,
      destination,
      planType: request.planType,
      activityType: request.activityType,
      targetDistanceMeters: request.targetDistanceMeters,
    });

    const cached = store.getCachedRoute(checksum);
    if (cached) {
      store.setPlannedRoute(cached);
      return cached;
    }

    // 2. Rota tipine uygun stratejiyi seç (OneWay veya RoundTrip)
    const strategy = getPlanningStrategy(request.planType);

    // 3. Hesaplamayı gerçekleştir
    // RoundTrip durumunda: B→A dönüş hesabı ve toplam mesafe entegrasyonu strategy içinde yapılır.
    // OneWay durumunda: Mevcut normal A→B mantığı korunur.
    const result = await strategy.plan(origin, request, destination, fetchPathFn);

    // 4. Sonucu önbelleğe al ve duruma kaydet
    store.cacheRoute(result.checksum, result);
    store.setPlannedRoute(result);

    return result;
  };

  return {
    store,
    calculateRoute,
    getCachedRoute: (checksum: string) => store.getCachedRoute(checksum),
    hasCachedRoute: (checksum: string) => store.hasCachedRoute(checksum),
  };
}
