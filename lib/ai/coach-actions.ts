// Koçun önerdiği EYLEMLER.
//
// NEDEN VAR: koç bugüne kadar yalnız metin döndürüyordu. "25 dakika yürüyüş
// öneriyorum" diyen bir yanıtın ardından kullanıcı uygulamayı kendi gezip o
// şeyi kendi kurmak zorundaydı. Eylemlerle sohbet, uygulamanın kontrol
// katmanına dönüşüyor.
//
// TAŞIMA BİÇİMİ: model, yanıtının SONUNA ```hedefit-actions``` etiketli bir
// JSON bloğu ekleyebilir. Sağlayıcıdan bağımsız çalışması için tool-calling
// yerine bu yol seçildi — uygulama üç farklı sağlayıcıyla ve cihaz üstü bir
// modelle çalışıyor, hepsinin araç çağırma desteği aynı değil. Blok yoksa ya
// da bozuksa eylem üretilmez; kullanıcı normal metni görür.
//
// İKİ KURAL PAZARLIĞA KAPALI:
//   1. Eylem kullanıcı ONAYI olmadan uygulanmaz. Buradaki her şey bir ÖNERİdir;
//      düğmeye basan kullanıcıdır. Model kendi başına hedef değiştiremez.
//   2. Yerel yedek yanıtlarda eylem ayrıştırılmaz (bkz. çağıran taraf):
//      cihaz üstü küçük modelin ürettiği yapılandırılmış çağrıya güvenilmez.
//
// ÜÇÜNCÜ KURAL: model var olmayan/uydurma bir exerciseId ÖNEREMEZ. Egzersiz
// kataloğuna karşı doğrulanmayan tüm exerciseId/replacementId/regressionId/
// progressionId alanları içeren eylemler burada sessizce düşürülür (bkz.
// isKnownExerciseId / isSuggestableExerciseId). Bu, generate-plan rotasındaki
// `atlasLocked` doğrulamasının (app/api/generate-plan/route.ts) sohbet
// eylemleri için karşılığıdır.

import { getExerciseById } from "../exercise-service.ts";

/** Egzersiz zaten kullanıcının planında var (id çözülebiliyor mu — legacy dahil). */
function isKnownExerciseId(id: string): boolean {
  return getExerciseById(id) !== null;
}

/** Model YENİ bir egzersiz öneriyor: aktif, güncel katalogda olmalı (legacy hariç). */
function isSuggestableExerciseId(id: string): boolean {
  const exercise = getExerciseById(id);
  return exercise !== null && exercise.isActive !== false && exercise.source !== "legacy";
}

export type CoachAction =
  /** Önerilen antrenmanı bugünün planına ekler. */
  | { type: "openWorkout" }
  /** Belirli bir bölgeye program kurma ekranını açar. */
  | { type: "createWorkout"; region: string }
  /** Hedefit Rota'yı açık hava aktivitesi için başlatır. */
  | { type: "startOutdoor" }
  /** Beslenme ekranını açar; hedef kalori önerilmişse taşır. */
  | { type: "suggestMeal"; targetKcal?: number }
  /** Günlük hatırlatma ayarlarını açar. */
  | { type: "remind" }
  /** Hedef planı ekranını açar. */
  | { type: "changeGoal" }
  /** Egzersizi alternatif veya regresyon/progresyon hareketiyle değiştirir. */
  | { type: "replace_exercise"; exerciseId?: string; replacementId: string; replacementName: string; sets?: number; reps?: string; restSeconds?: number; reason?: string }
  /** Antrenman yoğunluğunu ve hacmini toparlanma için düşürür. */
  | { type: "reduce_intensity"; percent?: number; reason?: string }
  /** Antrenmanı yeni kısıtlara göre yeniden üretir. */
  | { type: "regenerate_workout"; equipment?: string; focus?: string }
  /** Antrenmanı hedef dakikaya (ör. 15, 20, 30 dk) uyarlar. */
  | { type: "shorten_workout"; targetMinutes: number }
  /** Hazırlık ve toparlanma kontrolünü başlatır. */
  | { type: "start_recovery_check" }
  /** Egzersiz form rehberini açar. */
  | { type: "show_exercise_tutorial"; exerciseId: string; exerciseName?: string }
  /** Egzersizin daha kolay regresyonunu önerir. */
  | { type: "show_regression"; exerciseId: string; regressionId?: string; regressionName?: string }
  /** Egzersizin daha zor progresyonunu önerir. */
  | { type: "show_progression"; exerciseId: string; progressionId?: string; progressionName?: string }
  /** Belirli bir hareketin set sayısını günceller. */
  | { type: "modify_sets"; exerciseId: string; sets: number }
  /** Belirli bir hareketin tekrar sayısını günceller. */
  | { type: "modify_reps"; exerciseId: string; reps: string }
  /** Belirli bir hareketin dinlenme süresini günceller. */
  | { type: "modify_rest_time"; exerciseId: string; restSeconds: number };

