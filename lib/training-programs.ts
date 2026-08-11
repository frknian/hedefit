// Antrenman ekranındaki program sistemi.
//
// Önceden ekranda iki ayrı şey vardı: üstte "hazır programlar" kartları, altta
// "günün antrenmanı" listesi. İkisi farklı hareketler gösterip aynı şeyi
// anlatıyor gibi duruyordu. Artık tek bir kavram var: PROGRAM.
//
//   • smart    — profil testinden yapay zekânın ürettiği program
//   • fullBody — tüm vücut; salon/ev ayrımı var
//   • split    — bölgesel; salon/ev ayrımı var
//   • custom   — kullanıcının hareket kütüphanesinden kendi kurduğu (3 slot)
//
// Salon/ev ayrımı ekipman profiline çevrilir: salonda salon kataloğu, evde
// kullanıcının GERÇEKTEN sahip olduğu ekipman (yoksa vücut ağırlığı).

import { detectUserEquipmentProfile, type EquipmentProfile } from "./ready-programs.ts";

export type ProgramKind = "smart" | "fullBody" | "split" | "custom";
export type TrainingPlace = "home" | "gym";

/** Kullanıcının kurabileceği program sayısı. */
export const CUSTOM_PROGRAM_SLOTS = 3;

/**
 * Programdaki tek bir hareketin reçetesi.
 *
 * Önceden yalnız hareket ADI saklanıyordu; set, tekrar ve dinlenme her seansta
 * profilden yeniden türetiliyordu. Kullanıcı "bu programda 4x8 çalışırım"
 * diyemiyordu. Reçete artık programın parçası ve slot ile birlikte kaydedilir.
 *
 * `reps` metindir: "8-10" gibi aralıklar ve "30 sn" gibi süreler de geçerli
 * girdilerdir; sayıya zorlamak bunları kaybettirirdi.
 */
export type ProgramExercise = {
  /** Katalogdaki hareket adı. İsimle saklanır; katalog kimlikleri değişebilir. */
  name: string;
  sets: number;
  reps: string;
  restSeconds: number;
  /** Son sette ağırlığı düşürüp devam etme (Stitch tasarımındaki "Drop Set"). */
  dropSet: boolean;
};

export const PROGRAM_EXERCISE_LIMITS = { sets: { min: 1, max: 10 }, rest: { min: 15, max: 300 }, reps: { maxLength: 12 } } as const;

export const DEFAULT_PROGRAM_EXERCISE: Omit<ProgramExercise, "name"> = { sets: 3, reps: "10", restSeconds: 60, dropSet: false };

export type CustomProgram = {
  /** Slot kimliği: "custom-1" … "custom-3". Sıra sabit kalsın diye slot bazlı. */
  id: string;
  name: string;
  /** Sıralı hareket reçeteleri. Dizi sırası antrenman sırasıdır. */
  exercises: ProgramExercise[];
  updatedAt: string;
};

const clampNumber = (value: unknown, min: number, max: number, fallback: number) => {
  const parsed = typeof value === "number" ? value : Number.parseInt(String(value ?? ""), 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.round(parsed)));
};

/** Tek bir reçeteyi güvenli sınırlara çeker; adı olmayan kayıt atılır. */
export function normalizeProgramExercise(raw: unknown): ProgramExercise | null {
  if (typeof raw === "string") {
    const name = raw.trim().slice(0, 80);
    return name ? { name, ...DEFAULT_PROGRAM_EXERCISE } : null;
  }
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const name = typeof value.name === "string" ? value.name.trim().slice(0, 80) : "";
  if (!name) return null;
  const reps = typeof value.reps === "string" || typeof value.reps === "number"
    ? String(value.reps).trim().slice(0, PROGRAM_EXERCISE_LIMITS.reps.maxLength)
    : "";
  return {
    name,
    sets: clampNumber(value.sets, PROGRAM_EXERCISE_LIMITS.sets.min, PROGRAM_EXERCISE_LIMITS.sets.max, DEFAULT_PROGRAM_EXERCISE.sets),
    reps: reps || DEFAULT_PROGRAM_EXERCISE.reps,
    restSeconds: clampNumber(value.restSeconds, PROGRAM_EXERCISE_LIMITS.rest.min, PROGRAM_EXERCISE_LIMITS.rest.max, DEFAULT_PROGRAM_EXERCISE.restSeconds),
    dropSet: value.dropSet === true,
  };
}

/** Geriye dönük okuma: eski kayıtlar `exerciseNames: string[]` tutuyordu. */
function readProgramExercises(value: Record<string, unknown>): ProgramExercise[] {
  const source = Array.isArray(value.exercises) ? value.exercises : Array.isArray(value.exerciseNames) ? value.exerciseNames : [];
  const seen = new Set<string>();
  const list: ProgramExercise[] = [];
  for (const entry of source) {
    const exercise = normalizeProgramExercise(entry);
    if (!exercise || seen.has(exercise.name)) continue;
    seen.add(exercise.name);
    list.push(exercise);
    if (list.length >= 12) break;
  }
  return list;
}

/** Hareket adları: kuyruğu kuran ve katalogla eşleştiren kod bunu kullanır. */
export const programExerciseNames = (program: CustomProgram): string[] => program.exercises.map((exercise) => exercise.name);

