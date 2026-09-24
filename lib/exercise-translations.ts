import type { Exercise } from "@/types/exercise";
import type { Locale } from "@/lib/i18n/server";

const labelsTr: Record<string, string> = {
  abdominals: "Karın",
  abductors: "Dış kalça",
  adductors: "Bacak",
  biceps: "Ön kol",
  calves: "Baldır",
  chest: "Göğüs",
  forearms: "Bilek",
  glutes: "Kalça",
  hamstrings: "Bacak",
  lats: "Kanat",
  "lower back": "Sırt",
  "middle back": "Sırt",
  neck: "Boyun",
  quadriceps: "Bacak",
  shoulders: "Omuz",
  traps: "Trapez",
  triceps: "Arka kol",
  bands: "Direnç bandı",
  barbell: "Halter",
  "body only": "Vücut ağırlığı",
  cable: "Kablo",
  dumbbell: "Dambıl",
  "e-z curl bar": "EZ bar",
  "exercise ball": "Egzersiz topu",
  kettlebells: "Kettlebell",
  machine: "Makine",
  "medicine ball": "Sağlık topu",
  none: "Ekipmansız",
  other: "Diğer",
  beginner: "Başlangıç",
  intermediate: "Orta seviye",
  expert: "İleri seviye",
  cardio: "Kardiyo",
  "olympic weightlifting": "Olimpik kaldırış",
  plyometrics: "Pliyometrik",
  powerlifting: "Güç kaldırışı",
  strength: "Kuvvet",
  stretching: "Esneme",
  compound: "Bileşik",
  isolation: "İzole",
  push: "İtiş",
  pull: "Çekiş",
  static: "Sabit",
  dynamic: "Dinamik",
  advanced: "İleri seviye",
  olympic: "Olimpik kaldırış",

  // RepDB kas etiketleri (bkz. scripts/import-repdb.mjs)
  pectoralis_major: "Göğüs",
  serratus_anterior: "Ön dişli kas",
  latissimus_dorsi: "Kanat (Lat)",
  trapezius: "Trapez",
  rhomboids: "Kürek arası",
  erector_spinae: "Sırt (bel dikleştirici)",
  quadratus_lumborum: "Bel",
  anterior_deltoid: "Ön omuz",
  lateral_deltoid: "Yan omuz",
  posterior_deltoid: "Arka omuz",
  supraspinatus: "Rotator kaf",
  hip_flexors: "Kalça bükücü",
  gluteus_maximus: "Kalça",
  gluteus_medius: "Kalça (yan)",
  biceps_brachii: "Ön kol (biceps)",
  brachialis: "Ön kol",
  brachioradialis: "Önkol",
  triceps_brachii: "Arka kol (triceps)",
  forearm_flexors: "Önkol bükücü",
  forearm_extensors: "Önkol açıcı",
  gastrocnemius: "Baldır",
  soleus: "Baldır (soleus)",
  rectus_abdominis: "Karın (düz kas)",
  obliques: "Yan karın",
  transverse_abdominis: "İç karın",

  // RepDB ekipman etiketleri
  ab_crunch_machine: "Karın makinesi",
  ab_wheel: "Karın tekerleği",
  air_bike: "Air bike",
  assisted_pullup_machine: "Asistanlı barfiks makinesi",
  back_extension_machine: "Sırt açma makinesi",
  battle_rope: "Savaş halatı",
  bicep_curl_machine: "Biceps makinesi",
  chest_fly_machine: "Göğüs açma makinesi",
  chest_press_machine: "Göğüs presi makinesi",
  climbing_rope: "Tırmanma halatı",
  dip_machine: "Dip makinesi",
  dip_station: "Dip istasyonu",
  donkey_calf_raise_machine: "Baldır makinesi",
  elliptical: "Eliptik bisiklet",
  ez_bar: "EZ bar",
  flat_bench: "Düz sehpa",
  glute_ham_developer: "GHD sehpası",
  hack_squat: "Hack squat makinesi",
  hip_abduction_machine: "Kalça açma makinesi",
  hip_adduction_machine: "Kalça kapama makinesi",
  hip_thrust_machine: "Kalça itiş makinesi",
  jump_rope: "İp atlama",
  kettlebell: "Kettlebell",
  lat_pulldown_machine: "Lat pulldown makinesi",
  leg_curl: "Bacak curl makinesi",
  leg_extension: "Bacak açma makinesi",
  leg_press: "Leg press",
  loop_band: "Direnç bandı (halka)",
  pec_deck: "Pec deck",
  plate_loaded_lateral_raise_machine: "Yan kaldırış makinesi",
  plates: "Ağırlık diski",
  plyo_box: "Pliometri kutusu",
  preacher_curl_machine: "Preacher curl sehpası",
  pull_up_bar: "Barfiks barı",
  resistance_band: "Direnç bandı",
  rings: "Halka (ring)",
  rower: "Kürek makinesi",
  seated_calf_raise_machine: "Oturarak baldır makinesi",
  shoulder_press_machine: "Omuz presi makinesi",
  shrug_machine: "Shrug makinesi",
  slam_ball: "Slam ball",
  sled: "Kızak",
  smith_machine: "Smith makinesi",
  stability_ball: "Denge topu",
  stair_climber: "Merdiven makinesi",
  standing_calf_raise_machine: "Ayakta baldır makinesi",
  stationary_bike: "Sabit bisiklet",
  suspension_trainer: "Askı sistemi (TRX)",
  trap_bar: "Trap bar",
  treadmill: "Koşu bandı",
  tricep_extension_machine: "Triceps makinesi",
  wrist_roller: "Bilek silindiri",
};

