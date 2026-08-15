import assert from "node:assert/strict";
import test from "node:test";
import { isRewardedAdReward } from "../lib/mobile.ts";

// showRewardVideoAd() bir AdMobRewardItem ({ type, amount }) ile çözülüyordu
// ve eski kod `Boolean(reward)` ile kontrol ediyordu — bu, boş bir {} nesnesi
// için bile true dönerdi, yani reklamı hiç izlemeden de (veya SDK hatalı/boş
// bir nesneyle çözüldüğünde) kullanıcı ödül kazanmış sayılabilirdi.

test("isRewardedAdReward: normal durum — pozitif miktarlı gerçek ödül kabul edilir", () => {
  assert.equal(isRewardedAdReward({ type: "coin", amount: 1 }), true);
  assert.equal(isRewardedAdReward({ type: "coin", amount: 10 }), true);
});

test("isRewardedAdReward: edge case — miktar 0 veya negatifse ödül sayılmaz", () => {
  assert.equal(isRewardedAdReward({ type: "coin", amount: 0 }), false);
  assert.equal(isRewardedAdReward({ type: "coin", amount: -1 }), false);
  // Sayıya çevrilemeyen bir "amount" da (NaN) reddedilmeli.
  assert.equal(isRewardedAdReward({ type: "coin", amount: "abc" }), false);
});

test("isRewardedAdReward: hatalı input — boş nesne, null, undefined veya ilkel değerler reddedilir", () => {
  // Eski `Boolean(reward)` kontrolünün tam olarak kör olduğu nokta: boş {}
  // truthy'dir ama gerçek bir ödül taşımaz.
  assert.equal(isRewardedAdReward({}), false);
  assert.equal(isRewardedAdReward(null), false);
  assert.equal(isRewardedAdReward(undefined), false);
  assert.equal(isRewardedAdReward(true), false);
  assert.equal(isRewardedAdReward("amount:1"), false);
  assert.equal(isRewardedAdReward(1), false);
});
