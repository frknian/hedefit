import { formatDistanceKm, formatDuration, formatPace } from "./activity-format.ts";
import { decodePolyline, type LatLng } from "./polyline.ts";

// Aktivite sonrası paylaşılan görsel: üstte kat edilen rota, altta koyu bir
// şeritte aktivite adı ve sayılar. Sosyal ağların hikâye biçimine (9:16)
// göre üretilir; kare/yatay ağlar da bunu sorunsuz kırpar.
//
// Yalnızca tarayıcıda çalışır (canvas gerekir). Saf biçimlendirme
// fonksiyonları lib/activity-format.ts'te ayrı durur ve test edilir.

export const SHARE_CARD_WIDTH = 1080;
export const SHARE_CARD_HEIGHT = 1920;

const INK = "#f4f5ef";
const MUTED = "#a8ad9b";
const BACKDROP = "#16180f";
const ROUTE_GREEN = "#5fbf3f";

export type ShareCardInput = {
  title: string;
  distanceKm: number;
  durationMs: number;
  /** Haritanın PNG data URL'i. Yoksa rota düz bir zemine çizilir. */
  mapDataUrl?: string | null;
  /** Harita alınamadığında yedek çizim için rota noktaları. */
  route?: LatLng[];
  encodedPolyline?: string | null;
  labels: { pace: string; time: string; distance: string };
};

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("harita görseli yüklenemedi"));
    image.src = src;
  });
}

/** Görseli kırpmadan taşırarak alanı tamamen doldurur (CSS object-fit: cover). */
function drawCover(ctx: CanvasRenderingContext2D, image: HTMLImageElement, width: number, height: number) {
  const scale = Math.max(width / image.width, height / image.height);
  const drawWidth = image.width * scale;
  const drawHeight = image.height * scale;
  ctx.drawImage(image, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight);
}

/** Harita yoksa rotayı zemine çizer: paylaşım yine de anlamlı bir şey gösterir. */
function drawRouteFallback(ctx: CanvasRenderingContext2D, route: LatLng[], width: number, height: number) {
  ctx.fillStyle = "#20241a";
  ctx.fillRect(0, 0, width, height);
  if (route.length < 2) return;

  const lats = route.map((point) => point.lat);
  const lngs = route.map((point) => point.lng);
  const minLat = Math.min(...lats);
  const maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs);
  const maxLng = Math.max(...lngs);
  const padding = width * 0.12;
  const scale = Math.min(
    (width - padding * 2) / Math.max(maxLng - minLng, 1e-6),
    (height - padding * 2) / Math.max(maxLat - minLat, 1e-6),
  );
  const offsetX = (width - (maxLng - minLng) * scale) / 2;
  const offsetY = (height - (maxLat - minLat) * scale) / 2;

  ctx.beginPath();
  route.forEach((point, index) => {
    const x = offsetX + (point.lng - minLng) * scale;
    const y = height - (offsetY + (point.lat - minLat) * scale);
    if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  ctx.strokeStyle = "#ffffff";
  ctx.lineWidth = 22;
  ctx.globalAlpha = 0.85;
  ctx.stroke();
  ctx.globalAlpha = 1;
  ctx.strokeStyle = ROUTE_GREEN;
  ctx.lineWidth = 14;
  ctx.stroke();
}

function drawStat(ctx: CanvasRenderingContext2D, label: string, value: string, x: number, y: number) {
  ctx.fillStyle = MUTED;
  ctx.font = "600 34px system-ui, -apple-system, Segoe UI, sans-serif";
  ctx.fillText(label, x, y);
  ctx.fillStyle = INK;
  ctx.font = "700 78px system-ui, -apple-system, Segoe UI, sans-serif";
  ctx.fillText(value, x, y + 84);
}

/** Paylaşıma hazır PNG üretir. */
export async function renderShareCard(input: ShareCardInput): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = SHARE_CARD_WIDTH;
  canvas.height = SHARE_CARD_HEIGHT;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("canvas kullanılamıyor");

  ctx.fillStyle = BACKDROP;
  ctx.fillRect(0, 0, SHARE_CARD_WIDTH, SHARE_CARD_HEIGHT);

  let drewMap = false;
  if (input.mapDataUrl) {
    try {
      drawCover(ctx, await loadImage(input.mapDataUrl), SHARE_CARD_WIDTH, SHARE_CARD_HEIGHT);
      drewMap = true;
    } catch {
      drewMap = false;
    }
  }
  if (!drewMap) {
    const route = input.route?.length ? input.route : input.encodedPolyline ? decodePolyline(input.encodedPolyline) : [];
    drawRouteFallback(ctx, route, SHARE_CARD_WIDTH, SHARE_CARD_HEIGHT);
  }

  // Alt yarıya inen karartma: sayılar açık renkli döşemelerin üstünde de okunur.
  const fade = ctx.createLinearGradient(0, SHARE_CARD_HEIGHT * 0.34, 0, SHARE_CARD_HEIGHT);
  fade.addColorStop(0, "rgba(22,24,15,0)");
  fade.addColorStop(0.45, "rgba(22,24,15,0.82)");
  fade.addColorStop(1, "rgba(22,24,15,0.98)");
  ctx.fillStyle = fade;
  ctx.fillRect(0, 0, SHARE_CARD_WIDTH, SHARE_CARD_HEIGHT);

  const left = 96;
  ctx.textBaseline = "alphabetic";

  ctx.fillStyle = INK;
  ctx.font = "700 72px system-ui, -apple-system, Segoe UI, sans-serif";
  ctx.fillText(input.title, left, 1330);

  drawStat(ctx, input.labels.pace, formatPace(input.distanceKm, input.durationMs), left, 1440);
  drawStat(ctx, input.labels.time, formatDuration(input.durationMs), left + 470, 1440);
  drawStat(ctx, input.labels.distance, formatDistanceKm(input.distanceKm), left, 1620);

  // Marka: "Hedefit" — f harfi uygulamanın lime vurgusuyla.
  ctx.font = "800 54px system-ui, -apple-system, Segoe UI, sans-serif";
  const brandY = 1800;
  const hede = "Hede";
  const f = "f";
  const it = "it";
  const totalWidth = ctx.measureText(hede + f + it).width;
  let cursor = SHARE_CARD_WIDTH - left - totalWidth;
  ctx.fillStyle = INK;
  ctx.fillText(hede, cursor, brandY);
  cursor += ctx.measureText(hede).width;
  ctx.fillStyle = "#bfe94a";
  ctx.fillText(f, cursor, brandY);
  cursor += ctx.measureText(f).width;
  ctx.fillStyle = INK;
  ctx.fillText(it, cursor, brandY);

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("görsel üretilemedi"))), "image/png");
  });
}