export type CoachActionType = CoachAction["type"];

/** Ekranda gösterilecek en fazla eylem. Fazlası yanıtı menüye çevirir. */
export const MAX_COACH_ACTIONS = 3;

const BLOCK = /```hedefit-actions\s*([\s\S]*?)```/i;

// Model bazen sistem promptundaki <facts>/<memory>/... etiket sözdizimini
// kullanıcıya giden metne kopyalıyor (ör. "olan <facts>2329</facts> kaloriye
// dikkat ederek..."). İçerideki değer (2329) doğru ve kalsın; yalnız etiket
// işaretlerini kaldırıyoruz. Prompt tarafında da bu ayrıca yasaklanır
// (bkz. prompts.ts NO_RAW_TAGS_RULE); bu, ikinci ve garanti savunma hattı.
const LEAKED_CONTEXT_TAGS = /<\/?(?:facts|memory|knowledge|atlas|workout_context|conversation_summary)>/gi;

export function stripLeakedContextTags(text: string): string {
  return text.replace(LEAKED_CONTEXT_TAGS, "").replace(/[ \t]{2,}/g, " ").trim();
}

/** Katalogdaki bölge adları; uydurulmuş bir bölge kabul edilmez. */
const REGIONS = new Set(["Göğüs", "Sırt", "Omuz", "Kol", "Bacak", "Kalça", "Core", "Kondisyon"]);

