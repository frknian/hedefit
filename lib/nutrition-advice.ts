export type NutritionAdviceInput = {
  calorieTarget: number;
  proteinTarget: number;
  carbsTarget: number;
  fatTarget: number;
  totals: { calories: number; protein: number; carbs: number; fat: number };
  meals: Array<{ name: string; meal: string; calories: number; protein: number; carbs: number; fat: number }>;
};

function gap(target: number, current: number) {
  return Math.max(0, Math.round(target - current));
}

/**
 * Hedefe kalan miktarlar — deterministik.
 *
 * Modele ham "hedef + tüketilen" gönderip farkı ONA hesaplatmak, dil
 * modellerinin en güvenilmez olduğu işi (aritmetik) kritik bir yerde yapmaktı.
 * `localNutritionAdvice` bu farkları zaten hesaplıyordu; aynı `gap()` burada da
 * kullanılır ki yerel öneri ile AI önerisi aynı sayıyı görsün.
 */
export function nutritionGaps(input: NutritionAdviceInput) {
  return {
    remainingCalories: gap(input.calorieTarget, input.totals.calories),
    remainingProteinGrams: gap(input.proteinTarget, input.totals.protein),
    remainingCarbsGrams: gap(input.carbsTarget, input.totals.carbs),
    remainingFatGrams: gap(input.fatTarget, input.totals.fat),
    calorieTargetReached: gap(input.calorieTarget, input.totals.calories) === 0,
  };
}

export type MealAdviceTotals = { calories: number; protein: number; carbs: number; fat: number };
export type MealAdviceTargets = { calorieTarget: number; proteinTarget: number; carbsTarget: number; fatTarget: number };
export type MealAdviceSnapshot = { date: string; totals: MealAdviceTotals; targets: MealAdviceTargets; locale: "tr" | "en"; revision: number };

/**
 * Öneri isteğinin CalorieTracker'da yeniden atılıp atılmayacağına karar
 * verir. Önceden totals/dailyEntries her değiştiğinde (yani her yemek
 * girişinde) yeniden isteniyordu; ücretsiz kullanıcının günlük
 * nutrition_advice hakkı tek bir öğün ekleme oturumunda tükenebiliyordu.
 *
 * "Yenile" düğmesi (revision) her ARTIŞINDA zorunlu yeniden istek anlamına
 * gelir — sabit bir eşik (ör. revision > 0) kullanmak ilk tıklamadan SONRA
 * kalıcı olarak devreye girip bu eşik atlamasını sonsuza dek açık bırakırdı;
 * bu yüzden önceki çağrının revision'ı ile KARŞILAŞTIRILIR.
 *
 * locale ve targets (calorieTarget vb.) isteğe AYNEN binen alanlardır; ilk
 * sürümde bu ikisi izlenmiyordu — kullanıcı dili değiştirdiğinde veya
 * hedeflerini güncellediğinde (totals aynı kalsa bile) eski dildeki/eski
 * hedefe göre üretilmiş öneri metni ekranda takılı kalıyordu. Bunlar sayısal
 * "ayar" değerleri olduğu için totals'taki gibi bir eşik uygulanmaz — HERHANGİ
 * bir değişiklik yeniden istek gerektirir.
 */
export function shouldRefetchMealAdvice(previous: MealAdviceSnapshot | null, next: MealAdviceSnapshot): boolean {
  if (!previous) return true;
  if (next.revision !== previous.revision) return true;
  if (next.date !== previous.date) return true;
  if (next.locale !== previous.locale) return true;
  if (next.targets.calorieTarget !== previous.targets.calorieTarget
    || next.targets.proteinTarget !== previous.targets.proteinTarget
    || next.targets.carbsTarget !== previous.targets.carbsTarget
    || next.targets.fatTarget !== previous.targets.fatTarget) return true;
  const meaningfulChange = (current: number, before: number) => Math.abs(current - before) >= Math.max(current, before) * 0.05 + 1;
  return meaningfulChange(next.totals.calories, previous.totals.calories)
    || meaningfulChange(next.totals.protein, previous.totals.protein)
    || meaningfulChange(next.totals.carbs, previous.totals.carbs)
    || meaningfulChange(next.totals.fat, previous.totals.fat);
}

export function localNutritionAdvice(input: NutritionAdviceInput, locale: "tr" | "en" = "tr") {
  if (locale === "en") {
    if (!input.meals.length) return "For your first meal, consider a protein source, a vegetable or fruit, and a carb that fits your needs. Adjust the portion to your hunger.";
    const proteinGap = gap(input.proteinTarget, input.totals.protein);
    const carbsGap = gap(input.carbsTarget, input.totals.carbs);
    const fatGap = gap(input.fatTarget, input.totals.fat);
    const calorieGap = gap(input.calorieTarget, input.totals.calories);
    if (!calorieGap) return "You've hit your daily energy target. Watch your hunger for your next choice; pick something simple like a vegetable, yogurt, or fruit if needed.";
    if (proteinGap >= Math.max(15, carbsGap * 0.45)) return `You have about ${proteinGap} g of room left for protein. You could pair a protein source like yogurt, eggs, chicken, fish, or legumes with a vegetable for your next meal.`;
    if (carbsGap > 35) return `You have about ${carbsGap} g of room left for carbs. You could add a portion that fits your needs from bulgur, oats, potatoes, whole-grain bread, or fruit.`;
    if (fatGap > 12) return "For your next meal, you could consider a small portion of unsaturated fat sources like olive oil, walnuts, or avocado.";
    return `You have about ${calorieGap} kcal of room left. A balanced meal with protein, fibrous vegetables, and a moderate carb can help you get closer to your target.`;
  }
  if (!input.meals.length) return "İlk öğününde protein kaynağı, sebze veya meyve ve ihtiyacına uygun bir karbonhidratı birlikte düşün. Porsiyonu açlık durumuna göre ayarla.";
  const proteinGap = gap(input.proteinTarget, input.totals.protein);
  const carbsGap = gap(input.carbsTarget, input.totals.carbs);
  const fatGap = gap(input.fatTarget, input.totals.fat);
  const calorieGap = gap(input.calorieTarget, input.totals.calories);
  if (!calorieGap) return "Günlük enerji hedefin doldu. Sonraki seçiminde açlık durumunu izle; gerekirse sebze, yoğurt veya meyve gibi sade bir seçenek tercih et.";
  if (proteinGap >= Math.max(15, carbsGap * 0.45)) return `Protein hedefinde yaklaşık ${proteinGap} g alan var. Sonraki öğünde yoğurt, yumurta, tavuk, balık veya bakliyat gibi bir protein kaynağını sebzeyle eşleştirebilirsin.`;
  if (carbsGap > 35) return `Karbonhidrat hedefinde yaklaşık ${carbsGap} g alan var. Bulgur, yulaf, patates, tam tahıllı ekmek veya meyveden ihtiyacına uygun bir porsiyon ekleyebilirsin.`;
  if (fatGap > 12) return "Sonraki öğünde zeytinyağı, ceviz veya avokado gibi doymamış yağ kaynaklarından küçük bir porsiyon düşünebilirsin.";
  return `Yaklaşık ${calorieGap} kcal alanın kaldı. Protein, lifli sebze ve ölçülü bir karbonhidrat içeren dengeli bir öğün hedefe yaklaşmanı kolaylaştırabilir.`;
}