const labelsEn: Record<string, string> = {
  abdominals: "Abs",
  abductors: "Abductors",
  adductors: "Adductors",
  biceps: "Biceps",
  calves: "Calves",
  chest: "Chest",
  forearms: "Forearms",
  glutes: "Glutes",
  hamstrings: "Hamstrings",
  lats: "Lats",
  "lower back": "Lower back",
  "middle back": "Middle back",
  quadriceps: "Quads",
  shoulders: "Shoulders",
  traps: "Traps",
  triceps: "Triceps",
  bands: "Resistance band",
  barbell: "Barbell",
  "body only": "Bodyweight",
  cable: "Cable",
  dumbbell: "Dumbbell",
  "e-z curl bar": "EZ bar",
  "exercise ball": "Exercise ball",
  kettlebells: "Kettlebell",
  machine: "Machine",
  "medicine ball": "Medicine ball",
  none: "No equipment",
  other: "Other",
  beginner: "Beginner",
  intermediate: "Intermediate",
  expert: "Advanced",
  cardio: "Cardio",
  "olympic weightlifting": "Olympic weightlifting",
  plyometrics: "Plyometrics",
  powerlifting: "Powerlifting",
  strength: "Strength",
  stretching: "Stretching",
  compound: "Compound",
  isolation: "Isolation",
  push: "Push",
  pull: "Pull",
  static: "Static",
  dynamic: "Dynamic",
  advanced: "Advanced",
  olympic: "Olympic weightlifting",

  pectoralis_major: "Chest",
  serratus_anterior: "Serratus anterior",
  latissimus_dorsi: "Lats",
  trapezius: "Traps",
  rhomboids: "Rhomboids",
  erector_spinae: "Lower back",
  quadratus_lumborum: "Lower back",
  anterior_deltoid: "Front delts",
  lateral_deltoid: "Side delts",
  posterior_deltoid: "Rear delts",
  supraspinatus: "Rotator cuff",
  hip_flexors: "Hip flexors",
  gluteus_maximus: "Glutes",
  gluteus_medius: "Glutes (medius)",
  biceps_brachii: "Biceps",
  brachialis: "Brachialis",
  brachioradialis: "Forearms",
  triceps_brachii: "Triceps",
  forearm_flexors: "Forearm flexors",
  forearm_extensors: "Forearm extensors",
  gastrocnemius: "Calves",
  soleus: "Calves (soleus)",
  rectus_abdominis: "Abs",
  obliques: "Obliques",
  transverse_abdominis: "Deep core",

  ab_crunch_machine: "Ab crunch machine",
  ab_wheel: "Ab wheel",
  air_bike: "Air bike",
  assisted_pullup_machine: "Assisted pull-up machine",
  back_extension_machine: "Back extension machine",
  battle_rope: "Battle rope",
  bicep_curl_machine: "Bicep curl machine",
  chest_fly_machine: "Chest fly machine",
  chest_press_machine: "Chest press machine",
  climbing_rope: "Climbing rope",
  dip_machine: "Dip machine",
  dip_station: "Dip station",
  donkey_calf_raise_machine: "Calf raise machine",
  elliptical: "Elliptical",
  ez_bar: "EZ bar",
  flat_bench: "Flat bench",
  glute_ham_developer: "Glute-ham developer",
  hack_squat: "Hack squat machine",
  hip_abduction_machine: "Hip abduction machine",
  hip_adduction_machine: "Hip adduction machine",
  hip_thrust_machine: "Hip thrust machine",
  jump_rope: "Jump rope",
  kettlebell: "Kettlebell",
  lat_pulldown_machine: "Lat pulldown machine",
  leg_curl: "Leg curl machine",
  leg_extension: "Leg extension machine",
  leg_press: "Leg press",
  loop_band: "Loop band",
  pec_deck: "Pec deck",
  plate_loaded_lateral_raise_machine: "Lateral raise machine",
  plates: "Weight plates",
  plyo_box: "Plyo box",
  preacher_curl_machine: "Preacher curl bench",
  pull_up_bar: "Pull-up bar",
  resistance_band: "Resistance band",
  rings: "Gymnastic rings",
  rower: "Rowing machine",
  seated_calf_raise_machine: "Seated calf raise machine",
  shoulder_press_machine: "Shoulder press machine",
  shrug_machine: "Shrug machine",
  slam_ball: "Slam ball",
  sled: "Sled",
  smith_machine: "Smith machine",
  stability_ball: "Stability ball",
  stair_climber: "Stair climber",
  standing_calf_raise_machine: "Standing calf raise machine",
  stationary_bike: "Stationary bike",
  suspension_trainer: "Suspension trainer",
  trap_bar: "Trap bar",
  treadmill: "Treadmill",
  tricep_extension_machine: "Tricep extension machine",
  wrist_roller: "Wrist roller",
};

function labelsFor(locale: Locale) {
  return locale === "en" ? labelsEn : labelsTr;
}

export function translateExerciseLabel(value: string | null | undefined, locale: Locale = "tr", fallback = locale === "en" ? "Not specified" : "Belirtilmemiş") {
  if (!value) return fallback;
  return labelsFor(locale)[value.toLocaleLowerCase("en-US")] || value;
}

export function translateExerciseList(values: string[], locale: Locale = "tr") {
  return values.map((value) => translateExerciseLabel(value, locale)).join(" · ");
}

