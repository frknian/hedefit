import assert from "node:assert/strict";
import test from "node:test";
import { sanitizeMemory, dedupeMemories, rankMemories, formatMemories, MIN_CONFIDENCE } from "../lib/ai/memory.ts";
import { buildCoachContext, summarizeOlderMessages, factsJson, contextToSystemPrompt, RECENT_MESSAGE_BUDGET, MEMORY_BUDGET } from "../lib/ai/context-builder.ts";
import { analyze } from "../lib/ai/intelligence.ts";
import { staticKnowledgeRetriever } from "../lib/ai/knowledge.ts";

// --------------------------------------------------------------- hafıza

test("geçersiz hafıza kaydı sessizce atılır, uygulamayı bozmaz", () => {
  // Modelin JSON'una asla güvenilmez (bkz. lib/ai/memory.ts).
  assert.equal(sanitizeMemory(null), null);
  assert.equal(sanitizeMemory({ type: "uydurma_kategori", key: "a", value: "b" }), null);
  assert.equal(sanitizeMemory({ type: "exercise_preference", key: "", value: "b" }), null);
  assert.equal(sanitizeMemory({ type: "exercise_preference", key: "a", value: "" }), null);
  assert.equal(sanitizeMemory("düz metin"), null);
});

test("düşük güvenli çıkarım saklanmaz", () => {
  const rejected = sanitizeMemory({ type: "food_preference", key: "brokoli", value: "dislike", confidence: MIN_CONFIDENCE - 0.1 });
  assert.equal(rejected, null, "yanlış bir 'sevmiyor' kaydı, hiç kayıt olmamasından zararlıdır");
  assert.ok(sanitizeMemory({ type: "food_preference", key: "brokoli", value: "dislike", confidence: MIN_CONFIDENCE }));
});

test("anahtar normalize edilir ve uzunluk sınırlanır", () => {
  const memory = sanitizeMemory({ type: "exercise_preference", key: "  KOŞU  ", value: "dislike" });
  assert.equal(memory.key, "koşu");
  const long = sanitizeMemory({ type: "exercise_preference", key: "a".repeat(200), value: "b".repeat(400) });
  assert.equal(long.key.length, 60);
  assert.equal(long.value.length, 120);
});

test("aynı (tür, anahtar) tek gerçektir: çelişen kayıtlar birleştirilir", () => {
  const merged = dedupeMemories([
    { type: "exercise_preference", key: "koşu", value: "dislike", confidence: 0.7, source: "inferred" },
    { type: "exercise_preference", key: "koşu", value: "like", confidence: 0.9, source: "user_explicit" },
  ]);
  assert.equal(merged.length, 1);
  assert.equal(merged[0].value, "like", "kullanıcının açık ifadesi çıkarımı yenmeli");
});

test("çıkarım, daha güvenli olsa bile açık ifadenin yerine geçmez", () => {
  const merged = dedupeMemories([
    { type: "goal", key: "hedef", value: "kilo verme", confidence: 0.8, source: "user_explicit" },
    { type: "goal", key: "hedef", value: "kas kazanma", confidence: 0.99, source: "inferred" },
  ]);
  assert.equal(merged[0].value, "kilo verme");
});

test("bağlama giren hafıza sayısı sınırlıdır", () => {
  const many = Array.from({ length: 30 }, (unused, index) => ({
    type: "habit", key: `alışkanlık-${index}`, value: "x", confidence: index / 30, source: "inferred",
  }));
  const ranked = rankMemories(many, MEMORY_BUDGET);
  assert.equal(ranked.length, MEMORY_BUDGET);
  // En güvenli kayıtlar öne alınmalı.
  assert.ok(ranked[0].confidence > ranked[MEMORY_BUDGET - 1].confidence);
});

test("hafıza modele okunabilir satırlar olarak verilir", () => {
  const lines = formatMemories([{ type: "exercise_preference", key: "koşu", value: "dislike", confidence: 1, source: "user_explicit" }]);
  assert.deepEqual(lines, ["exercise_preference/koşu: dislike"]);
});

