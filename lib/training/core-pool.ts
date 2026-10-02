// Core exercise pool: the curated, slot-organised subset of the atlas that plan
// generation draws from (data/core-exercises.json, built by
// scripts/build-core-exercises.mjs). The 601-move atlas stays the reference for
// search, manual logging and history; the pool only guides plan selection.

import corePoolData from "../../data/core-exercises.json" with { type: "json" };

export type CoreRole = "main" | "accessory" | "core" | "conditioning";

export type CoreEntry = {
  id: string;
  slot: string;
  role: CoreRole;
  /** "staple" = the first two moves of a slot; preferred when no rotation seed is given. */
  tier: "staple" | "variation";
};

type RawPool = {
  slots: Array<{ key: string; role: CoreRole; exercises: Array<{ id: string; tier: "staple" | "variation" }> }>;
  warmup: Array<{ id: string }>;
  cooldown: Array<{ id: string }>;
};

const entries = new Map<string, CoreEntry>();
for (const slot of (corePoolData as RawPool).slots) {
  for (const exercise of slot.exercises) entries.set(exercise.id, { id: exercise.id, slot: slot.key, role: slot.role, tier: exercise.tier });
}

export const getCoreEntry = (id: string): CoreEntry | undefined => entries.get(id);
export const isCoreExercise = (id: string): boolean => entries.has(id);
export const coreExerciseIds = (): string[] => [...entries.keys()];
export const coreWarmupIds = (): string[] => (corePoolData as RawPool).warmup.map((exercise) => exercise.id);
export const coreCooldownIds = (): string[] => (corePoolData as RawPool).cooldown.map((exercise) => exercise.id);

/** Moves sharing a slot are interchangeable: used to rotate and to pick replacements. */
export function coreSlotMates(id: string): string[] {
  const slot = entries.get(id)?.slot;
  if (!slot) return [];
  return [...entries.values()].filter((entry) => entry.slot === slot && entry.id !== id).map((entry) => entry.id);
}
