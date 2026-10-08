// Google AdMob ödüllü reklam sunucu doğrulaması (SSV).
// Belge: AdMob "Rewarded ads server-side verification". AdMob, ödül hak edildiğinde bu uca imzalı bir
// GET atar. İmza, sorgu dizesinin `&signature=` öncesindeki kısmı üzerinde ECDSA P-256 / SHA-256'dır;
// açık anahtarlar Google'ın yayınladığı listeden (key_id ile) alınır.

const KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const KEYS_TTL_MS = 86_400_000;
export const SSV_MAX_AGE_MS = 24 * 3_600_000;
export const SSV_ALLOWED_FEATURES = ["chat", "text_nutrition"] as const;
export type SsvFeature = (typeof SSV_ALLOWED_FEATURES)[number];

type VerifierKey = { keyId: number | string; base64: string };
let keysCache: { keys: VerifierKey[]; fetchedAt: number } | null = null;

export function resetSsvKeyCache() {
  keysCache = null;
}

async function loadKeys(force: boolean, now: number): Promise<VerifierKey[]> {
  if (!force && keysCache && now - keysCache.fetchedAt < KEYS_TTL_MS) return keysCache.keys;
  const response = await fetch(KEYS_URL);
  if (!response.ok) throw new Error("ssv_keys_unavailable");
  const body = (await response.json()) as { keys?: VerifierKey[] };
  keysCache = { keys: body.keys ?? [], fetchedAt: now };
  return keysCache.keys;
}

function base64ToBytes(value: string, urlSafe = false): Uint8Array<ArrayBuffer> {
  const normalized = urlSafe ? value.replace(/-/g, "+").replace(/_/g, "/") : value;
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return Uint8Array.from(atob(padded), (char) => char.charCodeAt(0)) as Uint8Array<ArrayBuffer>;
}

/** ECDSA imzası DER (SEQUENCE{INTEGER r, INTEGER s}) gelir; WebCrypto ham r||s (64 bayt) ister. */
export function derToRawEcdsa(der: Uint8Array): Uint8Array<ArrayBuffer> | null {
  if (der.length < 8 || der[0] !== 0x30) return null;
  let offset = 2;
  if (der[1] & 0x80) offset = 2 + (der[1] & 0x7f);
  const readInt = (): Uint8Array | null => {
    if (der[offset] !== 0x02) return null;
    const length = der[offset + 1];
    const value = der.slice(offset + 2, offset + 2 + length);
    offset += 2 + length;
    return value.length === length ? value : null;
  };
  const r = readInt();
  const s = readInt();
  if (!r || !s) return null;
  const raw = new Uint8Array(64);
  const place = (value: Uint8Array, at: number) => {
    let bytes = value;
    while (bytes.length > 32 && bytes[0] === 0) bytes = bytes.slice(1);
    if (bytes.length > 32) return false;
    raw.set(bytes, at + 32 - bytes.length);
    return true;
  };
  return place(r, 0) && place(s, 32) ? raw : null;
}

export type SsvParams = {
  adUnit: string | null;
  customData: string | null;
  timestampMs: number | null;
  transactionId: string | null;
  userId: string | null;
};

export type SsvResult = { valid: true; params: SsvParams } | { valid: false; reason: string };

/**
 * İsteğin AdMob'dan geldiğini ve yeni olduğunu doğrular. Anahtar listesi alınamazsa fırlatır
 * (çağıran 5xx döner; AdMob yeniden dener).
 */
export async function verifyAdMobSsv(requestUrl: string, now = Date.now()): Promise<SsvResult> {
  const url = new URL(requestUrl);
  const query = url.search.startsWith("?") ? url.search.slice(1) : url.search;
  const signatureIndex = query.indexOf("&signature=");
  if (signatureIndex < 0) return { valid: false, reason: "missing_signature" };
  const message = query.slice(0, signatureIndex);
  const signature = url.searchParams.get("signature");
  const keyId = url.searchParams.get("key_id");
  if (!signature || !keyId) return { valid: false, reason: "missing_signature" };

  const raw = derToRawEcdsa(base64ToBytes(signature, true));
  if (!raw) return { valid: false, reason: "bad_signature_format" };

  let key = (await loadKeys(false, now)).find((candidate) => String(candidate.keyId) === keyId);
  if (!key) key = (await loadKeys(true, now)).find((candidate) => String(candidate.keyId) === keyId);
  if (!key) return { valid: false, reason: "unknown_key" };

  const publicKey = await crypto.subtle.importKey("spki", base64ToBytes(key.base64), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, publicKey, raw, new TextEncoder().encode(message));
  if (!ok) return { valid: false, reason: "signature_mismatch" };

  const timestamp = Number(url.searchParams.get("timestamp"));
  const timestampMs = Number.isFinite(timestamp) && timestamp > 0 ? timestamp : null;
  // Geçerli imzalı eski bir isteğin yeniden oynatılmasını (replay) sınırla; asıl koruma transaction_id'dir.
  if (timestampMs === null || now - timestampMs > SSV_MAX_AGE_MS || timestampMs - now > 5 * 60_000) return { valid: false, reason: "stale_timestamp" };

  return {
    valid: true,
    params: {
      adUnit: url.searchParams.get("ad_unit"),
      customData: url.searchParams.get("custom_data"),
      timestampMs,
      transactionId: url.searchParams.get("transaction_id"),
      userId: url.searchParams.get("user_id"),
    },
  };
}
