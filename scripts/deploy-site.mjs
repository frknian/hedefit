#!/usr/bin/env node
// Tanıtım sitesini yayınlar (statik varlıklı Worker) ya da yerelde önizler.
//   Yayın:    npm run site:deploy
//             SITE_URL=https://alanadi.com npm run site:deploy     (özel alan adı: Worker o alan adına da bağlanır)
//             GOOGLE_PLAY_URL=… APP_STORE_URL=… npm run site:deploy  (mağaza butonları aktifleşir)
//   Önizleme: npm run site:preview                                 (http://localhost:8788)
//
// Wrangler bilinçli olarak depo DIŞINDAN çalıştırılır: kökteki `.wrangler/deploy`
// yönlendirmesi (API Worker'ı) komutları yanlış yapılandırmaya çeker.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_SITE_URL = "https://hedefit-site.frknian.workers.dev";
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(root, ".site-dist");
const wrangler = join(root, "node_modules", ".bin", "wrangler");
const mode = process.argv[2] === "preview" ? "preview" : "deploy";
const run = (cmd, args, opts = {}) => {
  const r = spawnSync(cmd, args, { stdio: "inherit", ...opts });
  if (r.status !== 0) process.exit(r.status ?? 1);
};

if (!existsSync(wrangler)) { console.error("wrangler bulunamadı. Önce: npm install"); process.exit(1); }

if (mode === "preview") {
  run("node", [join(root, "scripts/build-site.mjs"), "--preview"]);
  run(wrangler, ["pages", "dev", out, "--port", "8788"], { cwd: tmpdir() });
} else {
  run("node", [join(root, "scripts/build-site.mjs")], { env: { ...process.env, SITE_URL: process.env.SITE_URL || DEFAULT_SITE_URL } });
  // Özel alan adı: SITE_URL workers.dev dışındaysa Worker o alan adına (Cloudflare'deki bir bölgeye) bağlanır.
  const host = new URL(process.env.SITE_URL || DEFAULT_SITE_URL).hostname;
  const domainArgs = host.endsWith(".workers.dev") ? [] : ["--domain", host];
  run(wrangler, ["deploy", "-c", join(root, "scripts/wrangler.site.jsonc"), "--message", "Site deploy", ...domainArgs], { cwd: tmpdir() });
}
