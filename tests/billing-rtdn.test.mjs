import assert from "node:assert/strict";
import { generateKeyPairSync, webcrypto } from "node:crypto";
import test from "node:test";
import { verifyGoogleOidcToken, resetGoogleJwksCache } from "../lib/billing/google-oidc.ts";
import { parsePubSubPush } from "../lib/billing/rtdn.ts";
import { obfuscatedAccountId, resetPlayTokenCache } from "../lib/billing/play.ts";
import { reconcileSubscriptions } from "../lib/billing/reconcile.ts";
import { createClient } from "@supabase/supabase-js";
import { withSupabaseAuthEnv } from "./helpers/auth.mjs";

const AUDIENCE = "https://hedefit.example/api/billing/rtdn";
const PUSH_EMAIL = "pubsub-push@hedefit.iam.gserviceaccount.com";
const FUTURE = new Date(Date.now() + 20 * 86_400_000).toISOString();
const TOKEN = "play-purchase-token-abcdefghijklmnop";
const USER = "00000000-0000-4000-8000-0000000000b1";

const b64url = (input) => Buffer.from(input).toString("base64url");

async function makeSigner(kid = "kid-1") {
  const pair = await webcrypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
  const jwk = { ...(await webcrypto.subtle.exportKey("jwk", pair.publicKey)), kid, alg: "RS256", use: "sig" };
  async function sign(claims = {}, header = {}) {
    const now = Math.floor(Date.now() / 1000);
    const h = b64url(JSON.stringify({ alg: "RS256", kid, typ: "JWT", ...header }));
    const p = b64url(JSON.stringify({ iss: "https://accounts.google.com", aud: AUDIENCE, email: PUSH_EMAIL, email_verified: true, iat: now, exp: now + 3600, ...claims }));
    const sig = await webcrypto.subtle.sign("RSASSA-PKCS1-v1_5", pair.privateKey, new TextEncoder().encode(`${h}.${p}`));
    return `${h}.${p}.${Buffer.from(sig).toString("base64url")}`;
  }
  return { jwk, sign };
}

function jwksFetch(...jwks) {
  const calls = { count: 0 };
  const handler = async (url) => {
    assert.match(String(url), /oauth2\/v3\/certs/);
    calls.count += 1;
    return Response.json({ keys: jwks });
  };
  return { handler, calls };
}

async function withFetch(handler, fn) {
  const previous = globalThis.fetch;
  globalThis.fetch = handler;
  resetGoogleJwksCache();
  try { return await fn(); } finally { globalThis.fetch = previous; }
}

function pushBody(payload, messageId = "msg-1") {
  return { message: { data: Buffer.from(JSON.stringify(payload)).toString("base64"), messageId }, subscription: "projects/p/subscriptions/s" };
}

// ---- parsePubSubPush -----------------------------------------------------------

test("parsePubSubPush: abonelik, iade ve test bildirimlerini ayrıştırır", () => {
  const sub = parsePubSubPush(pushBody({ packageName: "com.hedefit.app", subscriptionNotification: { notificationType: 2, purchaseToken: TOKEN, subscriptionId: "hedefit_plus" } }));
  assert.deepEqual(sub, { messageId: "msg-1", kind: "subscription", packageName: "com.hedefit.app", notificationType: "RENEWED", purchaseToken: TOKEN });
  assert.equal(parsePubSubPush(pushBody({ packageName: "p", voidedPurchaseNotification: { purchaseToken: TOKEN, productType: 1 } })).kind, "voided");
  assert.equal(parsePubSubPush(pushBody({ packageName: "p", testNotification: { version: "1.0" } })).kind, "test");
});

test("parsePubSubPush: bilinmeyen tür adı hata vermez, tek seferlik ürün iadesi yok sayılır", () => {
  assert.equal(parsePubSubPush(pushBody({ subscriptionNotification: { notificationType: 99, purchaseToken: TOKEN } })).notificationType, "TYPE_99");
  assert.equal(parsePubSubPush(pushBody({ voidedPurchaseNotification: { purchaseToken: TOKEN, productType: 2 } })).kind, "other");
  assert.equal(parsePubSubPush(pushBody({ oneTimeProductNotification: {} })).kind, "other");
});

