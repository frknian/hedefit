import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import test from "node:test";
import { authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";
import { interpretSubscription, obfuscatedAccountId, resetPlayTokenCache, BillingVerificationError } from "../lib/billing/play.ts";

let nextUserId = 100;
const freshUserId = () => `00000000-0000-4000-8000-${String(++nextUserId).padStart(12, "0")}`;
const FUTURE = new Date(Date.now() + 20 * 86_400_000).toISOString();
const PAST = new Date(Date.now() - 86_400_000).toISOString();
const TOKEN = "play-purchase-token-abcdefghijklmnop";

function googleResponse(userAccountId, overrides = {}) {
  return {
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    externalAccountIdentifiers: { obfuscatedExternalAccountId: userAccountId },
    lineItems: [{ productId: "hedefit_plus", expiryTime: FUTURE, autoRenewingPlan: { autoRenewEnabled: true }, offerDetails: { basePlanId: "monthly" } }],
    ...overrides,
  };
}

function withBillingEnv() {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048, privateKeyEncoding: { type: "pkcs8", format: "pem" }, publicKeyEncoding: { type: "spki", format: "pem" } });
  const previous = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT;
  process.env.GOOGLE_PLAY_SERVICE_ACCOUNT = JSON.stringify({ client_email: "svc@test.iam.gserviceaccount.com", private_key: privateKey });
  resetPlayTokenCache();
  return () => {
    if (previous === undefined) delete process.env.GOOGLE_PLAY_SERVICE_ACCOUNT; else process.env.GOOGLE_PLAY_SERVICE_ACCOUNT = previous;
    resetPlayTokenCache();
  };
}

