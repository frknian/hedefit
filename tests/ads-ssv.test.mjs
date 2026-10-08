import assert from "node:assert/strict";
import { generateKeyPairSync, createSign } from "node:crypto";
import test from "node:test";
import { derToRawEcdsa, resetSsvKeyCache, verifyAdMobSsv } from "../lib/ads/ssv.ts";
import { authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const USER = "00000000-0000-4000-8000-0000000000c1";
const KEY_ID = "3335741209";

function makeSigner(keyId = KEY_ID) {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const base64 = publicKey.export({ type: "spki", format: "der" }).toString("base64");
  const sign = (message) => createSign("SHA256").update(message).sign({ key: privateKey, dsaEncoding: "der" }).toString("base64url");
  return { key: { keyId: Number(keyId), base64 }, keyId, sign };
}

/** AdMob'un gönderdiği biçimde imzalı callback URL'si. */
function ssvUrl(signer, overrides = {}, { tamper } = {}) {
  const params = {
    ad_network: "5450213213286189855", ad_unit: "6660516300", custom_data: "chat", reward_amount: "1", reward_item: "question",
    timestamp: String(Date.now()), transaction_id: `tx-${Math.random().toString(36).slice(2)}`, user_id: USER, ...overrides,
  };
  const message = Object.entries(params).filter(([, v]) => v !== undefined).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join("&");
  const signature = signer.sign(message);
  const query = `${message}&signature=${signature}&key_id=${signer.keyId}`;
  return `https://hedefit.example/api/ads/ssv?${tamper ? tamper(query) : query}`;
}

async function withKeys(keys, fn, counter = { count: 0 }) {
  const previous = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    if (String(url).includes("verifier-keys.json")) { counter.count += 1; return Response.json({ keys: typeof keys === "function" ? keys() : keys }); }
    return previous(url, init);
  };
  resetSsvKeyCache();
  try { return await fn(counter); } finally { globalThis.fetch = previous; }
}

test("derToRawEcdsa: DER imzayı 64 baytlık r||s'ye çevirir, bozuk girdiyi reddeder", () => {
  const signer = makeSigner();
  const der = Buffer.from(signer.sign("m"), "base64url");
  const raw = derToRawEcdsa(new Uint8Array(der));
  assert.equal(raw.length, 64);
  assert.equal(derToRawEcdsa(new Uint8Array([1, 2, 3])), null);
  assert.equal(derToRawEcdsa(new Uint8Array([0x30, 0x05, 0x02, 0x01, 0x01, 0x04, 0x00, 0x00])), null);
});

test("SSV: geçerli imzalı, taze istek kabul edilir ve parametreler döner", async () => {
  const signer = makeSigner();
  await withKeys([signer.key], async () => {
    const result = await verifyAdMobSsv(ssvUrl(signer, { transaction_id: "tx-1" }));
    assert.equal(result.valid, true);
    assert.equal(result.params.transactionId, "tx-1");
    assert.equal(result.params.userId, USER);
    assert.equal(result.params.customData, "chat");
  });
});

test("SSV: parametre değiştirilirse (user_id) imza tutmaz", async () => {
  const signer = makeSigner();
  await withKeys([signer.key], async () => {
    const forged = ssvUrl(signer, {}, { tamper: (q) => q.replace(USER, "00000000-0000-4000-8000-0000000000ff") });
    const result = await verifyAdMobSsv(forged);
    assert.deepEqual(result, { valid: false, reason: "signature_mismatch" });
  });
});

test("SSV: başka bir anahtarla (saldırgan) imzalanan istek reddedilir", async () => {
  const real = makeSigner();
  const attacker = makeSigner();
  await withKeys([real.key], async () => {
    assert.equal((await verifyAdMobSsv(ssvUrl(attacker))).valid, false);
  });
});

