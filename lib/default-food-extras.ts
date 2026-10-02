// Additions to the default food catalogue (lib/default-food-catalog.ts).
//
// Why this file exists: a probe of 173 foods people actually log found 80 (46%)
// missing from every catalogue, so they fell to a flat 135 kcal/100 g guess
// (cola came out at 3x, olive oil at 6x too low…). Values are per 100 g (or
// 100 ml for drinks) from USDA FoodData Central / TürKomp-style reference
// values, rounded; "portions" are realistic gram weights per unit, keyed by the
// accent-folded unit name (see normalizeTurkishText), because a generic
// "tabak = 250 g" is wrong for oil, honey, nuts or supplements.

export type PortionTable = Record<string, number>;

export type ExtraFood = {
  id: string;
  name: string;
  aliases: string[];
  calories: number;
  protein: number;
  carbohydrates: number;
  fat: number;
  fiber: number;
  portions: PortionTable;
};

const food = (
  id: string, name: string, aliases: string[],
  calories: number, protein: number, carbohydrates: number, fat: number, fiber: number,
  portions: PortionTable,
): ExtraFood => ({ id, name, aliases, calories, protein, carbohydrates, fat, fiber, portions });

export const EXTRA_FOODS: ExtraFood[] = [
  // --- et, tavuk, balık, şarküteri -------------------------------------------------------------
  food("tavuk-but", "Tavuk but, pişmiş", ["tavuk but", "tavuk butu", "tavuk baget", "fırın tavuk but", "ızgara tavuk but"], 229, 24.8, 0, 14.7, 0, { adet: 110, porsiyon: 150 }),
  food("tavuk-kanat", "Tavuk kanat, pişmiş", ["tavuk kanat", "tavuk kanadı", "fırın tavuk kanat"], 254, 23.8, 0, 17.2, 0, { adet: 35, porsiyon: 150 }),
  food("hindi-fume", "Hindi füme", ["hindi füme", "füme hindi", "hindi jambon"], 105, 17, 2, 2.5, 0, { dilim: 20, porsiyon: 60 }),
  food("kuzu-pirzola", "Kuzu pirzola, pişmiş", ["kuzu pirzola", "pirzola", "kuzu eti", "kuzu"], 294, 25.6, 0, 20.9, 0, { adet: 60, porsiyon: 150 }),
  food("sucuk", "Sucuk", ["sucuk", "dilimlenmiş sucuk"], 440, 21, 3, 38, 0, { dilim: 10, adet: 25, porsiyon: 50 }),
  food("salam", "Salam", ["salam", "dana salam"], 300, 14, 2, 26, 0, { dilim: 10, porsiyon: 40 }),
  food("sosis", "Sosis", ["sosis", "dana sosis", "tavuk sosis"], 290, 12, 3, 26, 0, { adet: 40, porsiyon: 80 }),
  food("pastirma", "Pastırma", ["pastırma"], 195, 32, 1, 7, 0, { dilim: 15, porsiyon: 50 }),
  food("cipura-levrek", "Çipura / levrek, ızgara", ["çipura", "levrek", "ızgara çipura", "ızgara levrek", "ızgara balık", "balık ızgara"], 140, 24, 0, 4.5, 0, { adet: 150, porsiyon: 150 }),
  food("ton-baligi-yagda", "Ton balığı, yağda", ["yağlı ton", "ton balığı yağda", "ton balığı (yağda)"], 198, 29, 0, 8.2, 0, { kutu: 100, porsiyon: 100 }),
  food("karides", "Karides, pişmiş", ["karides", "ızgara karides"], 99, 24, 0.2, 0.3, 0, { adet: 12, porsiyon: 100 }),
  // --- süt ürünleri, yağlar, ezmeler, tatlandırıcılar -----------------------------------------------
  food("kefir", "Kefir", ["kefir"], 55, 3.3, 4.5, 3, 0, { bardak: 200, kase: 200, porsiyon: 200 }),
  food("labne", "Labne", ["labne", "labne peyniri"], 190, 6, 4, 17, 0, { "yemek kasigi": 20, dilim: 25, porsiyon: 40 }),
  food("tulum-peyniri", "Tulum peyniri", ["tulum peyniri", "tulum"], 330, 22, 2, 26, 0, { dilim: 30, porsiyon: 40 }),
  food("tereyagi", "Tereyağı", ["tereyağı", "tereyag"], 717, 0.9, 0.1, 81, 0, { "yemek kasigi": 14, "tatli kasigi": 9, "cay kasigi": 5, dilim: 10, porsiyon: 10, adet: 10 }),
  food("bal", "Bal", ["bal", "süzme bal", "petek bal"], 304, 0.3, 82, 0, 0.2, { "yemek kasigi": 21, "tatli kasigi": 14, "cay kasigi": 7, porsiyon: 20, kasik: 15 }),
  food("recel", "Reçel", ["reçel", "marmelat", "vişne reçeli", "çilek reçeli", "kayısı reçeli"], 250, 0.4, 65, 0.1, 1, { "yemek kasigi": 20, "tatli kasigi": 12, "cay kasigi": 7, porsiyon: 30 }),
  food("pekmez", "Pekmez", ["pekmez", "üzüm pekmezi", "dut pekmezi"], 292, 1, 74, 0, 0, { "yemek kasigi": 20, "tatli kasigi": 12, "cay kasigi": 7, porsiyon: 30 }),
  food("tahin", "Tahin", ["tahin", "tahini"], 595, 17, 21, 54, 9, { "yemek kasigi": 15, "tatli kasigi": 10, "cay kasigi": 5, porsiyon: 20 }),
  food("fistik-ezmesi", "Fıstık ezmesi", ["fıstık ezmesi", "yer fıstığı ezmesi", "peanut butter"], 588, 25, 20, 50, 6, { "yemek kasigi": 16, "tatli kasigi": 10, "cay kasigi": 6, porsiyon: 32 }),
  food("findik-kremasi", "Fındık kreması (Nutella)", ["nutella", "fındık kreması", "kakaolu fındık kreması"], 539, 6.3, 57.5, 30.9, 3.4, { "yemek kasigi": 18, "tatli kasigi": 10, "cay kasigi": 6, porsiyon: 15 }),
  food("seker", "Şeker", ["şeker", "toz şeker", "kesme şeker", "küp şeker", "sofra şekeri"], 387, 0, 100, 0, 0, { "cay kasigi": 4, "tatli kasigi": 8, "yemek kasigi": 12.5, adet: 4, kasik: 8, porsiyon: 8 }),
  food("ketcap", "Ketçap", ["ketçap", "ketcap"], 100, 1, 25, 0.1, 0.3, { "yemek kasigi": 17, "cay kasigi": 6, porsiyon: 20 }),
  food("mayonez", "Mayonez", ["mayonez"], 680, 1, 0.6, 75, 0, { "yemek kasigi": 14, "cay kasigi": 5, porsiyon: 15 }),
  food("hardal", "Hardal", ["hardal"], 66, 4.4, 5.8, 3.3, 4, { "cay kasigi": 5, "yemek kasigi": 15, porsiyon: 10 }),
  // --- atıştırmalık, tatlı, kuruyemiş -----------------------------------------------------------------
  food("cikolata", "Çikolata, sütlü", ["çikolata", "sütlü çikolata", "tablet çikolata"], 535, 7.7, 59, 30, 3.4, { adet: 40, kare: 6, dilim: 6, porsiyon: 25 }),
  food("bitter-cikolata", "Çikolata, bitter", ["bitter çikolata", "bitter"], 598, 7.8, 46, 43, 10.9, { adet: 40, kare: 6, dilim: 6, porsiyon: 20 }),
  food("biskuvi", "Bisküvi", ["bisküvi", "petibör", "tea biscuit"], 440, 7, 72, 13, 2, { adet: 8, porsiyon: 30, avuc: 30, paket: 100 }),
  food("kraker", "Kraker", ["kraker", "tuzlu kraker"], 430, 9, 71, 10, 2.5, { adet: 5, porsiyon: 30, avuc: 25 }),
  food("gofret", "Gofret", ["gofret", "çikolatalı gofret"], 520, 5, 66, 26, 1.5, { adet: 20, porsiyon: 20, paket: 40 }),
  food("cips", "Cips", ["cips", "patates cipsi"], 536, 6.5, 53, 34, 4.4, { porsiyon: 30, avuc: 20, kase: 40, paket: 50 }),
  food("patlamis-misir", "Patlamış mısır", ["patlamış mısır", "popcorn"], 387, 13, 78, 4.5, 14.5, { kase: 25, bardak: 8, porsiyon: 30, avuc: 8 }),
  food("karisik-kuruyemis", "Kuruyemiş, karışık", ["kuruyemiş", "karışık kuruyemiş", "çiğ kuruyemiş", "kuru yemiş"], 607, 20, 21, 54, 7, { avuc: 30, porsiyon: 30, kase: 60, "yemek kasigi": 10 }),
  food("findik", "Fındık", ["fındık"], 628, 15, 17, 61, 10, { adet: 1.5, avuc: 30, porsiyon: 30, kase: 60, "yemek kasigi": 10 }),
  food("yer-fistigi", "Yer fıstığı, kavrulmuş", ["yer fıstığı", "fıstık", "kavrulmuş fıstık", "yerfıstığı"], 585, 24, 21, 50, 8, { adet: 0.8, avuc: 30, porsiyon: 30, kase: 60, "yemek kasigi": 10 }),
  food("antep-fistigi", "Antep fıstığı", ["antep fıstığı"], 560, 20, 28, 45, 10, { adet: 0.8, avuc: 30, porsiyon: 30, kase: 60, "yemek kasigi": 10 }),
  food("kaju", "Kaju", ["kaju", "kaju fıstığı"], 553, 18, 30, 44, 3.3, { adet: 1.5, avuc: 30, porsiyon: 30, kase: 60, "yemek kasigi": 10 }),
  food("ay-cekirdegi", "Ay çekirdeği, kabuksuz", ["ay çekirdeği", "çekirdek"], 582, 21, 20, 51, 9, { avuc: 25, porsiyon: 30, kase: 80, "yemek kasigi": 10 }),
  food("hurma", "Hurma", ["hurma", "kuru hurma"], 282, 2.5, 75, 0.4, 8, { adet: 15, porsiyon: 60, avuc: 40 }),
  food("kuru-incir", "Kuru incir", ["kuru incir"], 249, 3.3, 64, 0.9, 9.8, { adet: 20, porsiyon: 60, avuc: 40 }),
  food("dondurma", "Dondurma", ["dondurma", "vanilyalı dondurma", "sade dondurma", "maraş dondurması"], 207, 3.5, 24, 11, 0.7, { adet: 100, top: 50, kase: 150, kepce: 50, porsiyon: 100 }),
  food("kek", "Kek", ["kek", "sade kek", "limonlu kek", "kakaolu kek", "muffin", "kek dilimi"], 375, 5, 55, 15, 1, { dilim: 50, adet: 60, porsiyon: 80 }),
  food("kurabiye", "Kurabiye", ["kurabiye", "un kurabiyesi"], 482, 6, 65, 22, 2, { adet: 15, porsiyon: 40, avuc: 40 }),
  food("kadayif", "Kadayıf, şerbetli", ["kadayıf", "tel kadayıf", "ekmek kadayıfı"], 385, 4, 50, 19, 1.5, { porsiyon: 120, adet: 80, dilim: 80 }),
  food("tiramisu", "Tiramisu", ["tiramisu"], 278, 4.5, 29, 16, 0.5, { porsiyon: 120, dilim: 100, kase: 150 }),
  food("granola", "Granola", ["granola"], 471, 10, 64, 20, 7, { kase: 50, "yemek kasigi": 10, porsiyon: 40 }),
  food("misir-gevregi", "Mısır gevreği", ["mısır gevreği", "corn flakes", "cornflakes", "gevrek"], 357, 7.5, 84, 0.4, 3, { kase: 40, porsiyon: 30, bardak: 25, "yemek kasigi": 4 }),
  food("musli", "Müsli", ["müsli", "muesli"], 365, 10, 66, 6.5, 7, { kase: 50, porsiyon: 45, "yemek kasigi": 10 }),
  food("yulaf-lapasi", "Yulaf lapası, pişmiş", ["yulaf lapası", "pişmiş yulaf", "porridge"], 71, 2.5, 12, 1.5, 1.7, { kase: 240, porsiyon: 240, bardak: 200, tabak: 300 }),
  food("dolma-genel", "Dolma, zeytinyağlı", ["dolma", "zeytinyağlı dolma"], 153, 2.5, 20, 7, 2, { adet: 50, porsiyon: 150, tabak: 200 }),
  // --- sebze, meyve ---------------------------------------------------------------------------------
  food("kabak", "Kabak, pişmiş", ["kabak", "sote kabak", "haşlanmış kabak"], 20, 1.1, 3.5, 0.3, 1.1, { adet: 200, porsiyon: 150, tabak: 200 }),
  food("patlican", "Patlıcan, pişmiş", ["patlıcan", "közlenmiş patlıcan"], 35, 0.8, 8.7, 0.2, 2.5, { adet: 200, porsiyon: 150, tabak: 200 }),
  food("karnabahar", "Karnabahar, pişmiş", ["karnabahar"], 23, 1.8, 4.1, 0.5, 2.3, { porsiyon: 150, tabak: 200 }),
  food("mantar", "Mantar", ["mantar", "kültür mantarı"], 22, 3.1, 3.3, 0.3, 1, { adet: 18, porsiyon: 100, kase: 70 }),
  food("avokado", "Avokado", ["avokado"], 160, 2, 8.5, 14.7, 6.7, { adet: 140, dilim: 35, porsiyon: 70 }),
  food("kavun", "Kavun", ["kavun"], 34, 0.8, 8.2, 0.2, 0.9, { dilim: 200, porsiyon: 200, kase: 160 }),
  food("kiraz", "Kiraz", ["kiraz"], 63, 1.1, 16, 0.2, 2.1, { adet: 8, avuc: 80, kase: 150, porsiyon: 100 }),
  food("nar", "Nar", ["nar"], 83, 1.7, 19, 1.2, 4, { adet: 140, porsiyon: 100, kase: 100 }),
  food("ananas", "Ananas", ["ananas"], 50, 0.5, 13, 0.1, 1.4, { dilim: 80, porsiyon: 150, kase: 140 }),
  food("incir-taze", "İncir, taze", ["incir", "taze incir"], 74, 0.8, 19, 0.3, 2.9, { adet: 50, porsiyon: 150 }),
  // --- içecekler (100 ml ≈ 100 g) -------------------------------------------------------------------------
  food("kola", "Kola", ["kola", "coca cola", "cola", "pepsi", "coca-cola"], 42, 0, 10.6, 0, 0, { kutu: 330, sise: 500, bardak: 200, porsiyon: 330 }),
  food("kola-sekersiz", "Kola, şekersiz", ["kola zero", "zero kola", "diyet kola", "light kola", "şekersiz kola", "coca cola zero", "pepsi max"], 1, 0, 0, 0, 0, { kutu: 330, sise: 500, bardak: 200, porsiyon: 330 }),
  food("gazoz", "Gazoz", ["gazoz", "sprite", "fanta", "meşrubat", "7up"], 40, 0, 10, 0, 0, { kutu: 330, sise: 250, bardak: 200, porsiyon: 330 }),
  food("soda", "Soda / maden suyu", ["soda", "maden suyu", "sade soda"], 0, 0, 0, 0, 0, { kutu: 200, sise: 200, bardak: 200, porsiyon: 200 }),
  food("meyve-suyu", "Meyve suyu", ["meyve suyu", "vişne suyu", "şeftali suyu", "kayısı suyu", "elma suyu"], 46, 0.5, 11, 0.1, 0.2, { kutu: 200, bardak: 200, sise: 250, porsiyon: 200 }),
  food("latte", "Latte", ["latte", "caffè latte", "cafe latte", "süt kahve", "flat white"], 54, 3.4, 5, 2.2, 0, { porsiyon: 300, adet: 300, bardak: 250, fincan: 150 }),
  food("cappuccino", "Cappuccino", ["cappuccino", "kapuçino", "kapuccino"], 45, 2.5, 4, 2, 0, { porsiyon: 200, adet: 200, bardak: 200, fincan: 150 }),
  food("kahve-3u1", "Kahve 3ü1 arada (toz)", ["nescafe 3ü1 arada", "nescafe 3u1 arada", "kahve 3ü1 arada", "üçü bir arada kahve"], 410, 3, 68, 14, 0, { adet: 18, porsiyon: 18, "cay kasigi": 6 }),
  food("enerji-icecegi", "Enerji içeceği", ["enerji içeceği", "red bull", "redbull", "monster", "burn"], 45, 0, 11, 0, 0, { kutu: 250, sise: 250, porsiyon: 250 }),
  food("bira", "Bira", ["bira", "efes", "tuborg"], 43, 0.5, 3.6, 0, 0, { sise: 330, kutu: 500, bardak: 200, porsiyon: 330 }),
  food("sarap", "Şarap", ["şarap", "kırmızı şarap", "beyaz şarap", "wine"], 83, 0.1, 2.6, 0, 0, { kadeh: 150, bardak: 150, sise: 750, porsiyon: 150 }),
  food("raki", "Rakı", ["rakı", "raki"], 250, 0, 0, 0, 0, { kadeh: 50, bardak: 50, sise: 700, porsiyon: 50 }),
  food("sert-icki", "Sert alkollü içki (votka, viski, cin)", ["votka", "viski", "cin", "tekila", "konyak"], 230, 0, 0, 0, 0, { kadeh: 40, bardak: 40, porsiyon: 40, sise: 700 }),
  // --- spor beslenmesi ------------------------------------------------------------------------------------
  food("protein-tozu", "Protein tozu (whey)", ["protein tozu", "whey protein", "whey", "protein tozu (whey)"], 400, 80, 8, 6, 0, { olcu: 30, "yemek kasigi": 10, porsiyon: 30, adet: 30, kasik: 15 }),
  food("protein-bar", "Protein bar", ["protein bar", "protein barı", "proteinli bar"], 370, 33, 35, 11, 5, { adet: 55, porsiyon: 55 }),
  food("tuz", "Tuz", ["tuz", "sofra tuzu"], 0, 0, 0, 0, 0, { "cay kasigi": 6, "tatli kasigi": 10, "yemek kasigi": 18, porsiyon: 2 }),
  food("kreatin", "Kreatin", ["kreatin", "creatine"], 0, 0, 0, 0, 0, { "cay kasigi": 5, "yemek kasigi": 15, olcu: 5, porsiyon: 5, adet: 5 }),
];

