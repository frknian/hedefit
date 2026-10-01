import assert from "node:assert/strict";
import test from "node:test";
import { recognizeGymEquipment } from "../lib/ai-equipment-recognizer.ts";
import { providerRegistry } from "../lib/ai/providers/registry.ts";

function visionProvider(object, onRequest = () => {}) {
  return {
    id: "vision-test",
    kind: "remote",
    categories: ["vision"],
    isAvailable: async () => true,
    generateText: async () => { throw new Error("text generation must not be used"); },
    generateObject: async (request) => {
      onRequest(request);
      return { object, provider: "vision-test", model: "vision-model", latencyMs: 1 };
    },
  };
}

test.afterEach(() => providerRegistry.reset());

test("vision recognition sends the image and preserves canonical structured output", async () => {
  let request;
  providerRegistry.reset([visionProvider({
    recognized: true, equipmentName: "leg_press", localizedName: "Leg Press", category: "Makine", confidence: .91,
    alternatives: [{ equipmentName: "smith_machine", localizedName: "Smith Machine", confidence: .22 }],
    visibleFeatures: ["geniş ayak platformu", "eğimli kızak"],
  }, (value) => { request = value; })]);
  const result = await recognizeGymEquipment({ mimeType: "image/jpeg", base64: "jpeg-data" });
  assert.equal(result.equipmentName, "leg_press");
  assert.equal(result.confidence, .91);
  assert.equal(request.category, "vision");
  assert.deepEqual(request.image, { mimeType: "image/jpeg", base64: "jpeg-data" });
});

test("low confidence never exposes an exact equipment name", async () => {
  providerRegistry.reset([visionProvider({
    recognized: true, equipmentName: "bench_press", localizedName: "Bench Press", category: "Serbest ağırlık", confidence: .42,
    alternatives: [{ equipmentName: "chest_press", localizedName: "Chest Press", confidence: .4 }], visibleFeatures: ["yatay minder"],
  })]);
  const result = await recognizeGymEquipment({ mimeType: "image/jpeg", base64: "jpeg-data" });
  assert.equal(result.recognized, false);
  assert.equal(result.equipmentName, null);
  assert.equal(result.localizedName, null);
  assert.deepEqual(result.alternatives, []);
});

test("unknown labels are rejected instead of becoming fabricated equipment", async () => {
  providerRegistry.reset([visionProvider({
    recognized: true, equipmentName: "mystery_machine", localizedName: "Gizemli Makine", category: "Makine", confidence: .98,
    alternatives: [], visibleFeatures: [],
  })]);
  const result = await recognizeGymEquipment({ mimeType: "image/jpeg", base64: "jpeg-data" });
  assert.equal(result.recognized, false);
  assert.equal(result.equipmentName, null);
});
