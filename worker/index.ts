/** Cloudflare Worker entry point for the Hedefit API. */
import { withSecurityHeaders } from "../lib/security-headers";
import { runWithAiUsageContext } from "../lib/ai/usage-log";
import { runScheduledReconcile } from "../lib/billing/reconcile";
import { handleSupabaseProxy } from "./supabase-proxy";

type Env = Record<string, unknown>;

interface ExecutionContext {
  waitUntil(promise: Promise<unknown>): void;
  passThroughOnException(): void;
}

type AppRouterHandler = {
  fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response>;
};

let appRouterHandler: Promise<AppRouterHandler> | null = null;

function getAppRouterHandler() {
  appRouterHandler ??= import("vinext/server/app-router-entry").then((module) => module.default);
  return appRouterHandler;
}

const worker = {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    // Supabase auth/data trafiğini Vinext ve React route motorunu başlatmadan geçir.
    // Bu yol Cloudflare ücretsiz Worker CPU limitinin altında kalacak kadar hafiftir.
    const supabaseResponse = await handleSupabaseProxy(request);
    if (supabaseResponse) return withSecurityHeaders(supabaseResponse, { noStore: true });

    // API yanıtları kimlik doğrulamalı olduğu için hiçbir katmanda önbelleğe alınmaz.
    const handler = await getAppRouterHandler();
    // İstek başına AI maliyet bağlamı: checkAndConsumeUsage kullanıcıyı/özelliği doldurur,
    // router her model çağrısını bu bağlama yazar (lib/ai/usage-log.ts).
    const response = await runWithAiUsageContext(() => handler.fetch(request, env, ctx));
    return withSecurityHeaders(response, { noStore: url.pathname.startsWith("/api/") });
  },

  /** Günlük cron (vite.config.ts → triggers.crons): Play abonelikleri ile planları uzlaştırır. */
  async scheduled(_controller: unknown, _env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(
      runScheduledReconcile().catch((error) => console.error("[billing] scheduled reconcile failed", error instanceof Error ? error.message : "unknown")),
    );
  },
};

export default worker;
