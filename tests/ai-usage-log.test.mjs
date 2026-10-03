import assert from "node:assert/strict";
import test from "node:test";
import { estimateCostMicroUsd } from "../lib/ai/pricing.ts";
import { buildUsageRow, recordAiUsage, runWithAiUsageContext, setAiUsageContext } from "../lib/ai/usage-log.ts";
import { withSupabaseAuthEnv } from "./helpers/auth.mjs";

const successEvent = { category: "conversation", provider: "openai", model: "gpt-4o-mini", outcome: "success", fallbackUsed: false, latencyMs: 900, inputTokens: 2400, outputTokens: 300, promptVersion: "v4" };

test("estimateCostMicroUsd: bilinen modellerin liste fiyatıyla hesaplar", () => {
  assert.equal(estimateCostMicroUsd("gpt-4o", 1_000_000, 0), 2_500_000);
  assert.equal(estimateCostMicroUsd("gpt-4o", 0, 1_000_000), 10_000_000);
  assert.equal(estimateCostMicroUsd("gpt-4o-mini", 2400, 300), Math.round(2400 * 0.15 + 300 * 0.6));
  assert.equal(estimateCostMicroUsd("gpt-5.1", 3000, 8000), Math.round(3000 * 1.25 + 8000 * 10));
});

test("estimateCostMicroUsd: gpt-4o-mini, gpt-4o ile karışmaz (daha spesifik eşleşme önce)", () => {
  assert.ok(estimateCostMicroUsd("gpt-4o-mini-2024-07-18", 1000, 1000) < estimateCostMicroUsd("gpt-4o-2024-08-06", 1000, 1000));
});

test("estimateCostMicroUsd: bilinmeyen model veya eksik token için 0 değil null döner", () => {
  assert.equal(estimateCostMicroUsd("bilinmeyen-model", 10, 10), null);
  assert.equal(estimateCostMicroUsd(undefined, 10, 10), null);
  assert.equal(estimateCostMicroUsd("gpt-4o", undefined, 10), null);
});

test("buildUsageRow: bağlam yoksa veya kullanıcı bilinmiyorsa kayıt üretmez", () => {
  assert.equal(buildUsageRow(undefined, successEvent), null);
  assert.equal(buildUsageRow({ feature: "chat" }, successEvent), null);
  assert.equal(buildUsageRow({ userId: "u" }, successEvent), null);
});

test("buildUsageRow: normal durum — metin içermeyen satır üretir", () => {
  const row = buildUsageRow({ userId: "u1", feature: "chat", planTier: "plus" }, successEvent);
  assert.deepEqual(row, {
    user_id: "u1", feature: "chat", plan_tier: "plus", category: "conversation", model: "gpt-4o-mini", outcome: "success",
    fallback_used: false, input_tokens: 2400, output_tokens: 300, est_cost_micro_usd: 540, latency_ms: 900,
  });
});

test("buildUsageRow: yerel (tokensız) başarı ve atlanan sağlayıcı kaydedilmez, hata kaydedilir", () => {
  const context = { userId: "u1", feature: "chat" };
  assert.equal(buildUsageRow(context, { ...successEvent, provider: "local", model: "rule-based", inputTokens: undefined, outputTokens: undefined }), null);
  assert.equal(buildUsageRow(context, { ...successEvent, outcome: "skipped" }), null);
  const failed = buildUsageRow(context, { category: "conversation", provider: "openai", outcome: "error", fallbackUsed: false, errorKind: "timeout", promptVersion: "v4" });
  assert.equal(failed.outcome, "error");
  assert.equal(failed.est_cost_micro_usd, null);
});

test("recordAiUsage: bağlamdaki kullanıcıya ai_usage_events tablosuna yazar", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  const inserts = [];
  globalThis.fetch = async (url, init) => {
    assert.match(String(url), /\/rest\/v1\/ai_usage_events/);
    inserts.push(JSON.parse(String(init.body)));
    return new Response(null, { status: 201 });
  };
  try {
    await runWithAiUsageContext(async () => {
      setAiUsageContext({ userId: "00000000-0000-4000-8000-0000000000aa", feature: "photo", planTier: "pro" });
      await recordAiUsage({ ...successEvent, model: "gpt-4o" });
    });
    assert.equal(inserts.length, 1);
    assert.equal(inserts[0].user_id, "00000000-0000-4000-8000-0000000000aa");
    assert.equal(inserts[0].feature, "photo");
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("recordAiUsage: bağlam yoksa ağ isteği yapılmaz", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("ağ isteği beklenmiyordu"); };
  try {
    await recordAiUsage(successEvent);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("recordAiUsage: veritabanı hatası veya ağ arızası fırlatmaz (AI akışı etkilenmez)", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  try {
    for (const behavior of [() => new Response(JSON.stringify({ code: "42P01", message: "yok" }), { status: 404 }), () => { throw new TypeError("fetch failed"); }]) {
      globalThis.fetch = async () => behavior();
      await runWithAiUsageContext(async () => {
        setAiUsageContext({ userId: "u", feature: "chat" });
        await recordAiUsage(successEvent);
      });
    }
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("bağlamlar eşzamanlı isteklerde birbirine karışmaz", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  const seen = [];
  globalThis.fetch = async (_url, init) => { seen.push(JSON.parse(String(init.body)).user_id); return new Response(null, { status: 201 }); };
  try {
    await Promise.all(["a", "b", "c"].map((id, index) => runWithAiUsageContext(async () => {
      setAiUsageContext({ userId: id, feature: "chat" });
      await new Promise((resolve) => setTimeout(resolve, (3 - index) * 5));
      await recordAiUsage(successEvent);
    })));
    assert.deepEqual([...seen].sort(), ["a", "b", "c"]);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});
