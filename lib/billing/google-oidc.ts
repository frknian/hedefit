// Pub/Sub push isteklerinin Google tarafından imzalanmış OIDC jetonunu doğrular.
// Yalnız imza + iss + aud + exp + beklenen servis hesabı e-postası; ağ erişimi
// yalnız Google'ın açık anahtar listesi (JWKS) içindir.

const JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const ISSUERS = new Set(["https://accounts.google.com", "accounts.google.com"]);
const JWKS_TTL_MS = 3_600_000;
const CLOCK_SKEW_SECONDS = 60;

type Jwk = JsonWebKey & { kid?: string };
let jwksCache: { keys: Jwk[]; fetchedAt: number } | null = null;

export function resetGoogleJwksCache() {
  jwksCache = null;
}

function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return Uint8Array.from(atob(padded), (char) => char.charCodeAt(0)) as Uint8Array<ArrayBuffer>;
}

function parseSegment<T>(segment: string): T | null {
  try {
    return JSON.parse(new TextDecoder().decode(base64UrlToBytes(segment))) as T;
  } catch {
    return null;
  }
}

async function loadJwks(force: boolean, now: number): Promise<Jwk[]> {
  if (!force && jwksCache && now - jwksCache.fetchedAt < JWKS_TTL_MS) return jwksCache.keys;
  const response = await fetch(JWKS_URL);
  if (!response.ok) throw new Error("jwks_unavailable");
  const body = (await response.json()) as { keys?: Jwk[] };
  jwksCache = { keys: body.keys ?? [], fetchedAt: now };
  return jwksCache.keys;
}

export type OidcExpectation = { audience: string; email: string };

/** Geçerliyse true. JWKS alınamazsa fırlatır (çağıran 5xx döner, Pub/Sub yeniden dener). */
export async function verifyGoogleOidcToken(token: string, expected: OidcExpectation, now = Date.now()): Promise<boolean> {
  const parts = token.split(".");
  if (parts.length !== 3) return false;
  const header = parseSegment<{ alg?: string; kid?: string }>(parts[0]);
  const claims = parseSegment<{ iss?: string; aud?: string; email?: string; email_verified?: boolean; exp?: number; iat?: number }>(parts[1]);
  if (!header || !claims || header.alg !== "RS256" || !header.kid) return false;

  let jwk = (await loadJwks(false, now)).find((key) => key.kid === header.kid);
  // Anahtar dönmüş olabilir: bilinmeyen kid için JWKS'i bir kez yenile.
  if (!jwk) jwk = (await loadJwks(true, now)).find((key) => key.kid === header.kid);
  if (!jwk) return false;

  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    base64UrlToBytes(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
  );
  if (!valid) return false;

  const nowSeconds = Math.floor(now / 1000);
  if (!claims.iss || !ISSUERS.has(claims.iss)) return false;
  if (claims.aud !== expected.audience) return false;
  if (claims.email_verified !== true || claims.email?.toLowerCase() !== expected.email.toLowerCase()) return false;
  if (typeof claims.exp !== "number" || claims.exp + CLOCK_SKEW_SECONDS < nowSeconds) return false;
  if (typeof claims.iat === "number" && claims.iat - CLOCK_SKEW_SECONDS > nowSeconds) return false;
  return true;
}