test("SSV: imza yok / bozuk / bilinmeyen key_id reddedilir", async () => {
  const signer = makeSigner();
  await withKeys([signer.key], async () => {
    assert.equal((await verifyAdMobSsv("https://x.example/api/ads/ssv?user_id=1&transaction_id=2")).reason, "missing_signature");
    assert.equal((await verifyAdMobSsv("https://x.example/api/ads/ssv?a=1&signature=AAAA&key_id=1")).reason, "bad_signature_format");
    const unknown = makeSigner("999");
    assert.equal((await verifyAdMobSsv(ssvUrl(unknown))).reason, "unknown_key");
  });
});

test("SSV: eski veya gelecekteki zaman damgası reddedilir (replay sınırı)", async () => {
  const signer = makeSigner();
  await withKeys([signer.key], async () => {
    const old = await verifyAdMobSsv(ssvUrl(signer, { timestamp: String(Date.now() - 25 * 3_600_000) }));
    assert.deepEqual(old, { valid: false, reason: "stale_timestamp" });
    const future = await verifyAdMobSsv(ssvUrl(signer, { timestamp: String(Date.now() + 3_600_000) }));
    assert.equal(future.reason, "stale_timestamp");
    const missing = await verifyAdMobSsv(ssvUrl(signer, { timestamp: "abc" }));
    assert.equal(missing.reason, "stale_timestamp");
  });
});

test("SSV: anahtar listesi önbelleğe alınır; bilinmeyen key_id için bir kez yenilenir", async () => {
  const first = makeSigner("1");
  const second = makeSigner("2");
  let served = [first.key];
  await withKeys(() => served, async (counter) => {
    assert.equal((await verifyAdMobSsv(ssvUrl(first))).valid, true);
    assert.equal((await verifyAdMobSsv(ssvUrl(first))).valid, true);
    assert.equal(counter.count, 1, "ikinci doğrulama önbellekten");
    served = [first.key, second.key];
    assert.equal((await verifyAdMobSsv(ssvUrl(second))).valid, true);
    assert.equal(counter.count, 2, "yeni anahtar için yenilendi");
  });
});

test("SSV: anahtar listesi alınamazsa fırlatır", async () => {
  const signer = makeSigner();
  const previous = globalThis.fetch;
  globalThis.fetch = async () => new Response("down", { status: 503 });
  resetSsvKeyCache();
  try { await assert.rejects(() => verifyAdMobSsv(ssvUrl(signer))); } finally { globalThis.fetch = previous; }
});

// ---- GET /api/ads/ssv ------------------------------------------------------------

async function callSsv(url, { rpc, expectedUnit, keys } = {}) {
  const restoreEnv = withSupabaseAuthEnv();
  const previousUnit = process.env.ADMOB_REWARDED_AD_UNIT_ID;
  if (expectedUnit) process.env.ADMOB_REWARDED_AD_UNIT_ID = expectedUnit; else delete process.env.ADMOB_REWARDED_AD_UNIT_ID;
  const calls = [];
  const previous = globalThis.fetch;
  globalThis.fetch = async (u, init) => {
    const href = String(u);
    if (href.includes("verifier-keys.json")) return Response.json({ keys });
    const name = href.match(/\/rpc\/(\w+)/)?.[1];
    if (name) { calls.push({ name, args: JSON.parse(String(init.body)) }); return rpc ? rpc(name) : Response.json("granted:1"); }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  };
  resetSsvKeyCache();
  try {
    const { GET } = await import(`../app/api/ads/ssv/route.ts?test=${Date.now()}${Math.random()}`);
    const response = await GET(new Request(url));
    return { response, json: await response.json(), calls };
  } finally {
    globalThis.fetch = previous;
    if (previousUnit === undefined) delete process.env.ADMOB_REWARDED_AD_UNIT_ID; else process.env.ADMOB_REWARDED_AD_UNIT_ID = previousUnit;
    restoreEnv();
  }
}

test("ssv route: geçerli callback hak verir (transaction_id, kullanıcı ve özellik ile)", async () => {
  const signer = makeSigner();
  const { response, json, calls } = await callSsv(ssvUrl(signer, { transaction_id: "tx-grant" }), { keys: [signer.key] });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "granted:1");
  assert.deepEqual(calls, [{ name: "grant_ad_reward", args: { p_transaction_id: "tx-grant", p_user_id: USER, p_feature: "chat" } }]);
});

