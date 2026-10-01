import assert from "node:assert/strict";
import test from "node:test";
import { parseCoachActions } from "../lib/ai/coach-actions.ts";

test("Coach Actions: Parses replace_exercise action from response block", () => {
  const raw = `Squat hareketinde zorlandıysan daha dengeli olan Goblet Squat'a geçebiliriz.
\`\`\`hedefit-actions
[{"type":"replace_exercise","exerciseId":"squat","replacementId":"goblet-squat","replacementName":"Goblet Squat","sets":3,"reps":"10–12","restSeconds":75,"reason":"too_hard"}]
\`\`\``;

  const parsed = parseCoachActions(raw);
  assert.equal(parsed.text, "Squat hareketinde zorlandıysan daha dengeli olan Goblet Squat'a geçebiliriz.");
  assert.equal(parsed.actions.length, 1);
  const action = parsed.actions[0];
  assert.equal(action.type, "replace_exercise");
  assert.equal(action.exerciseId, "squat");
  assert.equal(action.replacementId, "goblet-squat");
  assert.equal(action.replacementName, "Goblet Squat");
  assert.equal(action.sets, 3);
});

test("Coach Actions: Drops replace_exercise when the model hallucinates an exercise id not in the catalog", () => {
  const raw = `İşte alternatif hareketin.
\`\`\`hedefit-actions
[{"type":"replace_exercise","exerciseId":"squat","replacementId":"floating-unicorn-squat","replacementName":"Floating Unicorn Squat"}]
\`\`\``;

  const parsed = parseCoachActions(raw);
  assert.equal(parsed.actions.length, 0, "A replacement pointing at a non-catalog id must never reach the user");
});

test("Coach Actions: show_regression/show_progression drop non-catalog exerciseId, but keep valid ones", () => {
  const validRaw = `Regresyon önerisi.
\`\`\`hedefit-actions
[{"type":"show_regression","exerciseId":"squat","regressionId":"bodyweight-squat"}]
\`\`\``;
  const validParsed = parseCoachActions(validRaw);
  assert.equal(validParsed.actions.length, 1);
  assert.equal(validParsed.actions[0].regressionId, "bodyweight-squat");

  const invalidRaw = `Regresyon önerisi.
\`\`\`hedefit-actions
[{"type":"show_regression","exerciseId":"totally-made-up-exercise","regressionId":"bodyweight-squat"}]
\`\`\``;
  assert.equal(parseCoachActions(invalidRaw).actions.length, 0);
});

test("Coach Actions: Parses reduce_intensity, shorten_workout and start_recovery_check", () => {
  const raw = `Yorgun olduğunu anlıyorum. Antrenmanı kısaltıp toparlanma kontrolü yapalım.
\`\`\`hedefit-actions
[
  {"type":"shorten_workout","targetMinutes":20},
  {"type":"reduce_intensity","percent":30,"reason":"yorgunluk"},
  {"type":"start_recovery_check"}
]
\`\`\``;

  const parsed = parseCoachActions(raw);
  assert.equal(parsed.actions.length, 3);
  assert.equal(parsed.actions[0].type, "shorten_workout");
  assert.equal(parsed.actions[0].targetMinutes, 20);
  assert.equal(parsed.actions[1].type, "reduce_intensity");
  assert.equal(parsed.actions[1].percent, 30);
  assert.equal(parsed.actions[2].type, "start_recovery_check");
});

test("Coach Actions: Gracefully handles malformed json and limits to max 3 actions", () => {
  const malformed = `Metin burada.
\`\`\`hedefit-actions
[{invalid_json}
\`\`\``;
  const badParsed = parseCoachActions(malformed);
  assert.equal(badParsed.text, "Metin burada.");
  assert.equal(badParsed.actions.length, 0);

  const tooMany = `Metin.
\`\`\`hedefit-actions
[
  {"type":"openWorkout"},
  {"type":"startOutdoor"},
  {"type":"start_recovery_check"},
  {"type":"changeGoal"}
]
\`\`\``;
  const cappedParsed = parseCoachActions(tooMany);
  assert.equal(cappedParsed.actions.length, 3);
});
