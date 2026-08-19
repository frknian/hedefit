import type { SupabaseClient } from "@supabase/supabase-js";

export type CoreActivityKind = "walking" | "running" | "cycling" | "swimming";
export type ActivitySource = "manual" | "gps" | "strava" | "wearable";
export type HeartRateSource = "ble" | "health";

export type ActivityEntry = {
  id: string;
  activityType: "walk" | "sport";
  activityKey: string;
  activityName: string;
  occurredAt: string;
  localDate: string;
  durationMinutes: number;
  distanceKm: number | null;
  estimatedCalories: number | null;
  steps: number | null;
  intensity: "hafif" | "orta" | "yuksek";
  notes: string | null;
  source: ActivitySource;
  provider: string | null;
  externalActivityId: string | null;
  routeReference: string | null;
  metadata: Record<string, unknown>;
  schemaVersion: number;
  avgSpeedKmh: number | null;
  maxSpeedKmh: number | null;
  avgHeartRate: number | null;
  maxHeartRate: number | null;
  heartRateSource: HeartRateSource | null;
};

export type CreateActivityInput = Omit<ActivityEntry, "id" | "source" | "provider" | "externalActivityId" | "routeReference" | "metadata" | "schemaVersion" | "avgSpeedKmh" | "maxSpeedKmh" | "avgHeartRate" | "maxHeartRate" | "heartRateSource"> & {
  details?: Record<string, number>;
};

export type ActivityRoute = {
  activityEntryId: string;
  encodedPolyline: string;
  pointCount: number;
  startedAt: string | null;
  endedAt: string | null;
};

export type CreateGpsActivityInput = Omit<CreateActivityInput, "activityType"> & {
  activityType: "walk" | "sport";
  avgSpeedKmh?: number | null;
  maxSpeedKmh?: number | null;
  avgHeartRate?: number | null;
  maxHeartRate?: number | null;
  heartRateSource?: HeartRateSource | null;
  encodedPolyline: string;
  pointCount: number;
  startedAt: string;
  endedAt: string;
};

export type DailyActivitySummary = {
  localDate: string;
  activityCount: number;
  durationMinutes: number;
  distanceKm: number;
  estimatedCalories: number;
  steps: number;
};

export interface ActivityRepository {
  create(input: CreateActivityInput): Promise<ActivityEntry>;
  list(limit?: number): Promise<ActivityEntry[]>;
  createWithRoute(input: CreateGpsActivityInput): Promise<ActivityEntry>;
  getRoute(activityEntryId: string): Promise<ActivityRoute | null>;
}

type ActivityRow = {
  id: string;
  activity_type: "walk" | "sport";
  sport_key: string;
  sport_name: string;
  occurred_at: string;
  local_date: string;
  duration_minutes: number;
  distance_km: number | null;
  estimated_calories: number | null;
  steps: number | null;
  intensity: "hafif" | "orta" | "yuksek";
  notes: string | null;
  source: ActivitySource;
  provider: string | null;
  external_activity_id: string | null;
  route_reference: string | null;
  metadata: Record<string, unknown> | null;
  schema_version: number;
  avg_speed_kmh: number | null;
  max_speed_kmh: number | null;
  avg_heart_rate: number | null;
  max_heart_rate: number | null;
  heart_rate_source: HeartRateSource | null;
};

type ActivityRouteRow = {
  activity_entry_id: string;
  encoded_polyline: string;
  point_count: number;
  started_at: string | null;
  ended_at: string | null;
};

function mapActivityRow(row: ActivityRow): ActivityEntry {
  return {
    id: row.id,
    activityType: row.activity_type,
    activityKey: row.sport_key,
    activityName: row.sport_name,
    occurredAt: row.occurred_at,
    localDate: row.local_date,
    durationMinutes: Number(row.duration_minutes),
    distanceKm: row.distance_km === null ? null : Number(row.distance_km),
    estimatedCalories: row.estimated_calories === null ? null : Number(row.estimated_calories),
    steps: row.steps === null ? null : Number(row.steps),
    intensity: row.intensity,
    notes: row.notes,
    source: row.source,
    provider: row.provider,
    externalActivityId: row.external_activity_id,
    routeReference: row.route_reference,
    metadata: row.metadata || {},
    schemaVersion: Number(row.schema_version) || 1,
    avgSpeedKmh: row.avg_speed_kmh === null || row.avg_speed_kmh === undefined ? null : Number(row.avg_speed_kmh),
    maxSpeedKmh: row.max_speed_kmh === null || row.max_speed_kmh === undefined ? null : Number(row.max_speed_kmh),
    avgHeartRate: row.avg_heart_rate === null || row.avg_heart_rate === undefined ? null : Number(row.avg_heart_rate),
    maxHeartRate: row.max_heart_rate === null || row.max_heart_rate === undefined ? null : Number(row.max_heart_rate),
    heartRateSource: row.heart_rate_source || null,
  };
}

