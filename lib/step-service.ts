import type { SupabaseClient } from "@supabase/supabase-js";

export type DailyStepEntry = {
  localDate: string;
  steps: number;
  source: "device" | "manual";
  syncedAt: string;
};

type DailyStepRow = {
  local_date: string;
  steps: number;
  source: "device" | "manual";
  synced_at: string;
};

function mapStepRow(row: DailyStepRow): DailyStepEntry {
  return { localDate: row.local_date, steps: Number(row.steps), source: row.source, syncedAt: row.synced_at };
}

export interface StepRepository {
  upsertToday(localDate: string, steps: number, source?: "device" | "manual"): Promise<DailyStepEntry>;
  list(days?: number): Promise<DailyStepEntry[]>;
}

export function createStepRepository(client: SupabaseClient, userId: string): StepRepository {
  return {
    async upsertToday(localDate, steps, source = "device") {
      const { data, error } = await client
        .from("daily_steps")
        .upsert(
          { user_id: userId, local_date: localDate, steps: Math.max(0, Math.round(steps)), source, synced_at: new Date().toISOString(), updated_at: new Date().toISOString() },
          { onConflict: "user_id,local_date" },
        )
        .select("*")
        .single();
      if (error || !data) throw new Error(error?.message || "Adım sayısı kaydedilemedi");
      return mapStepRow(data as DailyStepRow);
    },
    async list(days = 7) {
      const { data, error } = await client
        .from("daily_steps")
        .select("*")
        .eq("user_id", userId)
        .order("local_date", { ascending: false })
        .limit(Math.max(1, Math.min(60, days)));
      if (error) throw new Error(error.message);
      return (data || []).map((row) => mapStepRow(row as DailyStepRow));
    },
  };
}
