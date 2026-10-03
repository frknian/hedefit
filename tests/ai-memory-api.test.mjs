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
