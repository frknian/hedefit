// Hedefit'e özel egzersiz illüstrasyonları: açılarla tanımlanan 2B iskelet figür → SVG.
// Her görsel bu dosyadaki kodla ÜRETİLİR (üçüncü taraf içerik yok); lisans: Hedefit'e ait (HEDEFIT_ORIGINAL_ASSET).
//
// Koordinat: matematiksel açı (0° = sağ, 90° = yukarı). Figür sağa bakar. Birim: piksel (tuval 512×512).
//   standing: torso 90, bacaklar 270 (aşağı). Sırtüstü (baş solda): torso 180, bacaklar 0.

const L = { torso: 112, head: 46, upperArm: 62, foreArm: 58, hand: 12, thigh: 98, shin: 94, foot: 36 };
const W = { torso: 46, upperArm: 19, foreArm: 16, thigh: 27, shin: 20, foot: 14, head: 25 };

const rad = (deg) => (deg * Math.PI) / 180;
const vec = (angle, length) => [Math.cos(rad(angle)) * length, -Math.sin(rad(angle)) * length]; // SVG: y aşağı
const add = (a, b) => [a[0] + b[0], a[1] + b[1]];

/** Bir pozun tüm eklem noktalarını hesaplar. */
export function solve(pose) {
  // `anchor`: dik duran/diz üstü pozlarda destek ayağının bileği sabitlenir (pelvis bundan hesaplanır),
  // böylece iki kare arasında ayak aynı noktada kalır. `anchorLeg`: "N" (varsayılan) ya da "F".
  let pelvis = pose.pelvis;
  if (pose.anchor) {
    const angles = (pose.anchorLeg === "F" ? pose.legF ?? pose.legN : pose.legN) ?? [270, 270, 0];
    pelvis = [pose.anchor[0] - (vec(angles[0], L.thigh)[0] + vec(angles[1], L.shin)[0]), pose.anchor[1] - (vec(angles[0], L.thigh)[1] + vec(angles[1], L.shin)[1])];
  }
  // `bend` omurgayı iki parçaya böler (kedi/inek, yuvarlanma): + öne yuvarlanır, − bel/üst sırt gerilir.
  const bend = pose.bend ?? 0;
  const mid = add(pelvis, vec(pose.torso - bend / 2, L.torso / 2));
  const shoulder = add(mid, vec(pose.torso + bend / 2, L.torso / 2));
  const head = add(shoulder, vec(pose.head ?? pose.torso, L.head));
  const limb = (start, angles, lengths) => {
    const points = [start];
    angles.forEach((angle, i) => points.push(add(points[i], vec(angle, lengths[i]))));
    return points;
  };
  return {
    pelvis, mid, shoulder, head,
    armN: limb(shoulder, pose.armN ?? [270, 270], [L.upperArm, L.foreArm + L.hand]),
    armF: limb(shoulder, pose.armF ?? pose.armN ?? [270, 270], [L.upperArm, L.foreArm + L.hand]),
    legN: limb(pelvis, pose.legN ?? [270, 270, 0], [L.thigh, L.shin, L.foot]),
    legF: limb(pelvis, pose.legF ?? pose.legN ?? [270, 270, 0], [L.thigh, L.shin, L.foot]),
  };
}

// Çalışan bölge → hangi parçalar vurgulanır.
const FOCUS = {
  core: ["torso"], back: ["torso"], chest: ["torso"], glutes: ["thighN", "thighF"], hips: ["thighN", "thighF"],
  quads: ["thighN", "thighF"], hamstrings: ["thighN", "thighF"], calves: ["shinN", "shinF"], shoulders: ["upperArmN", "upperArmF"],
  arms: ["upperArmN", "upperArmF", "foreArmN", "foreArmF"], legs: ["thighN", "thighF", "shinN", "shinF"], full: ["torso", "thighN", "thighF", "upperArmN", "upperArmF"],
};

const COLORS = { bg0: "#101419", bg1: "#07090B", mat: "#1C232B", matEdge: "#2A343F", body: "#D7DEE5", bodyFar: "#8E99A5", focus: "#7DC866", focusFar: "#4F9440", head: "#EDF1F5", prop: "#3A4654", propHi: "#7DC866" };

