import assert from "node:assert/strict";
import test from "node:test";
import { authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

let nextUser = 700;
const freshUser = () => `00000000-0000-4000-8000-${String(++nextUser).padStart(12, "0")}`;
const MEMORY_ID = "11111111-2222-4333-8444-555555555555";

async function call(method, query, handler) {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  const seen = [];
  globalThis.fetch = withAuthenticatedFetch((url, init) => {
    seen.push({ url: String(url), method: init?.method });
    return handler(String(url), init);
  }, freshUser());
  try {
    const route = await import(`../app/api/ai/memory/route.ts?test=${Date.now()}${Math.random()}`);
    const response = await route[method](authorizedRequest(`http://localhost/api/ai/memory${query}`, { method }));
    return { response, json: await response.json(), seen };
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
}

test("ai/memory GET: kullanıcının hafızasını listeler", async () => {
  const { response, json } = await call("GET", "", () => Response.json([
    { id: MEMORY_ID, memory_type: "constraint", memory_key: "knee", memory_value: "diz ağrısı", confidence: 0.9, source: "user_explicit", created_at: "2026-10-01T00:00:00Z", updated_at: "2026-10-02T00:00:00Z" },
  ]));
  assert.equal(response.status, 200);
  assert.equal(json.memories.length, 1);
  assert.equal(json.memories[0].id, MEMORY_ID);
  assert.equal(json.memories[0].type, "constraint");
});

test("ai/memory DELETE: tek kayıt id ile silinir", async () => {
  const { response, json, seen } = await call("DELETE", `?id=${MEMORY_ID}`, () => new Response(null, { status: 204 }));
  assert.equal(response.status, 200);
  assert.equal(json.deleted, true);
  assert.match(seen.at(-1).url, new RegExp(`ai_memories\\?id=eq\\.${MEMORY_ID}`));
});

test("ai/memory DELETE: geçersiz id 400, hiçbir silme isteği gitmez", async () => {
  const { response, seen } = await call("DELETE", "?id=1%27%20or%20true", () => new Response(null, { status: 204 }));
  assert.equal(response.status, 400);
  assert.ok(!seen.some((call) => call.url.includes("ai_memories")));
});

test("ai/memory DELETE: id'siz ve all'sız çağrı hiçbir şey silmez (yanlışlıkla toplu silme yok)", async () => {
  const { response, seen } = await call("DELETE", "", () => new Response(null, { status: 204 }));
  assert.equal(response.status, 400);
  assert.ok(!seen.some((call) => call.url.includes("ai_memories")));
  const wrongValue = await call("DELETE", "?all=1", () => new Response(null, { status: 204 }));
  assert.equal(wrongValue.response.status, 400);
});

test("ai/memory DELETE ?all=true: tüm hafıza tek istekle silinir", async () => {
  const { response, json, seen } = await call("DELETE", "?all=true", () => new Response(null, { status: 204 }));
  assert.equal(response.status, 200);
  assert.deepEqual(json, { deleted: true, all: true });
  const deletion = seen.find((entry) => entry.url.includes("ai_memories"));
  assert.equal(deletion.method, "DELETE");
  assert.match(deletion.url, /id=not\.is\.null/);
});

test("ai/memory DELETE ?all=true: veritabanı hatasında deleted=false", async () => {
  const { json } = await call("DELETE", "?all=true", () => Response.json({ code: "XX000", message: "boom" }, { status: 500 }));
  assert.equal(json.deleted, false);
});

test("ai/memory: kimliksiz istek 401", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  try {
    const route = await import(`../app/api/ai/memory/route.ts?test=${Date.now()}`);
    assert.equal((await route.GET(new Request("http://localhost/api/ai/memory"))).status, 401);
    assert.equal((await route.DELETE(new Request("http://localhost/api/ai/memory?all=true", { method: "DELETE" }))).status, 401);
  } finally {
    restoreEnv();
  }
});

// ---- POST /api/ai/memory (sohbetten hafıza çıkarımı) -------------------------------

import { providerRegistry } from "../lib/ai/providers/registry.ts";
import { withUsageMock } from "./helpers/auth.mjs";

function memoryProvider(memories, calls) {
  return {
    id: "openai-compatible", kind: "remote", isAvailable: async () => true,
    generateText: async () => { throw new Error("metin üretimi beklenmiyordu"); },
    generateObject: async (request) => { calls.model += 1; calls.lastPrompt = request.prompt; return { object: { memories }, provider: "openai-compatible", model: "test", latencyMs: 1 }; },
  };
}

async function postMemory({ message, memories = [], allowed = true, upsertStatus = 201, apiKey = "test-key" }) {
  const restoreEnv = withSupabaseAuthEnv();
  const previousKey = process.env.OPENAI_API_KEY;
  if (apiKey) process.env.OPENAI_API_KEY = apiKey; else delete process.env.OPENAI_API_KEY;
  const previousFetch = globalThis.fetch;
  const calls = { model: 0, usage: 0, refunds: 0, upserts: [] };
  providerRegistry.reset([memoryProvider(memories, calls)]);
  const base = withUsageMock({ planTier: "plus", isPremium: false, allowed }, (url, init) => {
    const href = String(url);
    if (href.includes("/rpc/refund_usage_counter_for_user")) { calls.refunds += 1; return Response.json(null); }
    if (href.includes("/rest/v1/ai_memories")) { calls.upserts.push(JSON.parse(String(init.body))); return new Response(null, { status: upsertStatus }); }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  });
  globalThis.fetch = async (url, init) => { if (String(url).includes("/rpc/check_and_consume_usage")) calls.usage += 1; return base(url, init); };
  try {
    const route = await import(`../app/api/ai/memory/route.ts?test=${Date.now()}${Math.random()}`);
    const response = await route.POST(authorizedRequest("http://localhost/api/ai/memory", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ message, locale: "tr" }) }));
    return { response, json: await response.json(), calls };
  } finally {
    globalThis.fetch = previousFetch;
    providerRegistry.reset();
    if (previousKey === undefined) delete process.env.OPENAI_API_KEY; else process.env.OPENAI_API_KEY = previousKey;
    restoreEnv();
  }
}

