#!/usr/bin/env node
// site/ → .site-dist/ : yayın adresini (__SITE_URL__) yerleştirir, sitemap.xml ve robots.txt üretir.
//   SITE_URL=https://hedefit.app node scripts/build-site.mjs
//   node scripts/build-site.mjs --preview      (yerel önizleme: http://localhost:8788)
import { cpSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const src = join(root, "site");
const out = join(root, ".site-dist");
const preview = process.argv.includes("--preview");
const raw = preview ? "http://localhost:8788" : process.env.SITE_URL;
if (!raw || !/^https?:\/\/[^/\s]+$/.test(raw.replace(/\/+$/, ""))) {
  console.error("SITE_URL gerekli ve yalnız alan adı olmalı. Örnek: SITE_URL=https://hedefit.app npm run site:build");
  process.exit(1);
}
const base = raw.replace(/\/+$/, "");

rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
cpSync(src, out, { recursive: true });

const walk = (dir) => readdirSync(dir).flatMap((f) => {
  const p = join(dir, f);
  return statSync(p).isDirectory() ? walk(p) : [p];
});
let replaced = 0;
for (const f of walk(out)) {
  if (!/\.(html|txt|xml|json|webmanifest)$/.test(f)) continue;
  const s = readFileSync(f, "utf8");
  if (s.includes("__SITE_URL__")) { writeFileSync(f, s.replaceAll("__SITE_URL__", base)); replaced++; }
}

const pages = ["/", "/planlar", "/gizlilik", "/destek", "/hesap-silme", "/detay", "/en/detail", "/en/", "/en/plans", "/en/privacy", "/en/support", "/en/delete-account"];
const today = new Date().toISOString().slice(0, 10);
writeFileSync(join(out, "sitemap.xml"),
  `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
  pages.map((p) => `  <url><loc>${base}${p}</loc><lastmod>${today}</lastmod></url>`).join("\n") + `\n</urlset>\n`);
writeFileSync(join(out, "robots.txt"), `User-agent: *\nAllow: /\n\nSitemap: ${base}/sitemap.xml\n`);

// Güvenlik ağı: yerleştirilmemiş yer tutucu kalmasın.
const left = walk(out).filter((f) => /\.(html|txt|xml)$/.test(f) && readFileSync(f, "utf8").includes("__SITE_URL__"));
if (left.length) { console.error("Yer tutucu kaldı:", left.map((f) => relative(out, f))); process.exit(1); }

const bytes = walk(out).reduce((n, f) => n + statSync(f).size, 0);
console.log(`Hazır: ${relative(root, out)}  (${walk(out).length} dosya, ${(bytes / 1048576).toFixed(1)} MB)`);
console.log(`Adres: ${base}  ·  ${replaced} dosyada yer tutucu dolduruldu`);