function mapActivityRouteRow(row: ActivityRouteRow): ActivityRoute {
  return {
    activityEntryId: row.activity_entry_id,
    encodedPolyline: row.encoded_polyline,
    pointCount: Number(row.point_count) || 0,
    startedAt: row.started_at,
    endedAt: row.ended_at,
  };
}

export function createActivityRepository(client: SupabaseClient, userId: string): ActivityRepository {
  return {
    async create(input) {
      const { data, error } = await client.from("sport_activity_entries").insert({
        user_id: userId,
        activity_type: input.activityType,
        sport_key: input.activityKey,
        sport_name: input.activityName,
        occurred_at: input.occurredAt,
        local_date: input.localDate,
        duration_minutes: input.durationMinutes,
        intensity: input.intensity,
        distance_km: input.distanceKm,
        estimated_calories: input.estimatedCalories,
        steps: input.steps,
        notes: input.notes,
        details: input.details || {},
        source: "manual",
        provider: null,
        external_activity_id: null,
        route_reference: null,
        metadata: {},
        schema_version: 1,
      }).select("*").single();
      if (error || !data) throw new Error(error?.message || "Aktivite kaydedilemedi");
      return mapActivityRow(data as ActivityRow);
    },
    async list(limit = 40) {
      const { data, error } = await client.from("sport_activity_entries").select("*").eq("user_id", userId).order("occurred_at", { ascending: false }).limit(Math.max(1, Math.min(100, limit)));
      if (error) throw new Error(error.message);
      return (data || []).map((row) => mapActivityRow(row as ActivityRow));
    },
    async createWithRoute(input) {
      const { data, error } = await client.rpc("create_gps_activity_entry", {
        p_activity_type: input.activityType,
        p_sport_key: input.activityKey,
        p_sport_name: input.activityName,
        p_occurred_at: input.occurredAt,
        p_local_date: input.localDate,
        p_duration_minutes: input.durationMinutes,
        p_intensity: input.intensity,
        p_distance_km: input.distanceKm,
        p_estimated_calories: input.estimatedCalories,
        p_steps: input.steps,
        p_notes: input.notes,
        p_details: input.details || {},
        p_avg_speed_kmh: input.avgSpeedKmh ?? null,
        p_max_speed_kmh: input.maxSpeedKmh ?? null,
        p_avg_heart_rate: input.avgHeartRate ?? null,
        p_max_heart_rate: input.maxHeartRate ?? null,
        p_heart_rate_source: input.heartRateSource ?? null,
        p_encoded_polyline: input.encodedPolyline,
        p_point_count: input.pointCount,
        p_started_at: input.startedAt,
        p_ended_at: input.endedAt,
      }).single();
      if (error || !data) throw new Error(error?.message || "Rota kaydedilemedi");
      return mapActivityRow(data as ActivityRow);
    },
    async getRoute(activityEntryId) {
      const { data, error } = await client.from("activity_routes").select("*").eq("activity_entry_id", activityEntryId).eq("user_id", userId).maybeSingle();
      if (error) throw new Error(error.message);
      return data ? mapActivityRouteRow(data as ActivityRouteRow) : null;
    },
  };
}

export function summarizeActivities(entries: ActivityEntry[]): DailyActivitySummary[] {
  const byDate = new Map<string, DailyActivitySummary>();
  for (const entry of entries) {
    const summary = byDate.get(entry.localDate) || { localDate: entry.localDate, activityCount: 0, durationMinutes: 0, distanceKm: 0, estimatedCalories: 0, steps: 0 };
    summary.activityCount += 1;
    summary.durationMinutes += Math.max(0, entry.durationMinutes || 0);
    summary.distanceKm += Math.max(0, entry.distanceKm || 0);
    summary.estimatedCalories += Math.max(0, entry.estimatedCalories || 0);
    summary.steps += Math.max(0, entry.steps || 0);
    byDate.set(entry.localDate, summary);
  }
  return [...byDate.values()].sort((a, b) => b.localDate.localeCompare(a.localDate));
}