test("ssv route: sahte imza 400, hiçbir hak verilmez", async () => {
  const real = makeSigner();
  const attacker = makeSigner();
  const { response, calls } = await callSsv(ssvUrl(attacker), { keys: [real.key] });
  assert.equal(response.status, 400);
  assert.equal(calls.length, 0);
});

test("ssv route: imzalı ama geçersiz parametreler 200 ile yok sayılır (yeniden deneme yok), hak verilmez", async () => {
  const signer = makeSigner();
  for (const overrides of [{ custom_data: "photo" }, { custom_data: "chat; drop table" }, { user_id: "not-a-uuid" }, { transaction_id: undefined }]) {
    const { response, json, calls } = await callSsv(ssvUrl(signer, overrides), { keys: [signer.key] });
    assert.equal(response.status, 200, JSON.stringify(overrides));
    assert.equal(json.ignored, "invalid_params");
    assert.equal(calls.length, 0);
  }
});

test("ssv route: yapılandırılmış reklam birimi dışındaki birim yok sayılır", async () => {
  const signer = makeSigner();
  const other = await callSsv(ssvUrl(signer, { ad_unit: "111" }), { keys: [signer.key], expectedUnit: "ca-app-pub-5328854373446190/6660516300" });
  assert.equal(other.json.ignored, "other_ad_unit");
  assert.equal(other.calls.length, 0);
  const matching = await callSsv(ssvUrl(signer, { ad_unit: "6660516300" }), { keys: [signer.key], expectedUnit: "ca-app-pub-5328854373446190/6660516300" });
  assert.equal(matching.calls.length, 1);
});

test("ssv route: tekrarlanan transaction_id ikinci kez hak vermez (veritabanı 'duplicate' döner)", async () => {
  const signer = makeSigner();
  const { response, json } = await callSsv(ssvUrl(signer), { keys: [signer.key], rpc: () => Response.json("duplicate") });
  assert.equal(response.status, 200);
  assert.equal(json.outcome, "duplicate");
});

test("ssv route: veritabanı hatası 503 (AdMob yeniden dener)", async () => {
  const signer = makeSigner();
  const { response } = await callSsv(ssvUrl(signer), { keys: [signer.key], rpc: () => Response.json({ code: "XX000", message: "boom" }, { status: 500 }) });
  assert.equal(response.status, 503);
});

// ---- GET /api/ads/reward (bonus yoklama) -------------------------------------------

async function callReward(query, handler) {
  const restoreEnv = withSupabaseAuthEnv();
  const previous = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url, init) => handler(String(url), init), "00000000-0000-4000-8000-0000000000d1");
  try {
    const { GET } = await import(`../app/api/ads/reward/route.ts?test=${Date.now()}${Math.random()}`);
    const response = await GET(authorizedRequest(`http://localhost/api/ads/reward${query}`));
    return { response, json: await response.json() };
  } finally {
    globalThis.fetch = previous;
    restoreEnv();
  }
}

test("ads/reward GET: bugünkü bonusu okur, yazmaz", async () => {
  const { response, json } = await callReward("?feature=chat", (url) => url.includes("/rpc/ad_bonus_today") ? Response.json(2) : new Response("x", { status: 500 }));
  assert.equal(response.status, 200);
  assert.deepEqual(json, { bonusCount: 2, maxBonus: 3 });
});

test("ads/reward GET: geçersiz özellik 400", async () => {
  const { response } = await callReward("?feature=plan", () => Response.json(0));
  assert.equal(response.status, 400);
});

test("ads/reward GET: kimliksiz istek 401", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  try {
    const { GET } = await import(`../app/api/ads/reward/route.ts?test=${Date.now()}`);
    assert.equal((await GET(new Request("http://localhost/api/ads/reward?feature=chat"))).status, 401);
  } finally { restoreEnv(); }
});
