// Güvenlik katmanı.
//
// NEDEN PROMPTUN DIŞINDA? Göç öncesinde tüm tıbbi sınırlar sistem promptunun
// içindeydi: "tanı koyma, göğüs ağrısında durdur". Bu, MODEL UYARSA çalışan
// bir kuraldır. Sağlayıcı değiştiğinde, model küçüldüğünde veya kullanıcı
// promptu dolaylı olarak zorladığında sessizce kaybolur. Acil bir belirtinin
// ("göğsüm ağrıyor") doğru yanıtı, hangi modelin cevapladığından bağımsız
// olmalıdır — bu yüzden kural burada, deterministik kodda.
//
// Katman iki iş yapar:
//   1) ENGELLE: acil/klinik durumlarda modele hiç gitmeden sabit, güvenli
//      yönlendirme döner (aynı zamanda ücretli çağrıdan da tasarruf).
//   2) UYAR: klinik sınıra yakın ama engellenmesi gereksiz konularda modele
//      ek bir kısıt cümlesi ekler.

export type SafetyDecision =
  | { blocked: true; reason: SafetyReason; response: string }
  | { blocked: false; reason?: SafetyReason; extraInstruction?: string };

export type SafetyReason = "emergency" | "self_harm" | "eating_disorder" | "medication" | "diagnosis" | "extreme_restriction";

// Kalıplar bilerek dar tutulur. Geniş bir kalıp ("ağrı") her kas ağrısı
// sorusunu acile çevirir; koç kullanılamaz hale gelir ve kullanıcı uyarıyı
// ciddiye almayı bırakır.
const PATTERNS: Array<{ reason: SafetyReason; tr: RegExp; en: RegExp }> = [
  {
    reason: "emergency",
    tr: /göğs(üm|ünde)?\s*(ağrı|sıkış)|göğüs ağrı|bayıl|bilincimi kaybet|nefes alamıyorum|felç|konuşmakta zorlan/i,
    // "hurts" da kapsanır: kullanıcılar "chest pain" gibi klinik bir ifade
    // yerine gündelik dili kullanıyor ve acil bir belirti bu yüzden kaçamaz.
    en: /chest\s*(pain|tight|hurt)|faint(ed|ing)?|lost consciousness|can'?t breathe|passing out|numb on one side/i,
  },
  {
    reason: "self_harm",
    tr: /kendime zarar|intihar|yaşamak istemiyorum|canıma kıy/i,
    en: /self[- ]?harm|suicid|kill myself|don'?t want to live/i,
  },
  {
    reason: "eating_disorder",
    tr: /kusarak|yedikten sonra kus|çıkarıyorum yediklerimi|anoreks|bulimi|yeme bozukluğu|müshil.*(zayıf|kilo)/i,
    en: /purg(e|ing)|make myself (vomit|throw up)|anorex|bulimi|eating disorder|laxative.*(weight|thin)/i,
  },
  {
    reason: "extreme_restriction",
    // Türkçede iki sözcük sırası da doğal: "günde 500 kalori" ve "500 kalori
    // ... günde". Tek yönlü kalıp ilkini hiç yakalamıyordu.
    tr: /(günde|günlük)\s*\d{2,4}\s*kalori|(\d{2,4})\s*kalori.*(gün|günde)|hiç(bir)? ?şey yemeden|aç kalarak.*(kilo|zayıf)|(\d+)\s*gün.*su orucu/i,
    en: /(\d{2,4})\s*calor(ie|ies).*(a day|per day|daily)|eat nothing|starv(e|ing) myself|(\d+)[- ]day water fast/i,
  },
  {
    reason: "medication",
    tr: /hangi ilac|ilaç öner|doz(um|unu)? (ne|kaç)|steroid.*(kullan|başla)|reçete/i,
    en: /which (drug|medication)|prescribe|what dose|dosage should i|start (a )?steroid/i,
  },
  {
    reason: "diagnosis",
    tr: /(bende|bana) .*(hastalık|kanser|tiroit|diyabet) mi|teşhis (koy|et)|tanı koy/i,
    en: /do i have (cancer|diabetes|thyroid|a disease)|diagnos(e|is) me/i,
  },
];

