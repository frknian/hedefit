// Plan variety: which exercises the person did recently, and a seed that makes
// accessory choices change from block to block while staying reproducible
// inside one block (so regenerating a plan twice in a row never reshuffles it).

import { createClient } from "@supabase/supabase-js";
import { bearerToken } from "../api-auth.ts";
import { normalizeSupabaseUrl } from "../supabase/url.ts";

/**
 * How often the person wants a fresh block. Blocks are calendar-aligned so
 * "weekly" means "new every Monday" and "monthly" means "new every 1st".
 */
export type RotationPeriod = "weekly" | "monthly";
export const DEFAULT_ROTATION_PERIOD: RotationPeriod = "monthly";

export const normalizeRotationPeriod = (value: unknown): RotationPeriod => (value === "weekly" ? "weekly" : DEFAULT_ROTATION_PERIOD);

const DAY_MS = 86_400_000;
// 1970-01-05 (epoch day 4) is a Monday: weekly blocks start there.
const FIRST_MONDAY_EPOCH_DAY = 4;

const epochDay = (date: Date) => Math.floor(date.getTime() / DAY_MS);
const isoDay = (date: Date) => date.toISOString().slice(0, 10);

/**
 * The person's own calendar date as a UTC-midnight Date. Clients send
 * `localDate` ("YYYY-MM-DD") so a block rolls over at THEIR midnight, not the
 * server's; values more than two days from now are ignored (clock skew / abuse).
 */
export function resolveRotationDate(localDate: unknown, now: Date = new Date()): Date {
  const today = new Date(`${isoDay(now)}T00:00:00.000Z`);
  if (typeof localDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(localDate)) return today;
  const parsed = new Date(`${localDate}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime()) || isoDay(parsed) !== localDate) return today;
  return Math.abs(parsed.getTime() - today.getTime()) <= 2 * DAY_MS ? parsed : today;
}

/** Monotonic block number: weeks since the first Monday, or months since year 0. */
export function rotationBlockId(period: RotationPeriod, date: Date): number {
  if (period === "weekly") return Math.floor((epochDay(date) - FIRST_MONDAY_EPOCH_DAY) / 7);
  return date.getUTCFullYear() * 12 + date.getUTCMonth();
}

function blockStart(period: RotationPeriod, blockId: number): Date {
  if (period === "weekly") return new Date((blockId * 7 + FIRST_MONDAY_EPOCH_DAY) * DAY_MS);
  return new Date(Date.UTC(Math.floor(blockId / 12), blockId % 12, 1));
}

export type RotationInfo = { period: RotationPeriod; blockId: number; startsAt: string; nextBlockAt: string };

export function rotationInfo(period: RotationPeriod, date: Date): RotationInfo {
  const blockId = rotationBlockId(period, date);
  return { period, blockId, startsAt: isoDay(blockStart(period, blockId)), nextBlockAt: isoDay(blockStart(period, blockId + 1)) };
}

/** True once a plan generated at `generatedAt` belongs to an earlier block than `now`. */
export function isNewBlockDue(period: RotationPeriod, generatedAt: Date | null, now: Date): boolean {
  if (!generatedAt) return false;
  return rotationBlockId(period, now) > rotationBlockId(period, generatedAt);
}

/**
 * Seed is per person, per period and per block. `override` lets a client ask
 * for a fresh variation inside the current block (e.g. a "refresh" button).
 */
export function rotationSeed(userId: string, period: RotationPeriod, date: Date, override?: unknown): string {
  const suffix = typeof override === "string" && override.trim() ? override.trim().slice(0, 40) : String(rotationBlockId(period, date));
  return `${userId}:${period}:${suffix}`;
}

/** Deterministic 0 ≤ x < 1 from a seed and an exercise id (FNV-1a). */
export function seededUnit(seed: string, id: string): number {
  let hash = 2166136261;
  for (const character of `${seed}|${id}`) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 16777619) >>> 0;
  }
  return hash / 4294967296;
}

export const RECENT_WINDOW_WEEKS = 8;
const RECENT_LIMIT = 400;
// The history read sits on the plan-generation path and is only an
// improvement: no retries (the client would back off 1+2+4 s on a transient
// failure) and a short cut-off, then plan without it.
const RECENT_TIMEOUT_MS = 2_500;

/**
 * Exercise ids the person logged in the last weeks, from their own
 * workout_exercise_logs (RLS-scoped via their token). Never throws: variety is
 * an improvement, not a precondition for generating a plan.
 */
export async function loadRecentExerciseIds(request: Request, now: Date = new Date()): Promise<string[]> {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  const token = bearerToken(request);
  if (!url || !anonKey || !token) return [];
  try {
    const client = createClient(url, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { Authorization: `Bearer ${token}` } },
    });
    const since = new Date(now.getTime() - RECENT_WINDOW_WEEKS * 7 * DAY_MS).toISOString();
    const { data, error } = await client
      .from("workout_exercise_logs")
      .select("exercise_id")
      .gte("completed_at", since)
      .order("completed_at", { ascending: false })
      .limit(RECENT_LIMIT)
      .retry(false)
      .abortSignal(AbortSignal.timeout(RECENT_TIMEOUT_MS));
    if (error || !Array.isArray(data)) return [];
    return [...new Set((data as Array<{ exercise_id: string | null }>).map((row) => row.exercise_id).filter((id): id is string => typeof id === "string" && id.length > 0))];
  } catch {
    return [];
  }
}