async function runVerify({ userId = freshUserId(), body, google, rpc, isGuest = false, configured = true }) {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreBilling = configured ? withBillingEnv() : () => { delete process.env.GOOGLE_PLAY_SERVICE_ACCOUNT; };
  if (!configured) delete process.env.GOOGLE_PLAY_SERVICE_ACCOUNT;
  const previousFetch = globalThis.fetch;
  const calls = { acknowledge: 0, rpc: [], googleGet: 0 };
  globalThis.fetch = withAuthenticatedFetch(async (url, init) => {
    const href = String(url);
    if (href.includes("oauth2.googleapis.com/token")) return Response.json({ access_token: "g-token", expires_in: 3600 });
    if (href.includes("purchases/subscriptionsv2/tokens/")) {
      calls.googleGet += 1;
      const value = typeof google === "function" ? await google(userId) : google;
      return value instanceof Response ? value : Response.json(value);
    }
    if (href.includes(":acknowledge")) { calls.acknowledge += 1; return Response.json({}); }
    if (href.includes("/rpc/apply_play_subscription")) {
      const args = JSON.parse(String(init.body));
      calls.rpc.push(args);
      return rpc ? rpc(args) : Response.json(args.p_tier);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  if (isGuest) {
    const inner = globalThis.fetch;
    globalThis.fetch = async (url, init) => {
      if (String(url).includes("/auth/v1/user")) {
        return Response.json({ id: userId, aud: "authenticated", role: "authenticated", is_anonymous: true, app_metadata: {}, user_metadata: {}, created_at: "2026-01-01T00:00:00.000Z" });
      }
      return inner(url, init);
    };
  }
  try {
    const { POST } = await import(`../app/api/billing/verify/route.ts?test=${Date.now()}${Math.random()}`);
    const response = await POST(authorizedRequest("http://localhost/api/billing/verify", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body ?? { productId: "hedefit_plus", purchaseToken: TOKEN }),
    }));
    return { response, calls, json: await response.json() };
  } finally {
    globalThis.fetch = previousFetch;
    restoreBilling();
    restoreEnv();
  }
}

// ---- interpretSubscription (saf mantık) -------------------------------------

test("interpretSubscription: aktif abonelik yetki verir ve plana eşlenir", async () => {
  const userId = freshUserId();
  const result = await interpretSubscription(googleResponse(await obfuscatedAccountId(userId)), "hedefit_plus", userId);
  assert.equal(result.tier, "plus");
  assert.equal(result.state, "active");
  assert.equal(result.entitled, true);
  assert.equal(result.needsAcknowledge, true);
  assert.equal(result.basePlanId, "monthly");
});

test("interpretSubscription: premium ürün 'pro' planına eşlenir", async () => {
  const userId = freshUserId();
  const response = googleResponse(await obfuscatedAccountId(userId), { lineItems: [{ productId: "hedefit_premium", expiryTime: FUTURE }] });
  assert.equal((await interpretSubscription(response, "hedefit_premium", userId)).tier, "pro");
});

test("interpretSubscription: iptal edilmiş ama süresi bitmemiş abonelik erişimi korur, süresi dolan kaybeder", async () => {
  const userId = freshUserId();
  const id = await obfuscatedAccountId(userId);
  const canceled = await interpretSubscription(googleResponse(id, { subscriptionState: "SUBSCRIPTION_STATE_CANCELED" }), "hedefit_plus", userId);
  assert.equal(canceled.entitled, true);
  const lapsed = await interpretSubscription(googleResponse(id, { subscriptionState: "SUBSCRIPTION_STATE_CANCELED", lineItems: [{ productId: "hedefit_plus", expiryTime: PAST }] }), "hedefit_plus", userId);
  assert.equal(lapsed.entitled, false);
});

test("interpretSubscription: askıda, bekletilen ve süresi dolan abonelik yetki vermez", async () => {
  const userId = freshUserId();
  const id = await obfuscatedAccountId(userId);
  for (const state of ["SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING"]) {
    assert.equal((await interpretSubscription(googleResponse(id, { subscriptionState: state }), "hedefit_plus", userId)).entitled, false, state);
  }
});

test("interpretSubscription: grace period yetkiyi sürdürür", async () => {
  const userId = freshUserId();
  const result = await interpretSubscription(googleResponse(await obfuscatedAccountId(userId), { subscriptionState: "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" }), "hedefit_plus", userId);
  assert.equal(result.state, "grace");
  assert.equal(result.entitled, true);
});

test("interpretSubscription: başka kullanıcıya bağlı (obfuscatedAccountId uyuşmuyor) satın alma reddedilir", async () => {
  const response = googleResponse(await obfuscatedAccountId(freshUserId()));
  await assert.rejects(() => interpretSubscription(response, "hedefit_plus", freshUserId()), (error) => error instanceof BillingVerificationError && error.code === "account_mismatch");
});

test("interpretSubscription: hesap kimliği hiç yoksa reddedilir", async () => {
  const response = googleResponse(undefined, { externalAccountIdentifiers: undefined });
  await assert.rejects(() => interpretSubscription(response, "hedefit_plus", freshUserId()), (error) => error.code === "account_mismatch");
});

test("interpretSubscription: istenen ürün yanıtta yoksa veya bilinmiyorsa reddedilir", async () => {
  const userId = freshUserId();
  const response = googleResponse(await obfuscatedAccountId(userId));
  await assert.rejects(() => interpretSubscription(response, "hedefit_premium", userId), (error) => error.code === "unknown_product");
  await assert.rejects(() => interpretSubscription(response, "baska_urun", userId), (error) => error.code === "unknown_product");
});

test("interpretSubscription: bilinmeyen abonelik durumu güvenli tarafta reddedilir", async () => {
  const userId = freshUserId();
  const response = googleResponse(await obfuscatedAccountId(userId), { subscriptionState: "SUBSCRIPTION_STATE_UNSPECIFIED" });
  await assert.rejects(
    () => interpretSubscription(response, "hedefit_plus", userId),
    (error) => error.code === "unknown_state",
  );
});

// ---- POST /api/billing/verify -----------------------------------------------

test("billing/verify: normal durum — plan yazılır, satın alma onaylanır", async () => {
  const userId = freshUserId();
  const { response, json, calls } = await runVerify({ userId, google: async (id) => googleResponse(await obfuscatedAccountId(id)) });
  assert.equal(response.status, 200);
  assert.equal(json.plan_tier, "plus");
  assert.equal(json.entitled, true);
  assert.equal(calls.rpc.length, 1);
  assert.equal(calls.rpc[0].p_user, userId);
  assert.equal(calls.rpc[0].p_tier, "plus");
  assert.equal(calls.acknowledge, 1);
});

test("billing/verify: zaten onaylanmış satın alma yeniden onaylanmaz", async () => {
  const { response, calls } = await runVerify({ google: async (id) => googleResponse(await obfuscatedAccountId(id), { acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" }) });
  assert.equal(response.status, 200);
  assert.equal(calls.acknowledge, 0);
});

test("billing/verify: istemci plan alanı gönderse de yok sayılır, sunucu Google'ın ürününü kullanır", async () => {
  const { json, calls } = await runVerify({
    body: { productId: "hedefit_plus", purchaseToken: TOKEN, plan_tier: "pro", tier: "pro" },
    google: async (id) => googleResponse(await obfuscatedAccountId(id)),
  });
  assert.equal(calls.rpc[0].p_tier, "plus");
  assert.equal(json.plan_tier, "plus");
});

test("billing/verify: hatalı input — bilinmeyen ürün ve geçersiz jeton Google'a gitmeden 400", async () => {
  const unknown = await runVerify({ body: { productId: "hedefit_free", purchaseToken: TOKEN }, google: {} });
  assert.equal(unknown.response.status, 400);
  assert.equal(unknown.calls.googleGet, 0);
  const short = await runVerify({ body: { productId: "hedefit_plus", purchaseToken: "x" }, google: {} });
  assert.equal(short.response.status, 400);
  const huge = await runVerify({ body: { productId: "hedefit_plus", purchaseToken: "x".repeat(5000) }, google: {} });
  assert.equal(huge.response.status, 400);
  const notString = await runVerify({ body: { productId: "hedefit_plus", purchaseToken: 12345678901234 }, google: {} });
  assert.equal(notString.response.status, 400);
});

test("billing/verify: misafir kullanıcı satın alma doğrulayamaz", async () => {
  const { response, calls } = await runVerify({ isGuest: true, google: {} });
  assert.equal(response.status, 403);
  assert.equal(calls.googleGet, 0);
});

test("billing/verify: kimliksiz istek reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  try {
    const { POST } = await import(`../app/api/billing/verify/route.ts?test=${Date.now()}`);
    const response = await POST(new Request("http://localhost/api/billing/verify", { method: "POST", body: "{}" }));
    assert.equal(response.status, 401);
  } finally {
    restoreEnv();
  }
});

test("billing/verify: Google token'ı tanımıyorsa (404) plan yazılmaz", async () => {
  const { response, calls } = await runVerify({ google: new Response("{}", { status: 404 }) });
  assert.equal(response.status, 400);
  assert.equal(calls.rpc.length, 0);
});

test("billing/verify: Google geçici hata verirse 502, plan yazılmaz", async () => {
  const { response, calls } = await runVerify({ google: new Response("{}", { status: 503 }) });
  assert.equal(response.status, 502);
  assert.equal(calls.rpc.length, 0);
});

test("billing/verify: başka kullanıcının hesap kimliğiyle gelen jeton 409, plan yazılmaz", async () => {
  const { response, calls } = await runVerify({ google: async () => googleResponse(await obfuscatedAccountId(freshUserId())) });
  assert.equal(response.status, 409);
  assert.equal(calls.rpc.length, 0);
});

test("billing/verify: jeton veritabanında başka kullanıcıya bağlıysa 409", async () => {
  const { response, calls } = await runVerify({
    google: async (id) => googleResponse(await obfuscatedAccountId(id)),
    rpc: () => Response.json({ code: "P0001", message: "token_owned_by_other_user" }, { status: 400 }),
  });
  assert.equal(response.status, 409);
  assert.equal(calls.acknowledge, 0, "reddedilen satın alma onaylanmamalı");
});

test("billing/verify: bekleyen ödeme 202 döner, yetki verilmez ve onaylanmaz", async () => {
  const { response, json, calls } = await runVerify({ google: async (id) => googleResponse(await obfuscatedAccountId(id), { subscriptionState: "SUBSCRIPTION_STATE_PENDING" }) });
  assert.equal(response.status, 202);
  assert.equal(json.status, "pending");
  assert.equal(calls.rpc.length, 0);
  assert.equal(calls.acknowledge, 0);
});

test("billing/verify: süresi dolmuş abonelik kaydedilir ama onaylanmaz ve yetki vermez", async () => {
  const { response, json, calls } = await runVerify({
    google: async (id) => googleResponse(await obfuscatedAccountId(id), { subscriptionState: "SUBSCRIPTION_STATE_EXPIRED", lineItems: [{ productId: "hedefit_plus", expiryTime: PAST }] }),
    rpc: () => Response.json("free"),
  });
  assert.equal(response.status, 200);
  assert.equal(json.entitled, false);
  assert.equal(json.plan_tier, "free");
  assert.equal(calls.acknowledge, 0);
});

test("billing/verify: onay (acknowledge) başarısız olsa da kullanıcı planını alır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreBilling = withBillingEnv();
  const previousFetch = globalThis.fetch;
  const userId = freshUserId();
  globalThis.fetch = withAuthenticatedFetch(async (url, init) => {
    const href = String(url);
    if (href.includes("oauth2.googleapis.com/token")) return Response.json({ access_token: "g", expires_in: 3600 });
    if (href.includes("subscriptionsv2")) return Response.json(googleResponse(await obfuscatedAccountId(userId)));
    if (href.includes(":acknowledge")) return new Response("{}", { status: 500 });
    if (href.includes("/rpc/apply_play_subscription")) return Response.json("plus");
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { POST } = await import(`../app/api/billing/verify/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/billing/verify", { method: "POST", body: JSON.stringify({ productId: "hedefit_plus", purchaseToken: TOKEN }) }));
    assert.equal(response.status, 200);
    assert.equal((await response.json()).plan_tier, "plus");
  } finally {
    globalThis.fetch = previousFetch;
    restoreBilling();
    restoreEnv();
  }
});

test("billing/verify: servis hesabı yoksa 503 (kapalı tarafa düşer)", async () => {
  const { response } = await runVerify({ configured: false, google: {} });
  assert.equal(response.status, 503);
});

test("billing/verify: aşırı istek hız sınırına takılır", async () => {
  const userId = freshUserId();
  let last;
  for (let attempt = 0; attempt < 21; attempt += 1) {
    last = await runVerify({ userId, body: { productId: "hedefit_free", purchaseToken: TOKEN }, google: {} });
  }
  assert.equal(last.response.status, 429);
});
