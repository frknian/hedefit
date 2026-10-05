import assert from "node:assert/strict";
import test from "node:test";
import { enforceOutputSafety, evaluateSafety } from "../lib/ai/safety.ts";
import { sanitizeCoachSignals } from "../lib/ai/signals.ts";
import { analyze } from "../lib/ai/intelligence.ts";
import { factsJson } from "../lib/ai/context-builder.ts";
import { AI_COACH_PROMPT_VERSION, buildCoachSystemPrompt } from "../lib/ai/prompts.ts";
import { staticKnowledgeRetriever } from "../lib/ai/knowledge.ts";
import { adaptationFacts, explainAdaptation } from "../lib/ai/adaptive-explainer.ts";
import { adaptTodaysPlan } from "../lib/training/adaptive-engine.ts";
import { normalizeTrainingProfile } from "../lib/training/profile-normalizer.ts";

test("güvenlik: klinik üreme sağlığı soruları engellenir (TR/EN), genel döngü-antrenman soruları engellenmez", () => {
  for (const [text, locale] of [["Adetim 10 gün gecikti, hamile miyim?", "tr"], ["Doğum kontrol hapı dozu kaç olmalı?", "tr"], ["PCOS için hangi hormon tedavisi?", "tr"], ["Am I pregnant? My period is late", "en"], ["Which hormone therapy should I start?", "en"]]) {
    const decision = evaluateSafety(text, locale);
    assert.equal(decision.blocked, true, text); assert.equal(decision.reason, "reproductive_health");
    assert.ok(decision.response.length > 40);
  }
  for (const text of ["Adet döneminde antrenman yapmalı mıyım?", "Döngümün ilk günlerinde Pilates iyi olur mu?", "Is it ok to train during my period?"]) {
    const decision = evaluateSafety(text, "tr");
    assert.equal(decision.blocked, false, text);
    assert.match(decision.extraInstruction, /hormon|hormone/i, "döngü sohbetinde ek kısıt cümlesi var");
  }
  assert.equal(evaluateSafety("Bugün hangi antrenmanı yapmalıyım?", "tr").extraInstruction, undefined);
});

