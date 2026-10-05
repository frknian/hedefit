// Recovery — 7 hareket: sakinleştirici esnetme, nefes ve pasif toparlanma.
const FLOOR = 430;
const SUP = { pelvis: [200, 300], torso: 180, head: 180 };
const sup = (o) => ({ ...SUP, ...o });
const ST = (o) => ({ anchor: [250, FLOOR], torso: 90, head: 90, armN: [270, 270], legN: [270, 270, 0], ...o });

export const recovery = [
  {
    id: "wl-recovery-diaphragmatic-breathing", name: "Diaphragmatic Breathing", nameTr: "Diyafram Nefesi", modality: ["recovery"], sub: ["full_body", "breathing"], level: "beginner", impact: "low",
    equipment: null, bodyPart: "core", primary: ["transverse_abdominis"], secondary: ["obliques"], force: "static", mechanic: "isolation", met: 1.3,
    goals: ["mobility", "general_fitness"], focus: ["core"], category: "stretching",
    frames: [{ ...sup({ armN: [335, 160], legN: [62, 282, 345] }), props: [{ type: "ring", at: [196, 262], r: 16 }] }, { ...sup({ armN: [335, 160], legN: [62, 282, 345] }), props: [{ type: "ring", at: [196, 262], r: 34 }] }],
    desc: "Slow belly breathing that calms the nervous system and teaches the diaphragm to do its job.",
    tips: ["Let the belly rise, keeping the chest quiet.", "Make the exhale a little longer than the inhale."],
    instructions: ["Lie on your back with your knees bent and one hand on your belly.", "Inhale slowly through the nose so the belly rises under your hand.", "Exhale gently through pursed lips, letting the belly fall.", "Continue for 2 to 5 minutes."],
    instructionsTr: ["Sırtüstü uzan, dizlerin bükülü; bir elin karnının üzerinde olsun.", "Burundan yavaşça nefes al; karnın elinin altında yükselsin.", "Dudaklarını büzerek yumuşakça nefes ver; karnın insin.", "2–5 dakika devam et."],
    tipsTr: ["Göğüs sakin kalırken karnın yükselsin.", "Nefes vermeyi nefes almadan biraz uzun tut."],
    descTr: "Sinir sistemini sakinleştiren ve diyaframa görevini öğreten yavaş karın nefesi.",
  },
  {
    id: "wl-recovery-supine-hamstring-stretch", name: "Supine Hamstring Stretch", nameTr: "Sırtüstü Arka Bacak Esnetme", modality: ["recovery"], sub: ["lower_body", "full_body"], level: "beginner", impact: "low",
    equipment: null, bodyPart: "upper_legs", primary: ["hamstrings"], secondary: ["gastrocnemius", "gluteus_maximus"], force: "static", mechanic: "isolation", met: 1.8,
    goals: ["mobility", "general_fitness"], focus: ["hamstrings"], category: "stretching",
    frames: [sup({ armN: [0, 0], legN: [0, 0, 90] }), sup({ armN: [18, 18], legN: [0, 0, 90], legF: [92, 92, 100] })],
    desc: "Lie on your back and draw one straight leg toward you to release the back of the thigh.",
    tips: ["Keep the lower back on the floor.", "Use a towel or strap around the foot if you cannot reach."],
    instructions: ["Lie on your back with both legs extended.", "Lift one leg and hold behind the thigh or calf, keeping the leg as straight as comfortable.", "Gently draw it toward you until you feel a stretch behind the thigh.", "Hold for 30 seconds, then switch."],
    instructionsTr: ["Sırtüstü, iki bacağın uzun uzan.", "Bir bacağını kaldır; uyluğunu ya da baldırını tut, bacağı rahatça düz tut.", "Uyluğunun arkasında esneme hissedene kadar nazikçe kendine çek.", "30 saniye bekle, sonra değiştir."],
    tipsTr: ["Belini yerde tut.", "Ulaşamıyorsan ayağına havlu ya da bant dola."],
    descTr: "Sırtüstü yatıp düz bir bacağı kendine çekerek uyluğun arkasını gevşetmek.",
  },
];