// Engellenen durumlarda gösterilen metinler. Kısa, suçlayıcı olmayan ve TEK bir
// eyleme yönlendiren cümleler; uzun uyarı listeleri okunmuyor.
const RESPONSES: Record<SafetyReason, { tr: string; en: string }> = {
  emergency: {
    tr: "Anlattığın belirtiler acil olabilir. Lütfen antrenmanı hemen bırak ve vakit kaybetmeden 112'yi ara ya da en yakın acil servise başvur. Ben bu konuda yönlendirme yapamam.",
    en: "What you're describing may be an emergency. Please stop exercising and contact emergency services or go to the nearest emergency room right away. I can't advise on this.",
  },
  self_harm: {
    tr: "Bunu paylaştığın için teşekkür ederim; yalnız değilsin. Ben bu konuda yardımcı olabilecek biri değilim. Türkiye'de 7/24 ücretsiz destek için 112'yi arayabilir ya da güvendiğin birine hemen ulaşabilirsin.",
    en: "Thank you for telling me — you don't have to handle this alone. I'm not the right source of help here. Please contact your local emergency number or a crisis line right away, or reach out to someone you trust.",
  },
  eating_disorder: {
    tr: "Bu anlattıkların bir sağlık uzmanının değerlendirmesi gereken bir konu ve ben burada beslenme önerisi vermeyeceğim. Bir doktora ya da klinik diyetisyene başvurman en doğrusu olur. Hedefit'i bu süreçte kilo takibi için kullanmamanı öneririm.",
    en: "What you're describing needs a health professional, and I won't give nutrition advice here. Please talk to a doctor or a clinical dietitian. I'd also suggest pausing weight tracking in Hedefit while you do.",
  },
  extreme_restriction: {
    tr: "Bu düzeyde bir kısıtlama güvenli değil ve sana böyle bir plan veremem. Sürdürülebilir bir açık için beslenme hedefini uygulamadaki hesaplanmış değere yakın tut; daha agresif bir plan istiyorsan bunu bir sağlık uzmanıyla konuş.",
    en: "That level of restriction isn't safe and I can't build a plan around it. Keep your intake close to the calculated target in the app for a sustainable deficit; if you want something more aggressive, please discuss it with a health professional.",
  },
  medication: {
    tr: "İlaç, doz veya takviye önerisi veremem — bu bir hekimin işi. Antrenman ve beslenme tarafında nasıl ilerleyeceğini konuşmak istersen buradayım.",
    en: "I can't recommend medication, dosages, or supplements — that's a doctor's call. I'm happy to help with the training and nutrition side instead.",
  },
  diagnosis: {
    tr: "Tanı koyamam; bunu ancak bir hekim muayene ve tetkikle söyleyebilir. Belirtilerin sürüyorsa bir sağlık kuruluşuna başvur. Bu arada antrenmanda ağrısız ve kontrollü kalmanı öneririm.",
    en: "I can't diagnose anything — only a doctor can, after an examination. If your symptoms persist, please see a healthcare provider. In the meantime, keep training pain-free and controlled.",
  },
};

/**
 * Kullanıcı girdisini değerlendirir. `blocked` ise istek HİÇBİR sağlayıcıya
 * gönderilmez.
 */
export function evaluateSafety(text: string, locale: "tr" | "en" = "tr"): SafetyDecision {
  const value = (text || "").slice(0, 2_000);
  if (!value.trim()) return { blocked: false };

  for (const pattern of PATTERNS) {
    // Her iki dilin kalıbı da denenir: kullanıcı arayüz dili Türkçeyken
    // İngilizce yazabilir; güvenlik kuralı dil seçimine bağlı olmamalı.
    if (pattern.tr.test(value) || pattern.en.test(value)) {
      return { blocked: true, reason: pattern.reason, response: RESPONSES[pattern.reason][locale] };
    }
  }
  return { blocked: false };
}

/**
 * Modelin çıktısına uygulanan son kontrol. Model, girdide hiçbir tetikleyici
 * olmasa bile kendiliğinden tanı cümlesi kurabilir; bu durumda yanıtın önüne
 * hatırlatma eklenir. Yanıtı SİLMEYİZ — faydalı içerik kaybolmasın.
 */
export function enforceOutputSafety(text: string, locale: "tr" | "en" = "tr"): string {
  const claimsDiagnosis = locale === "en"
    ? /\byou (have|are suffering from)\b.*\b(diabetes|cancer|thyroid|hypertension|anemia)\b/i.test(text)
    : /\b(sende|sizde)\b.*\b(diyabet|kanser|tiroit|hipertansiyon|anemi)\b.*\bvar\b/i.test(text);
  if (!claimsDiagnosis) return text;
  const notice = locale === "en"
    ? "Note: I can't diagnose conditions — please confirm anything health-related with a doctor.\n\n"
    : "Not: Tanı koyamam — sağlıkla ilgili her konuyu bir hekime doğrulatman gerekir.\n\n";
  return notice + text;
}
