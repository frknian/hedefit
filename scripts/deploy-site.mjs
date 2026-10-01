#!/usr/bin/env node
// Statik siteyi Cloudflare Pages'e yayınlar ya da yerelde önizler.
//   Yayın:    SITE_URL=https://hedefit.app CF_PAGES_PROJECT=hedefit-site npm run site:deploy
//   Önizleme: npm run site:preview            (http://localhost:8788)
//
// Wrangler bilinçli olarak depo DIŞINDAN çalıştırılır: kökteki `.wrangler/deploy`
// yönlendirmesi (API Worker'ı) Pages komutlarını yanlış yapılandırmaya çeker.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

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
  const { SITE_URL, CF_PAGES_PROJECT } = process.env;
  if (!SITE_URL || !CF_PAGES_PROJECT) {
    console.error("SITE_URL ve CF_PAGES_PROJECT gerekli.\nÖrnek: SITE_URL=https://hedefit.app CF_PAGES_PROJECT=hedefit-site npm run site:deploy");
    process.exit(1);
  }
  run("node", [join(root, "scripts/build-site.mjs")]);
  run(wrangler, ["pages", "deploy", out, `--project-name=${CF_PAGES_PROJECT}`, "--branch=main"], { cwd: tmpdir() });
}