/** Extra names for entries that already exist in the catalogue (keyed by id). */
export const ALIAS_ADDITIONS: Record<string, string[]> = {
  "tavuk-gogsu": ["tavuk göğsü", "ızgara tavuk göğsü", "haşlanmış tavuk göğsü", "tavuk göğüs", "haşlanmış tavuk", "fırın tavuk göğsü"],
  "dana-yagsiz": ["biftek", "dana biftek", "steak", "antrikot", "bonfile", "ızgara et"],
  "ton-baligi": ["konserve ton", "konserve ton balığı", "ton balığı konservesi"],
  "sade-kahve": ["filtre kahve", "americano", "sade nescafe", "nescafe", "espresso"],
  yulaf: ["yulaf ezmesi", "kuru yulaf", "oatmeal"],
};

/**
 * Per-food portion weights for entries that already exist. Without these a
 * "bowl of oats" used the generic 250 g bowl against the 379 kcal/100 g DRY oat
 * value (948 kcal for one bowl) and a tablespoon of olive oil weighed 25 g.
 */
export const PORTION_OVERRIDES: Record<string, PortionTable> = {
  yulaf: { kase: 50, "yemek kasigi": 10, bardak: 80, porsiyon: 40, avuc: 30, tabak: 80 },
  zeytinyagi: { "yemek kasigi": 13.5, "tatli kasigi": 9, "cay kasigi": 4.5, porsiyon: 13.5 },
  badem: { adet: 1.2, avuc: 30, porsiyon: 30, kase: 60, "yemek kasigi": 10 },
  ceviz: { adet: 8, avuc: 30, porsiyon: 30, kase: 60, "yemek kasigi": 10 },
  "tavuk-gogsu": { porsiyon: 120, adet: 150, dilim: 40, avuc: 40, tabak: 150, "yemek kasigi": 20 },
  "hindi-gogsu": { porsiyon: 120, dilim: 25, adet: 150 },
  kiyma: { porsiyon: 100, adet: 100 },
  kofte: { adet: 30, porsiyon: 150 },
  somon: { porsiyon: 150, dilim: 120, adet: 150 },
  "ton-baligi": { kutu: 100, porsiyon: 100 },
  sut: { bardak: 200, kase: 200, fincan: 100, porsiyon: 200, sise: 250 },
  yogurt: { kase: 150, bardak: 200, "yemek kasigi": 20, porsiyon: 150, kepce: 60 },
  "suzme-yogurt": { kase: 150, bardak: 200, "yemek kasigi": 20, porsiyon: 150, kepce: 60 },
  "beyaz-peynir": { dilim: 30, porsiyon: 40, adet: 30, "yemek kasigi": 20 },
  "lor-peyniri": { "yemek kasigi": 20, porsiyon: 50 },
  "yumurta-beyazi": { adet: 33, tane: 33 },
};