/** Uçları incelen kapsül: iki daire + aradaki teğet dörtgen. */
function capsule(a, b, wa, wb, color) {
  const dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy) || 1;
  const nx = -dy / len, ny = dx / len;
  const ra = wa / 2, rb = wb / 2;
  const p = [[a[0] + nx * ra, a[1] + ny * ra], [b[0] + nx * rb, b[1] + ny * rb], [b[0] - nx * rb, b[1] - ny * rb], [a[0] - nx * ra, a[1] - ny * ra]];
  return `<path d="M${p.map((q) => `${q[0].toFixed(1)} ${q[1].toFixed(1)}`).join(" L")} Z" fill="${color}"/><circle cx="${a[0].toFixed(1)}" cy="${a[1].toFixed(1)}" r="${ra}" fill="${color}"/><circle cx="${b[0].toFixed(1)}" cy="${b[1].toFixed(1)}" r="${rb}" fill="${color}"/>`;
}

function drawFigure(joints, focus) {
  const f = new Set((focus ?? []).flatMap((key) => FOCUS[key] ?? []));
  const pick = (key, near, far, isFar) => (f.has(key) ? (isFar ? COLORS.focusFar : COLORS.focus) : isFar ? far : near);
  const out = [];
  const far = (key, base) => pick(key, COLORS.bodyFar, COLORS.bodyFar, true);
  const near = (key) => pick(key, COLORS.body, COLORS.bodyFar, false);
  // Arkadaki (uzak) uzuvlar önce ve daha koyu.
  out.push(capsule(joints.legF[0], joints.legF[1], 32, 24, far("thighF")));
  out.push(capsule(joints.legF[1], joints.legF[2], 24, 17, far("shinF")));
  out.push(capsule(joints.legF[2], joints.legF[3], 16, 12, COLORS.bodyFar));
  out.push(capsule(joints.armF[0], joints.armF[1], 21, 17, far("upperArmF")));
  out.push(capsule(joints.armF[1], joints.armF[2], 17, 13, far("foreArmF")));
  // gövde: kalçada dar, göğüste geniş
  const torsoColor = f.has("torso") ? COLORS.focus : COLORS.body;
  out.push(capsule(joints.pelvis, joints.mid, 40, 46, torsoColor));
  out.push(capsule(joints.mid, joints.shoulder, 46, 50, torsoColor));
  out.push(capsule(joints.legN[0], joints.legN[1], 34, 25, near("thighN")));
  out.push(capsule(joints.legN[1], joints.legN[2], 25, 18, near("shinN")));
  out.push(capsule(joints.legN[2], joints.legN[3], 17, 12, COLORS.body));
  out.push(capsule(joints.armN[0], joints.armN[1], 22, 18, near("upperArmN")));
  out.push(capsule(joints.armN[1], joints.armN[2], 18, 14, near("foreArmN")));
  // boyun + baş
  out.push(capsule(joints.shoulder, joints.head, 20, 18, COLORS.body));
  out.push(`<circle cx="${joints.head[0].toFixed(1)}" cy="${joints.head[1].toFixed(1)}" r="${W.head}" fill="${COLORS.head}"/>`);
  return out.join("");
}

/** Pozların tüm noktalarının sınır kutusu (iki kare aynı ölçekle çizilsin diye). */
function bounds(allJoints, props) {
  const points = allJoints.flatMap((j) => [j.pelvis, j.mid, j.shoulder, j.head, ...j.armN, ...j.armF, ...j.legN, ...j.legF]);
  for (const prop of props ?? []) { if (Array.isArray(prop.at) && prop.type !== "wall") points.push(prop.at); if (prop.hand) points.push([prop.hand[0] + 130, prop.hand[1]]); }
  const xs = points.map((p) => p[0]), ys = points.map((p) => p[1]);
  return { minX: Math.min(...xs) - 34, maxX: Math.max(...xs) + 34, minY: Math.min(...ys) - 34, maxY: Math.max(...ys) + 34 };
}

