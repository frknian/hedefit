import { expandMuscleFilter, filterExercises } from "@/lib/exercise-service";
import { clientKey, rateLimit, tooManyRequests } from "@/lib/rate-limit";
import { translateExerciseLabel, translateExerciseName, turkishExerciseInstructions } from "@/lib/exercise-translations";

const safeParam = (value: string | null) => (value || "").trim().slice(0, 100);

const GROUP_LABELS: Record<string, [string, string]> = {
  dumbbell: ["Dambıl", "Dumbbell"], barbell: ["Halter", "Barbell"], kettlebell: ["Kettlebell", "Kettlebell"],
  band: ["Direnç bandı", "Resistance band"], pull_up_bar: ["Barfiks barı", "Pull-up bar"], bench: ["Sehpa", "Bench"],
  cable: ["Kablo", "Cable"], machine: ["Makine", "Machine"], suspension: ["TRX / halka", "TRX / rings"],
  stability_ball: ["Pilates topu", "Stability ball"], jump_rope: ["Atlama ipi", "Jump rope"], ab_wheel: ["Karın tekerleği", "Ab wheel"],
  plates: ["Plaka", "Weight plate"], dip_station: ["Dips istasyonu", "Dip station"], plyo_box: ["Plyo kutusu", "Plyo box"],
  gym_gear: ["Salon ekipmanı", "Gym equipment"], cardio_machine: ["Kardiyo makinesi", "Cardio machine"],
};

/** "Dambıl + Sehpa", "Kettlebell / Dambıl" for alternatives, "Ekipmansız" when nothing is needed. */
function equipmentLabel(options: string[][] | undefined, raw: string | null, locale: "tr" | "en") {
  if (!options) return translateExerciseLabel(raw, locale);
  const en = locale === "en";
  if (options.some((option) => option.length === 0)) return en ? "No equipment" : "Ekipmansız";
  return options
    .map((option) => option.map((group) => GROUP_LABELS[group]?.[en ? 1 : 0] ?? group).join(" + "))
    .join(" / ");
}

export function GET(request: Request) {
  // Herkese açık katalog: kimlik gerektirmez, ancak toplu kazımaya karşı sınırlandırılır.
  const rateLimitResult = rateLimit(`exercises:${clientKey(request)}`, 120, 60_000);
  if (!rateLimitResult.ok) return tooManyRequests(rateLimitResult.retryAfterSeconds);

  const { searchParams } = new URL(request.url);
  const locale = searchParams.get("locale") === "en" ? "en" : "tr";
  const page = Math.max(1, Math.min(1000, Number.parseInt(searchParams.get("page") || "1", 10) || 1));
  const limit = Math.max(1, Math.min(1000, Number.parseInt(searchParams.get("limit") || "24", 10) || 24));
  const environment = safeParam(searchParams.get("environment"));
  const muscle = safeParam(searchParams.get("muscle"));
  const muscleRole = safeParam(searchParams.get("muscleRole"));
  const force = safeParam(searchParams.get("force"));
  const mechanic = safeParam(searchParams.get("mechanic"));
  const muscleTargets = expandMuscleFilter(muscle);
  const owned = safeParam(searchParams.get("owned")).split(",").map((item) => item.trim()).filter(Boolean);
  const filtered = filterExercises({ search: safeParam(searchParams.get("search")), equipment: safeParam(searchParams.get("equipment")), owned, level: safeParam(searchParams.get("level")), category: safeParam(searchParams.get("category")) });
  const byMuscle = muscleTargets.length
    ? filtered.filter((item) => [...item.primaryMuscles, ...item.secondaryMuscles].some((value) => muscleTargets.includes(value)))
    : filtered;
  const byEnvironment = environment === "gym" || environment === "home"
    ? byMuscle.filter((item) => (item.environment ?? []).includes(environment))
    : byMuscle;
  const byRole = muscleRole === "primary" && muscle
    ? byEnvironment.filter((item) => item.primaryMuscles.some((value) => muscleTargets.includes(value)))
    : muscleRole === "secondary" && muscle
      ? byEnvironment.filter((item) => item.secondaryMuscles.some((value) => muscleTargets.includes(value)))
      : byEnvironment;
  const items = byRole
    .filter((item) => !force || item.force === force)
    .filter((item) => !mechanic || item.mechanic === mechanic)
    .sort((a, b) => {
      const aPrimary = a.primaryMuscles.some((value) => muscleTargets.includes(value));
      const bPrimary = b.primaryMuscles.some((value) => muscleTargets.includes(value));
      if (muscle && aPrimary !== bPrimary) return aPrimary ? -1 : 1;
      // Bölgesel atlas ve programda önce temel/bileşik hareketler görünür;
      // ardından daha hedefli izolasyon hareketleri gelir.
      const aOrder = a.mechanic === "compound" ? 0 : a.mechanic === "isolation" ? 2 : 1;
      const bOrder = b.mechanic === "compound" ? 0 : b.mechanic === "isolation" ? 2 : 1;
      return aOrder !== bOrder ? aOrder - bOrder : a.name.localeCompare(b.name);
    });
  const offset = (page - 1) * limit;
  const localized = items.slice(offset, offset + limit).map((item) => ({
    ...item,
    name: translateExerciseName(item.name, locale),
    primaryMuscles: item.primaryMuscles.map((value) => translateExerciseLabel(value, locale)),
    secondaryMuscles: item.secondaryMuscles.map((value) => translateExerciseLabel(value, locale)),
    equipment: equipmentLabel(item.requiredEquipment, item.equipment, locale),
    requiredEquipment: item.requiredEquipment ?? [],
    level: translateExerciseLabel(item.level, locale),
    levelKey: item.level,
    category: translateExerciseLabel(item.category, locale),
    instructions: turkishExerciseInstructions(item, locale),
  }));
  return Response.json({ items: localized, page, limit, total: items.length, totalPages: Math.ceil(items.length / limit), locale });
}