test("ai/memory POST: tercih içermeyen mesaj için model çağrılmaz ve kota harcanmaz", async () => {
  const { json, calls } = await postMemory({ message: "bugün kaç kalorim kaldı acaba" });
  assert.deepEqual(json, { saved: 0 });
  assert.equal(calls.model, 0);
  assert.equal(calls.usage, 0);
});

test("ai/memory POST: kalıcı tercih içeren mesajdan not çıkarılır ve kullanıcıya bağlı kaydedilir", async () => {
  const { json, calls } = await postMemory({ message: "koşmayı sevmiyorum ama yürüyüşü seviyorum", memories: [{ type: "exercise_preference", key: "koşu", value: "sevmiyor", confidence: 0.9 }] });
  assert.deepEqual(json, { saved: 1 });
  assert.equal(calls.model, 1);
  assert.equal(calls.usage, 1);
  assert.equal(calls.upserts.length, 1);
  assert.equal(calls.upserts[0][0].memory_key, "koşu");
  assert.match(calls.upserts[0][0].user_id, /^00000000-0000-4000-8000-/);
  assert.equal(calls.refunds, 0);
});

test("ai/memory POST: model not çıkarmazsa kota iade edilir", async () => {
  const { json, calls } = await postMemory({ message: "koşmayı sevmiyorum", memories: [] });
  assert.deepEqual(json, { saved: 0 });
  assert.equal(calls.refunds, 1);
  assert.equal(calls.upserts.length, 0);
});

test("ai/memory POST: kayıt başarısızsa kota iade edilir", async () => {
  const { json, calls } = await postMemory({ message: "koşmayı sevmiyorum", memories: [{ type: "exercise_preference", key: "koşu", value: "sevmiyor", confidence: 0.9 }], upsertStatus: 500 });
  assert.deepEqual(json, { saved: 0 });
  assert.equal(calls.refunds, 1);
});

test("ai/memory POST: günlük hafıza kotası dolduysa model çağrılmaz", async () => {
  const { json, calls } = await postMemory({ message: "koşmayı sevmiyorum", allowed: false });
  assert.deepEqual(json, { saved: 0 });
  assert.equal(calls.model, 0);
});

test("ai/memory POST: uzak sağlayıcı yoksa sessizce 0 döner, kota harcanmaz", async () => {
  const { json, calls } = await postMemory({ message: "koşmayı sevmiyorum", apiKey: "" });
  assert.deepEqual(json, { saved: 0 });
  assert.equal(calls.usage, 0);
});

test("ai/memory POST: aşırı uzun mesaj modele 600 karakterle sınırlanarak gider", async () => {
  const long = "koşmayı sevmiyorum " + "x".repeat(2000);
  const { calls } = await postMemory({ message: long, memories: [] });
  assert.ok(calls.lastPrompt.length < 1500, "istem makul boyutta kalmalı");
  assert.ok(!calls.lastPrompt.includes("x".repeat(700)), "600 karakterden uzun kısım modele gitmemeli");
});
