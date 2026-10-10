import assert from "node:assert/strict";
import { createPrivateKey, createSign, X509Certificate } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { interpretAppleTransaction, setAppleRootFingerprintForTests, verifyAppleJws } from "../lib/billing/apple.ts";
import { withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const USER = "00000000-0000-4000-8000-0000000000c1";
const OTHER_USER = "00000000-0000-4000-8000-0000000000c2";
const ORIGINAL_ID = "2000000123456789";
const FUTURE = Date.now() + 25 * 86_400_000;
const PAST = Date.now() - 2 * 86_400_000;

// ---- Sahte Apple sertifika zinciri (openssl ile): kök → ara → yaprak -----------------

function openssl(...args) { return execFileSync("openssl", args, { stdio: ["ignore", "pipe", "pipe"] }); }

function makeChain() {
  const dir = mkdtempSync(join(tmpdir(), "apple-chain-"));
  const f = (name) => join(dir, name);
  try {
    for (const name of ["root", "inter", "leaf"]) openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", f(`${name}.key`));
    openssl("req", "-x509", "-new", "-key", f("root.key"), "-sha256", "-days", "30", "-subj", "/CN=Test Root", "-out", f("root.pem"));
    writeFileSync(f("ca.ext"), "basicConstraints=critical,CA:TRUE\n");
    for (const [name, parent] of [["inter", "root"], ["leaf", "inter"]]) {
      openssl("req", "-new", "-key", f(`${name}.key`), "-subj", `/CN=Test ${name}`, "-out", f(`${name}.csr`));
      openssl("x509", "-req", "-in", f(`${name}.csr`), "-CA", f(`${parent}.pem`), "-CAkey", f(`${parent}.key`), "-CAcreateserial", "-days", "30", "-sha256", "-extfile", f("ca.ext"), "-out", f(`${name}.pem`));
    }
    const der = (name) => new X509Certificate(readFileSync(f(`${name}.pem`))).raw.toString("base64");
    return {
      x5c: [der("leaf"), der("inter"), der("root")],
      rootFingerprint: new X509Certificate(readFileSync(f("root.pem"))).fingerprint256,
      leafKey: createPrivateKey(readFileSync(f("leaf.key"))),
    };
  } finally { rmSync(dir, { recursive: true, force: true }); }
}

let chain = null;
try { chain = makeChain(); } catch { /* openssl yoksa testler atlanır */ }
const skip = chain ? false : "openssl bulunamadı";

const b64url = (v) => Buffer.from(v).toString("base64url");

function signJws(payload, { header = {}, key = chain.leafKey, x5c = chain.x5c } = {}) {
  const h = b64url(JSON.stringify({ alg: "ES256", x5c, ...header }));
  const p = b64url(JSON.stringify(payload));
  const sig = createSign("SHA256").update(`${h}.${p}`).sign({ key, dsaEncoding: "ieee-p1363" });
  return `${h}.${p}.${sig.toString("base64url")}`;
}

function tx(overrides = {}) {
  return { transactionId: "2000000999", originalTransactionId: ORIGINAL_ID, productId: "hedefit_plus_monthly", bundleId: "com.hedefit.app", appAccountToken: USER, expiresDate: FUTURE, environment: "Sandbox", ...overrides };
}

function trusted(fn) {
  return async () => {
    setAppleRootFingerprintForTests(chain.rootFingerprint);
    try { await fn(); } finally { setAppleRootFingerprintForTests(null); }
  };
}

// ---- verifyAppleJws ------------------------------------------------------------------

test("verifyAppleJws: geçerli zincir ve imza işlemi çözer", { skip }, trusted(() => {
  const decoded = verifyAppleJws(signJws(tx()));
  assert.equal(decoded.originalTransactionId, ORIGINAL_ID);
  assert.equal(decoded.productId, "hedefit_plus_monthly");
}));

test("verifyAppleJws: güvenilmeyen kök reddedilir (Apple kökü sabitlenmiştir)", { skip }, () => {
  assert.throws(() => verifyAppleJws(signJws(tx())), (e) => e.code === "untrusted_root");
});

test("verifyAppleJws: değiştirilmiş yük imzayı bozar", { skip }, trusted(() => {
  const [h, , s] = signJws(tx()).split(".");
  const forged = `${h}.${b64url(JSON.stringify(tx({ productId: "hedefit_premium_yearly" })))}.${s}`;
  assert.throws(() => verifyAppleJws(forged), (e) => e.code === "bad_signature");
}));

test("verifyAppleJws: ES256 dışı algoritma ve eksik zincir reddedilir", { skip }, trusted(() => {
  assert.throws(() => verifyAppleJws(signJws(tx(), { header: { alg: "none" } })), (e) => e.code === "invalid_jws");
  assert.throws(() => verifyAppleJws(signJws(tx(), { x5c: chain.x5c.slice(0, 1) })), (e) => e.code === "invalid_jws");
  assert.throws(() => verifyAppleJws("a.b"), (e) => e.code === "invalid_jws");
}));

test("verifyAppleJws: zincir sırası bozuksa reddedilir", { skip }, trusted(() => {
  const swapped = [chain.x5c[0], chain.x5c[2], chain.x5c[1]];
  assert.throws(() => verifyAppleJws(signJws(tx(), { x5c: swapped })), (e) => e.code === "invalid_chain" || e.code === "untrusted_root");
}));

// ---- interpretAppleTransaction -------------------------------------------------------

test("interpretAppleTransaction: aktif abonelik plan açar", () => {
  const r = interpretAppleTransaction(tx(), "hedefit_plus_monthly", USER);
  assert.deepEqual({ tier: r.tier, state: r.state, entitled: r.entitled, basePlanId: r.basePlanId, token: r.token, isTest: r.isTest },
    { tier: "plus", state: "active", entitled: true, basePlanId: "monthly", token: `apple:${ORIGINAL_ID}`, isTest: true });
  assert.equal(interpretAppleTransaction(tx({ productId: "hedefit_premium_yearly" }), "hedefit_premium_yearly", USER).tier, "pro");
});

test("interpretAppleTransaction: süresi dolmuş veya iade edilmiş işlem plan açmaz", () => {
  assert.equal(interpretAppleTransaction(tx({ expiresDate: PAST }), "hedefit_plus_monthly", USER).entitled, false);
  const refunded = interpretAppleTransaction(tx({ revocationDate: PAST }), "hedefit_plus_monthly", USER);
  assert.equal(refunded.entitled, false);
  assert.equal(refunded.state, "expired");
});

test("interpretAppleTransaction: başka hesap, ürün veya uygulama reddedilir", () => {
  assert.throws(() => interpretAppleTransaction(tx(), "hedefit_plus_monthly", OTHER_USER), (e) => e.code === "account_mismatch");
  assert.throws(() => interpretAppleTransaction(tx({ appAccountToken: undefined }), "hedefit_plus_monthly", USER), (e) => e.code === "account_mismatch");
  assert.throws(() => interpretAppleTransaction(tx(), "hedefit_premium_monthly", USER), (e) => e.code === "unknown_product");
  assert.throws(() => interpretAppleTransaction(tx({ productId: "hedefit_free" }), "hedefit_free", USER), (e) => e.code === "unknown_product");
  assert.throws(() => interpretAppleTransaction(tx({ bundleId: "com.evil.app" }), "hedefit_plus_monthly", USER), (e) => e.code === "bundle_mismatch");
});

// ---- /api/billing/apple/notifications ------------------------------------------------

async function runNotification({ notification, transaction = tx(), renewal, row = { user_id: USER, product_id: "hedefit_plus_monthly", plan_tier: "plus" }, duplicate = false, rpcFail = null, rawBody }) {
  const restoreEnv = withSupabaseAuthEnv();
  setAppleRootFingerprintForTests(chain.rootFingerprint);
  const previousFetch = globalThis.fetch;
  const calls = { rpc: [] };
  globalThis.fetch = async (url, init = {}) => {
    const href = String(url);
    const rpc = href.match(/\/rpc\/([a-z_]+)/)?.[1];
    if (rpc) {
      const args = JSON.parse(String(init.body ?? "{}"));
      calls.rpc.push({ name: rpc, args });
      if (rpcFail === rpc) return Response.json({ code: "XX000", message: "boom" }, { status: 500 });
      if (rpc === "begin_billing_event") return Response.json(!duplicate);
      if (rpc === "void_play_subscription") return Response.json("free");
      if (rpc === "apply_play_subscription") return Response.json(args.p_tier);
      return Response.json(null);
    }
    if (href.includes("/rest/v1/subscriptions")) return Response.json(row ? [row] : [], { headers: { "content-range": row ? "0-0/1" : "*/0" } });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  };
  try {
    const { POST } = await import(`../app/api/billing/apple/notifications/route.ts?test=${Date.now()}${Math.random()}`);
    const signedPayload = signJws({ notificationUUID: `uuid-${Math.random()}`, bundleId: "com.hedefit.app", ...notification,
      data: { bundleId: "com.hedefit.app", signedTransactionInfo: signJws(transaction), ...(renewal ? { signedRenewalInfo: signJws(renewal) } : {}), ...notification?.data } });
    const response = await POST(new Request("http://localhost/api/billing/apple/notifications", { method: "POST", body: rawBody ?? JSON.stringify({ signedPayload }) }));
    return { response, json: await response.json(), calls };
  } finally { globalThis.fetch = previousFetch; restoreEnv(); setAppleRootFingerprintForTests(null); }
}

const applied = (calls) => calls.rpc.find((c) => c.name === "apply_play_subscription")?.args;

test("notifications: yenileme planı yeni bitiş tarihiyle günceller", { skip }, async () => {
  const { response, json, calls } = await runNotification({ notification: { notificationType: "DID_RENEW" }, renewal: { autoRenewStatus: 1 } });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "active");
  const a = applied(calls);
  assert.equal(a.p_user, USER);
  assert.equal(a.p_token, `apple:${ORIGINAL_ID}`);
  assert.equal(a.p_state, "active");
  assert.equal(a.p_tier, "plus");
  assert.equal(a.p_auto_renewing, true);
  assert.equal(a.p_expires, new Date(FUTURE).toISOString());
  assert.deepEqual(calls.rpc.map((c) => c.name), ["begin_billing_event", "apply_play_subscription", "finish_billing_event"]);
});