test("parsePubSubPush: bozuk zarf/içerik null döner", () => {
  assert.equal(parsePubSubPush(null), null);
  assert.equal(parsePubSubPush({}), null);
  assert.equal(parsePubSubPush({ message: { data: "###", messageId: "m" } }), null);
  assert.equal(parsePubSubPush({ message: { data: Buffer.from("not json").toString("base64"), messageId: "m" } }), null);
  assert.equal(parsePubSubPush({ message: { data: Buffer.from("{}").toString("base64") } }), null);
});

// ---- verifyGoogleOidcToken ------------------------------------------------------

test("OIDC: geçerli Google jetonu kabul edilir", async () => {
  const signer = await makeSigner();
  const jwks = jwksFetch(signer.jwk);
  await withFetch(jwks.handler, async () => {
    assert.equal(await verifyGoogleOidcToken(await signer.sign(), { audience: AUDIENCE, email: PUSH_EMAIL }), true);
  });
});

test("OIDC: audience, e-posta, doğrulanmamış e-posta, issuer ve süre hatalarında reddedilir", async () => {
  const signer = await makeSigner();
  const jwks = jwksFetch(signer.jwk);
  const expected = { audience: AUDIENCE, email: PUSH_EMAIL };
  const now = Math.floor(Date.now() / 1000);
  await withFetch(jwks.handler, async () => {
    assert.equal(await verifyGoogleOidcToken(await signer.sign({ aud: "https://baska" }), expected), false, "aud");
    assert.equal(await verifyGoogleOidcToken(await signer.sign({ email: "saldirgan@evil.com" }), expected), false, "email");
    assert.equal(await verifyGoogleOidcToken(await signer.sign({ email_verified: false }), expected), false, "email_verified");
    assert.equal(await verifyGoogleOidcToken(await signer.sign({ iss: "https://evil.example" }), expected), false, "iss");
    assert.equal(await verifyGoogleOidcToken(await signer.sign({ exp: now - 3600 }), expected), false, "exp");
    assert.equal(await verifyGoogleOidcToken(await signer.sign({ iat: now + 3600 }), expected), false, "iat gelecekte");
    assert.equal(await verifyGoogleOidcToken(await signer.sign({}, { alg: "none" }), expected), false, "alg");
  });
});

test("OIDC: başka bir anahtarla imzalanmış (sahte) jeton reddedilir", async () => {
  const real = await makeSigner("kid-1");
  const attacker = await makeSigner("kid-1");
  await withFetch(jwksFetch(real.jwk).handler, async () => {
    assert.equal(await verifyGoogleOidcToken(await attacker.sign(), { audience: AUDIENCE, email: PUSH_EMAIL }), false);
  });
});

test("OIDC: imzası bozulmuş veya biçimsiz jeton reddedilir", async () => {
  const signer = await makeSigner();
  await withFetch(jwksFetch(signer.jwk).handler, async () => {
    const good = await signer.sign();
    const tampered = good.slice(0, -4) + (good.endsWith("AAAA") ? "BBBB" : "AAAA");
    assert.equal(await verifyGoogleOidcToken(tampered, { audience: AUDIENCE, email: PUSH_EMAIL }), false);
    assert.equal(await verifyGoogleOidcToken("a.b", { audience: AUDIENCE, email: PUSH_EMAIL }), false);
    assert.equal(await verifyGoogleOidcToken("a.b.c", { audience: AUDIENCE, email: PUSH_EMAIL }), false);
  });
});

