// Premium: bugünün uyarlamasının KİŞİYE ÖZEL açıklaması (koç sesiyle). Şablon açıklama her zaman vardır ve yedektir;
// bu fonksiyon yalnızca onu daha kişisel hale getirir.
//
// PRİVACY: modele YALNIZCA kaba, türetilmiş bilgi gider (seviye, nedenler, uygulanan eylemler, süre). Ham sağlık
// sayıları ve döngü bilgisi (gün, faz) GÖNDERİLMEZ; döngü yalnızca "küçük bir bağlam kullanıldı" bayrağı olarak geçer.

import type { AdaptiveResult } from "../training/adaptive-engine.ts";
import { generateCoachTaskText } from "./coach.ts";
import { LOCAL_PROVIDER_ID } from "./providers/deterministic-local.ts";

const RULES = {
  tr: "Kullanıcının bugünkü plan uyarlamasını 2–3 cümleyle, sıcak ve sade Türkçeyle açıkla. Yalnızca <facts> içindekileri kullan; yeni sayı, hareket ya da gerekçe uydurma. Programı çöpe atmadığını, kullanıcının KENDİ cevaplarına göre uyarlandığını vurgula. Tıbbi iddia, teşhis, hormon ya da doğurganlık yorumu yapma. Yalnızca düz metin yaz.",
  en: "Explain the user's plan adaptation for today in 2–3 sentences, warm and plain. Use only what is in <facts>; do not invent numbers, exercises or reasons. Stress that the program was not scrapped and was adapted to the user's OWN answers. Make no medical, diagnostic, hormonal or fertility claims. Write plain text only.",
};

export function adaptationFacts(result: AdaptiveResult) {
  return {
    level: result.level,
    intensity: result.intensity,
    reasons: result.signals.reasons,
    actions: result.applied.map((entry) => entry.action),
    estimatedMinutes: result.estimatedMinutes,
    sessionKind: result.wellnessKind ?? undefined,
    smallContextUsed: result.signals.cycle === "nudged" || undefined,
  };
}

export async function explainAdaptation(input: { result: AdaptiveResult; locale: "tr" | "en"; generate?: typeof generateCoachTaskText }): Promise<string | null> {
  if (!input.result.adapted) return null;
  try {
    const generate = input.generate ?? generateCoachTaskText;
    const response = await generate({
      facts: adaptationFacts(input.result),
      domainRules: RULES[input.locale],
      prompt: input.locale === "en" ? "Write today's adaptation explanation." : "Bugünkü uyarlama açıklamasını yaz.",
      category: "daily_summary",
      locale: input.locale,
      maxOutputTokens: 220,
      abortSignal: AbortSignal.timeout(7_000),
      policy: { mode: "auto" },
    });
    const text = response.text.trim().replace(/\s+/g, " ").slice(0, 420);
    // Yerel yedek sağlayıcı şablon üretir; bunu "kişisel AI açıklaması" diye sunmayız.
    return text && response.provider !== LOCAL_PROVIDER_ID ? text : null;
  } catch {
    return null;
  }
}