test("notifications: otomatik yenileme kapatılınca 'canceled' (süre bitene dek plan sürer)", { skip }, async () => {
  const { json, calls } = await runNotification({ notification: { notificationType: "DID_CHANGE_RENEWAL_STATUS", subtype: "AUTO_RENEW_DISABLED" }, renewal: { autoRenewStatus: 0 } });
  assert.equal(json.outcome, "canceled");
  assert.equal(applied(calls).p_auto_renewing, false);
});

test("notifications: ödeme sorunu ek süre boyunca 'grace' yazar", { skip }, async () => {
  const graceUntil = Date.now() + 5 * 86_400_000;
  const { json, calls } = await runNotification({ notification: { notificationType: "DID_FAIL_TO_RENEW", subtype: "GRACE_PERIOD" }, transaction: tx({ expiresDate: PAST }), renewal: { autoRenewStatus: 1, gracePeriodExpiresDate: graceUntil, isInBillingRetryPeriod: true } });
  assert.equal(json.outcome, "grace");
  assert.equal(applied(calls).p_expires, new Date(graceUntil).toISOString());
});

test("notifications: süre dolumu 'expired' yazar", { skip }, async () => {
  const { json, calls } = await runNotification({ notification: { notificationType: "EXPIRED", subtype: "VOLUNTARY" }, transaction: tx({ expiresDate: PAST }), renewal: { autoRenewStatus: 0 } });
  assert.equal(json.outcome, "expired");
  assert.equal(applied(calls).p_state, "expired");
});