test("OIDC: bilinmeyen kid için JWKS bir kez yenilenir; anahtar dönüşümü sorunsuz", async () => {
  const oldKey = await makeSigner("old");
  const newKey = await makeSigner("new");
  let served = [oldKey.jwk];
  const calls = { count: 0 };
  await withFetch(async () => { calls.count += 1; return Response.json({ keys: served }); }, async () => {
    assert.equal(await verifyGoogleOidcToken(await oldKey.sign(), { audience: AUDIENCE, email: PUSH_EMAIL }), true);
    served = [oldKey.jwk, newKey.jwk];
    assert.equal(await verifyGoogleOidcToken(await newKey.sign(), { audience: AUDIENCE, email: PUSH_EMAIL }), true);
    assert.equal(calls.count, 2);
    const unknown = await makeSigner("never");
    assert.equal(await verifyGoogleOidcToken(await unknown.sign(), { audience: AUDIENCE, email: PUSH_EMAIL }), false);
  });
});

test("OIDC: JWKS alınamazsa fırlatır (çağıran 5xx döner)", async () => {
  const signer = await makeSigner();
  const token = await signer.sign();
  await withFetch(async () => new Response("down", { status: 503 }), async () => {
    await assert.rejects(() => verifyGoogleOidcToken(token, { audience: AUDIENCE, email: PUSH_EMAIL }));
  });
});

// ---- POST /api/billing/rtdn -----------------------------------------------------

function rtdnEnv(overrides = {}) {
  const restoreAuth = withSupabaseAuthEnv();
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048, privateKeyEncoding: { type: "pkcs8", format: "pem" }, publicKeyEncoding: { type: "spki", format: "pem" } });
  const keys = ["GOOGLE_PLAY_SERVICE_ACCOUNT", "GOOGLE_PLAY_RTDN_AUDIENCE", "GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL", "GOOGLE_PLAY_PACKAGE_NAME"];
  const previous = Object.fromEntries(keys.map((key) => [key, process.env[key]]));
  process.env.GOOGLE_PLAY_SERVICE_ACCOUNT = JSON.stringify({ client_email: "svc@test.iam.gserviceaccount.com", private_key: privateKey });
  process.env.GOOGLE_PLAY_RTDN_AUDIENCE = AUDIENCE;
  process.env.GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL = PUSH_EMAIL;
  delete process.env.GOOGLE_PLAY_PACKAGE_NAME;
  for (const [key, value] of Object.entries(overrides)) { if (value === undefined) delete process.env[key]; else process.env[key] = value; }
  resetPlayTokenCache();
  return () => {
    for (const key of keys) { if (previous[key] === undefined) delete process.env[key]; else process.env[key] = previous[key]; }
    restoreAuth();
    resetPlayTokenCache();
  };
}

function subscriptionRow(overrides = {}) {
  return { purchase_token: TOKEN, user_id: USER, product_id: "hedefit_plus", base_plan_id: "monthly", plan_tier: "plus", expires_at: FUTURE, ...overrides };
}

async function googleSub(state = "SUBSCRIPTION_STATE_ACTIVE", extra = {}) {
  return {
    subscriptionState: state, acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    externalAccountIdentifiers: { obfuscatedExternalAccountId: await obfuscatedAccountId(USER) },
    lineItems: [{ productId: "hedefit_plus", expiryTime: FUTURE, autoRenewingPlan: { autoRenewEnabled: true }, offerDetails: { basePlanId: "monthly" } }],
    ...extra,
  };
}

