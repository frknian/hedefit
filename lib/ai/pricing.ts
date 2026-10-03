// Model başına liste fiyatı (USD / 1M token). Maliyet TAHMİNİDİR: önbellek ve
// toplu indirimler, reasoning token farkları dahil değildir. Fiyat değişince
// buradan güncellenir; bilinmeyen model için maliyet null döner (0 yazılmaz).
const PRICES_USD_PER_MILLION: Array<{ match: RegExp; input: number; output: number }> = [
  { match: /^gpt-4o-mini/i, input: 0.15, output: 0.6 },
  { match: /^gpt-4o/i, input: 2.5, output: 10 },
  { match: /^gpt-5\.1/i, input: 1.25, output: 10 },
];

/** Tahmini maliyet, mikro-dolar (1 USD = 1_000_000). Model/token bilinmiyorsa null. */
export function estimateCostMicroUsd(model: string | undefined, inputTokens: number | undefined, outputTokens: number | undefined): number | null {
  if (!model || inputTokens === undefined || outputTokens === undefined) return null;
  const price = PRICES_USD_PER_MILLION.find((entry) => entry.match.test(model));
  if (!price) return null;
  // token * (USD/1M) = mikro-dolar.
  return Math.round(inputTokens * price.input + outputTokens * price.output);
}
