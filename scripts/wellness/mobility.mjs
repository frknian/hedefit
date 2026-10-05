// Mobility — 8 hareket (omurga, kalça, ayak bileği, omuz). Ayakta pozlar `anchor` ile ayağı sabitler.
const FLOOR = 430;
const QUAD = { pelvis: [180, 272], torso: 17, head: 22, armN: [270, 270], legN: [270, 180, 180] };
const STAND = (o) => ({ anchor: [250, FLOOR], torso: 90, head: 90, armN: [270, 270], legN: [270, 270, 0], ...o });

export const mobility = [
  {
    id: "wl-mobility-quadruped-rock-back", name: "Quadruped Rock-Back", nameTr: "Dört Ayak Geri Sallanma", modality: ["mobility"], sub: ["hip", "back", "evening"], level: "beginner", impact: "low",
    equipment: null, bodyPart: "upper_legs", primary: ["gluteus_maximus", "hip_flexors"], secondary: ["erector_spinae", "adductors"], force: "dynamic", mechanic: "isolation", met: 2.3,
    goals: ["mobility", "general_fitness"], focus: ["hips"], category: "stretching",
    frames: [QUAD, { pelvis: [88, 336], torso: 8, head: 10, armN: [4, 4], legN: [340, 180, 180] }],
    desc: "Shift your hips back toward your heels to open the hips and lengthen the lower back, then return to the start.",
    tips: ["Keep the spine long; do not round forward.", "Go only as far back as feels comfortable."],
    instructions: ["Start on hands and knees with a long, neutral spine.", "Exhale and slowly send your hips back toward your heels, reaching your arms forward.", "Pause where you feel a gentle stretch in your hips.", "Inhale and glide forward to the start. Repeat 8 to 10 times."],
    instructionsTr: ["Dört ayak üzerinde, uzun ve nötr bir omurgayla başla.", "Nefes ver ve kalçanı yavaşça topuklarına doğru gönder, kollarını öne uzat.", "Kalçanda hafif bir esneme hissettiğin yerde bekle.", "Nefes al ve başlangıca doğru kay. 8–10 tekrar yap."],
    tipsTr: ["Omurgayı uzun tut, öne yuvarlanma.", "Yalnızca rahat ettiğin kadar geriye git."],
    descTr: "Kalçayı topuklara doğru geri göndererek kalçayı açar ve beli uzatır, sonra başlangıca dönersin.",
  },
  {
    id: "wl-mobility-ankle-rocks", name: "Wall Ankle Rocks", nameTr: "Duvarda Ayak Bileği Sallama", modality: ["mobility"], sub: ["full_body", "morning"], level: "beginner", impact: "low",
    equipment: null, bodyPart: "lower_legs", primary: ["gastrocnemius", "soleus"], secondary: ["quadriceps"], force: "dynamic", mechanic: "isolation", met: 2.3,
    goals: ["mobility", "general_fitness"], focus: ["calves"], category: "stretching",
    frames: [STAND({ armN: [20, 5], anchor: [220, FLOOR] }), STAND({ armN: [20, 5], anchor: [220, FLOOR], legN: [292, 252, 0], torso: 96, head: 96 })],
    desc: "Rock the knee forward over the toes with the heel down to improve ankle dorsiflexion for squats and lunges.",
    tips: ["Keep the whole heel on the floor.", "Track the knee over the second toe."],
    instructions: ["Stand facing a wall with one foot forward and your hands on the wall.", "Keep your heel down and glide your knee forward toward the wall.", "Return to the start and repeat 10 times.", "Switch sides."],
    instructionsTr: ["Bir duvara bakarak dur; bir ayağın önde, elleri duvarda olsun.", "Topuğunu yerde tutarak dizini duvara doğru kaydır.", "Başlangıca dön ve 10 kez tekrarla.", "Taraf değiştir."],
    tipsTr: ["Topuğunun tamamı yerde kalsın.", "Dizi ikinci parmağın doğrultusunda tut."],
    descTr: "Topuk yerdeyken dizi parmakların üzerine sallayarak çömelme ve hamlelerde gereken ayak bileği hareketini artırır.",
    props: [{ type: "wall", at: "hand" }],
  },
  {
    id: "wl-mobility-hip-hinge-reach", name: "Hip Hinge Reach", nameTr: "Kalça Menteşesi Uzanma", modality: ["mobility"], sub: ["hip", "back", "morning"], level: "beginner", impact: "low",
    equipment: null, bodyPart: "back", primary: ["hamstrings", "gluteus_maximus"], secondary: ["erector_spinae"], force: "dynamic", mechanic: "compound", met: 2.5,
    goals: ["mobility", "general_fitness"], focus: ["hamstrings", "glutes"], category: "stretching",
    frames: [STAND({ armN: [90, 90] }), STAND({ torso: 40, head: 44, armN: [38, 38], legN: [266, 270, 0] })],
    desc: "Hinge at the hips with a long spine and reach forward; teaches the hip-dominant pattern behind safe lifting.",
    tips: ["Send the hips back as if closing a door with them.", "Keep your back long, not rounded."],
    instructions: ["Stand tall with your arms overhead and your knees soft.", "Push your hips back and lean your torso forward with a long spine.", "Reach your arms forward until you feel the back of your legs.", "Squeeze your glutes to stand tall. Repeat 10 times."],
    instructionsTr: ["Kollarını başının üstüne uzat, dizlerin hafif bükülü dik dur.", "Kalçanı geriye it ve uzun bir omurgayla gövdeni öne eğ.", "Bacağının arkasında gerginlik hissedene kadar kollarını öne uzat.", "Kalçanı sıkarak doğrul. 10 tekrar yap."],
    tipsTr: ["Kalçanla bir kapıyı kapatır gibi geriye it.", "Sırtını yuvarlama, uzun tut."],
    descTr: "Uzun bir omurgayla kalçadan menteşelenip öne uzanmak: güvenli kaldırışların temelindeki kalça baskın hareket kalıbını öğretir.",
  },
];