async function runRtdn({ payload, messageId, token, signer, row = subscriptionRow(), google, begin = true, beginError, configured = true, headers = {} }) {
  const restore = rtdnEnv(configured ? {} : { GOOGLE_PLAY_RTDN_AUDIENCE: undefined });
  const calls = { googleGet: 0, rpc: [] };
  const jwks = jwksFetch(signer.jwk);
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const href = String(url);
    if (href.includes("oauth2/v3/certs")) return jwks.handler(url, init);
    if (href.includes("oauth2.googleapis.com/token")) return Response.json({ access_token: "g", expires_in: 3600 });
    if (href.includes("subscriptionsv2/tokens/")) { calls.googleGet += 1; const v = typeof google === "function" ? await google() : google; return v instanceof Response ? v : Response.json(v); }
    if (href.includes("/rest/v1/subscriptions")) return Response.json(row ?? null, { headers: { "Content-Type": "application/json" } });
    const rpc = href.match(/\/rpc\/(\w+)/)?.[1];
    if (rpc) {
      const args = init?.body ? JSON.parse(String(init.body)) : {};
      calls.rpc.push({ name: rpc, args });
      if (rpc === "begin_billing_event") return beginError ? Response.json({ code: "XX000", message: "boom" }, { status: 500 }) : Response.json(begin);
      if (rpc === "void_play_subscription") return Response.json(row ? "free" : null);
      if (rpc === "apply_play_subscription") return Response.json("plus");
      return Response.json(null);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  };
  resetGoogleJwksCache();
  try {
    const { POST } = await import(`../app/api/billing/rtdn/route.ts?test=${Date.now()}${Math.random()}`);
    const authHeader = token === undefined ? { Authorization: `Bearer ${await signer.sign()}` } : token ? { Authorization: `Bearer ${token}` } : {};
    const response = await POST(new Request("http://localhost/api/billing/rtdn", { method: "POST", headers: { "Content-Type": "application/json", ...authHeader, ...headers }, body: JSON.stringify(payload ?? pushBody({ packageName: "com.hedefit.app", subscriptionNotification: { notificationType: 3, purchaseToken: TOKEN } }, messageId)) }));
    return { response, calls, json: await response.json() };
  } finally {
    globalThis.fetch = previousFetch;
    restore();
  }
}

test("rtdn: jetonsuz veya sahte jetonlu istek 401, hiçbir şey yazılmaz", async () => {
  const signer = await makeSigner();
  const attacker = await makeSigner();
  const none = await runRtdn({ signer, token: "" });
  assert.equal(none.response.status, 401);
  const forged = await runRtdn({ signer, token: await attacker.sign() });
  assert.equal(forged.response.status, 401);
  const wrongAudience = await runRtdn({ signer, token: await signer.sign({ aud: "https://baska" }) });
  assert.equal(wrongAudience.response.status, 401);
  for (const result of [none, forged, wrongAudience]) assert.equal(result.calls.rpc.length, 0);
});

test("rtdn: yapılandırma eksikse 503 (kapalı tarafa düşer)", async () => {
  const signer = await makeSigner();
  const { response } = await runRtdn({ signer, configured: false });
  assert.equal(response.status, 503);
});

test("rtdn: test bildirimi 200 ile onaylanır, işlem yapılmaz", async () => {
  const signer = await makeSigner();
  const { response, calls } = await runRtdn({ signer, payload: pushBody({ packageName: "com.hedefit.app", testNotification: { version: "1.0" } }) });
  assert.equal(response.status, 200);
  assert.equal(calls.rpc.length, 0);
});

test("rtdn: bozuk mesaj ve başka paket 200 ile onaylanır (zehirli mesaj döngüsü yok)", async () => {
  const signer = await makeSigner();
  const malformed = await runRtdn({ signer, payload: { message: { data: "###", messageId: "x" } } });
  assert.equal(malformed.response.status, 200);
  const other = await runRtdn({ signer, payload: pushBody({ packageName: "com.baska.uygulama", subscriptionNotification: { notificationType: 2, purchaseToken: TOKEN } }) });
  assert.equal(other.response.status, 200);
  assert.equal(other.json.ignored, "other_package");
  assert.equal(malformed.calls.rpc.length + other.calls.rpc.length, 0);
});

test("rtdn: normal durum — Google'dan yeniden okunur, kayıt ve plan güncellenir, olay tamamlanır", async () => {
  const signer = await makeSigner();
  const { response, calls, json } = await runRtdn({ signer, messageId: "m-ok", google: await googleSub("SUBSCRIPTION_STATE_CANCELED") });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "applied");
  assert.equal(calls.googleGet, 1);
  const apply = calls.rpc.find((call) => call.name === "apply_play_subscription");
  assert.equal(apply.args.p_state, "canceled");
  assert.equal(apply.args.p_user, USER);
  assert.deepEqual(calls.rpc.map((call) => call.name), ["begin_billing_event", "apply_play_subscription", "finish_billing_event"]);
});

