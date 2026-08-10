import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const tp = await readFile(new URL("../components/TrainingPrograms.tsx", import.meta.url), "utf8");
const css = await readFile(new URL("../app/globals.css", import.meta.url), "utf8");
const prefs = await readFile(new URL("../lib/preferences.ts", import.meta.url), "utf8");
const sync = await readFile(new URL("../lib/preference-sync.ts", import.meta.url), "utf8");

test("akıllı programdaki her hareketin yanında değiştir düğmesi vardır", () => {
  // AI aynı listeyi her gün tekrarlıyordu (bkz. yorum satırı); kullanıcı 2.
  // günde de aynı hareketleri gördü. Kalıcı çözüm (günlük split) yerine
  // kullanıcının kendi değiştirebilmesi eklendi.
  assert.match(tp, /selection\.kind === "smart" && <button\s*\n\s*type="button"\s*\n\s*className=\{isSwapped \? "exercise-swap-btn active" : "exercise-swap-btn"\}/);
  // Yalnız akıllı programda: full body/bölgesel/özel programlarda kullanıcı
  // hareketleri zaten kendisi seçmiş ya da profilden geliyor.
  const article = tp.slice(tp.indexOf('<div className="program-exercise-head">'), tp.indexOf("</article>"));
  assert.match(article, /selection\.kind === "smart" && swapOpenFor === index/);
});

test("değiştirme kalıcı tercihte İSME göre saklanır, bileşen state'inde değil", () => {
  // Eskiden bileşen state'inde indekse göre tutuluyordu ve ekrandan çıkınca
  // kayboluyordu ("otomatik kaydedilsin" isteğiyle çelişiyordu). Artık
  // isme göre saklanır: plan yeniden üretilip hareketler yer değiştirse
  // bile "bu hareketi görürsen böyle değiştir" anlamı geçerli kalır.
  assert.match(tp, /const storedSwaps = useStoredSmartProgramSwaps\(\);/);
  assert.match(tp, /setStoredSmartProgramSwaps\(\{ \.\.\.storedSwaps, \[original\.name\]: replacement\.name \}\);/);
  // Alternatif önerisi yine orijinal hareketin (index'teki) bölgesine göre
  // hesaplanır; değiştirilmiş hareketin bölgesine göre değil.
  assert.match(tp, /function swapAlternatives\(index: number\): CatalogItem\[\] \{\s*\n\s*const original = smartWorkouts\[index\];/);
});

test("hareket değiştirme localStorage'a yazar ve cihazlar arası eşitlenir", () => {
  assert.match(prefs, /const smartProgramSwapsStore = jsonPreference\("hedefit:smart-program-swaps"\);/);
  assert.match(prefs, /export function setStoredSmartProgramSwaps\(swaps: Record<string, string>\)/);
  assert.match(prefs, /export function useStoredSmartProgramSwaps\(\): Record<string, string>/);
  // PreferenceSync bilmediği anahtarı sessizce atar; kayıtlı olmazsa
  // değişiklik yalnız bu cihazda kalırdı.
  assert.match(sync, /"hedefit:smart-program-swaps"/);
});

test("orijinaline dönmek kayıttan siler, kalıcı olarak sıfırlanmamış bırakmaz", () => {
  const fn = tp.slice(tp.indexOf("function revertSwap"), tp.indexOf("function regionLabel"));
  assert.match(fn, /delete next\[original\.name\];/);
  assert.match(fn, /setStoredSmartProgramSwaps\(next\);/);
});

test("başka bir programa geçilince açık panel kapanır", () => {
  // storedSwaps artık kalıcı olduğu için sıfırlanmaz (bu istenen davranış:
  // "otomatik kaydedilsin"); yalnız o an açık olan panel kapanmalı, yoksa
  // yeni programda yanlış hareketin altında görünür.
  assert.match(tp, /function openSelection\(next: Selection \| null\) \{\s*\n\s*setSwapOpenFor\(null\);\s*\n\s*setSelection\(next\);\s*\n\s*\}/);
  assert.doesNotMatch(tp, /onClick=\{\(\) => setSelection\(/, "doğrudan setSelection çağrısı kalmamalı, openSelection kullanılmalı");
});

test("değiştirilen hareketin set/tekrar/dinlenme reçetesi korunur", () => {
  // Yalnızca hareketin kendisi değişmeli; kullanıcının planındaki yük şeması
  // (ör. "3 set · 12 tekrar") değişmemeli.
  const memo = tp.slice(tp.indexOf("const smartListWithSwaps"), tp.indexOf("function swapAlternatives"));
  assert.match(memo, /sets: item\.sets, rest: item\.rest, seconds: item\.seconds/);
});

test("değiştirilen liste hem gösterimde hem antrenmanı başlatırken kullanılır", () => {
  // İki yerden biri unutulsaydı, "Başla" düğmesi ekranda görünenden farklı
  // (değişmemiş) bir hareketle antrenmanı başlatırdı.
  assert.match(tp, /if \(selection\.kind === "smart"\) \{ onStart\(smartListWithSwaps, activeKey\); return; \}/);
  assert.match(tp, /const list: AiWorkout\[\] = selection\.kind === "smart" \? smartListWithSwaps : activeExercises\.map\(catalogItemToWorkout\);/);
});

test("değiştirdikten sonra kısa bir onay yazısı gösterilir", () => {
  assert.match(tp, /const \[justSwappedName, setJustSwappedName\] = useState<string \| null>\(null\);/);
  assert.match(tp, /setJustSwappedName\(replacement\.name\);/);
  assert.match(tp, /justSwappedName === item\.name && <small className="swap-confirm">/);
});

test("modernize edilmiş değiştirme arayüzü: ikon rozet, aktif durum, ikonlu alternatif kartları", () => {
  assert.match(css, /\.exercise-swap-btn \{ display:inline-flex/);
  assert.match(css, /\.exercise-swap-btn\.active \{/);
  assert.match(css, /\.swap-panel-head \{/);
  assert.match(css, /\.swap-current \{/);
  assert.match(css, /\.swap-option-icon \{/);
  // Antrenman oynatıcısındaki sade sürüm (.swap-trigger/.swap-panel temel
  // kuralları) bilerek korunuyor; yeni sınıflar üstüne eklendi, yerine değil.
  assert.match(css, /\.swap-trigger \{/);
  assert.match(css, /\.program-exercise-head \{ display:flex/);
});
