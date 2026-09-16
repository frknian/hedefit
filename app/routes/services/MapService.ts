/**
 * Harita Servisi (MapService).
 * Harita önbellekleme, geodesic polyline çizimi, kamera sınırları ve
 * mükerrer API/harita oluşturmalarını önleyen early-return başlatma mantığı.
 */

import { mapManager, type MapInstance } from '../plugins/map.ts';
import type { PlannedRouteResult, RoutePoint } from '../modules/routePlanning.ts';
import { routeStore } from '../stores/routeState.ts';
import { geoDistanceMeters } from '../utils/routeAlgorithms.ts';

export interface RouteRenderOptions {
  strokeColor?: string;
  strokeWeight?: number;
  strokeOpacity?: number;
  geodesic?: boolean;
}

export class MapService {
  private static instance: MapService | null = null;
  private currentRenderedChecksum: string | null = null;
  private isRouteStarted: boolean = false;

  private constructor() {}

  public static getInstance(): MapService {
    if (!MapService.instance) {
      MapService.instance = new MapService();
    }
    return MapService.instance;
  }

  /**
   * Planlanan rotayı başlatır.
   * Önbellek kontrolü (early return) mekanizması:
   * Eğer rota zaten önbellekte mevcutsa veya halihazırda haritada çizilmişse,
   * yeniden harita oluşturulmaz ve gereksiz harita/API çağrıları yapılmaz.
   */
  public async startRoute(
    route: PlannedRouteResult,
    mapContainer?: HTMLElement | string
  ): Promise<{ success: boolean; fromCache: boolean; map: MapInstance }> {
    // 1. Checksum ve önbellek kontrolü (Early Return)
    if (this.currentRenderedChecksum === route.checksum && this.isRouteStarted) {
      const existingMap = mapManager.getInstance();
      if (existingMap) {
        // Zaten hazır ve çizili, doğrudan önbellekten dön
        return { success: true, fromCache: true, map: existingMap };
      }
    }

    // 2. Harita örneğini al veya başlat
    let map = mapManager.getInstance();
    if (!map) {
      map = await mapManager.initializeMap(
        mapContainer || 'map-container',
        route.origin,
        14
      );
    }

    // 3. Rotayı haritaya çiz (geodesic polyline ve birleşik marker'lar)
    this.renderRouteOnMap(map, route);

    // 4. Kamera sınırlarını (bounds) ayarla
    map.fitBounds({
      southWest: route.bounds.southWest,
      northEast: route.bounds.northEast,
    });

    // 5. Durumu güncelle ve önbelleğe işaretle
    this.currentRenderedChecksum = route.checksum;
    this.isRouteStarted = true;
    routeStore.setMapLoaded(true);
    routeStore.setNavigating(true, route.id);

    return { success: true, fromCache: false, map };
  }

  /**
   * Harita üzerine rota polyline'ı ve başlangıç/bitiş marker'larını çizer.
   */
  public renderRouteOnMap(map: MapInstance, route: PlannedRouteResult): void {
    map.clearOverlays();

    // 1. Geodesic Polyline çizimi (düz çizgiler engellenir)
    const polylineOptions: RouteRenderOptions = {
      strokeColor: '#0ea5e9', // Hedefit primary cyan/blue
      strokeWeight: 5,
      strokeOpacity: 0.9,
      geodesic: true,
    };
    map.renderPolyline(route.points, polylineOptions);

    // 2. Başlangıç / Bitiş Marker Çizimi
    const isSamePoint =
      route.isRoundTrip ||
      geoDistanceMeters(route.origin, route.destination) < 15.0;

    if (isSamePoint) {
      // Dönüşlü modda veya başlangıç === bitiş durumunda: Tek ikon ve "Başlangıç/Bitiş"
      map.renderMarker(route.origin, 'Başlangıç / Bitiş', {
        iconType: 'loop_point',
        color: '#10b981', // Yeşil
        suffix: 'start_end',
      });
    } else {
      // Normal A→B modunda ayrı A ve B marker'ları
      map.renderMarker(route.origin, 'Başlangıç (A)', {
        iconType: 'start_point',
        color: '#10b981',
        suffix: 'start',
      });
      map.renderMarker(route.destination, 'Hedef (B)', {
        iconType: 'end_point',
        color: '#ef4444', // Kırmızı
        suffix: 'end',
      });
    }
  }

  /**
   * Haritayı temizler ve durumu sıfırlar.
   */
  public stopRoute(): void {
    this.isRouteStarted = false;
    this.currentRenderedChecksum = null;
    const map = mapManager.getInstance();
    if (map) {
      map.clearOverlays();
    }
    routeStore.setNavigating(false, null);
  }

  public getCurrentRenderedChecksum(): string | null {
    return this.currentRenderedChecksum;
  }
}

export const mapService = MapService.getInstance();
