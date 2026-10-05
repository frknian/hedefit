// Katalog satırının API çıktısına çevrilmesi (dil, etiketler, yazılmış talimatlar) ve modalite filtresi.
// Rotadan ayrıldı ki node testleri `@/` takma adına ihtiyaç duymadan doğrulayabilsin.

import type { Exercise } from "../types/exercise";
import { SUBCATEGORY_LABELS, type Modality } from "./exercise-modality.ts";
import { translateExerciseLabel, translateExerciseName, turkishExerciseInstructions } from "./exercise-translations.ts";

const GROUP_LABELS: Record<string, [string, string]> = {
  dumbbell: ["Dambıl", "Dumbbell"], barbell: ["Halter", "Barbell"], kettlebell: ["Kettlebell", "Kettlebell"],
  band: ["Direnç bandı", "Resistance band"], pull_up_bar: ["Barfiks barı", "Pull-up bar"], bench: ["Sehpa", "Bench"],
  cable: ["Kablo", "Cable"], machine: ["Makine", "Machine"], suspension: ["TRX / halka", "TRX / rings"],
  stability_ball: ["Pilates topu", "Stability ball"], jump_rope: ["Atlama ipi", "Jump rope"], ab_wheel: ["Karın tekerleği", "Ab wheel"],
  plates: ["Plaka", "Weight plate"], dip_station: ["Dips istasyonu", "Dip station"], plyo_box: ["Plyo kutusu", "Plyo box"],
  gym_gear: ["Salon ekipmanı", "Gym equipment"], cardio_machine: ["Kardiyo makinesi", "Cardio machine"],
};

/** "Dambıl + Sehpa", "Kettlebell / Dambıl" for alternatives, "Ekipmansız" when nothing is needed. */
export function equipmentLabel(options: string[][] | undefined, raw: string | null, locale: "tr" | "en") {
  if (!options) return translateExerciseLabel(raw, locale);
  const en = locale === "en";
  if (options.some((option) => option.length === 0)) return en ? "No equipment" : "Ekipmansız";
  return options
    .map((option) => option.map((group) => GROUP_LABELS[group]?.[en ? 1 : 0] ?? group).join(" + "))
    .join(" / ");
}


export function filterByModality(items: Exercise[], modality: Modality, subcategory: string): Exercise[] {
  return items.filter((item) => item.modalities?.includes(modality) && (!subcategory || item.subcategories?.includes(subcategory)));
}

export function presentExercise(item: Exercise, locale: "tr" | "en") {
  return {
    ...item,
    name: translateExerciseName(item.name, locale),
    primaryMuscles: item.primaryMuscles.map((value) => translateExerciseLabel(value, locale)),
    secondaryMuscles: item.secondaryMuscles.map((value) => translateExerciseLabel(value, locale)),
    equipment: equipmentLabel(item.requiredEquipment, item.equipment, locale),
    requiredEquipment: item.requiredEquipment ?? [],
    level: translateExerciseLabel(item.level, locale),
    levelKey: item.level,
    category: translateExerciseLabel(item.category, locale),
    // Hedefit'e özel hareketlerin kendi yazılmış talimatı vardır; diğerleri şablondan üretilir.
    instructions: item.source === "wellness" ? (locale === "en" ? item.instructions : (item.instructionsTr?.length ? item.instructionsTr : item.instructions)) : turkishExerciseInstructions(item, locale),
    modalities: item.modalities ?? [],
    subcategories: item.subcategories ?? [],
    subcategoryLabels: (item.subcategories ?? []).map((value) => SUBCATEGORY_LABELS[value]?.[locale] ?? value),
    impact: item.impact ?? null,
    description: locale === "en" ? item.descriptionEn ?? null : item.descriptionTr ?? item.descriptionEn ?? null,
    tips: locale === "en" ? item.tipsEn ?? [] : item.tipsTr ?? item.tipsEn ?? [],
  };
}
