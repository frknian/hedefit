// Biomechanical Regression and Progression Chains for Exercises
//
// RepDB ids (kebab-case slugs) replace the old, hand-curated free-exercise-db
// chains. Rather than hardcode ~600 exercises' worth of ids again, chains are
// derived at runtime from the standardized catalog: same movement pattern,
// tiered by difficulty then compound-before-isolation, alphabetical tie-break.
// This also means a future catalog refresh (new RepDB release) never leaves a
// chain pointing at a deleted id.

import { getStandardizedCatalog } from "./exercise-metadata.ts";
import type { MovementPattern, StandardizedExercise } from "./types.ts";

const DIFFICULTY_RANK: Record<StandardizedExercise["difficulty"], number> = {
  beginner: 0,
  intermediate: 1,
  advanced: 2,
};

function chainSortKey(exercise: StandardizedExercise): number {
  // Bodyweight-only compound movements anchor the easiest tier regardless of
  // the source's raw difficulty label; loaded/isolation variants progress up.
  const equipmentPenalty = exercise.equipment.includes("bodyweight") ? 0 : 1;
  const mechanicPenalty = exercise.compoundOrIsolation === "isolation" ? 0.5 : 0;
  return DIFFICULTY_RANK[exercise.difficulty] + equipmentPenalty * 0.25 + mechanicPenalty;
}

let cachedChains: Record<MovementPattern, string[]> | null = null;

function buildChains(): Record<MovementPattern, string[]> {
  const byPattern = new Map<MovementPattern, StandardizedExercise[]>();
  for (const exercise of getStandardizedCatalog()) {
    const bucket = byPattern.get(exercise.movementPattern);
    if (bucket) bucket.push(exercise);
    else byPattern.set(exercise.movementPattern, [exercise]);
  }
  const chains = {} as Record<MovementPattern, string[]>;
  for (const [pattern, list] of byPattern) {
    chains[pattern] = [...list]
      .sort((a, b) => chainSortKey(a) - chainSortKey(b) || a.name.localeCompare(b.name))
      .map((exercise) => exercise.id);
  }
  return chains;
}

function getChains(): Record<MovementPattern, string[]> {
  if (!cachedChains) cachedChains = buildChains();
  return cachedChains;
}

/**
 * Finds the position of an exercise in its pattern chain
 */
export function findInChain(exerciseId: string): { chainKey: string; index: number; chain: string[] } | null {
  const normalizedId = exerciseId.trim().toLowerCase();
  for (const [key, chain] of Object.entries(getChains())) {
    const idx = chain.findIndex((id) => id.toLowerCase() === normalizedId || id.toLowerCase().replace(/[-_]/g, "") === normalizedId.replace(/[-_]/g, ""));
    if (idx !== -1) {
      return { chainKey: key, index: idx, chain };
    }
  }
  return null;
}

/**
 * Finds a regression (easier variation) for an exercise.
 */
export function getRegressionExerciseId(exerciseId: string): string | null {
  const found = findInChain(exerciseId);
  if (found && found.index > 0) {
    return found.chain[found.index - 1];
  }
  return null;
}

/**
 * Finds a progression (harder variation) for an exercise.
 */
export function getProgressionExerciseId(exerciseId: string): string | null {
  const found = findInChain(exerciseId);
  if (found && found.index < found.chain.length - 1) {
    return found.chain[found.index + 1];
  }
  return null;
}
