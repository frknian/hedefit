type Bucket = { count: number; resetAt: number };

const buckets = new Map<string, Bucket>();
const MAX_TRACKED_KEYS = 10_000;
// Süpürme sonrası TAM MAX_TRACKED_KEYS'e kadar kırpmak sıfır boşluk bırakır:
// sürekli yeni anahtar gelen bir yükte bir sonraki tek ekleme haritayı hemen
// tekrar sınırın üstüne çıkarıp O(n) süpürmeyi neredeyse HER çağrıda yeniden
// tetikler. Bunun yerine biraz daha aşağıya (%90) kırpıp bu tetiklemenin
// nadir/amortize bir olay kalmasını sağlıyoruz.
const EVICTION_WATERMARK = Math.floor(MAX_TRACKED_KEYS * 0.9);

function sweepExpired(now: number) {
  for (const [key, bucket] of buckets) {
    if (now >= bucket.resetAt) buckets.delete(key);
  }
}

/**
 * Süpürme sadece SÜRESİ DOLMUŞ kovaları temizler; sürekli farklı anahtarla
 * (ör. rotasyonlu IP'lerle) gelen bir yük altında hepsi hâlâ aktif olabilir
 * ve süpürme haritayı hiç küçültmez — MAX_TRACKED_KEYS o zaman yalnızca bir
 * tetikleyiciydi, gerçek bir üst sınır değildi. Süpürmeden sonra hâlâ sınırın
 * üstündeyse Map ekleme sırasını korur; ondan yararlanıp EN ESKİ kovaları
 * sınıra dönene kadar atarız. Bu, o birkaç kullanıcının sayacını erken
 * sıfırlayabilir ama sınırsız bellek büyümesine karşı zorunlu bir taviz —
 * hız sınırlayıcının kendisi bellek tükenmesine yol açmamalı.
 */
function enforceMaxTrackedKeys(now: number) {
  if (buckets.size <= MAX_TRACKED_KEYS) return;
  sweepExpired(now);
  if (buckets.size <= MAX_TRACKED_KEYS) return;
  // EVICTION_WATERMARK'a (MAX_TRACKED_KEYS'in altına) kadar kırpar, tam
  // sınıra değil — bu boşluk, sonraki eklemelerin süpürmeyi hemen yeniden
  // tetiklemesini önler.
  const overflow = buckets.size - EVICTION_WATERMARK;
  const oldestKeys = buckets.keys();
  for (let index = 0; index < overflow; index += 1) {
    const next = oldestKeys.next();
    if (next.done) break;
    buckets.delete(next.value);
  }
}

/**
 * Basit sabit pencereli hız sınırlayıcı (OWASP A04 – Insecure Design: sınırsız kaynak tüketimi).
 * Not: sayaç süreç/isolate belleğinde tutulur; çok örnekli dağıtımda üst sınır örnek başına uygulanır.
 * Bu, kötüye kullanımı ve maliyet patlamasını tek başına bitirmez ama önemli ölçüde yavaşlatır.
 */
export function rateLimit(key: string, limit: number, windowMs: number): { ok: boolean; retryAfterSeconds: number } {
  const now = Date.now();
  enforceMaxTrackedKeys(now);

  const bucket = buckets.get(key);
  if (!bucket || now >= bucket.resetAt) {
    buckets.set(key, { count: 1, resetAt: now + windowMs });
    return { ok: true, retryAfterSeconds: 0 };
  }

  bucket.count += 1;
  if (bucket.count > limit) {
    return { ok: false, retryAfterSeconds: Math.max(1, Math.ceil((bucket.resetAt - now) / 1000)) };
  }
  return { ok: true, retryAfterSeconds: 0 };
}

export function tooManyRequests(retryAfterSeconds: number) {
  return Response.json(
    { error: "Çok fazla istek gönderdin. Kısa bir süre sonra tekrar dene." },
    { status: 429, headers: { "Retry-After": String(retryAfterSeconds) } },
  );
}

/** Yalnız testler için: haritanın güncel boyutu. Üretim kodunda kullanılmaz. */
export function __testOnlyTrackedKeyCount(): number {
  return buckets.size;
}

/** Kimliği doğrulanmamış uç noktalar için istemci ayrımı; proxy başlıklarına güvenilmez, yalnızca kabaca ayırır. */
export function clientKey(request: Request) {
  return request.headers.get("cf-connecting-ip")
    || request.headers.get("x-real-ip")
    || (request.headers.get("x-forwarded-for") || "").split(",")[0].trim()
    || "anonim";
}
