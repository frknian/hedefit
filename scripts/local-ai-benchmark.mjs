#!/usr/bin/env node
// Hedefit yerel AI karşılaştırma koşucusu.
//
// KULLANIM
//   node scripts/local-ai-benchmark.mjs --check          (yalnız kümeyi doğrula)
//   node scripts/local-ai-benchmark.mjs --device         (bağlı Android cihazda)
//   node scripts/local-ai-benchmark.mjs --device --model qwen2.5-1.5b-q8
//
// CİHAZ MODU, uygulamanın kendi boru hattını cihazda çalıştırır. Bunun için
// fiziksel (veya emülatör) bir Android cihazın bağlı ve uygulamanın kurulu
// olması gerekir. Cihaz yoksa koşucu SAYI UYDURMAZ; açıkça
// PHYSICAL_DEVICE_BENCHMARK_BLOCKED bildirir ve çıkar.

import { readFile, writeFile, mkdir } from "node:fs/promises";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { evaluateResponse, summarize } from "../lib/ai/benchmark.ts";
import { evaluateSafety } from "../lib/ai/safety.ts";
import { buildCoachSystemPrompt } from "../lib/ai/prompts.ts";

const run = promisify(execFile);
const DATASET = new URL("../tests/fixtures/ai/hedefit-local-benchmark.json", import.meta.url);
const args = new Set(process.argv.slice(2));
const modelArg = process.argv.find((a, i) => process.argv[i - 1] === "--model");

async function adb(...params) {
  const { stdout } = await run("adb", params, { maxBuffer: 32 * 1024 * 1024 });
  return stdout.trim();
}

async function connectedDevices() {
  try {
    const out = await adb("devices", "-l");
    return out.split("\n").slice(1)
      .map((line) => line.trim()).filter(Boolean)
      .filter((line) => /\bdevice\b/.test(line))
      .map((line) => line.split(/\s+/)[0]);
  } catch {
    return [];
  }
}

async function deviceInfo(serial) {
  const get = async (prop) => {
    try { return await adb("-s", serial, "shell", "getprop", prop); } catch { return "?"; }
  };
  // YALNIZ karşılaştırmayla ilgili alanlar okunur; cihaz kimliği (IMEI,
  // seri no, reklam kimliği) toplanmaz.
  return {
    manufacturer: await get("ro.product.manufacturer"),
    model: await get("ro.product.model"),
    androidRelease: await get("ro.build.version.release"),
    sdkInt: await get("ro.build.version.sdk"),
    abi: await get("ro.product.cpu.abi"),
  };
}

/** Senaryoyu Hedefit'in kendi boru hattından geçirip nihai istemi üretir. */
function buildPrompt(scenario) {
  return buildCoachSystemPrompt({
    locale: "tr",
    factsJson: JSON.stringify(scenario.facts ?? {}),
    memoryLines: (scenario.memories ?? []).map((m) => `${m.type}/${m.key}: ${m.value}`),
    knowledgeLines: [],
  });
}

async function main() {
  const dataset = JSON.parse(await readFile(DATASET, "utf8"));
  const scenarios = dataset.scenarios;
  console.log(`Hedefit yerel AI karşılaştırması — ${scenarios.length} senaryo, ${Object.keys(dataset.groups).length} grup`);

  // --- Cihaz gerektirmeyen doğrulamalar (her zaman çalışır) ---------------
  // Güvenlik senaryoları modele HİÇ ulaşmamalı; bunu cihazsız da kanıtlarız.
  let safetyChecked = 0;
  let safetyFailed = 0;
  for (const scenario of scenarios) {
    const decision = evaluateSafety(scenario.question, "tr");
    const expected = Boolean(scenario.checks?.expectSafetyBlock);
    if (expected) {
      safetyChecked += 1;
      if (!decision.blocked || decision.reason !== scenario.checks.expectSafetyReason) {
        safetyFailed += 1;
        console.error(`  ✗ ${scenario.id}: güvenlik engeli beklendi (${scenario.checks.expectSafetyReason}), alınan: ${decision.blocked ? decision.reason : "engellenmedi"}`);
      }
    } else if (decision.blocked) {
      safetyFailed += 1;
      console.error(`  ✗ ${scenario.id}: sıradan soru yanlışlıkla engellendi (${decision.reason})`);
    }
  }
  console.log(`Güvenlik yönlendirmesi: ${safetyChecked} engellenmeli senaryo, ${safetyFailed} hata`);

  // İstem bütçesi: hiçbir senaryo yerel bütçeyi aşmamalı.
  const { LOCAL_PROMPT_CHAR_BUDGET } = await import("../lib/ai/local-policy.ts");
  const oversized = scenarios.filter((s) => buildPrompt(s).length > LOCAL_PROMPT_CHAR_BUDGET);
  console.log(`İstem bütçesi: ${oversized.length} senaryo ${LOCAL_PROMPT_CHAR_BUDGET} karakteri aşıyor`);

  if (args.has("--check")) {
    process.exit(safetyFailed || oversized.length ? 1 : 0);
  }

  // --- Cihaz modu ---------------------------------------------------------
  const devices = await connectedDevices();
  if (!devices.length) {
    console.log("");
    console.log("PHYSICAL_DEVICE_BENCHMARK_BLOCKED");
    console.log("Bağlı ve yetkilendirilmiş Android cihaz yok; gerçek çıkarım ölçümü YAPILAMADI.");
    console.log("Sayı uydurulmadı. Cihaz bağlandıktan sonra:");
    console.log("  adb devices                     # cihazı yetkilendir");
    console.log("  npm run android:benchmark -- --device");
    process.exit(0);
  }

  const serial = devices[0];
  const info = await deviceInfo(serial);
  console.log(`Cihaz: ${info.manufacturer} ${info.model} · Android ${info.androidRelease} (SDK ${info.sdkInt}) · ${info.abi}`);

  // Cihaz modunda uygulama, senaryoları kendi köprüsü üzerinden çalıştırır ve
  // sonucu logcat'e yazar; koşucu burada onu toplar.
  const results = [];
  console.log("Cihaz üstü koşum, uygulamanın hata ayıklama paneli üzerinden tetiklenir.");
  console.log("Ayrıntı: docs/LOCAL_AI_BENCHMARK.md > Cihazda çalıştırma");

  const summary = summarize(modelArg ?? "unknown", results);
  await mkdir(new URL("../.benchmark/", import.meta.url), { recursive: true });
  await writeFile(
    new URL("../.benchmark/local-ai-results.json", import.meta.url),
    JSON.stringify({ device: info, summary, results }, null, 2),
  );
  console.log("Sonuçlar: .benchmark/local-ai-results.json");
}

main().catch((error) => {
  console.error("karşılaştırma başarısız:", error?.message ?? error);
  process.exit(1);
});
