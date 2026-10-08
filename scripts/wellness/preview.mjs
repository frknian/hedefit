// Kontak sayfası: node scripts/wellness/preview.mjs pilates  → /tmp/hf/wellness_pilates.jpg
import { createRequire } from "node:module";
import { renderFrame } from "./figure.mjs";
const sharp = createRequire(new URL("../../package.json", import.meta.url))("sharp");
const name = process.argv[2];
const { [name]: list } = await import(`./${name === "lowImpact" ? "low-impact" : name}.mjs`);
const cell = 300, cols = 6;
const rows = Math.ceil((list.length * 2) / cols);
const tiles = [];
for (const [i, exercise] of list.entries()) for (const f of [0, 1]) {
  const index = i * 2 + f;
  const input = await sharp(Buffer.from(renderFrame(exercise, f))).resize(cell, cell).png().toBuffer();
  const label = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${cell}" height="26"><text x="8" y="18" font-family="Helvetica" font-size="15" fill="#9fb0c0">${i + 1}. ${exercise.id.replace("wl-", "")} ${f ? "▶ peak" : "▶ start"}</text></svg>`);
  tiles.push({ input, left: (index % cols) * cell, top: Math.floor(index / cols) * cell }, { input: label, left: (index % cols) * cell, top: Math.floor(index / cols) * cell });
}
await sharp({ create: { width: cols * cell, height: rows * cell, channels: 3, background: "#000" } }).composite(tiles).jpeg({ quality: 80 }).toFile(`/tmp/hf/wellness_${name}.jpg`);
console.log("ok", list.length);