test("güvenlik: model kesin hormonal iddia kurarsa yanıtın önüne uyarı eklenir; normal metin değişmez", () => {
  assert.match(enforceOutputSafety("Şu an östrojen düzeyin düşüyor, bu yüzden yorgunsun.", "tr"), /^Not: Hormon düzeylerini/);
  assert.match(enforceOutputSafety("You are ovulating right now, so push hard.", "en"), /^Note: I can't know your hormone levels/);
  const normal = "Bugün hafif bir Pilates oturumu iyi gelebilir.";
  assert.equal(enforceOutputSafety(normal, "tr"), normal);
});

test("sinyaller: istemciden gelen döngü alanı atılır; uyarlama özeti yalnızca bilinen değerlerle kabul edilir", () => {
  const signals = sanitizeCoachSignals({
    wellness: {
      cycle: { cycleDay: 3, phase: "menstrual", periodLikely: true },
      adaptation: { level: "low", adapted: true, intensity: "recovery", actions: ["switch_recovery", "hack", 5], reasons: ["low_energy", "secret_diagnosis"], sessionKind: "pilates_today", estimatedMinutes: 20 },
    },
  });
  assert.equal(signals.wellness.cycle, undefined, "istemci döngü gönderemez");
  assert.deepEqual(signals.wellness.adaptation, { level: "low", adapted: true, actions: ["switch_recovery"], intensity: "recovery", reasons: ["low_energy"], sessionKind: "pilates_today", estimatedMinutes: 20 });
  assert.equal(sanitizeCoachSignals({ wellness: { adaptation: { level: "weird" } } }).wellness, undefined);
  assert.equal(sanitizeCoachSignals({}).wellness, undefined);
});

test("gerçekler ve istem: wellness grubu bağlama girer; v5 istem wellness kuralını ve döngü sınırını içerir", () => {
  const facts = analyze({ wellness: { adaptation: { level: "low", adapted: true, actions: ["switch_recovery"], intensity: "recovery", reasons: ["low_energy"] }, cycle: { cycleDay: 3, periodLikely: true } } });
  const json = JSON.parse(factsJson(facts));
  assert.equal(json.wellness.adaptation.level, "low"); assert.equal(json.wellness.cycle.cycleDay, 3);
  assert.equal(JSON.parse(factsJson(analyze({}))).wellness, undefined, "boşsa grup hiç yok");
  assert.equal(AI_COACH_PROMPT_VERSION, "v5");
  for (const locale of ["tr", "en"]) {
    const prompt = buildCoachSystemPrompt({ locale, factsJson: factsJson(facts) });
    assert.match(prompt, locale === "tr" ? /Pilates, mobilite, barre/ : /Pilates, mobility, barre/);
    assert.match(prompt, locale === "tr" ? /wellness\.cycle YOKSA/ : /wellness\.cycle is NOT in <facts>/);
    assert.match(prompt, /check-in/);
  }
});

test("bilgi tabanı: Pilates, mobilite, düşük etkili, adet döneminde antrenman ve demir parçaları sorgularla gelir; kaynakları var", async () => {
  const ids = async (query) => (await staticKnowledgeRetriever.retrieve(query, { limit: 3 })).map((chunk) => chunk.id);
  assert.ok((await ids("Pilates nasıl yapılır")).includes("pilates-basics"));
  assert.ok((await ids("mobilite için ne yapmalıyım")).includes("mobility-routine"));
  assert.ok((await ids("düşük etkili kardiyo")).includes("low-impact-cardio"));
  assert.ok((await ids("adet döneminde antrenman")).includes("menstrual-training"));
  assert.ok((await ids("demir içeren besinler")).includes("iron-foods-general"));
  for (const chunk of await staticKnowledgeRetriever.retrieve("pilates mobilite adet demir toparlanma", { limit: 20 })) {
    assert.ok(chunk.source.length > 5 && chunk.content.length > 60, chunk.id);
    assert.doesNotMatch(chunk.content, /\b\d+\s?mg\b|doz[ua]? (olarak|kadar)/i, "takviye dozu yok");
  }
});

const profile = normalizeTrainingProfile({ history: ["Kilo verme", "", "", "", "Başlangıç", "", "3 gün", "45 dk", "", "Evde", "Ekipman yok", "Yok"] });
const plan = ["plank", "glute-bridge", "dead-bug", "bird-dog"].map((id) => ({ id, name: id, english: id, area: "Core", sets: 3, reps: "10", restSeconds: 60 }));
const adapted = adaptTodaysPlan({ profile, exercises: plan, checkin: { day: "2026-10-05", energy: 2, sleepQuality: 3, sleepHours: 5, soreness: 6, pain: 0, availableMinutes: null }, cycle: { cycleDay: 3, phase: "menstrual", periodLikely: true, preMenstrualWindow: false, nextPeriodStart: "2026-10-29", daysToNextPeriod: 24, confidence: "high", stale: false }, tier: "pro", seed: "s", locale: "tr" });

test("açıklayıcı: modele yalnızca kaba bilgi gider (ham sayı ve döngü günü/fazı yok); yerel yedek sağlayıcı yanıtı reddedilir", async () => {
  assert.equal(adapted.adapted, true);
  const facts = adaptationFacts(adapted);
  const serialized = JSON.stringify(facts);
  for (const forbidden of ["cycleDay", "\"phase\"", "menstrual", "\"energy\"", "sleepQuality", "\"soreness\"", "\"pain\""]) assert.ok(!serialized.includes(forbidden), `modele giden bilgide ${forbidden} olmamalı`);
  assert.ok(Array.isArray(facts.reasons) && facts.actions.length > 0);
  let sent;
  const generate = async (request) => { sent = request; return { text: "  Bugün enerjin düşük olduğu için planını tamamen değiştirmeden hafiflettik.  ", provider: "openai", model: "x", promptVersion: "v5", fallbackUsed: false, latencyMs: 1 }; };
  assert.equal(await explainAdaptation({ result: adapted, locale: "tr", generate }), "Bugün enerjin düşük olduğu için planını tamamen değiştirmeden hafiflettik.");
  assert.equal(sent.category, "daily_summary"); assert.match(sent.domainRules, /Tıbbi iddia/);
  const local = async () => ({ text: "şablon", provider: "local-deterministic", model: "m", promptVersion: "v5", fallbackUsed: true, latencyMs: 1 });
  assert.equal(await explainAdaptation({ result: adapted, locale: "tr", generate: local }), null);
  assert.equal(await explainAdaptation({ result: adapted, locale: "tr", generate: async () => { throw new Error("boom"); } }), null);
  assert.equal(await explainAdaptation({ result: { ...adapted, adapted: false }, locale: "tr", generate }), null);
});