test("rtdn: bildirim türüne değil Google'ın gerçek durumuna güvenilir (RENEWED gelse de süresi dolmuşsa düşer)", async () => {
  const signer = await makeSigner();
  const payload = pushBody({ packageName: "com.hedefit.app", subscriptionNotification: { notificationType: 2, purchaseToken: TOKEN } });
  const { calls } = await runRtdn({ signer, payload, google: await googleSub("SUBSCRIPTION_STATE_EXPIRED") });
  assert.equal(calls.rpc.find((call) => call.name === "apply_play_subscription").args.p_state, "expired");
});

test("rtdn: tekrar teslim edilen (işlenmiş) mesaj Google'a gidilmeden atlanır", async () => {
  const signer = await makeSigner();
  const { response, calls, json } = await runRtdn({ signer, begin: false, google: await googleSub() });
  assert.equal(response.status, 200);
  assert.equal(json.duplicate, true);
  assert.equal(calls.googleGet, 0);
  assert.deepEqual(calls.rpc.map((call) => call.name), ["begin_billing_event"]);
});

test("rtdn: henüz /verify edilmemiş (bilinmeyen) jeton 200 ile onaylanır, plan yazılmaz", async () => {
  const signer = await makeSigner();
  const { response, calls, json } = await runRtdn({ signer, row: null, google: await googleSub() });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "unknown_token");
  assert.equal(calls.googleGet, 0);
  assert.ok(!calls.rpc.some((call) => call.name === "apply_play_subscription"));
  assert.ok(calls.rpc.some((call) => call.name === "finish_billing_event"));
});

test("rtdn: Google geçici hata verirse 500, olay tamamlanmaz (Pub/Sub yeniden dener)", async () => {
  const signer = await makeSigner();
  const { response, calls } = await runRtdn({ signer, google: new Response("{}", { status: 503 }) });
  assert.equal(response.status, 500);
  assert.ok(!calls.rpc.some((call) => call.name === "finish_billing_event"));
  assert.ok(!calls.rpc.some((call) => call.name === "apply_play_subscription"));
});

test("rtdn: veritabanı hatası (begin) 500 döner", async () => {
  const signer = await makeSigner();
  const { response } = await runRtdn({ signer, beginError: true, google: await googleSub() });
  assert.equal(response.status, 500);
});

test("rtdn: Google jetonu tanımıyorsa (404) kayıt süresi dolmuş işaretlenir", async () => {
  const signer = await makeSigner();
  const { response, calls, json } = await runRtdn({ signer, google: new Response("{}", { status: 404 }) });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "expired_by_google");
  assert.equal(calls.rpc.find((call) => call.name === "apply_play_subscription").args.p_state, "expired");
});

test("rtdn: iade (voided purchase) erişimi keser", async () => {
  const signer = await makeSigner();
  const payload = pushBody({ packageName: "com.hedefit.app", voidedPurchaseNotification: { purchaseToken: TOKEN, productType: 1, refundType: 1 } }, "m-void");
  const { response, calls, json } = await runRtdn({ signer, payload });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "voided");
  assert.deepEqual(calls.rpc.map((call) => call.name), ["begin_billing_event", "void_play_subscription", "finish_billing_event"]);
  assert.equal(calls.googleGet, 0, "iade Google'a yeniden sorulmaz");
});

test("rtdn: bilinmeyen jetonun iadesi 200 ile onaylanır", async () => {
  const signer = await makeSigner();
  const payload = pushBody({ packageName: "com.hedefit.app", voidedPurchaseNotification: { purchaseToken: TOKEN, productType: 1 } });
  const { response, json } = await runRtdn({ signer, payload, row: null });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "unknown_token");
});

// ---- reconcile -------------------------------------------------------------------