function toAction(raw: unknown): CoachAction | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  switch (value.type) {
    case "openWorkout": return { type: "openWorkout" };
    case "startOutdoor": return { type: "startOutdoor" };
    case "remind": return { type: "remind" };
    case "changeGoal": return { type: "changeGoal" };
    case "start_recovery_check": return { type: "start_recovery_check" };
    case "createWorkout": {
      const region = typeof value.region === "string" ? value.region.trim() : "";
      return REGIONS.has(region) ? { type: "createWorkout", region } : null;
    }
    case "suggestMeal": {
      const kcal = Number(value.targetKcal);
      const valid = Number.isFinite(kcal) && kcal >= 100 && kcal <= 2_000;
      return valid ? { type: "suggestMeal", targetKcal: Math.round(kcal) } : { type: "suggestMeal" };
    }
    case "replace_exercise": {
      const replacementId = typeof value.replacementId === "string" ? value.replacementId.trim() : "";
      const replacementName = typeof value.replacementName === "string" ? value.replacementName.trim() : "";
      if (!replacementId && !replacementName) return null;
      const finalReplacementId = replacementId || replacementName.toLowerCase().replace(/\s+/g, "-");
      // Model kataloğa girmeyen bir hareket uydurduysa öneriyi hiç gösterme.
      if (!isSuggestableExerciseId(finalReplacementId)) return null;
      const exerciseId = typeof value.exerciseId === "string" ? value.exerciseId.trim() : "";
      return {
        type: "replace_exercise",
        exerciseId: exerciseId && isKnownExerciseId(exerciseId) ? exerciseId : undefined,
        replacementId: finalReplacementId,
        replacementName: replacementName || replacementId,
        sets: typeof value.sets === "number" ? Math.max(1, Math.min(10, value.sets)) : undefined,
        reps: typeof value.reps === "string" ? value.reps : undefined,
        restSeconds: typeof value.restSeconds === "number" ? value.restSeconds : undefined,
        reason: typeof value.reason === "string" ? value.reason : undefined,
      };
    }
    case "reduce_intensity": {
      const percent = typeof value.percent === "number" ? Math.max(10, Math.min(60, value.percent)) : 25;
      return { type: "reduce_intensity", percent, reason: typeof value.reason === "string" ? value.reason : undefined };
    }
    case "shorten_workout": {
      const targetMinutes = typeof value.targetMinutes === "number" ? Math.max(10, Math.min(90, value.targetMinutes)) : 20;
      return { type: "shorten_workout", targetMinutes };
    }
    case "regenerate_workout": {
      return {
        type: "regenerate_workout",
        equipment: typeof value.equipment === "string" ? value.equipment : undefined,
        focus: typeof value.focus === "string" ? value.focus : undefined,
      };
    }
    case "show_exercise_tutorial": {
      const exerciseId = typeof value.exerciseId === "string" ? value.exerciseId.trim() : "";
      if (!exerciseId || !isKnownExerciseId(exerciseId)) return null;
      return { type: "show_exercise_tutorial", exerciseId, exerciseName: typeof value.exerciseName === "string" ? value.exerciseName : undefined };
    }
    case "show_regression": {
      const exerciseId = typeof value.exerciseId === "string" ? value.exerciseId.trim() : "";
      if (!exerciseId || !isKnownExerciseId(exerciseId)) return null;
      const regressionId = typeof value.regressionId === "string" ? value.regressionId.trim() : "";
      return {
        type: "show_regression",
        exerciseId,
        regressionId: regressionId && isSuggestableExerciseId(regressionId) ? regressionId : undefined,
        regressionName: typeof value.regressionName === "string" ? value.regressionName : undefined,
      };
    }
    case "show_progression": {
      const exerciseId = typeof value.exerciseId === "string" ? value.exerciseId.trim() : "";
      if (!exerciseId || !isKnownExerciseId(exerciseId)) return null;
      const progressionId = typeof value.progressionId === "string" ? value.progressionId.trim() : "";
      return {
        type: "show_progression",
        exerciseId,
        progressionId: progressionId && isSuggestableExerciseId(progressionId) ? progressionId : undefined,
        progressionName: typeof value.progressionName === "string" ? value.progressionName : undefined,
      };
    }
    case "modify_sets": {
      const exerciseId = typeof value.exerciseId === "string" ? value.exerciseId.trim() : "";
      const sets = typeof value.sets === "number" ? Math.max(1, Math.min(10, value.sets)) : null;
      if (!exerciseId || sets === null || !isKnownExerciseId(exerciseId)) return null;
      return { type: "modify_sets", exerciseId, sets };
    }
    case "modify_reps": {
      const exerciseId = typeof value.exerciseId === "string" ? value.exerciseId.trim() : "";
      const reps = typeof value.reps === "string" ? value.reps.trim() : "";
      if (!exerciseId || !reps || !isKnownExerciseId(exerciseId)) return null;
      return { type: "modify_reps", exerciseId, reps };
    }
    case "modify_rest_time": {
      const exerciseId = typeof value.exerciseId === "string" ? value.exerciseId.trim() : "";
      const restSeconds = typeof value.restSeconds === "number" ? Math.max(15, Math.min(300, value.restSeconds)) : null;
      if (!exerciseId || restSeconds === null || !isKnownExerciseId(exerciseId)) return null;
      return { type: "modify_rest_time", exerciseId, restSeconds };
    }
    default: return null;
  }
}

export type ParsedCoachResponse = {
  /** Kullanıcıya gösterilecek metin; eylem bloğu ayıklanmış hâlde. */
  text: string;
  actions: CoachAction[];
};

/**
 * Yanıttan eylem bloğunu ayıklar.
 *
 * Blok bulunamazsa, JSON bozuksa ya da hiçbir eylem tanınmazsa metin olduğu
 * gibi döner ve eylem listesi boş kalır — koç metin koçu olarak çalışmaya
 * devam eder. Bu, modelin biçime uymadığı durumda bozulmayan tek davranış.
 */
