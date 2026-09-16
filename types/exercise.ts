export interface Exercise {
  id: string;
  /** English display name (RepDB `name_en` / legacy `name`). */
  name: string;
  force: string | null;
  level: string;
  mechanic: string | null;
  /** Raw source equipment tag (RepDB slug, e.g. `dumbbell`), null = bodyweight. */
  equipment: string | null;
  primaryMuscles: string[];
  secondaryMuscles: string[];
  instructions: string[];
  category: string;
  images: string[];

  // --- RepDB canonical enrichment (additive; optional so legacy rows stay valid) ---
  /** Where this row came from. Legacy rows are kept addressable so old plans never break. */
  source?: "repdb" | "legacy";
  sourceExerciseId?: string;
  nameTr?: string;
  descriptionEn?: string;
  descriptionTr?: string;
  instructionsTr?: string[];
  tipsEn?: string[];
  tipsTr?: string[];
  commonMistakesTr?: string[];
  safetyNotesTr?: string[];
  bodyPart?: string;
  /** RepDB `goals[]` mapped to Hedefit's goal vocabulary. */
  goalCompatibility?: string[];
  /** home | gym | outdoor eligibility, derived from equipment/tags. */
  environment?: string[];
  laterality?: "bilateral" | "unilateral";
  isBodyweight?: boolean;
  metValue?: number;
  imageStart?: string | null;
  imageEnd?: string | null;
  mediaStatus?: "complete" | "partial" | "missing";
  /** True when instructionsTr/nameTr are dictionary-generated, not human/AI reviewed. */
  needsTranslationReview?: boolean;
  isActive?: boolean;
}

export interface ExerciseFilters {
  search?: string;
  muscle?: string;
  equipment?: string;
  level?: string;
  category?: string;
}

export interface AIExerciseContext {
  id: string;
  name: string;
  level?: string;
  equipment?: string;
  primaryMuscles: string[];
  /** İkincil kaslar plan üretimine GÖNDERİLMEZ (token maliyeti). */
  secondaryMuscles?: string[];
  category?: string;
}
