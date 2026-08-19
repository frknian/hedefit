import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const tracker = await readFile(new URL("../components/CalorieTracker.tsx", import.meta.url), "utf8");
const app = await readFile(new URL("../components/FitAiApp.tsx", import.meta.url), "utf8");
const training = await readFile(new URL("../components/TrainingPrograms.tsx", import.meta.url), "utf8");
const css = await readFile(new URL("../app/globals.css", import.meta.url), "utf8");

test("öğün ekleme alanı istenen sırada dizilir", () => {
  // Sıra: öğün seçimi → porsiyon → besin adı + analiz → ekle.
  const workspace = tracker.slice(tracker.indexOf('className="entry-workspace"'), tracker.indexOf('className="food-message"'));
  const at = (needle) => {
    const index = workspace.indexOf(needle);
    assert.ok(index > 0, `bulunamadı: ${needle}`);
    return index;
  };
  const meal = at("t.calorieTracker.mealLabel");
  const portion = at('className="portion-field"');
  const name = at('className="food-name-row"');
  const add = at('className="primary-btn add-food"');
  assert.ok(meal < portion, "porsiyon öğün seçiminin altında olmalı");
  assert.ok(portion < name, "besin adı porsiyonun altında olmalı");
  assert.ok(name < add, "ekle en sonda olmalı");
});

test("gram/ml ile ev ölçüleri porsiyon alanında birlikte durur", () => {
  const field = tracker.slice(tracker.indexOf('className="portion-field"'), tracker.indexOf('className="food-name-row"'));
  assert.match(field, /PRIMARY_PORTION_UNITS\.map/, "gram/ml anahtarı");
  assert.match(field, /HOUSEHOLD_PORTION_UNITS\.map/, "bardak, tabak, kase");
});

test("AI ile analiz et besin adının yanında durur", () => {
  const row = tracker.slice(tracker.indexOf('className="food-name-row"'), tracker.indexOf('className="ai-estimate-card"'));
  assert.match(row, /className="food-name"/);
  assert.match(row, /className="ai-estimate-btn"/);
  // Yalnız bir kez render edilmeli; eski konumu kaldırıldı.
  assert.equal(tracker.split('className="ai-estimate-btn"').length - 1, 1);
});

