// Kullanıcı başına AI maliyet telemetrisi (ai_usage_events).
//
// Yazılan: kullanıcı, özellik, plan, model, token sayıları, tahmini maliyet.
// Yazılmayan: istek/yanıt metni, bağlam, sağlık değerleri (bkz. telemetry.ts).
//
// Bağlam, Worker girişinde istek başına açılan bir AsyncLocalStorage'dan gelir;
// checkAndConsumeUsage kullanıcı/özellik/planı buraya yazar. Böylece
// routeText/routeObject çağrıları hiçbir rotada değişmeden kaydedilir.
// Worker'da waitUntil rotaya açık olmadığından kayıt yanıt öncesi, kısa bir
// zaman aşımıyla beklenir; başarısızlık AI akışını asla etkilemez.

import { AsyncLocalStorage } from "node:async_hooks";
import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../supabase/url.ts";
import { estimateCostMicroUsd } from "./pricing.ts";
import type { AiEvent } from "./telemetry.ts";

export type AiUsageContext = { userId?: string; feature?: string; planTier?: string };

const storage = new AsyncLocalStorage<AiUsageContext>();
const WRITE_TIMEOUT_MS = 1_500;

/** İstek boyunca geçerli bir (değiştirilebilir) bağlam açar. */
export function runWithAiUsageContext<T>(fn: () => T, initial: AiUsageContext = {}): T {
  return storage.run({ ...initial }, fn);
}

/** Açık bir bağlam varsa kullanıcıyı/özelliği/planı kaydeder; yoksa sessizce yok sayar. */
export function setAiUsageContext(update: AiUsageContext) {
  const store = storage.getStore();
  if (store) Object.assign(store, update);
}

export function currentAiUsageContext(): AiUsageContext | undefined {
  return storage.getStore();
}

export type AiUsageRow = {
  user_id: string;
  feature: string;
  plan_tier: string | null;
  category: string;
  model: string | null;
  outcome: "success" | "error";
  fallback_used: boolean;
  input_tokens: number | null;
  output_tokens: number | null;
  est_cost_micro_usd: number | null;
  latency_ms: number | null;
};

export function buildUsageRow(context: AiUsageContext | undefined, event: AiEvent): AiUsageRow | null {
  if (!context?.userId || !context.feature) return null;
  if (event.outcome === "skipped") return null;
  // Yerel deterministik yanıt ücretli çağrı değildir (token yok): kayıt dışı.
  if (event.outcome === "success" && event.inputTokens === undefined && event.outputTokens === undefined) return null;
  return {
    user_id: context.userId,
    feature: context.feature,
    plan_tier: context.planTier ?? null,
    category: event.category,
    model: event.model ?? null,
    outcome: event.outcome,
    fallback_used: event.fallbackUsed,
    input_tokens: event.inputTokens ?? null,
    output_tokens: event.outputTokens ?? null,
    est_cost_micro_usd: estimateCostMicroUsd(event.model, event.inputTokens, event.outputTokens),
    latency_ms: event.latencyMs ?? null,
  };
}

/** Olayı bağlamdaki kullanıcıya yazar. Asla fırlatmaz. */
export async function recordAiUsage(event: AiEvent): Promise<void> {
  try {
    const row = buildUsageRow(storage.getStore(), event);
    if (!row) return;
    const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
    const secretKey = process.env.SUPABASE_SECRET_KEY;
    if (!url || !secretKey) return;
    const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const write = admin.from("ai_usage_events").insert(row).then(({ error }) => {
      if (error) console.error("[ai-usage] insert failed", error.code);
    });
    await Promise.race([write, new Promise<void>((resolve) => setTimeout(resolve, WRITE_TIMEOUT_MS))]);
  } catch (error) {
    console.error("[ai-usage] record failed", (error as Error).message);
  }
}