function drawProps(props, transform, floorY) {
  const out = [];
  for (const prop of props ?? []) {
    if (prop.type === "ball") out.push(`<circle cx="${prop.at[0]}" cy="${prop.at[1]}" r="${prop.r ?? 46}" fill="${COLORS.prop}" stroke="${COLORS.propHi}" stroke-width="3" opacity=".95"/>`);
    if (prop.type === "barre") {
      const [x, y] = prop.at === "hand" ? [prop.hand[0] - 20, prop.hand[1] + 4] : prop.at;
      out.push(`<line x1="${x}" y1="${y}" x2="${x + (prop.length ?? 150)}" y2="${y}" stroke="${COLORS.propHi}" stroke-width="9" stroke-linecap="round"/>`);
      out.push(`<line x1="${x + 18}" y1="${y}" x2="${x + 18}" y2="${floorY}" stroke="${COLORS.prop}" stroke-width="8" stroke-linecap="round"/>`);
      out.push(`<line x1="${x + (prop.length ?? 150) - 18}" y1="${y}" x2="${x + (prop.length ?? 150) - 18}" y2="${floorY}" stroke="${COLORS.prop}" stroke-width="8" stroke-linecap="round"/>`);
    }
    if (prop.type === "ring") out.push(`<circle cx="${prop.at[0]}" cy="${prop.at[1]}" r="${prop.r ?? 30}" fill="none" stroke="${COLORS.propHi}" stroke-width="4" opacity=".7"/>`);
    if (prop.type === "wall") { const x = prop.at === "hand" ? prop.hand[0] + 6 : prop.at[0]; out.push(`<rect x="${x}" y="${floorY - 360}" width="14" height="360" rx="4" fill="${COLORS.prop}"/>`); }
    if (prop.type === "chair") {
      const [x] = prop.at;
      out.push(`<rect x="${x}" y="${floorY - 96}" width="70" height="10" rx="4" fill="${COLORS.prop}"/><line x1="${x + 6}" y1="${floorY - 86}" x2="${x + 6}" y2="${floorY}" stroke="${COLORS.prop}" stroke-width="8" stroke-linecap="round"/><line x1="${x + 64}" y1="${floorY - 86}" x2="${x + 64}" y2="${floorY}" stroke="${COLORS.prop}" stroke-width="8" stroke-linecap="round"/><line x1="${x + 64}" y1="${floorY - 96}" x2="${x + 64}" y2="${floorY - 190}" stroke="${COLORS.prop}" stroke-width="8" stroke-linecap="round"/>`);
    }
  }
  return out.join("");
}

/** Bir egzersizin tek karesi için SVG. `frame` = { pose, ...}, `exercise` = { focus, props, mat }. */
export function renderFrame(exercise, frameIndex, size = 512) {
  const poses = exercise.frames.map((frame) => solve(frame));
  const props = (exercise.frames[frameIndex].props ?? exercise.props ?? []).map((prop) => (prop.at === "hand" ? { ...prop, hand: poses[0].armN[2] } : prop));
  exercise = { ...exercise, props };
  const box = bounds(poses, exercise.props);
  const floorY = exercise.floorY ?? Math.max(...poses.flatMap((j) => [...j.legN, ...j.legF, ...j.armN, ...j.armF, j.pelvis].map((p) => p[1]))) + (exercise.floorGap ?? 18);
  const w = box.maxX - box.minX, h = Math.max(box.maxY, floorY + 20) - box.minY;
  const scale = Math.min((size - 56) / w, (size - 80) / h, 2.1);
  const tx = (size - w * scale) / 2 - box.minX * scale;
  const ty = (size - h * scale) / 2 - box.minY * scale + 6;
  const shadow = `<ellipse cx="${(box.minX + box.maxX) / 2}" cy="${floorY + 2}" rx="${w * 0.38}" ry="9" fill="#000" opacity=".38"/>`;
  const mat = shadow + (exercise.mat !== false
    ? `<rect x="${box.minX - 20}" y="${floorY}" width="${w + 40}" height="16" rx="8" fill="${COLORS.mat}" stroke="${COLORS.matEdge}" stroke-width="2"/>`
    : `<line x1="${box.minX - 20}" y1="${floorY + 4}" x2="${box.maxX + 20}" y2="${floorY + 4}" stroke="${COLORS.matEdge}" stroke-width="3" stroke-linecap="round"/>`);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
<defs><radialGradient id="g" cx="50%" cy="38%" r="75%"><stop offset="0" stop-color="${COLORS.bg0}"/><stop offset="1" stop-color="${COLORS.bg1}"/></radialGradient></defs>
<rect width="${size}" height="${size}" fill="url(#g)"/>
<g transform="translate(${tx.toFixed(1)} ${ty.toFixed(1)}) scale(${scale.toFixed(4)})">${mat}${drawProps(exercise.props, null, floorY)}${drawFigure(poses[frameIndex], exercise.focus)}</g>
</svg>`;
}