export function parseCoachActions(rawText: string): ParsedCoachResponse {
  const text = typeof rawText === "string" ? rawText : "";
  const match = BLOCK.exec(text);
  if (!match) return { text: stripLeakedContextTags(text), actions: [] };

  const cleaned = stripLeakedContextTags(text.replace(BLOCK, ""));
  let parsed: unknown;
  try {
    parsed = JSON.parse(match[1].trim());
  } catch {
    // Bozuk JSON kullanıcıya blok olarak GÖSTERİLMEZ: yanıtın sonunda ham
    // kod parçası görmek, çalışmayan bir özellikten daha kötü.
    return { text: cleaned, actions: [] };
  }

  const list = Array.isArray(parsed) ? parsed : Array.isArray((parsed as { actions?: unknown })?.actions) ? (parsed as { actions: unknown[] }).actions : [];
  const actions: CoachAction[] = [];
  const seen = new Set<string>();
  for (const item of list) {
    const action = toAction(item);
    if (!action) continue;
    // Aynı eylem iki kez önerilirse ekranda iki özdeş düğme olurdu.
    const key = action.type === "createWorkout" ? `createWorkout:${action.region}` : action.type;
    if (seen.has(key)) continue;
    seen.add(key);
    actions.push(action);
    if (actions.length >= MAX_COACH_ACTIONS) break;
  }
  return { text: cleaned, actions };
}

/**
 * Modele eylem biçimini anlatan talimat.
 *
 * Prompt'a YALNIZCA gerçek (uzak) sağlayıcı kullanılırken eklenir; cihaz üstü
 * küçük modelden yapılandırılmış çıktı beklenmiyor.
 */
export const COACH_ACTIONS_INSTRUCTION = {
  tr: `Yanıtın kullanıcıyı uygulamada bir işe veya antrenman uyarlamasına yönlendiriyorsa, metnin SONUNA şu biçimde bir blok ekleyebilirsin:
\`\`\`hedefit-actions
[{"type":"replace_exercise","replacementId":"goblet-squat","replacementName":"Goblet Squat","reason":"too_hard"}]
\`\`\`
Geçerli eylemler:
- openWorkout (antrenmanı aç), createWorkout + region, startOutdoor, suggestMeal + targetKcal, remind, changeGoal.
- replace_exercise + replacementId + replacementName (+ exerciseId, sets, reps, restSeconds, reason)
- reduce_intensity + percent (+ reason)
- shorten_workout + targetMinutes (ör. 15, 20, 30)
- start_recovery_check
- show_regression + exerciseId (+ regressionId, regressionName)
- show_progression + exerciseId (+ progressionId, progressionName)

ÖNEMLİ KURAL: Kullanıcı bir hareketi değiştirmek istediğinde ("hareketi değiştir", "bu hareketi yapamıyorum / çok zor / ağrı yapıyor", "squat yerine ne yapabilirim" vb.), MUTLAKA uygun ve güvenli bir alternatif hareket öner ve \`\`\`hedefit-actions\`\`\` bloğuna {"type":"replace_exercise","replacementId":"...","replacementName":"...","exerciseId":"..."} eylemini ekle. Varsa kullanıcının değiştirmek istediği hareketin id'sini exerciseId olarak geç.
En fazla 3 eylem öner. Eylem gerekmiyorsa blok ekleme. Blok dışında JSON yazma; eylemleri metin içinde tekrar anlatma.`,
  en: `If your answer points the user to an app task or workout adaptation, you may append a block in this format at the END of your text:
\`\`\`hedefit-actions
[{"type":"replace_exercise","replacementId":"goblet-squat","replacementName":"Goblet Squat","reason":"too_hard"}]
\`\`\`
Valid actions:
- openWorkout, createWorkout + region, startOutdoor, suggestMeal + targetKcal, remind, changeGoal.
- replace_exercise + replacementId + replacementName (+ exerciseId, sets, reps, restSeconds, reason)
- reduce_intensity + percent (+ reason)
- shorten_workout + targetMinutes (e.g. 15, 20, 30)
- start_recovery_check
- show_regression + exerciseId (+ regressionId, regressionName)
- show_progression + exerciseId (+ progressionId, progressionName)

IMPORTANT RULE: When the user requests to swap or replace an exercise ("hareketi değiştir", "replace exercise", "I can't do this", "too hard", "what can I do instead of X"), you MUST recommend a suitable replacement exercise and include the {"type":"replace_exercise","replacementId":"...","replacementName":"...","exerciseId":"..."} action in the \`\`\`hedefit-actions\`\`\` block. Include exerciseId if known from context.
Suggest at most 3 actions. Omit the block when no action is needed. Do not write JSON outside the block and do not restate the actions in prose.`,
} as const;