function reconcileClient(rows, google, calls) {
  const previous = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    const href = String(url);
    if (href.includes("oauth2.googleapis.com/token")) return Response.json({ access_token: "g", expires_in: 3600 });
    if (href.includes("subscriptionsv2/tokens/")) {
      const token = decodeURIComponent(href.split("/tokens/")[1]);
      calls.google.push(token);
      return google(token);
    }
    if (href.includes("/rest/v1/subscriptions")) {
      calls.queries.push(href);
      const single = href.includes("purchase_token=eq.");
      if (single) {
        const token = decodeURIComponent(href.match(/purchase_token=eq\.([^&]+)/)[1]);
        const row = rows.find((candidate) => candidate.purchase_token === token);
        return Response.json(row ?? null);
      }
      return Response.json(rows.map(({ purchase_token, user_id }) => ({ purchase_token, user_id })));
    }
    const rpc = href.match(/\/rpc\/(\w+)/)?.[1];
    if (rpc) { calls.rpc.push({ name: rpc, args: JSON.parse(String(init.body)) }); return Response.json("plus"); }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  };
  return { admin: createClient("https://test.supabase.co", "svc", { auth: { persistSession: false } }), restore: () => { globalThis.fetch = previous; } };
}

test("reconcile: bir abonelikteki Google hatası diğerlerini durdurmaz", async () => {
  const restoreEnv = rtdnEnv();
  const calls = { google: [], queries: [], rpc: [] };
  const otherUser = "00000000-0000-4000-8000-0000000000b2";
  const rows = [subscriptionRow({ purchase_token: "tok-a-0123456789" }), subscriptionRow({ purchase_token: "tok-b-0123456789", user_id: otherUser })];
  const otherSub = { ...(await googleSub()), externalAccountIdentifiers: { obfuscatedExternalAccountId: await obfuscatedAccountId(otherUser) } };
  const { admin, restore } = reconcileClient(rows, (token) => token === "tok-a-0123456789" ? new Response("{}", { status: 503 }) : Response.json(otherSub), calls);
  try {
    const summary = await reconcileSubscriptions(admin);
    assert.deepEqual(summary, { checked: 2, applied: 1, failed: 1 });
    const applied = calls.rpc.filter((call) => call.name === "apply_play_subscription");
    assert.equal(applied.length, 1);
    assert.equal(applied[0].args.p_user, otherUser);
    assert.equal(calls.rpc.filter((call) => call.name === "recompute_plan_tier").length, 2);
  } finally {
    restore();
    restoreEnv();
  }
});

test("reconcile: başarılı eşitleme apply + recompute çağırır", async () => {
  const restoreEnv = rtdnEnv();
  const calls = { google: [], queries: [], rpc: [] };
  const rows = [subscriptionRow({ purchase_token: "tok-ok-0123456789" })];
  const sub = await googleSub("SUBSCRIPTION_STATE_ACTIVE");
  const { admin, restore } = reconcileClient(rows, () => Response.json(sub), calls);
  try {
    const summary = await reconcileSubscriptions(admin);
    assert.deepEqual(summary, { checked: 1, applied: 1, failed: 0 });
    assert.ok(calls.rpc.some((call) => call.name === "apply_play_subscription"));
    assert.ok(calls.rpc.some((call) => call.name === "recompute_plan_tier" && call.args.p_user === USER));
    assert.match(calls.queries[0], /state=in\./);
  } finally {
    restore();
    restoreEnv();
  }
});

test("reconcile: aday yoksa hiçbir Google çağrısı yapılmaz", async () => {
  const restoreEnv = rtdnEnv();
  const calls = { google: [], queries: [], rpc: [] };
  const { admin, restore } = reconcileClient([], () => Response.json({}), calls);
  try {
    assert.deepEqual(await reconcileSubscriptions(admin), { checked: 0, applied: 0, failed: 0 });
    assert.equal(calls.google.length, 0);
  } finally {
    restore();
    restoreEnv();
  }
});
