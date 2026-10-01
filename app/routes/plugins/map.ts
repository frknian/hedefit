/**
 * Harita eklentisi (Map Plugin).
 * Harita nesnesinin (map instance) tekil yönetimini sağlar,
 * gereksiz yeniden oluşturmaları (re-initialization) engeller.
 */

export interface MapViewportBounds {
  southWest: { latitude: number; longitude: number };
  northEast: { latitude: number; longitude: number };
}

export interface MapInstance {
  id: string;
  container: HTMLElement | string;
  zoom: number;
  center: { latitude: number; longitude: number };
  isDestroyed: boolean;
  fitBounds(bounds: MapViewportBounds, padding?: number): void;
  setCenter(center: { latitude: number; longitude: number }, zoom?: number): void;
  clearOverlays(): void;
  renderPolyline(points: Array<{ latitude: number; longitude: number }>, options?: Record<string, unknown>): void;
  renderMarker(point: { latitude: number; longitude: number }, label?: string, options?: Record<string, unknown>): void;
}

class MapManager {
  private instance: MapInstance | null = null;
  private isInitializing: boolean = false;

  public async initializeMap(
    container: HTMLElement | string,
    initialCenter: { latitude: number; longitude: number } = { latitude: 41.0082, longitude: 28.9784 },
    initialZoom: number = 14
  ): Promise<MapInstance> {
    if (this.instance && !this.instance.isDestroyed) {
      // Zaten var olan harita örneğini koru (gereksiz re-initialization'ı önle)
      return this.instance;
    }

    if (this.isInitializing) {
      // Initialization devam ederken bekle
      while (this.isInitializing) {
        await new Promise((res) => setTimeout(res, 50));
      }
      if (this.instance && !this.instance.isDestroyed) {
        return this.instance;
      }
    }

    this.isInitializing = true;

    try {
      const polylines: Array<{ points: Array<{ latitude: number; longitude: number }>; options?: Record<string, unknown> }> = [];
      const markers: Array<{ point: { latitude: number; longitude: number }; label?: string; options?: Record<string, unknown> }> = [];

      const mapObj: MapInstance = {
        id: `map_${Date.now()}`,
        container,
        zoom: initialZoom,
        center: { ...initialCenter },
        isDestroyed: false,
        fitBounds(bounds: MapViewportBounds, padding = 30) {
          this.center = {
            latitude: (bounds.southWest.latitude + bounds.northEast.latitude) / 2,
            longitude: (bounds.southWest.longitude + bounds.northEast.longitude) / 2,
          };
          // bounds aralığına göre yaklaşık zoom hesapla
          const latDiff = Math.abs(bounds.northEast.latitude - bounds.southWest.latitude);
          const lngDiff = Math.abs(bounds.northEast.longitude - bounds.southWest.longitude);
          const maxDiff = Math.max(latDiff, lngDiff);
          if (maxDiff < 0.01) this.zoom = 16;
          else if (maxDiff < 0.05) this.zoom = 14;
          else if (maxDiff < 0.15) this.zoom = 13;
          else this.zoom = 11;
        },
        setCenter(center, zoom) {
          this.center = { ...center };
          if (zoom !== undefined) this.zoom = zoom;
        },
        clearOverlays() {
          polylines.length = 0;
          markers.length = 0;
        },
        renderPolyline(points, options) {
          polylines.push({ points, options });
        },
        renderMarker(point, label, options) {
          markers.push({ point, label, options });
        },
      };

      this.instance = mapObj;
      return mapObj;
    } finally {
      this.isInitializing = false;
    }
  }

  public getInstance(): MapInstance | null {
    if (this.instance && !this.instance.isDestroyed) {
      return this.instance;
    }
    return null;
  }

  public destroy(): void {
    if (this.instance) {
      this.instance.isDestroyed = true;
      this.instance.clearOverlays();
      this.instance = null;
    }
  }
}

export const mapManager = new MapManager();
