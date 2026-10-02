// Raw / dry weight values for foods that people weigh before cooking.
//
// Cooking changes weight, not energy: 100 g of dry rice is ~360 kcal, but the
// same rice cooked weighs ~280 g and is ~130 kcal per 100 g. The resolver's
// default is the cooked value (people usually weigh what is on the plate); only
// an explicit "çiğ", "kuru" or "pişmemiş" switches to these rows, so "100 gram
// çiğ pirinç" is no longer read as 100 g of pilav (145 vs ~360 kcal).

import { normalizeTurkishText } from "./turkish-food-database.ts";

export type RawFood = {
  id: string;
  name: string;
  /** Matched against the accent-folded food text. */
  pattern: RegExp;
  calories: number;
  protein: number;
  carbohydrates: number;
  fat: number;
  fiber: number;
  /** Unit weights in grams (accent-folded unit names). */
  portions: Record<string, number>;
  /** Used when neither grams nor a unit was given. */
  defaultGrams: number;
  /** "kuru fasulye" is a cooked dish name, so only an explicit "çiğ"/"pişmemiş" counts for it. */
  strongMarkerOnly?: boolean;
};

export const RAW_FOODS: RawFood[] = [
  { id: "cig-pirinc", name: "Pirinç, çiğ", pattern: /(^|\s)pirinc(\s|$)/, calories: 360, protein: 6.7, carbohydrates: 79.3, fat: 0.7, fiber: 1.3, portions: { bardak: 180, "yemek kasigi": 15, porsiyon: 80, kase: 150 }, defaultGrams: 80 },
  { id: "cig-bulgur", name: "Bulgur, çiğ", pattern: /(^|\s)bulgur(\s|$)/, calories: 342, protein: 12.3, carbohydrates: 75.9, fat: 1.3, fiber: 12.5, portions: { bardak: 160, "yemek kasigi": 15, porsiyon: 80, kase: 140 }, defaultGrams: 80 },
  { id: "kuru-makarna", name: "Makarna, kuru", pattern: /(^|\s)(makarna|spagetti|penne|eriste|burgu)(\s|$)/, calories: 371, protein: 13, carbohydrates: 74.7, fat: 1.5, fiber: 3.2, portions: { porsiyon: 80, avuc: 80, bardak: 100, kase: 100 }, defaultGrams: 80 },
  { id: "kuru-mercimek", name: "Mercimek, kuru", pattern: /(^|\s)mercimek(\s|$)/, calories: 352, protein: 24.6, carbohydrates: 63.4, fat: 1.1, fiber: 10.7, portions: { bardak: 190, "yemek kasigi": 15, porsiyon: 80, kase: 150 }, defaultGrams: 80 },
  { id: "kuru-nohut", name: "Nohut, kuru", pattern: /(^|\s)nohut(\s|$)/, calories: 364, protein: 19.3, carbohydrates: 60.7, fat: 6, fiber: 17.4, portions: { bardak: 180, "yemek kasigi": 15, porsiyon: 80, kase: 150 }, defaultGrams: 80 },
  { id: "kuru-fasulye-cig", name: "Kuru fasulye, çiğ", pattern: /(^|\s)fasulye(\s|$)/, calories: 333, protein: 23.4, carbohydrates: 60, fat: 0.8, fiber: 15.2, portions: { bardak: 180, "yemek kasigi": 15, porsiyon: 80, kase: 150 }, defaultGrams: 80, strongMarkerOnly: true },
  { id: "cig-tavuk-gogsu", name: "Tavuk göğsü, çiğ", pattern: /(^|\s)tavuk(\s+gogsu|\s+gogus)?(\s|$)/, calories: 120, protein: 22.5, carbohydrates: 0, fat: 2.6, fiber: 0, portions: { porsiyon: 150, adet: 200, dilim: 40 }, defaultGrams: 150 },
  { id: "cig-kiyma", name: "Dana kıyma, çiğ", pattern: /(^|\s)kiyma(\s|$)/, calories: 250, protein: 17, carbohydrates: 0, fat: 20, fiber: 0, portions: { porsiyon: 100, adet: 100, "yemek kasigi": 20 }, defaultGrams: 100 },
];

// Words that mean the text names a prepared dish, not the bare ingredient.
const DISH_WORDS = /(corba|yemegi|kofte|salata|pilav|dolma|sarma|borek|manti|sote|tava|kavurma|doner|wrap|burger)/;
const STRONG_MARKER = /(^|\s)(cig|pismemis)(\s|$)/;
const WEAK_MARKER = /(^|\s)(kuru|ham)(\s|$)/;
// "kuru" also starts names that are not raw grains.
const NOT_A_RAW_REQUEST = /kuru (kayisi|uzum|incir|domates|dut|erik|yemis|hurma|fasulye)/;

/** The raw/dry row a text asks for ("çiğ pirinç", "kuru makarna"), or null. */
export function matchRawFood(text: string): RawFood | null {
  const normalized = normalizeTurkishText(text);
  if (DISH_WORDS.test(normalized)) return null;
  const strong = STRONG_MARKER.test(normalized);
  const weak = WEAK_MARKER.test(normalized) && !NOT_A_RAW_REQUEST.test(normalized);
  if (!strong && !weak) return null;
  return RAW_FOODS.find((raw) => raw.pattern.test(normalized) && (strong || !raw.strongMarkerOnly)) ?? null;
}