test("alan dikey akışa alınır ve kural en sonda tanımlanır", () => {
  // .manual-fields birden çok yerde tanımlı; sıralama hatası bu dosyada daha
  // önce sessiz bir hataya yol açmıştı (bkz. .ai-nutrition-values).
  const override = css.lastIndexOf(".manual-fields { grid-template-columns:minmax(0,1fr);");
  assert.ok(override > 0, "dikey akış kuralı bulunmalı");
  const others = [...css.matchAll(/\.manual-fields \{ grid-template-columns:/g)].map((match) => match.index);
  assert.ok(others.every((index) => index <= override), "kural diğer tanımlardan sonra gelmeli");
  assert.match(css, /\.food-name-row \{ display:flex;/);
});

test("günün özeti sayfanın en üstünde, hedef paneli en altında", () => {
  // Bu ekranın ilk sorusu "ne kadar kaldı". Özet formun altındayken kullanıcı
  // öğün ekledikten sonra sonucu görmek için kaydırmak zorunda kalıyordu.
  // Hedef DÜZENLEME paneli ise ayda bir dokunulan bir ayar, en sonda durur.
  const at = (needle) => {
    const index = tracker.indexOf(needle);
    assert.ok(index > 0, `bulunamadı: ${needle}`);
    return index;
  };
  const head = at('className="calorie-page-head"');
  const hero = at('className="calorie-hero"');
  const form = at('className="food-entry-panel"');
  const log = at('className="food-log"');
  const goals = at("<NutritionGoalsPanel");
  assert.ok(head < hero, "tarih şeridi en üstte");
  assert.ok(hero < form, "günün özeti formun üstünde olmalı");
  assert.ok(form < log, "öğün günlüğü formun altında");
  assert.ok(log < goals, "hedef paneli günlükten sonra, en sonda");
});

test("özet kartı kalanı halkada ve makroları çubukla gösterir", () => {
  // "0/112g" metni tek başına ne kadar yol alındığını göstermiyordu.
  const hero = tracker.slice(tracker.indexOf('className="calorie-hero"'), tracker.indexOf('className="food-entry-panel"'));
  assert.match(hero, /--macro-progress/, "makrolar oransal çubuk taşımalı");
  assert.match(hero, /calorie-progress/, "kalan kalori halkada");
  assert.doesNotMatch(hero, /calorie-remaining/, "ayrı kalan sütunu halkaya taşındı");
  assert.match(css, /\.macro-row span::after \{ content:""/, "çubuk için stil bulunmalı");
});

test("boş öğün grupları tek satıra iner", () => {
  // Dördü birden "Henüz kayıt yok" derken ekranın 400 pikselini yiyordu.
  const log = tracker.slice(tracker.indexOf('className="food-log"'));
  assert.match(log, /group\.length \? "meal-group" : "meal-group empty"/);
  assert.doesNotMatch(log, /empty-meal/, "ayrı boş satır bileşeni kaldırıldı");
  assert.match(css, /\.meal-group\.empty \.meal-group-head \{[^}]*border-bottom:0/);
});

test("sık yediklerin öğün ekleme panelinin altında", () => {
  // Kısayol, formun üstünde durunca asıl işi aşağı itiyordu.
  const panel = tracker.indexOf('className="food-entry-panel"');
  const frequent = tracker.indexOf('className="frequent-meals"');
  assert.ok(panel > 0 && frequent > 0);
  assert.ok(frequent > panel, "sık yenenler formdan sonra gelmeli");
});

test("spor ekle kendi sekmesinde, antrenman sekmesinden kalktı", () => {
  // Spor ekleme artık antrenman sekmesindeki bir düğmenin arkasında değil:
  // "Aktivite günlüğü" kendi sekmesi oldu ve ActivityLogger orada satır içi durur.
  assert.doesNotMatch(training, /className="activity-open"/, "buton antrenman sekmesinden kalkmalı");
  assert.doesNotMatch(training, /onOpenActivityLog/, "prop tamamen kalkmalı");
  assert.doesNotMatch(app, /onOpenActivityLog/);
  const page = app.slice(app.indexOf('activeView === "activity" ?'), app.indexOf('</> : <>', app.indexOf('activeView === "activity" ?')));
  assert.ok(page.length > 0, "aktivite sekmesi dalı olmalı");
  assert.match(page, /<ActivityLogger userId=\{authUser\.id\}/, "spor ekleme bu sayfada satır içi olmalı");
});

test("Hedefit Rota aktivite sayfasının başında, ana ekranda değil", () => {
  const marker = app.indexOf('activeView === "activity" ?');
  const page = app.slice(marker, app.indexOf('</> : <>', marker));
  const rota = page.indexOf('className="hedefit-rota-card"');
  const logger = page.indexOf("<ActivityLogger");
  assert.ok(rota > 0 && logger > rota, "Hedefit Rota kartı spor ekleme listesinin üstünde olmalı");
  assert.match(page, /setGpsTrackerOpen\(true\)/);
  // Ana ekranda ne kart ne de eski iki düğmelik şerit kalmalı.
  const homeStart = app.indexOf('className="dashboard-head"');
  const home = app.slice(homeStart, app.indexOf("</section>", homeStart));
  assert.doesNotMatch(home, /hedefit-rota-card|gps-activity-entry/);
});

test("aktiviteyi başlat ve aktivite günlüğü kısayollarda", async () => {
  const quick = await readFile(new URL("../lib/quick-actions.ts", import.meta.url), "utf8");
  assert.match(quick, /\{ id: "startActivity", view: "activity", overlay: "gpsTracker" \}/);
  assert.match(quick, /\{ id: "activityLog", view: "activity" \}/);
  // Kısayoldan açılan kaplama: sayfa değil diyalog olduğu için ayrı bir alan.
  assert.match(app, /if \(overlay === "gpsTracker"\) \{ setGpsTrackerOpen\(true\); return; \}/);
});

test("aktivite kaplamaları görünüm dallarının dışında kalır", () => {
  // Ana ekran bölümünün içinde kalsaydı koşullu dallarda kırpılabilirdi.
  const homeEnd = app.indexOf("</section>", app.indexOf('className="dashboard-head"'));
  assert.ok(app.indexOf('{gpsTrackerOpen && authUser &&') > homeEnd, "canlı takip kaplaması dışarıda olmalı");
  assert.ok(app.indexOf('{activityLogOpen && authUser &&') > homeEnd, "günlük kaplaması dışarıda olmalı");
  // Eski, ayrı ActivityLogger kaplaması kalktı: artık kendi sayfasında satır içi.
  assert.doesNotMatch(app, /activityOpen/);
});

test("ana ekran hedef planı, enerji satırı ve kısayolları bu sırayla dizer", () => {
  // İstenen sıra: selamlama → hedef planı → kalori dengesi + adım sayar → kısayollar.
  const homeStart = app.indexOf('className="dashboard-head"');
  const home = app.slice(homeStart, app.indexOf("</section>", homeStart));
  const at = (needle) => {
    const index = home.indexOf(needle);
    assert.ok(index > 0, `bulunamadı: ${needle}`);
    return index;
  };
  const header = at("<ActivityStreak");
  const goal = at("<GoalPlanCard compact");
  const energy = at('className="home-top-row"');
  const actions = at("<QuickActions");
  assert.ok(header < goal, "selamlama en üstte olmalı");
  assert.ok(goal < energy, "hedef planı enerji satırının üstünde olmalı");
  assert.ok(energy < actions, "kısayollar en altta olmalı");
  assert.doesNotMatch(home, /<MobilePager/, "sayfalama kaldırılmalı");
});
