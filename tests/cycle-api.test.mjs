import assert from "node:assert/strict";
import test from "node:test";
import { DELETE, GET, PUT } from "../app/api/cycle/route.ts";
import { cycleProfileFromRow, cycleProfileToRow, isMissingTable, personalizationFromRow } from "../lib/health-store.ts";
import { TEST_USER_ID, authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

const url = "http://localhost/api/cycle";

function withStore(handler) {
  const previousFetch = globalThis.fetch;
  const restore = withSupabaseAuthEnv();
  const calls = [];
  globalThis.fetch = withAuthenticatedFetch((input, init) => {
    calls.push({ url: String(input), method: init?.method ?? "GET", body: init?.body ? String(init.body) : "" });
    return handler(String(input), init);
  }, TEST_USER_ID);
  return { calls, restore: () => { globalThis.fetch = previousFetch; restore(); } };
}

const missingTable = () => Response.json({ code: "PGRST205", message: "Could not find the table 'public.cycle_profiles' in the schema cache" }, { status: 404 });

test("satır ↔ model dönüşümü: boş satır takip kapalı bir profil verir", () => {
  assert.equal(cycleProfileFromRow(null).trackingEnabled, false);
  const profile = cycleProfileFromRow({ tracking_enabled: true, last_period_start: "2026-10-01", cycle_length_days: 30, period_length_days: 6, regularity: "regular" });
  assert.deepEqual(cycleProfileToRow("u1", profile), { user_id: "u1", tracking_enabled: true, last_period_start: "2026-10-01", cycle_length_days: 30, period_length_days: 6, regularity: "regular" });
  assert.equal(cycleProfileFromRow({ regularity: "weird" }).regularity, "unknown");
  assert.deepEqual(personalizationFromRow(null), { adaptiveEnabled: true, cycleEnabled: false, aiHealthContextEnabled: false });
  assert.equal(isMissingTable({ code: "PGRST205" }), true);
  assert.equal(isMissingTable({ code: "42501" }), false);
});

test("kimliği doğrulanmamış istekler reddedilir", async () => {
  const { restore } = withStore(() => Response.json([]));
  try {
    for (const handler of [GET, PUT, DELETE]) assert.equal((await handler(new Request(url, { method: handler.name }))).status, 401);
  } finally { restore(); }
});

test("PUT: geçerli profil kaydedilir, kişiselleştirme bayrağı aynı eylemle açılır, faz hesaplanır", async () => {
  const { calls, restore } = withStore(() => Response.json([], { status: 201 }));
  try {
    const response = await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ trackingEnabled: true, lastPeriodStart: "2026-10-01", cycleLengthDays: 28, periodLengthDays: 5, regularity: "regular", localDate: "2026-10-05" }) }));
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.state.cycleDay, 5);
    assert.equal(body.state.phase, "menstrual");
    const writes = calls.filter((call) => call.method === "POST");
    assert.ok(writes.some((call) => call.url.includes("cycle_profiles") && call.body.includes("\"tracking_enabled\":true")));
    assert.ok(writes.some((call) => call.url.includes("personalization_settings") && call.body.includes("\"cycle_personalization_enabled\":true")));
  } finally { restore(); }
});

test("PUT: geçersiz girdi 400, veritabanına hiç gidilmez", async () => {
  const { calls, restore } = withStore(() => Response.json([]));
  try {
    const response = await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ trackingEnabled: true, cycleLengthDays: 7 }) }));
    assert.equal(response.status, 400);
    assert.equal((await response.json()).error, "invalid_cycle_length");
    assert.equal((await PUT(authorizedRequest(url, { method: "PUT", body: "{" }))).status, 400);
    assert.equal(calls.filter((call) => call.url.includes("/rest/v1/")).length, 0);
  } finally { restore(); }
});

test("PUT: takip kapalıyken hiçbir alan zorunlu değildir", async () => {
  const { restore } = withStore(() => Response.json([], { status: 201 }));
  try {
    const response = await PUT(authorizedRequest(url, { method: "PUT", body: JSON.stringify({ trackingEnabled: false }) }));
    assert.equal(response.status, 200);
    assert.equal((await response.json()).state, null);
  } finally { restore(); }
});

test("tablolar henüz kurulmamışsa 503 cycle_unavailable (çökme yok)", async () => {
  const { restore } = withStore(missingTable);
  try {
    for (const [handler, init] of [[GET, { method: "GET" }], [PUT, { method: "PUT", body: JSON.stringify({ trackingEnabled: true }) }], [DELETE, { method: "DELETE" }]]) {
      const response = await handler(authorizedRequest(url, init));
      assert.equal(response.status, 503, init.method);
      assert.equal((await response.json()).error, "cycle_unavailable");
    }
  } finally { restore(); }
});

test("GET: kayıt yoksa takip kapalı profil ve null durum döner", async () => {
  const { restore } = withStore(() => Response.json(null, { status: 200 }));
  try {
    const response = await GET(authorizedRequest(`${url}?localDate=2026-10-05`));
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.profile.trackingEnabled, false);
    assert.equal(body.state, null);
    assert.equal(body.personalization.cycleEnabled, false);
  } finally { restore(); }
});

test("DELETE: satır gerçekten silinir ve kişiselleştirme kapatılır", async () => {
  const { calls, restore } = withStore(() => Response.json([], { status: 200 }));
  try {
    const response = await DELETE(authorizedRequest(url, { method: "DELETE" }));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { deleted: true });
    assert.ok(calls.some((call) => call.method === "DELETE" && call.url.includes("cycle_profiles") && call.url.includes(`user_id=eq.${TEST_USER_ID}`)));
    assert.ok(calls.some((call) => call.url.includes("personalization_settings") && call.body.includes("\"cycle_personalization_enabled\":false")));
  } finally { restore(); }
});