test("notifications: iade planı geri alır (void)", { skip }, async () => {
  const { json, calls } = await runNotification({ notification: { notificationType: "REFUND" }, transaction: tx({ revocationDate: Date.now() }) });
  assert.equal(json.outcome, "voided");
  assert.ok(calls.rpc.some((c) => c.name === "void_play_subscription" && c.args.p_token === `apple:${ORIGINAL_ID}`));
  assert.equal(applied(calls), undefined);
});

test("notifications: yükseltme (Premium) planı pro yapar", { skip }, async () => {
  const { calls } = await runNotification({ notification: { notificationType: "DID_CHANGE_RENEWAL_PREF", subtype: "UPGRADE" }, transaction: tx({ productId: "hedefit_premium_yearly" }), renewal: { autoRenewStatus: 1 } });
  const a = applied(calls);
  assert.equal(a.p_tier, "pro");
  assert.equal(a.p_base_plan, "yearly");
});

test("notifications: aynı bildirim ikinci kez işlenmez", { skip }, async () => {
  const { response, json, calls } = await runNotification({ notification: { notificationType: "DID_RENEW" }, duplicate: true });
  assert.equal(response.status, 200);
  assert.equal(json.duplicate, true);
  assert.equal(applied(calls), undefined);
});

test("notifications: /verify ile bağlanmamış işlem yok sayılır, plan yazılmaz", { skip }, async () => {
  const { response, json, calls } = await runNotification({ notification: { notificationType: "DID_RENEW" }, row: null });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "unknown_token");
  assert.equal(applied(calls), undefined);
});

test("notifications: TEST bildirimi ve başka uygulamanın bildirimi 200 ile yok sayılır", { skip }, async () => {
  const test_ = await runNotification({ notification: { notificationType: "TEST" } });
  assert.equal(test_.response.status, 200);
  assert.equal(test_.calls.rpc.length, 0);
  const other = await runNotification({ notification: { notificationType: "DID_RENEW", data: { bundleId: "com.evil.app" } } });
  assert.equal(other.json.ignored, "other_bundle");
  assert.equal(other.calls.rpc.length, 0);
});

test("notifications: imzasız/sahte yük 401, bozuk gövde 200 (Apple döngüye girmesin)", { skip }, async () => {
  const forged = await runNotification({ notification: {}, rawBody: JSON.stringify({ signedPayload: "aaa.bbb.ccc" }) });
  assert.equal(forged.response.status, 401);
  assert.equal(forged.calls.rpc.length, 0);
  const malformed = await runNotification({ notification: {}, rawBody: "not json" });
  assert.equal(malformed.response.status, 200);
  assert.equal(malformed.json.ignored, "malformed");
});

test("notifications: geçici veritabanı hatasında 500 döner (Apple yeniden dener)", { skip }, async () => {
  const { response, calls } = await runNotification({ notification: { notificationType: "DID_RENEW" }, rpcFail: "apply_play_subscription" });
  assert.equal(response.status, 500);
  assert.ok(!calls.rpc.some((c) => c.name === "finish_billing_event"), "başarısız olay işlendi işaretlenmemeli");
});