const exerciseNameTerms: Array<[RegExp, string]> = [
  // Bu üç kural metni SİLİYOR (boş string), diğerleri gibi Türkçeye çevirmiyor.
  // Bu yüzden EN BAŞTA, yalnız ham İngilizce isim üzerinde çalışmalılar: JS'nin
  // \b sınırı ASCII-dışı harfleri (ğ, ş, ç, ı, ö, ü) "kelime dışı" sayıyor, bu
  // yüzden zincirin ilerleyen bir adımı "bodyweight"i "Vücut ağırlığı"ya
  // çevirdikten SONRA bu kurallar çalışsaydı, "ağırlığı"nın baştaki "a"sı
  // \ban?\b ile bağımsız bir "a" sanılıp silinir, "ğırlığı" kalırdı.
  // "Push-off" bileşik terim: sonraki satırdaki genel "off" temizliği bunu
  // "Push-" halinde bozardı, o yüzden burada kendi başına önce çevrilir.
  [/\bbehind[- ]the[- ]neck\b/gi, "Ense arkası"], [/\bbehind[- ]the[- ]back\b/gi, "Arkadan"], [/\bpush-off\b/gi, "itiş sıçraması"],
  [/\bthe\b/gi, ""], [/\ban?\b/gi, ""], [/\bof\b/gi, ""], [/\boff\b/gi, ""],
  [/\bon your side\b/gi, "Yan yatarak"],
  [/\ball fours\b/gi, "Dört ayak"], [/\bworld'?s greatest stretch\b/gi, "Tüm vücut esnetme"],
  [/\bab crunch machine\b/gi, "Karın sıkıştırma makinesi"], [/\bparallel bar dips?\b/gi, "Paralel bar itişi"],
  [/\bpushups?\b/gi, "Şınav"], [/\bpullups?\b/gi, "Barfiks"], [/\bwindmills?\b/gi, "Yel değirmeni"],
  [/\bair bike\b/gi, "Hava bisikleti"], [/\brecumbent bike\b/gi, "Yatay kondisyon bisikleti"],
  [/\b(?:ab|abs)\b/gi, "Karın"], [/\badductors?\b/gi, "İç bacak"], [/\babductors?\b/gi, "Dış kalça"],
  [/\bpulleys?\b/gi, "Makara"], [/\bleverage\b/gi, "Kaldıraçlı"], [/\bhammer\b/gi, "Çekiç"],
  [/\b(?:delt|deltoids?)\b/gi, "Omuz"], [/\bupright\b/gi, "Dik"], [/\bchains?\b/gi, "Zincir"],
  [/\bcrossover\b/gi, "Çapraz çekiş"], [/\blong\b/gi, "Uzun"], [/\blat\b/gi, "Kanat"],
  [/\bbridge\b/gi, "Köprü"], [/\bcross\b/gi, "Çapraz"], [/\bblocks?\b/gi, "Blok"],
  [/\boblique\b/gi, "Yan karın"], [/\belevated\b/gi, "Yükseltilmiş"], [/\bexternal\b/gi, "Dış"],
  [/\bhack\b/gi, "Hack"], [/\bbends?\b/gi, "Eğilme"], [/\bstep\b/gi, "Adım"],
  [/\battachment\b/gi, "Aparat"], [/\bconcentration\b/gi, "Konsantrasyon"], [/\bdepth\b/gi, "Derinlik"],
  [/\bdrags?\b/gi, "Sürükleme"], [/\bham\b/gi, "Arka bacak"], [/\bcone\b/gi, "Koni"],
  [/\bbalance\b/gi, "Denge"], [/\blifts?\b/gi, "Kaldırış"], [/\bhyperextensions?\b/gi, "Hiperekstansiyon"],
  [/\bmuscle\b/gi, "Kas"], [/\bdrill\b/gi, "Çalışması"], [/\blinear\b/gi, "Doğrusal"],
  [/\bresistance\b/gi, "Direnç"], [/\bspeed\b/gi, "Hız"], [/\bbackward\b/gi, "Geriye"],
  [/\bmid\b/gi, "Orta"], [/\bpositions?\b/gi, "Pozisyon"], [/\binternal\b/gi, "İç"],
  [/\babove\b/gi, "Üstünde"], [/\bacross\b/gi, "Çapraz"], [/\bparallel\b/gi, "Paralel"],
  [/\bstraps?\b/gi, "Kayış"], [/\bsqueezes?\b/gi, "Sıkma"], [/\btouch(?:es|ers)?\b/gi, "Dokunuş"],
  [/\bprogressions?\b/gi, "İlerleme"], [/\bpins?\b/gi, "Pim"],
  [/\brussian\b/gi, "Rus"], [/\bhandle\b/gi, "Tutacak"], [/\bdeficit\b/gi, "Yükselti"],
  [/\bversion\b/gi, "Sürümü"], [/\bbutt\b/gi, "Kalça"], [/\bkicks?\b/gi, "Tekme"],
  [/\bpronated\b/gi, "Pronasyon tutuşlu"], [/\bsupinated\b/gi, "Supinasyon tutuşlu"],
  [/\bfrog\b/gi, "Kurbağa"], [/\bhurdle\b/gi, "Engel"], [/\bbelow\b/gi, "Aşağıda"],
  [/\bhug\b/gi, "Sarılma"], [/\binverted\b/gi, "Ters"], [/\bexercise\b/gi, "Egzersiz"],
  [/\bjackknife\b/gi, "Çakı"], [/\btuck\b/gi, "Toparlanma"], [/\bolympic\b/gi, "Olimpik"],
  [/\bslam\b/gi, "Yere vurma"], [/\blaterals?\b/gi, "Yana açış"], [/\bplyo\b/gi, "Pliyometrik"],
  [/\bfeet\b/gi, "Ayaklar"], [/\bromanian\b/gi, "Romen"], [/\bharness\b/gi, "Koşum"],
  [/\bstride\b/gi, "Uzun adım"], [/\bstiff\b/gi, "Düz"], [/\bforward\b/gi, "İleri"],
  [/\bfarmer'?s?\b/gi, "Çiftçi"], [/\bhandstand\b/gi, "El duruşu"], [/\bpike\b/gi, "Çakı"],
  [/\bflexion\b/gi, "Bükme"], [/\bextension\b/gi, "Uzatma"], [/\bposterior\b/gi, "Arka"],
  [/\bdiagonal\b/gi, "Çapraz"], [/\bbear\b/gi, "Ayı"], [/\bcrawl\b/gi, "Yürüyüş"],
  [/\bgood morning\b/gi, "Good Morning"], [/\bmountain climbers?\b/gi, "Dağ tırmanışı"],
  [/\bskull crushers?\b/gi, "Alına indiriş"], [/\bbench press\b/gi, "Sehpa itişi"],
  [/\bchest press\b/gi, "Göğüs itişi"], [/\bshoulder press\b/gi, "Omuz itişi"],
  [/\bleg press\b/gi, "Bacak itişi"], [/\bmilitary press\b/gi, "Askeri itiş"],
  [/\bsit[- ]ups?\b/gi, "Mekik"], [/\bpull[- ]ups?\b/gi, "Barfiks"],
  [/\bchin[- ]ups?\b/gi, "Ters tutuş barfiks"], [/\bpush[- ]ups?\b/gi, "Şınav"],
  [/\bpushdowns?\b/gi, "Aşağı itiş"], [/\bpulldowns?\b/gi, "Aşağı çekiş"],
  [/\bkickbacks?\b/gi, "Geri açış"], [/\brollouts?\b/gi, "İleri yuvarlanma"],
  [/\bbodyweight\b/gi, "Vücut ağırlığı"], [/\bone[- ]arm\b/gi, "Tek kol"],
  [/\bsingle[- ]arm\b/gi, "Tek kol"], [/\btwo[- ]arm\b/gi, "Çift kol"],
  [/\bone[- ]legged\b/gi, "Tek bacak"], [/\bsingle[- ]leg\b/gi, "Tek bacak"],
  [/\bstiff[- ]legged\b/gi, "Düz bacak"], [/\bstraight[- ]arm\b/gi, "Düz kol"],
  [/\bbent[- ]arm\b/gi, "Bükülü kol"], [/\bbent[- ]over\b/gi, "Öne eğilerek"],
  [/\bclose[- ]grip\b/gi, "Dar tutuş"], [/\bwide[- ]grip\b/gi, "Geniş tutuş"],
  [/\bmedium[- ]grip\b/gi, "Orta tutuş"], [/\bneutral[- ]grip\b/gi, "Nötr tutuş"],
  [/\boverhead\b/gi, "Baş üstü"], [/\bbehind the neck\b/gi, "Ense arkasına"],
  [/\bdumbbells?\b/gi, "Dambıl"], [/\bbarbells?\b/gi, "Halter"], [/\bkettlebells?\b/gi, "Girya"],
  [/\bmedicine ball\b/gi, "Sağlık topu"], [/\bexercise ball\b/gi, "Egzersiz topu"],
  [/\bresistance bands?\b/gi, "Direnç bandı"], [/\bcables?\b/gi, "Kablo"],
  [/\bsmith machine\b/gi, "Smith makinesi"], [/\bmachine\b/gi, "Makine"],
  [/\bpreacher\b/gi, "Scott sehpası"], [/\bbench\b/gi, "Sehpa"],
  [/\bcrunch(?:es)?\b/gi, "Karın sıkıştırma"], [/\bplank\b/gi, "Düz duruş"],
  [/\bsquats?\b/gi, "Çömelme"], [/\blunges?\b/gi, "Hamle"], [/\bdeadlifts?\b/gi, "Yerden kaldırış"],
  [/\brows?\b/gi, "Kürek çekiş"], [/\bcurls?\b/gi, "Büküş"], [/\bextensions?\b/gi, "Uzatış"],
  [/\braises?\b/gi, "Kaldırış"], [/\bpress(?:es)?\b/gi, "İtiş"], [/\bfl(?:y|ys|yes)\b/gi, "Yana açış"],
  [/\bpullovers?\b/gi, "Baş üstü çekiş"], [/\bshrugs?\b/gi, "Omuz silkme"],
  [/\bstretch(?:es)?\b/gi, "Esnetme"], [/\bclean\b/gi, "Omuza alış"], [/\bsnatch\b/gi, "Koparma"], [/\bjerk\b/gi, "Silkme"],
  [/\bhip thrust\b/gi, "Kalça itişi"], [/\bhip\b/gi, "Kalça"], [/\bglutes?\b/gi, "Kalça"],
  [/\bhamstrings?\b/gi, "Arka bacak"], [/\bquadriceps?\b/gi, "Ön bacak"], [/\bquads?\b/gi, "Ön bacak"],
  [/\b(?:calf|calves)\b/gi, "Baldır"], [/\bchest\b/gi, "Göğüs"], [/\bshoulders?\b/gi, "Omuz"],
  [/\btriceps?\b/gi, "Arka kol"], [/\bbiceps?\b/gi, "Biseps"], [/\bforearms?\b/gi, "Ön kol"],
  [/\bwrists?\b/gi, "Bilek"], [/\bneck\b/gi, "Boyun"], [/\bback\b/gi, "Sırt"],
  [/\blegs?\b/gi, "Bacak"], [/\barms?\b/gi, "Kol"], [/\bknees?\b/gi, "Diz"], [/\bankles?\b/gi, "Ayak bileği"],
  [/\bstanding\b/gi, "Ayakta"], [/\bseated\b/gi, "Oturarak"], [/\blying\b/gi, "Yatarak"],
  [/\bkneeling\b/gi, "Diz çökerek"], [/\bprone\b/gi, "Yüzüstü"], [/\bsupine\b/gi, "Sırtüstü"],
  [/\bhanging\b/gi, "Asılı"], [/\bsuspended\b/gi, "Askıda"], [/\bwalking\b/gi, "Yürüyerek"],
  [/\balternating\b/gi, "Dönüşümlü"], [/\balternate\b/gi, "Dönüşümlü"], [/\breverse\b/gi, "Ters"],
  [/\bincline\b/gi, "Eğimli"], [/\bdecline\b/gi, "Ters eğimli"], [/\blateral\b/gi, "Yana"],
  [/\bfront\b/gi, "Ön"], [/\brear\b/gi, "Arka"], [/\binner\b/gi, "İç"], [/\bouter\b/gi, "Dış"],
  [/\bupper\b/gi, "Üst"], [/\blower\b/gi, "Alt"], [/\bhigh\b/gi, "Yüksek"], [/\blow\b/gi, "Alçak"],
  [/\bwide\b/gi, "Geniş"], [/\bnarrow\b/gi, "Dar"], [/\bclose\b/gi, "Yakın"], [/\bgrip\b/gi, "Tutuş"],
  [/\bstraight\b/gi, "Düz"], [/\bbent\b/gi, "Bükülü"], [/\bweighted\b/gi, "Ağırlıklı"],
  [/\bassisted\b/gi, "Destekli"], [/\bisometric\b/gi, "İzometrik"], [/\bpower\b/gi, "Güç"],
  [/\bjump(?:s)?\b/gi, "Sıçrama"], [/\bhops?\b/gi, "Sıçrayış"], [/\bbounds?\b/gi, "Atlamalı ilerleme"],
  [/\bsprints?\b/gi, "Sürat koşusu"], [/\bthrows?\b/gi, "Atış"], [/\brotations?\b/gi, "Dönüş"],
  [/\bcircles?\b/gi, "Daire"], [/\btwists?\b/gi, "Burgu"], [/\bwalk\b/gi, "Yürüyüş"],
  [/\bfloor\b/gi, "Yerde"], [/\bwall\b/gi, "Duvar"], [/\bchair\b/gi, "Sandalye"],
  [/\bbox\b/gi, "Kutu"], [/\brope\b/gi, "Halat"], [/\bplate\b/gi, "Ağırlık plakası"],
  [/\bsled\b/gi, "Kızak"], [/\bball\b/gi, "Top"], [/\bband(?:s)?\b/gi, "Bant"],
  [/\bpalms?\b/gi, "Avuçlar"], [/\bhead\b/gi, "Baş"], [/\bsides?\b/gi, "Yan"],
  [/\bsplit\b/gi, "Ayrık"], [/\bsingle\b/gi, "Tek"], [/\bdouble\b/gi, "Çift"],
  [/\bpull\b/gi, "Çekiş"], [/\bpush\b/gi, "İtiş"], [/\bskip\b/gi, "Atlama"],
  [/\badvanced\b/gi, "İleri"], [/\bintermediate\b/gi, "Orta"], [/\bbeginner\b/gi, "Başlangıç"],
  [/\bdynamic\b/gi, "Dinamik"], [/\bextended\b/gi, "Uzatılmış"], [/\brange\b/gi, "Hareket aralığı"],
  [/\bbehind\b/gi, "Arkasında"], [/\bbetween\b/gi, "Arasında"], [/\bmiddle\b/gi, "Orta"],
  [/\bbody\b/gi, "Gövde"], [/\bgroin\b/gi, "Kasık"], [/\bflexors?\b/gi, "Bükücü"],
  [/\bface\b/gi, "Yüze"], [/\bpalms?\b/gi, "Avuç"], [/\bopen\b/gi, "Açık"],
  [/\belbows?\b/gi, "Dirsekler"], [/\bhands?\b/gi, "Eller"], [/\bchin\b/gi, "Çene"],
  [/\bcat\b/gi, "Kedi"], [/\bdancer'?s\b/gi, "Dansçı"], [/\bdonkey\b/gi, "Eşek"],
  [/\bstability\b/gi, "Denge"], [/\bpoint\b/gi, "Nokta"], [/\bstance\b/gi, "Duruş"],
  [/\bmultiple\b/gi, "Çoklu"], [/\bresponse\b/gi, "Tepki"], [/\brelease\b/gi, "Bırakma"],
  [/\brun\b/gi, "Koşu"], [/\bfigure\b/gi, "Şekil"], [/\bhang\b/gi, "Asılı"],
  [/\bpass\b/gi, "Geçiriş"], [/\bpistol\b/gi, "Tabanca"], [/\bseesaw\b/gi, "Tahterevalli"],
  [/\bthruster\b/gi, "İtişli çömelme"], [/\bturkish get[- ]ups?\b/gi, "Türk kalkışı"], [/\bstyle\b/gi, "biçimi"],
  [/\bdead\b/gi, "Yerden"], [/\bflat\b/gi, "Düz"], [/\biron crosses?\b/gi, "Haç açışı"],
  [/\bone\b/gi, "Tek"], [/\btwo\b/gi, "Çift"], [/\bfull\b/gi, "Tam"], [/\bpartial\b/gi, "Kısmi"],
  [/\band\b/gi, "ve"], [/\bwith\b/gi, "ile"], [/\bwithout\b/gi, "olmadan"], [/\bfrom\b/gi, "başlangıçlı"],
  [/\bon\b/gi, "üzerinde"], [/\bin\b/gi, "içinde"], [/\bover\b/gi, "üzerinden"], [/\bup\b/gi, "yukarı"], [/\bdown\b/gi, "aşağı"],
  [/\bagainst\b/gi, "karşı"], [/\bthrough\b/gi, "içinden"], [/\bto\b/gi, "doğru"],

  // Yoga/mobilite duruşları ve eksik kalan İngilizce hareket terimleri
  // (taramada tespit edilen 106 çevrilmemiş kelime — bkz. konuşma geçmişi).
  [/\bpose\b/gi, "Duruşu"], [/\bbanded\b/gi, "Bantlı"], [/\bhold\b/gi, "Bekletme"],
  [/\bdownward\b/gi, "Aşağı"], [/\bupward\b/gi, "Yukarı"], [/\bfacing\b/gi, "Bakan"], [/\bdog\b/gi, "Köpek"],
  [/\bsumo\b/gi, "Sumo"], [/\bcarry\b/gi, "Taşıma"], [/\bswing\b/gi, "Savurma"], [/\bloaded\b/gi, "Yüklü"],
  [/\bbulgarian\b/gi, "Bulgar"], [/\bhalf\b/gi, "Yarım"], [/\bfold\b/gi, "Katlanış"], [/\bpause\b/gi, "Duraklama"],
  [/\bwarrior\b/gi, "Savaşçı"], [/\barcher\b/gi, "Okçu"], [/\blever\b/gi, "Kaldıraç"], [/\bcow\b/gi, "İnek"],
  [/\bclamshells?\b/gi, "Midye"], [/\bcrescent\b/gi, "Hilal"], [/\bbug\b/gi, "Böcek"], [/\bdrive\b/gi, "Sürme"],
  [/\bflag\b/gi, "Bayrak"], [/\bgoblet\b/gi, "Kadeh"], [/\bheel\b/gi, "Topuk"], [/\broll\b/gi, "Yuvarlanma"],
  [/\bspinal\b/gi, "Omurga"], [/\bspine\b/gi, "Omurga"], [/\brunning\b/gi, "Koşu"], [/\bflow\b/gi, "Akış"],
  [/\bthread\b/gi, "İplik"], [/\bneedle\b/gi, "İğne"], [/\blegged\b/gi, "Bacaklı"], [/\bspider\b/gi, "Örümcek"],
  [/\bnordic\b/gi, "Nordic"], [/\barnold\b/gi, "Arnold"], [/\bropes\b/gi, "Halatlar"], [/\bbattle\b/gi, "Savaş"],
  [/\bcouch\b/gi, "Kanepe"], [/\bbicycle\b/gi, "Bisiklet"], [/\bboat\b/gi, "Kayık"], [/\bbow\b/gi, "Yay"],
  [/\bcamel\b/gi, "Deve"], [/\bclap\b/gi, "Alkış"], [/\bcorpse\b/gi, "Ceset"], [/\bcossack\b/gi, "Kazak"],
  [/\bcrab\b/gi, "Yengeç"], [/\bcrow\b/gi, "Karga"], [/\bdancer\b/gi, "Dansçı"], [/\bdolphin\b/gi, "Yunus"],
  [/\bpedal\b/gi, "Pedal"], [/\bdragon\b/gi, "Ejderha"], [/\bsomersault\b/gi, "Takla"], [/\beagle\b/gi, "Kartal"],
  [/\beasy\b/gi, "Kolay"], [/\bangle\b/gi, "Açı"], [/\bfish\b/gi, "Balık"], [/\bgarland\b/gi, "Çelenk"],
  [/\bmoon\b/gi, "Ay"], [/\brock\b/gi, "Sallanma"], [/\bhappy\b/gi, "Mutlu"], [/\bbaby\b/gi, "Bebek"],
  [/\btoes?\b/gi, "Ayak ucu"], [/\bhex\b/gi, "Altıgen"], [/\bfoot\b/gi, "Ayak"], [/\bhollow\b/gi, "Çukur"],
  [/\bhorizontal\b/gi, "Yatay"], [/\bhuman\b/gi, "İnsan"], [/\boffset\b/gi, "Kaydırmalı"], [/\brotational\b/gi, "Dönüşlü"],
  [/\blizard\b/gi, "Kertenkele"], [/\blocust\b/gi, "Çekirge"], [/\bmountain\b/gi, "Dağ"], [/\bnegative\b/gi, "Negatif"],
  [/\bpigeon\b/gi, "Güvercin"], [/\bsaw\b/gi, "Testere"], [/\bplow\b/gi, "Saban"], [/\bpseudo\b/gi, "Yalancı"],
  [/\bpuppy\b/gi, "Yavru köpek"], [/\bpyramid\b/gi, "Piramit"], [/\brack\b/gi, "Raf"], [/\bclimb\b/gi, "Tırmanma"],
  [/\bscapular\b/gi, "Kürek kemiği"], [/\bscissors?\b/gi, "Makas"], [/\bstraddle\b/gi, "Ayrık bacak"],
  [/\bsuitcase\b/gi, "Bavul"], [/\bwindshield wipers?\b/gi, "Cam sileceği"], [/\bthoracic\b/gi, "Torasik"],
  [/\bthree\b/gi, "Üç"], [/\btree\b/gi, "Ağaç"], [/\btriangle\b/gi, "Üçgen"], [/\bapart\b/gi, "Ayırma"],
  [/\bfire\b/gi, "Ateş"], [/\bhydrant\b/gi, "Yangın musluğu"], [/\bterminal\b/gi, "Son açı"],
  [/\bbilateral\b/gi, "Çift taraflı"], [/\bbutterfly\b/gi, "Kelebek"], [/\bcheat\b/gi, "Hileli"],
  [/\bcobra\b/gi, "Kobra"], [/\bdoorway\b/gi, "Kapı çerçevesi"], [/\bhero\b/gi, "Kahraman"],
  [/\bhalo\b/gi, "Çember çevirme"], [/\bpinch\b/gi, "Sıkıştırma"], [/\bsphinx\b/gi, "Sfenks"],
  [/\bstrict\b/gi, "Sıkı"], [/\bsupported\b/gi, "Destekli"], [/\bkickstand\b/gi, "Destek ayaklı"],
  [/\bcaptain'?s\b/gi, "Kaptan"], [/\bchild'?s\b/gi, "Çocuk"], [/\bplanche\b/gi, "Planş"],
  [/\btabletop\b/gi, "Masa"], [/\brevolved\b/gi, "Döndürülmüş"], [/\bjumping jacks?\b/gi, "Zıplayarak kol-bacak açma"],
  [/\bbottoms\b/gi, "Alt"], [/\bpulses?\b/gi, "Vuruş"], [/\bpaused\b/gi, "Duraklamalı"],
  [/\bshoulderstand\b/gi, "Omuz duruşu"], [/\bups\b/gi, "yukarı"],
  [/\babduction\b/gi, "Açma"], [/\badduction\b/gi, "Kapama"],
];

const exactExerciseNamesTr: Record<string, string> = {
  "Ab Roller": "Karın Tekerleği",
  Adductor: "İç Bacak Makinesi",
  "Air Bike": "Hava Bisikleti",
  "Anterior Tibialis-SMR": "Ön Kaval Kası SMR",
  "Atlas Stone Trainer": "Atlas Taşı Antrenmanı",
  "Atlas Stones": "Atlas Taşları",
  "Backward Drag": "Geriye Kızak Çekişi",
  "Balance Board": "Denge Tahtası",
  "Battling Ropes": "Savaş Halatları",
  Bicycling: "Bisiklet Sürüşü",
  "Bicycling, Stationary": "Sabit Bisiklet",
  "Brachialis-SMR": "Brakiyalis SMR",
  "Butt-Ups": "Kalça Yukarı Kaldırış",
  "Butt Lift (Bridge)": "Kalça Kaldırma (Köprü)",
  Butterfly: "Kelebek Göğüs Açışı",
  "Car Drivers": "Direksiyon Çevirme",
  "Carioca Quick Step": "Carioca Hızlı Adım",
  "Child's Pose": "Çocuk Duruşu",
  "Circus Bell": "Sirk Dambılı Kaldırışı",
  Cocoons: "Koza Karın Sıkıştırma",
  "Conan's Wheel": "Conan Çarkı",
  Crucifix: "Haç Duruşu",
  "Downward Facing Balance": "Aşağı Bakan Denge Duruşu",
  "EZ-Bar Skullcrusher": "EZ Bar Alına İndiriş",
  "Elliptical Trainer": "Eliptik Bisiklet",
  "Fast Skipping": "Hızlı İp Atlama",
  "Flutter Kicks": "Çırpma Tekmeleri",
  "Foot-SMR": "Ayak Tabanı SMR",
  "Gironda Sternum Chins": "Gironda Göğüse Barfiks",
  Groiners: "Dinamik Kasık Esnetme",
  "Heavy Bag Thrust": "Ağır Çuval İtişi",
  "Iliotibial Tract-SMR": "İliotibial Bant SMR",
  Inchworm: "Tırtıl Yürüyüşü",
  "Iron Cross": "Demir Haç",
  "Jogging, Treadmill": "Koşu Bandında Hafif Koşu",
  "Keg Load": "Fıçı Yükleme",
  "Landmine 180's": "Landmine 180 Derece Dönüş",
  "Landmine Linear Jammer": "Landmine Doğrusal İtiş",
  "Latissimus Dorsi-SMR": "Kanat Kası SMR",
  "Linear 3-Part Start Technique": "Doğrusal Üç Aşamalı Çıkış Tekniği",
  "Log Lift": "Kütük Kaldırma",
  "London Bridges": "Londra Köprüsü",
  "Looking At Ceiling": "Tavana Bakış Esnetmesi",
  "Moving Claw Series": "Hareketli Pençe Serisi",
  "Parallel Bar Dip": "Paralel Bar Dips",
  "Pelvic Tilt Into Bridge": "Pelvik Eğişten Köprüye Geçiş",
  "Peroneals-SMR": "Peroneal Kas SMR",
  "Piriformis-SMR": "Piriformis Kası SMR",
  Pullups: "Barfiks",
  Pushups: "Şınav",
  Pyramid: "Piramit Koşusu",
  "Quick Leap": "Hızlı Sıçrayış",
  "Rack Delivery": "Raf Pozisyonuna Alış",
  "Rack Pulls": "Raf Yüksekliğinden Çekiş",
  "Recumbent Bike": "Yatay Kondisyon Bisikleti",
  "Rhomboids-SMR": "Romboid Kasları SMR",
  "Rickshaw Carry": "Rickshaw Ağırlık Taşıma",
  "Ring Dips": "Halka Dips",
  "Rowing, Stationary": "Sabit Kürek Ergometresi",
  "Running, Treadmill": "Koşu Bandında Koşu",
  "Sandbag Load": "Kum Torbası Yükleme",
  "Scissor Kick": "Makas Tekmesi",
  Skating: "Paten Adımı",
  "Sledgehammer Swings": "Balyoz Savurma",
  "Spell Caster": "Çapraz Ağırlık Savurma",
  "Spider Crawl": "Örümcek Yürüyüşü",
  Stairmaster: "Merdiven Tırmanma Makinesi",
  "Step Mill": "Basamak Makinesi",
  "Stomach Vacuum": "Karın Vakumu",
  Superman: "Süpermen Duruşu",
  "Pike Push-Up": "Pike Şınav",
  "Prone Y-T-W Raises": "Y-T-W Sırt ve Omuz Kaldırış",
  "Doorframe Bodyweight Row": "Kapı Kasasında Çekiş",
  "Bird Dog": "Kuş-Köpek (Bird Dog)",
  "Knee Push-Ups": "Diz Üstü Şınav",
  "Wall Sit": "Duvarda Oturuş (Wall Sit)",
  "Reverse Lunges": "Geriye Hamle (Reverse Lunge)",
  "Standing Bodyweight Calf Raise": "Ayakta Baldır Kaldırma",
  "Plank Shoulder Tap": "Plank Omuz Dokunuşu",
  "Towel Door Bicep Curl": "Havlu ile Kol Çekiş",
  "Diamond Push-Ups": "Elmas Şınav (Diamond Push-Up)",
  "Single Leg Glute Bridge": "Tek Bacak Kalça Köprüsü",
  "Thigh Abductor": "Kalça Dışa Açış Makinesi",
  "Thigh Adductor": "İç Bacak Kapama Makinesi",
  "Tire Flip": "Lastik Çevirme",
  "Toe Touchers": "Ayak Ucuna Dokunma",
  "V-Bar Pullup": "V Bar Barfiks",
  "Vertical Swing": "Dikey Savurma",
  Windmills: "Yel Değirmeni",
};

export function translateExerciseName(value: string, locale: Locale = "tr") {
  if (locale === "en") return value;
  const exact = exactExerciseNamesTr[value];
  if (exact) return exact;
  return exerciseNameTerms.reduce((name, [pattern, replacement]) => name.replace(pattern, replacement), value)
    .replace(/\s+/g, " ").replace(/\s+-\s+/g, " - ").trim();
}

export function turkishExerciseInstructions(exercise: Pick<Exercise, "name" | "force" | "category" | "primaryMuscles">, locale: Locale = "tr") {
  const name = exercise.name.toLocaleLowerCase("en-US");

  if (locale === "en") {
    const controlledEn = "Keep your torso stable throughout the movement, don't lock your joints, and stop if you feel pain.";
    if (/stretch/.test(name) || exercise.category === "stretching") {
      return [
        "Get into a comfortable starting position and steady your breathing.",
        "Ease in slowly until you feel a gentle stretch in the target muscle; don't bounce.",
        "Hold the position under control and return to the start at the same pace.",
      ];
    }
    if (/sprint|throw|bound|jump/.test(name) || exercise.category === "cardio" || exercise.category === "plyometrics") {
      return [
        "Take a balanced stance, brace your core, and clear space around you.",
        "Learn the movement slowly first, then build up the pace without losing form.",
        "Finish soft and controlled; don't hold your breath or lock your joints.",
      ];
    }
    if (/squat|lunge|step|leg press/.test(name)) {
      return [
        "Place your feet evenly, keep your chest up, and brace your core.",
        "Lower your hips under control while tracking your knees over your feet.",
        "Drive through your heels to rise and avoid locking your knees at the top.",
      ];
    }
    if (/deadlift|good morning/.test(name)) {
      return [
        "Keep the weight close to your body, spine neutral, and core braced.",
        "Hinge forward by sending your hips back; move from your hips, not your lower back.",
        "Drive through your heels and bring your hips forward to stand up under control.",
      ];
    }
    if (/bridge|hip raise|hip extension/.test(name)) {
      return [
        "Support your back, plant your feet firmly, and brace your core lightly.",
        "Push through your heels to lift your hips and squeeze your glutes at the top.",
        "Lower back down slowly without over-arching your lower back.",
      ];
    }
    if (/sit-up|sit up|crunch|leg lift|leg raise|plank/.test(name) || exercise.primaryMuscles.includes("abdominals")) {
      return [
        "Keep your lower back supported and draw your belly in.",
        "Start the movement from your abs, not your neck or momentum.",
        "Exhale as you squeeze, then return slowly without losing control of your lower back.",
      ];
    }
    if (/curl/.test(name) && !/leg curl/.test(name)) {
      return [
        "Keep your elbows close to your torso and your shoulders down.",
        "Pull the weight toward you under control without swinging.",
        "Lower the weight back down slowly while keeping tension on the muscle.",
      ];
    }
    if (/row|pulldown|pull-up|pull up/.test(name) || exercise.force === "pull") {
      return [
        "Keep your chest up, shoulders away from your ears, and torso stable.",
        "Pull your elbows back or down to bring your shoulder blades together.",
        "Extend your arms back out without losing control or rounding your shoulders forward.",
      ];
    }
    if (/press|push|dip|extension/.test(name) || exercise.force === "push") {
      return [
        "Get into a stable starting position and brace your shoulder blades and core.",
        "Lower the weight under control, then press it back up firmly but smoothly on the exhale.",
        "Return to the start without locking your elbows, keeping the same movement path.",
      ];
    }
    if (/calf/.test(name)) {
      return [
        "Place your feet evenly and use light support from a stable point.",
        "Raise your heels under control and briefly squeeze your calves at the top.",
        "Lower your heels slowly without letting your ankles roll in or out.",
      ];
    }
    return [
      "Get into a balanced starting position and steady your breathing.",
      controlledEn,
      "Complete the rep without rushing and return to the start under control.",
    ];
  }

  const controlled = "Hareket boyunca gövdeni sabit tut, eklemlerini kilitleme ve ağrı hissedersen dur.";

  if (/stretch/.test(name) || exercise.category === "stretching") {
    return [
      "Rahat bir başlangıç pozisyonu al ve nefesini düzenle.",
      "İlgili kas grubunda hafif gerilme hissedene kadar yavaşça ilerle; yaylanma yapma.",
      "Pozisyonu kontrollü koru ve aynı hızla başlangıca dön.",
    ];
  }
  if (/sprint|throw|bound|jump/.test(name) || exercise.category === "cardio" || exercise.category === "plyometrics") {
    return [
      "Dengeli bir duruş al, merkez bölgeni sık ve hareket alanını boş bırak.",
      "Hareketi önce düşük hızda öğren, ardından formun bozulmadan ritmi artır.",
      "Yumuşak ve kontrollü bitir; nefesini tutma ve eklemlerini kilitleme.",
    ];
  }
  if (/squat|lunge|step|leg press/.test(name)) {
    return [
      "Ayaklarını dengeli yerleştir, göğsünü açık ve merkez bölgeni sıkı tut.",
      "Kalçanı kontrollü indirirken dizlerini ayak yönünde takip ettir.",
      "Topuklarından güç alarak yüksel ve dizlerini üstte kilitleme.",
    ];
  }
  if (/deadlift|good morning/.test(name)) {
    return [
      "Ağırlığı vücuduna yakın tut, omurganı nötr ve karnını sıkı konumlandır.",
      "Kalçanı geriye göndererek öne katlan; hareketi belinden değil kalçandan yap.",
      "Topuklarından güç alıp kalçanı öne getirerek kontrollü biçimde doğrul.",
    ];
  }
  if (/bridge|hip raise|hip extension/.test(name)) {
    return [
      "Sırtını destekle, ayaklarını yere sağlam bas ve karnını hafifçe sık.",
      "Topuklarından iterek kalçanı kaldır ve tepede kalça kaslarını sık.",
      "Belini aşırı kavislendirmeden yavaşça başlangıç pozisyonuna dön.",
    ];
  }
  if (/sit-up|sit up|crunch|leg lift|leg raise|plank/.test(name) || exercise.primaryMuscles.includes("abdominals")) {
    return [
      "Belini destekli konumda tut ve karnını içeri doğru sık.",
      "Hareketi boyundan veya momentumdan değil karın kaslarından başlat.",
      "Nefes vererek sıkış, ardından bel kontrolünü kaybetmeden yavaşça dön.",
    ];
  }
  if (/curl/.test(name) && !/leg curl/.test(name)) {
    return [
      "Dirseklerini gövdene yakın ve omuzlarını aşağıda sabitle.",
      "Ağırlığı sallanmadan kontrollü biçimde kendine doğru çek.",
      "Kasın gerilimini koruyarak ağırlığı yavaşça başlangıca indir.",
    ];
  }
  if (/row|pulldown|pull-up|pull up/.test(name) || exercise.force === "pull") {
    return [
      "Göğsünü açık, omuzlarını kulaklarından uzak ve gövdeni sabit tut.",
      "Dirseklerini geriye veya aşağıya çekerek kürek kemiklerini birbirine yaklaştır.",
      "Kontrolü bırakmadan kollarını uzat ve omuzlarını öne düşürme.",
    ];
  }
  if (/press|push|dip|extension/.test(name) || exercise.force === "push") {
    return [
      "Dengeli bir başlangıç pozisyonu al, kürek kemiklerini ve merkez bölgeni sabitle.",
      "Ağırlığı kontrollü indir, ardından nefes vererek güçlü ama sarsıntısız biçimde it.",
      "Dirseklerini kilitlemeden başlangıç pozisyonuna dön ve hareket çizgisini koru.",
    ];
  }
  if (/calf/.test(name)) {
    return [
      "Ayak tabanını dengeli yerleştir ve bir destek noktasından hafifçe yardım al.",
      "Topuklarını kontrollü yükselt, üst noktada baldırlarını kısa süre sık.",
      "Topuklarını yavaşça indir ve ayak bileğinin içe ya da dışa kaçmasına izin verme.",
    ];
  }

  return [
    "Dengeli bir başlangıç pozisyonu al ve nefesini düzenle.",
    controlled,
    "Tekrarı acele etmeden tamamla ve başlangıç pozisyonuna kontrollü dön.",
  ];
}
