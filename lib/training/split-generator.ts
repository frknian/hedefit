// Training Split Generator

import type {
  PlannedSessionSlot,
  SplitType,
  TrainingProfile,
  TrainingSplitPlan,
} from "./types.ts";

const DAYS_TR = ["Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"];
const DAYS_EN = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

export function selectOptimalSplitType(days: number, level: string): SplitType {
  if (days <= 3) {
    return "full_body";
  }
  if (days === 4) {
    return "upper_lower";
  }
  if (days === 5) {
    return "hybrid"; // Upper/Lower + PPL hybrid
  }
  // 6 days
  if (level === "beginner") {
    return "upper_lower";
  }
  return "push_pull_legs";
}

export function generateTrainingSplit(profile: TrainingProfile, locale: "tr" | "en" = "tr"): TrainingSplitPlan {
  const days = Math.max(2, Math.min(6, profile.trainingDaysPerWeek));
  const splitType = selectOptimalSplitType(days, profile.fitnessLevel);
  const dayNames = locale === "en" ? DAYS_EN : DAYS_TR;
  const duration = profile.sessionDurationMinutes;

  const sessions: PlannedSessionSlot[] = [];

  if (splitType === "full_body") {
    // 2 or 3 Days Full Body
    const scheduleDayIndices = days === 2 ? [0, 3] : [0, 2, 4]; // Mon, Thu or Mon, Wed, Fri
    const variations: Array<{ focusName: string; focusEn: string }> = [
      { focusName: "Tüm Vücut - A (Kuvvet & İtme Odaklı)", focusEn: "Full Body A (Strength & Push Focus)" },
      { focusName: "Tüm Vücut - B (Hipertrofi & Çekme Odaklı)", focusEn: "Full Body B (Hypertrophy & Pull Focus)" },
      { focusName: "Tüm Vücut - C (Alt Vücut & Kondisyon Odaklı)", focusEn: "Full Body C (Lower Body & Conditioning)" },
    ];

    for (let i = 0; i < days; i++) {
      const dayIdx = scheduleDayIndices[i] ?? i;
      const v = variations[i % variations.length];
      sessions.push({
        dayIndex: i,
        dayName: dayNames[dayIdx],
        focus: "full_body",
        focusDisplayName: locale === "en" ? v.focusEn : v.focusName,
        durationMinutes: duration,
        muscleBudgets: [], // Filled by muscle-distribution
      });
    }

    return {
      splitType: "full_body",
      name: locale === "en" ? `${days}-Day Full Body Split` : `${days} Günlük Tüm Vücut Planı`,
      description: locale === "en"
        ? "Balanced full body stimulus with 48h recovery between sessions."
        : "Seanslar arası 48 saat toparlanma sağlayan dengeli tüm vücut uyarımı.",
      sessions,
    };
  }

  if (splitType === "upper_lower") {
    // 4 Days: Upper / Lower / Rest / Upper / Lower
    const scheduleDayIndices = [0, 1, 3, 4]; // Mon, Tue, Thu, Fri
    const splitMap: Array<{ focus: "upper" | "lower"; focusName: string; focusEn: string }> = [
      { focus: "upper", focusName: "Üst Vücut - A (Göğüs & Sırt Güç)", focusEn: "Upper Body A (Chest & Back Power)" },
      { focus: "lower", focusName: "Alt Vücut - A (Ön Bacak & Kalça)", focusEn: "Lower Body A (Quads & Glutes)" },
      { focus: "upper", focusName: "Üst Vücut - B (Omuz, Kol & Sırt)", focusEn: "Upper Body B (Shoulders, Arms & Lats)" },
      { focus: "lower", focusName: "Alt Vücut - B (Arka Bacak & Kalf)", focusEn: "Lower Body B (Hamstrings & Calves)" },
    ];

    for (let i = 0; i < days; i++) {
      const dayIdx = scheduleDayIndices[i] ?? i;
      const item = splitMap[i % splitMap.length];
      sessions.push({
        dayIndex: i,
        dayName: dayNames[dayIdx],
        focus: item.focus,
        focusDisplayName: locale === "en" ? item.focusEn : item.focusName,
        durationMinutes: duration,
        muscleBudgets: [],
      });
    }

    return {
      splitType: "upper_lower",
      name: locale === "en" ? "4-Day Upper / Lower Split" : "4 Günlük Üst / Alt Vücut Bölünmesi",
      description: locale === "en"
        ? "Optimal 2x frequency per muscle group with dedicated upper and lower recovery."
        : "Üst ve alt vücut için ayrı toparlanma ve haftalık 2x kas frekansı.",
      sessions,
    };
  }

  if (splitType === "push_pull_legs") {
    // 6 Days: Push / Pull / Legs / Push / Pull / Legs
    const splitMap: Array<{ focus: "push" | "pull" | "legs"; focusName: string; focusEn: string }> = [
      { focus: "push", focusName: "İtme - A (Göğüs, Ön Omuz, Triceps)", focusEn: "Push A (Chest, Front Delts, Triceps)" },
      { focus: "pull", focusName: "Çekme - A (Sırt, Arka Omuz, Biceps)", focusEn: "Pull A (Back, Rear Delts, Biceps)" },
      { focus: "legs", focusName: "Bacak - A (Quad & Kalf Odaklı)", focusEn: "Legs A (Quad & Calf Focus)" },
      { focus: "push", focusName: "İtme - B (Omuz & Üst Göğüs Odaklı)", focusEn: "Push B (Shoulders & Incline Chest)" },
      { focus: "pull", focusName: "Çekme - B (Kanat & Kol Odaklı)", focusEn: "Pull B (Lats & Arm Focus)" },
      { focus: "legs", focusName: "Bacak - B (Hamstring & Glute Odaklı)", focusEn: "Legs B (Hamstring & Glute Focus)" },
    ];

    for (let i = 0; i < days; i++) {
      const item = splitMap[i % splitMap.length];
      sessions.push({
        dayIndex: i,
        dayName: dayNames[i],
        focus: item.focus,
        focusDisplayName: locale === "en" ? item.focusEn : item.focusName,
        durationMinutes: duration,
        muscleBudgets: [],
      });
    }

    return {
      splitType: "push_pull_legs",
      name: locale === "en" ? "6-Day Push / Pull / Legs (PPL)" : "6 Günlük İtme / Çekme / Bacak (PPL)",
      description: locale === "en"
        ? "Advanced high-frequency hypertrophy routine grouping synergistic muscle patterns."
        : "Sinerjik kas gruplarını toplayan yüksek frekanslı ileri seviye hipertrofi planı.",
      sessions,
    };
  }

  // Hybrid Split: 5 Days (Push / Pull / Legs / Upper / Lower)
  const hybridMap: Array<{ focus: "push" | "pull" | "legs" | "upper" | "lower"; focusName: string; focusEn: string }> = [
    { focus: "push", focusName: "İtme (Göğüs, Omuz, Triceps)", focusEn: "Push (Chest, Shoulders, Triceps)" },
    { focus: "pull", focusName: "Çekme (Sırt, Biceps, Arka Omuz)", focusEn: "Pull (Back, Biceps, Rear Delts)" },
    { focus: "legs", focusName: "Bacak (Tüm Bacak & Kalf)", focusEn: "Legs (Full Legs & Calves)" },
    { focus: "upper", focusName: "Üst Vücut (Bileşik Kuvvet)", focusEn: "Upper Body (Compound Strength)" },
    { focus: "lower", focusName: "Alt Vücut & Core (Mobilite & Güç)", focusEn: "Lower Body & Core (Mobility & Power)" },
  ];

  for (let i = 0; i < days; i++) {
    const item = hybridMap[i % hybridMap.length];
    sessions.push({
      dayIndex: i,
      dayName: dayNames[i],
      focus: item.focus,
      focusDisplayName: locale === "en" ? item.focusEn : item.focusName,
      durationMinutes: duration,
      muscleBudgets: [],
    });
  }

  return {
    splitType: "hybrid",
    name: locale === "en" ? "5-Day PPL + Upper/Lower Hybrid" : "5 Günlük PPL + Üst/Alt Hibrit Planı",
    description: locale === "en"
      ? "Comprehensive 5-day split maximizing weekly frequency while safeguarding joint recovery."
      : "Eklem toparlanmasını korurken haftalık kas uyarımını maksimize eden 5 günlük hibrit plan.",
    sessions,
  };
}
