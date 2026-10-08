// Challenge veri erişimi. Her çağrı KULLANICININ KENDİ jetonuyla yapılır (RLS); yazımlar yalnızca doğrulayan
// RPC'ler üzerinden (bkz. supabase/migrations/20261008120000_challenge_system.sql). Yanıtlar loglanmaz.

import type { SupabaseClient } from "@supabase/supabase-js";
import { isMissingTable } from "../health-store.ts";
import type { ChallengePlan } from "./catalog.ts";
import { challengeState, type ChallengeState, type DayLog, type DayStatus } from "./engine.ts";
import { mergeXpRules, type XpRules } from "./xp-rules.ts";

export interface UserChallenge {
  id: string;
  templateKey: string;
  category: string;
  plan: ChallengePlan;
  status: "active" | "completed" | "abandoned";
  socialChallengeId: string | null;
  startedOn: string;
  startedAt: string;
  completedAt: string | null;
  days: DayLog[];
  state: ChallengeState;
}

type ChallengeRow = {
  id: string; template_key: string; category: string; plan: ChallengePlan; total_days: number; recovery_allowance: number;
  status: UserChallenge["status"]; social_challenge_id: string | null; started_on: string; started_at: string; completed_at: string | null;
};
type DayRow = { user_challenge_id: string; day_index: number; local_date: string; status: DayStatus; minutes: number | null };

/** Bilinen RPC hata metinlerini istemcinin yerelleştirdiği makine kodlarına çevirir. */
const KNOWN_ERRORS = new Set([
  "already_joined", "too_many_active", "invalid_plan", "invalid_day", "not_found", "not_active", "invalid_status",
  "task_not_verified", "adaptation_not_allowed", "no_recovery_left", "not_a_participant", "account_inactive",
  "not_friends", "no_friends", "invalid_mode", "invalid_metric", "invalid_template",
]);

export function errorCode(error: { message?: string; code?: string } | null | undefined): string {
  if (!error) return "challenge_failed";
  if (isMissingTable(error) || error.code === "PGRST202" || error.code === "42883") return "challenges_unavailable";
  const message = (error.message ?? "").trim();
  return KNOWN_ERRORS.has(message) ? message : "challenge_failed";
}

export function statusFor(code: string): number {
  if (code === "challenges_unavailable") return 503;
  if (code === "not_found") return 404;
  if (code === "already_joined") return 409;
  if (code === "not_a_participant" || code === "not_friends" || code === "account_inactive") return 403;
  if (code === "challenge_failed") return 500;
  if (code === "invalid_body" || code === "unknown_action") return 400;
  return 422;
}

export async function loadXpRules(client: SupabaseClient): Promise<XpRules> {
  const { data } = await client.from("xp_rules").select("source, amount, daily_capped");
  return mergeXpRules(data as { source: string; amount: number; daily_capped: boolean }[] | null);
}

export async function loadParticipantCounts(client: SupabaseClient): Promise<Record<string, number>> {
  const { data, error } = await client.rpc("hedefit_challenge_participant_counts");
  if (error || !Array.isArray(data)) return {};
  return Object.fromEntries((data as { template_key: string; participants: number }[]).map((row) => [row.template_key, Number(row.participants) || 0]));
}

function toUserChallenge(row: ChallengeRow, days: DayRow[], todayIso: string): UserChallenge {
  const logs: DayLog[] = days
    .filter((day) => day.user_challenge_id === row.id)
    .sort((a, b) => a.day_index - b.day_index)
    .map((day) => ({ dayIndex: day.day_index, localDate: day.local_date, status: day.status, minutes: day.minutes }));
  return {
    id: row.id,
    templateKey: row.template_key,
    category: row.category,
    plan: row.plan,
    status: row.status,
    socialChallengeId: row.social_challenge_id,
    startedOn: row.started_on,
    startedAt: row.started_at,
    completedAt: row.completed_at,
    days: logs,
    state: challengeState(row.total_days, logs, todayIso),
  };
}

const COLUMNS = "id, template_key, category, plan, total_days, recovery_allowance, status, social_challenge_id, started_on, started_at, completed_at";

/** Aktif challenge'lar + son 20 biten/bırakılan (geçmiş için). */
export async function loadUserChallenges(client: SupabaseClient, userId: string, todayIso: string): Promise<{ ok: true; value: UserChallenge[] } | { ok: false; code: string }> {
  const { data, error } = await client.from("user_challenges").select(COLUMNS).eq("user_id", userId).order("started_at", { ascending: false }).limit(40);
  if (error) return { ok: false, code: errorCode(error) };
  const rows = (data ?? []) as ChallengeRow[];
  const active = rows.filter((row) => row.status === "active");
  const history = rows.filter((row) => row.status !== "active").slice(0, 20);
  const selected = [...active, ...history];
  if (!selected.length) return { ok: true, value: [] };
  const days = await client.from("user_challenge_days").select("user_challenge_id, day_index, local_date, status, minutes").in("user_challenge_id", selected.map((row) => row.id));
  if (days.error) return { ok: false, code: errorCode(days.error) };
  return { ok: true, value: selected.map((row) => toUserChallenge(row, (days.data ?? []) as DayRow[], todayIso)) };
}

export async function loadUserChallenge(client: SupabaseClient, userId: string, id: string, todayIso: string): Promise<{ ok: true; value: UserChallenge } | { ok: false; code: string }> {
  const { data, error } = await client.from("user_challenges").select(COLUMNS).eq("user_id", userId).eq("id", id).maybeSingle();
  if (error) return { ok: false, code: errorCode(error) };
  if (!data) return { ok: false, code: "not_found" };
  const days = await client.from("user_challenge_days").select("user_challenge_id, day_index, local_date, status, minutes").eq("user_challenge_id", id);
  if (days.error) return { ok: false, code: errorCode(days.error) };
  return { ok: true, value: toUserChallenge(data as ChallengeRow, (days.data ?? []) as DayRow[], todayIso) };
}

export async function joinChallenge(client: SupabaseClient, plan: ChallengePlan, localDate: string, socialChallengeId: string | null = null): Promise<{ ok: true; id: string } | { ok: false; code: string }> {
  const { data, error } = await client.rpc("hedefit_join_challenge", { p_plan: plan, p_local_date: localDate, p_social_challenge_id: socialChallengeId });
  if (error) return { ok: false, code: errorCode(error) };
  return { ok: true, id: String(data) };
}
