import assert from "node:assert/strict";
import test from "node:test";

import { modelForTask, tierForTask } from "../lib/ai/models.ts";

test("sohbet ve kısa açıklamalar hafif (mini) katmana yönlenir", () => {
  for (const category of ["conversation", "simple_coaching", "daily_summary", "nutrition_explanation", "activity_summary", "goal_progress", "motivation"]) {
    assert.equal(tierForTask(category), "light", category);
  }
  assert.equal(modelForTask("conversation"), process.env.OPENAI_MODEL_LIGHT || "gpt-4o-mini");
});

test("çıkarım ve görsel okuma 4o katmanında kalır (doğruluk kritik)", () => {
  assert.equal(tierForTask("conversation"), "light");
  assert.equal(tierForTask("structured_extraction"), "cheap");
  assert.equal(tierForTask("vision"), "standard");
  assert.equal(modelForTask("vision"), process.env.OPENAI_MODEL_STANDARD || "gpt-4o");
  assert.equal(modelForTask("structured_extraction"), process.env.OPENAI_MODEL_CHEAP || "gpt-4o");
});

test("plan ve karmaşık değerlendirme güçlü modele yönlenir", () => {
  assert.equal(tierForTask("plan_generation"), "advanced");
  assert.equal(tierForTask("complex_reasoning"), "advanced");
  assert.equal(modelForTask("plan_generation"), process.env.OPENAI_MODEL_ADVANCED || "gpt-5.1");
});
