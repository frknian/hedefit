#!/usr/bin/env node
// "Yayınlanınca haber ver" listesini CSV olarak yazar: e-posta, kayıt zamanı, çıkış bağlantısı.
//   node scripts/export-subscribers.mjs > aboneler.csv     (SITE_URL ile çıkış bağlantısı kökünü değiştir)
import { spawnSync } from "node:child_process";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const wrangler = join(root, "node_modules", ".bin", "wrangler");
const base = (process.env.SITE_URL || "https://hedefit-site.frknian.workers.dev").replace(/\/+$/, "");
const NS = "SUBSCRIBERS";
const run = (args) => {
  const r = spawnSync(wrangler, ["kv", ...args, "--binding", NS, "--remote", "-c", join(root, "scripts/wrangler.site.jsonc")], { cwd: tmpdir(), encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (r.status !== 0) { process.stderr.write(r.stderr || "wrangler hata verdi\n"); process.exit(r.status ?? 1); }
  return r.stdout;
};
const keys = JSON.parse(run(["key", "list", "--prefix", "sub:"]));
console.log("email,kayit_zamani,cikis_baglantisi");
for (const { name } of keys) {
  const v = JSON.parse(run(["key", "get", name]));
  const email = name.slice(4);
  console.log(`${email},${v.at},${base}/api/unsubscribe?e=${encodeURIComponent(email)}&t=${v.t}`);
}
