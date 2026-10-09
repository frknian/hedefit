import type { PlanTier } from "../entitlements.ts";

/**
 * Play Console'daki abonelik ürünleri → uygulama planı. Fiyatlar Play Console'da
 * tanımlanır; aşağıdaki sabitler yalnız referanstır ve oradakiyle AYNI olmalıdır:
 * Plus ₺99/ay veya ₺849/yıl (7 gün ücretsiz deneme), Premium ₺169/ay veya ₺1.449/yıl.
 * Ücretsiz deneme Play'de Plus base plan'larına tanımlı bir "offer"dır; deneme
 * süresince abonelik ACTIVE döner ve plan açılır.
 */
export const PLAY_PRODUCT_TIERS: Record<string, Exclude<PlanTier, "free">> = {
  hedefit_plus: "plus",
  hedefit_premium: "pro",
};

export const PLAY_PRICES_TRY_MONTHLY = { plus: 99, pro: 169 } as const;
export const PLAY_PRICES_TRY_YEARLY = { plus: 849, pro: 1449 } as const;
export const PLAY_FREE_TRIAL_DAYS = { plus: 7, pro: 0 } as const;

export type PlaySubscriptionState = "active" | "grace" | "canceled" | "on_hold" | "paused" | "expired" | "pending";

export type GoogleSubscriptionV2 = {
  subscriptionState?: string;
  acknowledgementState?: string;
  linkedPurchaseToken?: string;
  testPurchase?: unknown;
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  lineItems?: Array<{
    productId?: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
    offerDetails?: { basePlanId?: string };
  }>;
};

const STATE_MAP: Record<string, PlaySubscriptionState> = {
  SUBSCRIPTION_STATE_ACTIVE: "active",
  SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "grace",
  SUBSCRIPTION_STATE_CANCELED: "canceled",
  SUBSCRIPTION_STATE_ON_HOLD: "on_hold",
  SUBSCRIPTION_STATE_PAUSED: "paused",
  SUBSCRIPTION_STATE_EXPIRED: "expired",
  SUBSCRIPTION_STATE_PENDING: "pending",
};

export type InterpretedSubscription = {
  productId: string;
  tier: Exclude<PlanTier, "free">;
  state: PlaySubscriptionState;
  expiresAt: string | null;
  basePlanId: string | null;
  autoRenewing: boolean;
  linkedToken: string | null;
  isTest: boolean;
  needsAcknowledge: boolean;
  entitled: boolean;
};

/** Play'in obfuscatedExternalAccountId alanı: istemci ve sunucu aynı değeri üretir (64 hex, sha256). */
export async function obfuscatedAccountId(userId: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`hedefit:${userId}`));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export class BillingVerificationError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

/** Google yanıtını doğrular ve plan kararına çevirir. Ağ erişimi yok; saf fonksiyon. */
export async function interpretSubscription(
  response: GoogleSubscriptionV2,
  requestedProductId: string,
  userId: string,
  now = Date.now(),
): Promise<InterpretedSubscription> {
  const item = response.lineItems?.find((line) => line.productId === requestedProductId && line.productId in PLAY_PRODUCT_TIERS);
  if (!item?.productId) throw new BillingVerificationError("unknown_product", "Satın alma bu ürünle eşleşmiyor.");

  const state = STATE_MAP[response.subscriptionState ?? ""];
  if (!state) throw new BillingVerificationError("unknown_state", "Abonelik durumu tanınmıyor.");

  const accountId = response.externalAccountIdentifiers?.obfuscatedExternalAccountId;
  if (!accountId || accountId !== (await obfuscatedAccountId(userId))) {
    throw new BillingVerificationError("account_mismatch", "Satın alma bu hesaba ait değil.");
  }

  const expiresAt = item.expiryTime ? new Date(item.expiryTime).toISOString() : null;
  const entitled =
    (state === "active" || state === "grace" || state === "canceled") && expiresAt !== null && Date.parse(expiresAt) > now;

  return {
    productId: item.productId,
    tier: PLAY_PRODUCT_TIERS[item.productId],
    state,
    expiresAt,
    basePlanId: item.offerDetails?.basePlanId ?? null,
    autoRenewing: item.autoRenewingPlan?.autoRenewEnabled === true,
    linkedToken: response.linkedPurchaseToken || null,
    isTest: response.testPurchase !== undefined && response.testPurchase !== null,
    needsAcknowledge: response.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
    entitled,
  };
}

// ---- Google Play Developer API ------------------------------------------------

const API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";

type ServiceAccount = { client_email: string; private_key: string };

export class PlayApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function base64Url(input: ArrayBuffer | string) {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function signJwt(account: ServiceAccount, nowSeconds: number) {
  const pem = account.private_key.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (char) => char.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({ iss: account.client_email, scope: SCOPE, aud: TOKEN_URL, iat: nowSeconds, exp: nowSeconds + 3600 }));
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  return `${header}.${claims}.${base64Url(signature)}`;
}

let cachedToken: { value: string; expiresAt: number } | null = null;

export function resetPlayTokenCache() {
  cachedToken = null;
}

function readServiceAccount(): ServiceAccount | null {
  const raw = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT;
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<ServiceAccount>;
    return parsed.client_email && parsed.private_key ? { client_email: parsed.client_email, private_key: parsed.private_key } : null;
  } catch {
    return null;
  }
}

export function playBillingConfigured() {
  return readServiceAccount() !== null;
}

function packageName() {
  return process.env.GOOGLE_PLAY_PACKAGE_NAME || "com.hedefit.app";
}

async function accessToken() {
  const now = Date.now();
  if (cachedToken && cachedToken.expiresAt - 60_000 > now) return cachedToken.value;
  const account = readServiceAccount();
  if (!account) throw new PlayApiError(503, "Play Billing yapılandırılmamış.");
  const assertion = await signJwt(account, Math.floor(now / 1000));
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new PlayApiError(502, "Google erişim jetonu alınamadı.");
  const data = (await response.json()) as { access_token?: string; expires_in?: number };
  if (!data.access_token) throw new PlayApiError(502, "Google erişim jetonu alınamadı.");
  cachedToken = { value: data.access_token, expiresAt: now + (data.expires_in ?? 3600) * 1000 };
  return cachedToken.value;
}

export async function fetchPlaySubscription(purchaseToken: string): Promise<GoogleSubscriptionV2> {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(packageName())}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`, {
    headers: { Authorization: `Bearer ${await accessToken()}` },
  });
  if (response.status === 400 || response.status === 404 || response.status === 410) throw new PlayApiError(404, "Satın alma bulunamadı.");
  if (!response.ok) throw new PlayApiError(502, "Google Play doğrulaması başarısız.");
  return (await response.json()) as GoogleSubscriptionV2;
}

export async function acknowledgePlaySubscription(productId: string, purchaseToken: string) {
  const response = await fetch(
    `${API_BASE}/${encodeURIComponent(packageName())}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`,
    { method: "POST", headers: { Authorization: `Bearer ${await accessToken()}`, "Content-Type": "application/json" }, body: "{}" },
  );
  if (!response.ok) throw new PlayApiError(502, "Satın alma onaylanamadı.");
}
