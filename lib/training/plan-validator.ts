// Plan Validator: Validates generated training plans against 13 core physiological rules

import type {
  ALL_MUSCLE_GROUPS,
  MuscleGroup,
  PlannedWorkoutSession,
  TrainingProfile,
  ValidationIssue,
  ValidationResult,
  WeeklyVolumeTargets,
} from "./types.ts";

export function validatePlan(
  sessions: PlannedWorkoutSession[],
  profile: TrainingProfile,
  targets: WeeklyVolumeTargets,
): ValidationResult {
  const issues: ValidationIssue[] = [];

  const weeklySetsByMuscle: Record<MuscleGroup, number> = {
    chest: 0,
    back: 0,
    shoulders: 0,
    quadriceps: 0,
    hamstrings: 0,
    glutes: 0,
    biceps: 0,
    triceps: 0,
    calves: 0,
    core: 0,
  };

  let totalPushSets = 0;
  let totalPullSets = 0;
  let totalQuadSets = 0;
  let totalHamstringGluteSets = 0;
  let totalExercises = 0;
  let duplicateCount = 0;
  let contraindicationViolations = 0;
  let totalEstimatedDurationSeconds = 0;

  sessions.forEach((session, sessionIndex) => {
    const sessionExerciseIds = new Set<string>();

    // Rule: Number of exercises per session (3 to 7)
    if (session.exercises.length < 3) {
      issues.push({
        rule: "number_of_exercises",
        severity: "critical",
        message: `Seans ${sessionIndex + 1} (${session.focus}) en az 3 egzersiz içermeli, bulunan: ${session.exercises.length}`,
        sessionIndex,
      });
    } else if (session.exercises.length > 7) {
      issues.push({
        rule: "number_of_exercises",
        severity: "warning",
        message: `Seans ${sessionIndex + 1} (${session.focus}) 7 egzersizden fazla içeriyor: ${session.exercises.length}`,
        sessionIndex,
      });
    }

    let sessionSets = 0;

    session.exercises.forEach((instance) => {
      totalExercises++;
      const ex = instance.exercise;
      sessionSets += instance.sets;

      // Rule: Duplicate exercises in same session
      if (sessionExerciseIds.has(ex.id)) {
        duplicateCount++;
        issues.push({
          rule: "duplicate_exercises",
          severity: "critical",
          message: `Seans ${sessionIndex + 1} içinde '${ex.name}' egzersizi tekrar ediyor`,
          sessionIndex,
          exerciseId: ex.id,
        });
      }
      sessionExerciseIds.add(ex.id);

      // Rule: Equipment compatibility
      const hasEquipment = ex.equipment.every((eq) => profile.equipment.includes(eq) || eq === "bodyweight" || profile.equipment.includes("gym"));
      if (!hasEquipment) {
        issues.push({
          rule: "equipment_compatibility",
          severity: "critical",
          message: `'${ex.name}' egzersizi için gerekli ekipman (${ex.equipment.join(", ")}) profilde yok`,
          sessionIndex,
          exerciseId: ex.id,
        });
      }

      // Rule: Injury compatibility
      for (const limit of ex.contraindications) {
        if (profile.limitations.includes(limit)) {
          contraindicationViolations++;
          issues.push({
            rule: "injury_compatibility",
            severity: "critical",
            message: `'${ex.name}' egzersizi kullanıcının '${limit}' kısıtlamasıyla çelişiyor`,
            sessionIndex,
            exerciseId: ex.id,
          });
        }
      }

      // Rule: Training level compatibility
      if ((profile.fitnessLevel === "beginner" || profile.detrained) && ex.difficulty === "advanced") {
        issues.push({
          rule: "training_level_compatibility",
          severity: "critical",
          message: `Başlangıç seviyesindeki kullanıcıya ileri seviye '${ex.name}' egzersizi verilemez`,
          sessionIndex,
          exerciseId: ex.id,
        });
      }

      // Calculate sets per muscle
      ex.primaryMuscles.forEach((m) => {
        weeklySetsByMuscle[m] = (weeklySetsByMuscle[m] || 0) + instance.sets;
      });

      // Calculate movement ratios
      if (ex.movementPattern === "horizontal_push" || ex.movementPattern === "vertical_push") {
        totalPushSets += instance.sets;
      }
      if (ex.movementPattern === "horizontal_pull" || ex.movementPattern === "vertical_pull") {
        totalPullSets += instance.sets;
      }
      if (ex.movementPattern === "squat" || ex.movementPattern === "lunge" || ex.primaryMuscles.includes("quadriceps")) {
        totalQuadSets += instance.sets;
      }
      if (ex.movementPattern === "hinge" || ex.primaryMuscles.includes("hamstrings") || ex.primaryMuscles.includes("glutes")) {
        totalHamstringGluteSets += instance.sets;
      }

      // Estimated duration
      totalEstimatedDurationSeconds += instance.sets * (ex.estimatedDurationSeconds + instance.restSeconds);
    });

    // Rule: Daily volume per session (not exceeding 24 sets)
    if (sessionSets > 24) {
      issues.push({
        rule: "daily_muscle_volume",
        severity: "warning",
        message: `Seans ${sessionIndex + 1} günlük set hacmi çok yüksek (${sessionSets} set)`,
        sessionIndex,
      });
    }
  });

  // Rule: Push / Pull Balance (between 0.65 and 1.5)
  const pushPullRatio = totalPullSets > 0 ? totalPushSets / totalPullSets : 1;
  if (totalPushSets > 0 && totalPullSets > 0) {
    if (pushPullRatio < 0.6 || pushPullRatio > 1.6) {
      issues.push({
        rule: "push_pull_balance",
        severity: "warning",
        message: `İtme/Çekme dengesizliği: Push=${totalPushSets} set, Pull=${totalPullSets} set (Oran: ${pushPullRatio.toFixed(2)})`,
      });
    }
  }

  // Rule: Quad / Hamstring-Glute Balance
  const quadHamstringRatio = totalHamstringGluteSets > 0 ? totalQuadSets / totalHamstringGluteSets : 1;
  if (totalQuadSets > 0 && totalHamstringGluteSets > 0) {
    if (quadHamstringRatio < 0.5 || quadHamstringRatio > 1.8) {
      issues.push({
        rule: "quad_hamstring_glute_balance",
        severity: "warning",
        message: `Ön Bacak / Arka Bacak-Kalça dengesizliği: Quad=${totalQuadSets} set, Hamstring+Glute=${totalHamstringGluteSets} set (Oran: ${quadHamstringRatio.toFixed(2)})`,
      });
    }
  }

  // Rule: Weekly volume targets
  for (const [m, target] of Object.entries(targets) as Array<[MuscleGroup, { minWeeklySets: number; targetWeeklySets: number; maxWeeklySets: number }]>) {
    const actual = weeklySetsByMuscle[m] || 0;
    if (actual > target.maxWeeklySets + 4) {
      issues.push({
        rule: "weekly_muscle_volume",
        severity: "warning",
        message: `${m} kas grubu haftalık maksimum hacmi aşıyor (Alınan: ${actual}, Max: ${target.maxWeeklySets})`,
        muscle: m,
      });
    }
  }

  // Rule: Priority muscle coverage
  for (const priority of profile.priorityMuscles) {
    const actual = weeklySetsByMuscle[priority] || 0;
    if (actual < targets[priority].minWeeklySets) {
      issues.push({
        rule: "priority_muscle_coverage",
        severity: "warning",
        message: `Öncelikli kas grubu '${priority}' minimum haftalık hacmin altında kaldı (${actual} < ${targets[priority].minWeeklySets})`,
        muscle: priority,
      });
    }
  }

  const averageSessionDuration = sessions.length > 0 ? Math.round(totalEstimatedDurationSeconds / sessions.length / 60) : profile.sessionDurationMinutes;

  const valid = issues.filter((i) => i.severity === "critical").length === 0;

  return {
    valid,
    issues,
    metrics: {
      weeklySetsByMuscle,
      pushPullRatio: Math.round(pushPullRatio * 100) / 100,
      quadHamstringRatio: Math.round(quadHamstringRatio * 100) / 100,
      totalExercises,
      averageSessionDuration,
      duplicateCount,
      contraindicationViolations,
    },
  };
}
