import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

// Bu dört migration SECURITY DEFINER fonksiyonlar ve bir trigger içeriyor;
// gerçek davranışları yalnızca canlı bir Postgres'te doğrulanabilir (bkz.
// tests/streak.test.mjs "keeps streak updates inside the database layer" ve
// tests/nutrition-tracking.test.mjs'teki benzer desen). Burada anahtar
// güvenlik özelliklerinin dosyada GERÇEKTEN yazılı olduğunu — yanlışlıkla
// silinmediğini/bozulmadığını — doğruluyoruz. Uygulama tarafındaki davranışsal
// karşılıkları tests/usage-limits.test.mjs ve tests/account-lifecycle.test.mjs'te.

test("20260812_profile_privilege_guard: normal durum — is_premium ve deletion_pending yalnız service_role dışında engellenir", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_profile_privilege_guard.sql", import.meta.url), "utf8");
  assert.match(sql, /create trigger profiles_guard_privileged_columns/);
  assert.match(sql, /before update on public\.profiles/);
  assert.match(sql, /coalesce\(auth\.role\(\), ''\) <> 'service_role'/);
  assert.match(sql, /new\.is_premium := old\.is_premium/);
  assert.match(sql, /new\.account_status = 'deletion_pending'/);
});

test("20260812_profile_privilege_guard: edge case — account_status 'active'/'frozen' geçişi (ProfileManager freeze/unfreeze) engellenmez", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_profile_privilege_guard.sql", import.meta.url), "utf8");
  // Yalnız 'deletion_pending' hedefi engellenmeli; 'frozen' veya 'active'
  // hedefleri İÇİN bir kısıtlama YAZILMAMALI, yoksa components/ProfileManager.tsx
  // freezeAccount/unfreezeAccount'un doğrudan istemciden yaptığı güncelleme kırılır.
  assert.doesNotMatch(sql, /new\.account_status = 'frozen'/);
  assert.doesNotMatch(sql, /new\.account_status = 'active'/);
});

test("20260812_refund_usage_counter: normal durum — sayaç negatife düşmeden geri alınır", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_refund_usage_counter.sql", import.meta.url), "utf8");
  assert.match(sql, /create or replace function public\.refund_usage_counter\(p_feature text, p_amount integer default 1\)/);
  assert.match(sql, /security definer/);
  assert.match(sql, /greatest\(uc\.count - greatest\(p_amount, 0\), 0\)/, "sayaç asla negatife düşmemeli");
  assert.match(sql, /grant execute on function public\.refund_usage_counter\(text, integer\) to authenticated/);
});

test("20260812_refund_usage_counter: hatalı input — geçersiz feature reddedilir, kimliksiz çağrı engellenir", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_refund_usage_counter.sql", import.meta.url), "utf8");
  assert.match(sql, /if v_user is null then\s*raise exception 'not authenticated'/);
  assert.match(sql, /if p_feature not in \('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice', 'plan'\) then/);
});

test("20260812_plan_usage_limit: normal durum — 'plan' özelliği kota tablosuna ve fonksiyona eklendi", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_plan_usage_limit.sql", import.meta.url), "utf8");
  assert.match(sql, /check \(feature in \('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice', 'plan'\)\)/);
  assert.match(sql, /if p_feature not in \('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice', 'plan'\) then/);
});

test("20260812_plan_usage_limit: edge case — bonus_count'u olmayan (yeni) satırlarda effective_limit yine de doğru hesaplanır", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_plan_usage_limit.sql", import.meta.url), "utf8");
  // 'plan' reklam ödülü bonusuna dahil değil (BONUS_ELIGIBLE_FEATURES'ta yok);
  // coalesce(v_bonus, 0) bu durumda bile fonksiyonun çökmemesini sağlıyor.
  assert.match(sql, /v_effective_limit := p_limit \+ coalesce\(v_bonus, 0\);/);
});

test("20260812_combined_usage_check: normal durum — premium okuma ve sayaç artırma TEK fonksiyonda birleşiyor", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_combined_usage_check.sql", import.meta.url), "utf8");
  assert.match(sql, /create or replace function public\.check_and_consume_usage\(p_feature text, p_free_limit integer, p_premium_limit integer\)/);
  assert.match(sql, /returns table \(allowed boolean, current_count integer, effective_limit integer, is_premium boolean\)/);
  assert.match(sql, /select coalesce\(p\.is_premium, false\) into v_is_premium from public\.profiles p where p\.id = v_user/);
  assert.match(sql, /security definer/);
  assert.match(sql, /grant execute on function public\.check_and_consume_usage\(text, integer, integer\) to authenticated/);
});

test("20260812_combined_usage_check: hatalı input — geçersiz feature ve kimliksiz çağrı reddedilir", async () => {
  const sql = await readFile(new URL("../db/migrations/20260812_combined_usage_check.sql", import.meta.url), "utf8");
  assert.match(sql, /if v_user is null then\s*raise exception 'not authenticated'/);
  assert.match(sql, /if p_feature not in \('chat', 'photo', 'text_nutrition', 'weekly_review', 'nutrition_advice', 'plan'\) then\s*raise exception 'invalid feature'/);
});

test("uygulama kodu bu dört migration'a doğru sırayla bağlanır", async () => {
  const [usageLimits, schemaNote] = await Promise.all([
    readFile(new URL("../lib/usage-limits.ts", import.meta.url), "utf8"),
    readFile(new URL("../db/supabase-schema.sql", import.meta.url), "utf8"),
  ]);
  assert.match(usageLimits, /check_and_consume_usage/);
  assert.match(usageLimits, /refund_usage_counter/);
  assert.match(usageLimits, /legacyCheckAndConsumeUsage/);
  // supabase-schema.sql, is_premium'un tek başına RLS ile korunaksız
  // olduğunu ve 20260812_profile_privilege_guard.sql'in ZORUNLU olduğunu
  // açıkça belirtmeli — yalnız temel şemayla üretime çıkan biri bu riski
  // görsün.
  assert.match(schemaNote, /20260812_profile_privilege_guard\.sql/);
});
