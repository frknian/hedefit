import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import test from "node:test";

const script = new URL("../scripts/build-site.mjs", import.meta.url).pathname;
const distMain = new URL("../.site-dist/main.js", import.meta.url).pathname;

function build(env) {
  const clean = { ...process.env };
  delete clean.GOOGLE_PLAY_URL; delete clean.APP_STORE_URL;
  return spawnSync("node", [script], { env: { ...clean, SITE_URL: "https://hedefit.example", ...env }, encoding: "utf8" });
}

test("site build: mağaza adresleri verilmezse butonlar pasif kalır (boş yer tutucu)", () => {
  const result = build({});
  assert.equal(result.status, 0, result.stderr);
  const main = readFileSync(distMain, "utf8");
  assert.match(main, /'google-play': live\(''\), 'app-store': live\(''\)/);
  assert.ok(!main.includes("__STORE_"));
});

test("site build: GOOGLE_PLAY_URL ve APP_STORE_URL main.js'e yerleştirilir", () => {
  const play = "https://play.google.com/store/apps/details?id=com.hedefit.app";
  const apple = "https://apps.apple.com/app/hedefit/id1234567890";
  const result = build({ GOOGLE_PLAY_URL: play, APP_STORE_URL: apple });
  assert.equal(result.status, 0, result.stderr);
  const main = readFileSync(distMain, "utf8");
  assert.ok(main.includes(`live('${play}')`));
  assert.ok(main.includes(`live('${apple}')`));
});

test("site build: https dışı ya da kod enjekte eden mağaza adresi derlemeyi durdurur", () => {
  for (const bad of ["javascript:alert(1)", "http://play.google.com/x", "https://x.example/a'b", "https://x.example/a b"]) {
    const result = build({ GOOGLE_PLAY_URL: bad });
    assert.equal(result.status, 1, bad);
    assert.match(result.stderr, /GOOGLE_PLAY_URL/);
  }
});

test("site build: SITE_URL zorunlu ve yalnız alan adı olmalı", () => {
  const missing = spawnSync("node", [script], { env: { ...process.env, SITE_URL: "" }, encoding: "utf8" });
  assert.equal(missing.status, 1);
  const withPath = build({ SITE_URL: "https://hedefit.example/yol" });
  assert.equal(withPath.status, 1);
});

test("site build: yayın adresi canonical ve sitemap'e işlenir", () => {
  const result = build({ SITE_URL: "https://hedefit.example/" });
  assert.equal(result.status, 0, result.stderr);
  const index = readFileSync(new URL("../.site-dist/index.html", import.meta.url).pathname, "utf8");
  assert.match(index, /<link rel="canonical" href="https:\/\/hedefit\.example\/">/);
  const sitemap = readFileSync(new URL("../.site-dist/sitemap.xml", import.meta.url).pathname, "utf8");
  assert.match(sitemap, /<loc>https:\/\/hedefit\.example\/gizlilik<\/loc>/);
});