/** Sürükleyerek sıralama: hareketi `from` konumundan `to` konumuna taşır. */
export function moveProgramExercise(exercises: ProgramExercise[], from: number, to: number): ProgramExercise[] {
  if (from === to || from < 0 || to < 0 || from >= exercises.length || to >= exercises.length) return exercises;
  const copy = exercises.slice();
  const [moved] = copy.splice(from, 1);
  copy.splice(to, 0, moved);
  return copy;
}

/**
 * Tasarımdaki "Est: 45 min" rozeti.
 *
 * Set süresi tekrar sayısıyla ölçeklenir (tekrar başına ~3,5 sn); "30 sn" gibi
 * süre girdileri doğrudan kullanılır. Son setten sonra dinlenme sayılmaz —
 * seans orada biter.
 */
export function estimateProgramMinutes(exercises: ProgramExercise[]): number {
  let seconds = 0;
  for (const exercise of exercises) {
    const durationMatch = exercise.reps.match(/(\d+)\s*(sn|sec|s)\b/i);
    const repsMatch = exercise.reps.match(/\d+/g);
    const workSeconds = durationMatch
      ? Number(durationMatch[1])
      : Math.max(20, Math.round(Number(repsMatch?.[repsMatch.length - 1] ?? 10) * 3.5));
    seconds += exercise.sets * workSeconds + Math.max(0, exercise.sets - 1) * exercise.restSeconds;
    if (exercise.dropSet) seconds += workSeconds;
  }
  // Hareket geçişleri: her hareket arası ~45 sn hazırlık.
  seconds += Math.max(0, exercises.length - 1) * 45;
  return exercises.length ? Math.max(1, Math.round(seconds / 60)) : 0;
}

export function customSlotId(index: number): string {
  return `custom-${index + 1}`;
}

/** Salon/ev seçimini ekipman profiline çevirir. */
export function placeToProfile(place: TrainingPlace, equipmentText: string): EquipmentProfile {
  return place === "gym" ? "gym" : detectUserEquipmentProfile(false, equipmentText);
}

/**
 * Kaydedilmiş özel programları doğrular.
 *
 * Bozuk ya da eski biçimli kayıt uygulamayı çökertmemeli; tanınmayan her şey
 * atılır, slot sayısı tavanla sınırlanır.
 */
export function normalizeCustomPrograms(raw: unknown): CustomProgram[] {
  if (!Array.isArray(raw)) return [];
  const seen = new Set<string>();
  const programs: CustomProgram[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const value = item as Record<string, unknown>;
    const id = typeof value.id === "string" ? value.id : "";
    const name = typeof value.name === "string" ? value.name.trim().slice(0, 60) : "";
    const exercises = readProgramExercises(value);
    if (!id || seen.has(id) || !exercises.length) continue;
    seen.add(id);
    programs.push({
      id,
      name: name || id,
      exercises,
      updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : new Date().toISOString(),
    });
    if (programs.length >= CUSTOM_PROGRAM_SLOTS) break;
  }
  return programs;
}

/** Slotu ekler ya da aynı kimlikteki kaydı değiştirir. */
export function upsertCustomProgram(programs: CustomProgram[], next: CustomProgram): CustomProgram[] {
  const existing = programs.findIndex((program) => program.id === next.id);
  if (existing >= 0) {
    const copy = programs.slice();
    copy[existing] = next;
    return copy;
  }
  return [...programs, next].slice(0, CUSTOM_PROGRAM_SLOTS);
}

export function removeCustomProgram(programs: CustomProgram[], id: string): CustomProgram[] {
  return programs.filter((program) => program.id !== id);
}

/** Boş olan ilk slotun kimliği; hepsi doluysa null. */
export function nextFreeSlot(programs: CustomProgram[]): string | null {
  for (let index = 0; index < CUSTOM_PROGRAM_SLOTS; index += 1) {
    const id = customSlotId(index);
    if (!programs.some((program) => program.id === id)) return id;
  }
  return null;
}

// --- İlerleme takibi --------------------------------------------------------

export type ProgramProgress = {
  /** Bu programla tamamlanan seans sayısı. */
  sessions: number;
  lastCompletedAt: string | null;
};

export type ProgramLogEntry = { programKey: string; completedAt: string };

/**
 * Program anahtarı: aynı programın salon ve ev sürümü ayrı ilerler, çünkü
 * hareketleri ve yükleri farklıdır.
 */
export function programKey(kind: ProgramKind, place?: TrainingPlace, customId?: string): string {
  if (kind === "custom") return `custom:${customId ?? ""}`;
  if (kind === "smart") return "smart";
  return `${kind}:${place ?? "home"}`;
}

/** Kayıtlardan program başına seans sayısı ve son tarih. */
export function summarizeProgramProgress(entries: ProgramLogEntry[]): Record<string, ProgramProgress> {
  const summary: Record<string, ProgramProgress> = {};
  for (const entry of entries) {
    if (!entry?.programKey) continue;
    const current = summary[entry.programKey] ?? { sessions: 0, lastCompletedAt: null };
    current.sessions += 1;
    if (!current.lastCompletedAt || entry.completedAt > current.lastCompletedAt) current.lastCompletedAt = entry.completedAt;
    summary[entry.programKey] = current;
  }
  return summary;
}
