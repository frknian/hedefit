import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const tp = await readFile(new URL("../components/TrainingPrograms.tsx", import.meta.url), "utf8");
const css = await readFile(new URL("../app/globals.css", import.meta.url), "utf8");

test("akıllı programdaki her hareketin yanında değiştir düğmesi vardır", () => {
  // AI aynı listeyi her gün tekrarlıyordu (bkz. yorum satırı); kullanıcı 2.
  // günde de aynı hareketleri gördü. Kalıcı çözüm (günlük split) yerine
  // kullanıcının kendi değiştirebilmesi eklendi.
  assert.match(tp, /selection\.kind === "smart" && <button type="button" className="swap-trigger"/);
  // Yalnız akıllı programda: full body/bölgesel/özel programlarda kullanıcı
  // hareketleri zaten kendisi seçmiş ya da profilden geliyor.
  const article = tp.slice(tp.indexOf('<div className="program-exercise-head">'), tp.indexOf("</article>"));
  assert.match(article, /selection\.kind === "smart" && swapOpenFor === index/);
});

test("değiştirme indekse göre saklanır, isme göre değil", () => {
  // Bir hareket ikinci kez değiştirildiğinde alternatifler yine ORİJİNAL
  // hareketin bölgesine göre önerilmeli; isme göre saklansaydı değiştirilmiş
  // hareketin adı anahtar olur ve ikinci değişiklik yanlış listeyi ararı.
  assert.match(tp, /const \[swapState, setSwapState\] = useState<\{ key: string; swaps: Record<number, string>; openFor: number \| null \}>/);
  assert.match(tp, /function swapAlternatives\(index: number\): CatalogItem\[\] \{\s*\n\s*const original = smartWorkouts\[index\];/);
});

test("başka bir programa geçilince değişiklikler sıfırlanır (effect ya da ref olmadan)", () => {
  // react-hooks/set-state-in-effect ve react-hooks/refs kurallarına takılan
  // iki yaklaşım (useEffect ile sıfırlama, useRef ile son anahtarı tutma)
  // denenip reddedildi; anahtar uyuşmazlığında türetilen değer kullanılır.
  assert.doesNotMatch(tp, /useEffect/, "bu dosyada useEffect kullanılmamalı");
  assert.doesNotMatch(tp, /useRef/, "bu dosyada useRef kullanılmamalı");
  assert.match(tp, /const swaps = useMemo\(\(\) => swapState\.key === selectionKey \? swapState\.swaps : \{\}, \[swapState, selectionKey\]\);/);
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

test("değiştirme paneli mevcut swap-panel stillerini yeniden kullanır", () => {
  // Antrenman oynatıcısındaki değiştirme paneliyle aynı görsel dil; yeni bir
  // CSS bileşeni gerekmiyor.
  assert.match(css, /\.swap-panel \{/);
  assert.match(css, /\.program-exercise-head \{ display:flex/);
});
