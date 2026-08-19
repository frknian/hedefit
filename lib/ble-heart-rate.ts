// Standart Bluetooth Heart Rate Service (0x180D) / Heart Rate Measurement
// characteristic (0x2A37) desteği. Format Bluetooth SIG spesifikasyonunda
// tanımlıdır ve tüm göğüs bandı/bilekten ölçüm cihazlarında aynıdır:
//   byte 0        = flags (bit 0: değer formatı, uint8 ya da uint16)
//   byte 1(-2)    = kalp atış hızı (bpm)
// Enerji harcaması / RR-interval alanları v1 kapsamında okunmaz.
export const HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb";
export const HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID = "00002a37-0000-1000-8000-00805f9b34fb";

export function parseHeartRateMeasurement(value: DataView | Uint8Array): number {
  const bytes = value instanceof DataView ? new Uint8Array(value.buffer, value.byteOffset, value.byteLength) : value;
  if (bytes.length < 2) throw new Error("Geçersiz nabız verisi");
  const flags = bytes[0];
  const isUint16 = (flags & 0x01) === 1;
  if (isUint16) {
    if (bytes.length < 3) throw new Error("Geçersiz nabız verisi");
    return bytes[1] | (bytes[2] << 8);
  }
  return bytes[1];
}

// Cihaz tarama/bağlanma katmanı — yalnızca native platformda çalışır (BLE
// donanımı web önizlemesinde yok). @capacitor-community/bluetooth-le'nin
// kurulumu maliyetli olduğundan (initialize() bir kez çağrılmalı) dinamik
// olarak içe aktarılır; bu dosyanın saf parser fonksiyonu (yukarıda) her
// ortamda bağımsız test edilebilir kalır.
import { isNativeApp } from "./mobile.ts";

export type HeartRateMonitor = {
  deviceId: string;
  deviceName: string | null;
  disconnect: () => Promise<void>;
};

let bleInitialized = false;

async function ensureBleReady() {
  const { BleClient } = await import("@capacitor-community/bluetooth-le");
  if (!bleInitialized) {
    await BleClient.initialize({ androidNeverForLocation: true });
    bleInitialized = true;
  }
  return BleClient;
}

/**
 * Kullanıcıya standart Heart Rate Service (0x180D) yayınlayan cihazları
 * listeleyen sistem seçim diyaloğunu açar, seçileni bağlar ve canlı nabız
 * bildirimlerine abone olur. Web'de/BLE desteklenmeyen ortamda null döner.
 */
export async function connectHeartRateMonitor(
  onHeartRate: (bpm: number) => void,
  onDisconnect?: () => void,
): Promise<HeartRateMonitor | null> {
  if (!isNativeApp()) return null;
  const BleClient = await ensureBleReady();
  const device = await BleClient.requestDevice({ services: [HEART_RATE_SERVICE_UUID] });
  await BleClient.connect(device.deviceId, () => onDisconnect?.());
  await BleClient.startNotifications(device.deviceId, HEART_RATE_SERVICE_UUID, HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID, (value) => {
    try { onHeartRate(parseHeartRateMeasurement(value)); } catch { /* geçersiz paket, yok say */ }
  });
  return {
    deviceId: device.deviceId,
    deviceName: device.name ?? null,
    disconnect: async () => {
      await BleClient.stopNotifications(device.deviceId, HEART_RATE_SERVICE_UUID, HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID).catch(() => undefined);
      await BleClient.disconnect(device.deviceId).catch(() => undefined);
    },
  };
}
