import assert from "node:assert/strict";
import test from "node:test";
import { DELETE, GET, PUT } from "../app/api/personalization/route.ts";
import { TEST_USER_ID, authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const url = "http://localhost/api/personalization";
function withStore(handler) {
  const previousFetch = globalThis.fetch;
  const restore = withSupabaseAuthEnv();
  const calls = [];
  globalThis.fetch = withAuthenticatedFetch((input, init) => { calls.push({ url: String(input), method: init?.method ?? "GET", body: init?.body ? String(init.body) : "" }); return handler(String(input), init); }, TEST_USER_ID);
  return { calls, restore: () => { globalThis.fetch = previousFetch; restore(); } };
}
const missing = () => Response.json({ code: "PGRST205", message: "Could not find the table" }, { status: 404 });

test("kimlik doğrulanmamış istekler reddedilir", async () => {
  const { restore } = withStore(() => Response.json([]));
  try { for (const handler of [GET, PUT, DELETE]) assert.equal((await handler(new Request(url, { method: handler.name }))).status, 401); } finally { restore(); }
});

test("PUT: AI sağlık bağlamı açılıp kapanır; döngü bayrağı buradan AÇILAMAZ, yalnız kapatılır", async () => {
  const { calls, restore } = withStore(() => Response.json([], { status: 201 }));
  try {
    assert.equal((await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ aiHealthContextEnabled: true, cycleEnabled: true }) }))).status, 200);
    const write = calls.find((call) => call.method === "POST" && call.url.includes("personalization_settings"));
    assert.ok(write.body.includes("\"ai_health_context_enabled\":true"));
    assert.ok(!write.body.includes("cycle_personalization_enabled"), "döngü rızası açılamaz");
    await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ cycleEnabled: false }) }));
    assert.ok(calls.some((call) => call.body.includes("\"cycle_personalization_enabled\":false")));
    assert.equal((await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ cycleEnabled: true }) }))).status, 400);
    assert.equal((await PUT(authorizedRequest(url, { method: "PUT", body: "{" }))).status, 400);
  } finally { restore(); }
});

test("DELETE: döngü, check-in ve ayar satırları GERÇEKTEN silinir; tablo yoksa 503", async () => {
  let { calls, restore } = withStore(() => Response.json([]));
  try {
    assert.equal((await DELETE(authorizedRequest(url, { method: "DELETE" }))).status, 200);
    for (const table of ["cycle_profiles", "daily_checkins", "personalization_settings"]) {
      assert.ok(calls.some((call) => call.method === "DELETE" && call.url.includes(table) && call.url.includes(`user_id=eq.${TEST_USER_ID}`)), table);
    }
  } finally { restore(); }
  ({ restore } = withStore(missing));
  try { assert.equal((await DELETE(authorizedRequest(url, { method: "DELETE" }))).status, 503); } finally { restore(); }
});

test("GET: tablo yoksa varsayılan ayarlar ve available=false (istemci özelliği gizler)", async () => {
  const { restore } = withStore(missing);
  try {
    const body = await (await GET(authorizedRequest(url))).json();
    assert.equal(body.available, false); assert.equal(body.personalization.cycleEnabled, false); assert.equal(body.personalization.aiHealthContextEnabled, false);
  } finally { restore(); }
});
