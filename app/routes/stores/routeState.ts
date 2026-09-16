/**
 * Rota durumu ve önbellek yönetim deposu (Route State Store).
 * Checksum tabanlı rota önbellekleme ve reaktif durum yönetimi sağlar.
 */

import type { RoutePoint, RoutePlanRequest, PlannedRouteResult, PlanType } from '../modules/routePlanning.ts';
import { generateRouteChecksum } from '../utils/routeAlgorithms.ts';

export interface RouteState {
  routeMode: PlanType;
  origin: RoutePoint | null;
  destination: RoutePoint | null;
  plannedRoute: PlannedRouteResult | null;
  activeRouteId: string | null;
  isNavigating: boolean;
  cacheChecksum: string | null;
  isMapLoaded: boolean;
  routeCache: Map<string, PlannedRouteResult>;
}

class RouteStateStore {
  private state: RouteState = {
    routeMode: 'roundTrip',
    origin: null,
    destination: null,
    plannedRoute: null,
    activeRouteId: null,
    isNavigating: false,
    cacheChecksum: null,
    isMapLoaded: false,
    routeCache: new Map<string, PlannedRouteResult>(),
  };

  private listeners = new Set<(state: RouteState) => void>();

  public getState(): Readonly<RouteState> {
    return this.state;
  }

  public subscribe(listener: (state: RouteState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    for (const listener of this.listeners) {
      listener({ ...this.state });
    }
  }

  public setRouteMode(mode: PlanType): void {
    if (this.state.routeMode !== mode) {
      this.state.routeMode = mode;
      // Mod değiştiğinde destination aynı noktaysa veya roundTrip ise senkronize et
      if (mode === 'roundTrip' && this.state.origin && !this.state.destination) {
        this.state.destination = { ...this.state.origin };
      }
      this.notify();
    }
  }

  public setOrigin(origin: RoutePoint): void {
    this.state.origin = origin;
    if (this.state.routeMode === 'roundTrip' && !this.state.destination) {
      this.state.destination = { ...origin };
    }
    this.notify();
  }

  public setDestination(destination: RoutePoint | null): void {
    this.state.destination = destination;
    this.notify();
  }

  public setPlannedRoute(route: PlannedRouteResult | null): void {
    this.state.plannedRoute = route;
    if (route) {
      this.state.cacheChecksum = route.checksum;
      this.cacheRoute(route.checksum, route);
    } else {
      this.state.cacheChecksum = null;
    }
    this.notify();
  }

  public setMapLoaded(loaded: boolean): void {
    this.state.isMapLoaded = loaded;
    this.notify();
  }

  public setNavigating(navigating: boolean, routeId: string | null = null): void {
    this.state.isNavigating = navigating;
    this.state.activeRouteId = routeId;
    this.notify();
  }

  public cacheRoute(checksum: string, route: PlannedRouteResult): void {
    this.state.routeCache.set(checksum, route);
  }

  public getCachedRoute(checksum: string): PlannedRouteResult | undefined {
    return this.state.routeCache.get(checksum);
  }

  public hasCachedRoute(checksum: string): boolean {
    return this.state.routeCache.has(checksum);
  }

  public computeChecksum(request: RoutePlanRequest): string | null {
    if (!this.state.origin) return null;
    return generateRouteChecksum({
      origin: this.state.origin,
      destination: this.state.destination,
      planType: request.planType || this.state.routeMode,
      activityType: request.activityType,
      targetDistanceMeters: request.targetDistanceMeters,
    });
  }

  public clearCache(): void {
    this.state.routeCache.clear();
    this.state.cacheChecksum = null;
    this.notify();
  }

  public reset(): void {
    this.state.plannedRoute = null;
    this.state.activeRouteId = null;
    this.state.isNavigating = false;
    this.state.cacheChecksum = null;
    this.notify();
  }
}

export const routeStore = new RouteStateStore();
