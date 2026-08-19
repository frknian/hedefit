import { mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const root = fileURLToPath(new URL("..", import.meta.url));
const source = join(root, "store-assets/hedefit-logo-master.png");

async function writePng(path, pipeline) {
  const target = join(root, path);
  await mkdir(dirname(target), { recursive: true });
  await pipeline.png({ compressionLevel: 9 }).toFile(target);
}

function square(size) {
  return sharp(source).resize(size, size, { fit: "cover" }).removeAlpha();
}

async function socialGraphic(width, height) {
  const logoSize = Math.round(height * 0.66);
  const logoLeft = Math.round(width * 0.075);
  const logoTop = Math.round((height - logoSize) / 2);
  const copyLeft = logoLeft + logoSize + Math.round(width * 0.055);
  const titleSize = Math.round(height * 0.15);
  const sloganSize = Math.round(height * 0.053);
  const accentWidth = Math.round(width * 0.075);
  const overlay = Buffer.from(`
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      <rect width="${width}" height="${height}" fill="#050505"/>
      <rect x="${copyLeft}" y="${Math.round(height * 0.31)}" width="${accentWidth}" height="${Math.max(5, Math.round(height * 0.012))}" rx="3" fill="#63F20F"/>
      <text x="${copyLeft}" y="${Math.round(height * 0.54)}" fill="#FFFFFF" font-family="Arial, Helvetica, sans-serif" font-size="${titleSize}" font-weight="800" letter-spacing="-3">Hedefit</text>
      <text x="${copyLeft}" y="${Math.round(height * 0.65)}" fill="#8AF64A" font-family="Arial, Helvetica, sans-serif" font-size="${sloganSize}" font-weight="600">Hedefin için fit plan.</text>
    </svg>
  `);
  const logo = await resizedLogoBuffer(logoSize);
  return sharp({ create: { width, height, channels: 3, background: "#050505" } })
    .composite([
      { input: overlay, left: 0, top: 0 },
      { input: logo, left: logoLeft, top: logoTop },
    ]);
}

async function resizedLogoBuffer(size) {
  return sharp(source).resize(size, size, { fit: "cover" }).png().toBuffer();
}

async function roundLauncher(size) {
  const logo = await resizedLogoBuffer(size);
  const mask = Buffer.from(`<svg width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg"><circle cx="${size / 2}" cy="${size / 2}" r="${size / 2}" fill="#fff"/></svg>`);
  return sharp(logo).ensureAlpha().composite([{ input: mask, blend: "dest-in" }]);
}

async function monochrome(size) {
  const { data, info } = await sharp(source)
    .resize(size, size, { fit: "cover" })
    .removeAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  const output = Buffer.alloc(info.width * info.height * 4);
  for (let i = 0; i < info.width * info.height; i += 1) {
    const r = data[i * 3];
    const g = data[i * 3 + 1];
    const b = data[i * 3 + 2];
    const alpha = Math.max(r, g, b) >= 72 ? 255 : 0;
    output[i * 4] = 255;
    output[i * 4 + 1] = 255;
    output[i * 4 + 2] = 255;
    output[i * 4 + 3] = alpha;
  }
  return sharp(output, { raw: { width: info.width, height: info.height, channels: 4 } });
}

const squareAssets = [
  ["public/favicon-16.png", 16],
  ["public/favicon-32.png", 32],
  ["public/apple-touch-icon.png", 180],
  ["public/icon-192.png", 192],
  ["public/icon-512.png", 512],
  ["store-assets/play-icon-512.png", 512],
  ["ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-512@2x.png", 1024],
];

for (const [path, size] of squareAssets) await writePng(path, square(size));

const densities = [
  ["mdpi", 48, 108],
  ["hdpi", 72, 162],
  ["xhdpi", 96, 216],
  ["xxhdpi", 144, 324],
  ["xxxhdpi", 192, 432],
];

for (const [density, launcherSize, adaptiveSize] of densities) {
  const launcher = await resizedLogoBuffer(launcherSize);
  await writePng(`android/app/src/main/res/mipmap-${density}/ic_launcher.png`, sharp(launcher));
  await writePng(`android/app/src/main/res/mipmap-${density}/ic_launcher_round.png`, await roundLauncher(launcherSize));
  await writePng(`android/app/src/main/res/mipmap-${density}/ic_launcher_foreground.png`, square(adaptiveSize));
  await writePng(`android/app/src/main/res/mipmap-${density}/ic_launcher_monochrome.png`, await monochrome(adaptiveSize));
}

await writePng("store-assets/feature-graphic-1024x500.png", await socialGraphic(1024, 500));
await writePng("store-assets/github-social-preview-1280x640.png", await socialGraphic(1280, 640));
await writePng("public/og.png", await socialGraphic(1200, 630));

console.log("Hedefit marka varlıkları üretildi.");