// --------------------------------------------------------- bağlam bütçesi

const messages = (count) => Array.from({ length: count }, (unused, index) => ({
  role: index % 2 === 0 ? "user" : "assistant",
  text: `mesaj ${index}`,
}));

test("uzun sohbette yalnız son mesajlar gönderilir, eskiler özetlenir", async () => {
  const context = await buildCoachContext({ facts: analyze({}), messages: messages(20) });
  assert.equal(context.recentMessages.length, RECENT_MESSAGE_BUDGET);
  assert.equal(context.recentMessages.at(-1).text, "mesaj 19");
  assert.match(context.conversationSummary, /daha önce şunları sordu/);
});

test("kısa sohbette özet üretilmez", async () => {
  const context = await buildCoachContext({ facts: analyze({}), messages: messages(3) });
  assert.equal(context.conversationSummary, undefined);
  assert.equal(context.recentMessages.length, 3);
});

test("özet sabit bir karakter bütçesini aşmaz", () => {
  const long = Array.from({ length: 40 }, () => ({ role: "user", text: "x".repeat(200) }));
  assert.ok(summarizeOlderMessages(long).length <= 600);
});

test("alakasız bilgi parçası bağlama GİRMEZ", async () => {
  const sleep = await buildCoachContext({ facts: analyze({}), messages: [{ role: "user", text: "uykum yetersiz, ne yapmalıyım?" }] });
  assert.ok(sleep.knowledge.length > 0);
  assert.ok(sleep.knowledge.every((chunk) => chunk.topic !== "nutrition"), "uyku sorusuna protein bilgisi eklenmemeli");

  const unrelated = await staticKnowledgeRetriever.retrieve("bugün hava nasıl?");
  assert.deepEqual(unrelated, [], "eşleşme yoksa bağlam şişirilmez");
});

test("her bilgi parçasının kaynağı vardır", async () => {
  const chunks = await staticKnowledgeRetriever.retrieve("protein ne kadar almalıyım", { limit: 5 });
  assert.ok(chunks.length > 0);
  for (const chunk of chunks) assert.ok(chunk.source && chunk.updatedAt, `provenanssız bilgi: ${chunk.id}`);
});

// ------------------------------------------------------------ prompt

test("hesaplanamayan alan JSON'a hiç konmaz, 0 olarak GÖNDERİLMEZ", () => {
  const payload = JSON.parse(factsJson(analyze({})));
  assert.equal(payload.today, undefined);
  assert.equal(payload.goals, undefined);
  assert.ok(Array.isArray(payload.unavailable));
});

test("sistem promptu gerçekleri kesin, hafıza/bilgiyi güvenilmez ilan eder", async () => {
  const facts = analyze({ profile: { heightCm: 180, weightKg: 85 }, goal: { goalType: "lose" }, today: { totals: { calories: 1500, protein: 80, carbs: 100, fat: 50 } } });
  const context = await buildCoachContext({
    facts,
    memories: [{ type: "exercise_preference", key: "koşu", value: "dislike", confidence: 1, source: "user_explicit" }],
    messages: [{ role: "user", text: "protein hedefime ulaştım mı?" }],
  });
  const prompt = contextToSystemPrompt(context, { locale: "tr" });

  assert.match(prompt, /KESİN DOĞRU/, "gerçekler otorite ilan edilmeli");
  assert.match(prompt, /yeniden hesaplama/, "model aritmetiği tekrar yapmamalı");
  assert.match(prompt, /GÜVENİLMEZ/, "prompt injection sınırı korunmalı");
  assert.match(prompt, /<facts>[\s\S]*<\/facts>/);
  assert.match(prompt, /<memory>[\s\S]*koşu[\s\S]*<\/memory>/);
});

test("hafıza yoksa boş <memory> etiketi eklenmez", async () => {
  const context = await buildCoachContext({ facts: analyze({}), messages: [{ role: "user", text: "merhaba" }] });
  const prompt = contextToSystemPrompt(context, { locale: "tr" });
  assert.ok(!prompt.includes("<memory>"));
});
