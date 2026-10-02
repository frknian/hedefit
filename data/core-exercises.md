# Çekirdek hareket havuzu

Toplam **206** hareket, **29** slot. Ayrıca 7 ısınma, 13 soğuma hareketi.

Atlas (601 hareket) değişmez. Plan üretimi ve rotasyon yalnız bu havuzdan seçer. Her slotun ilk iki hareketi *staple*, gerisi *varyasyon*.
**Rol:** ana = ilerleme için sabit kalan temel hareket · yardımcı = her blokta döner · core / kondisyon = serbest döner.

Değişiklik için `scripts/build-core-exercises.mjs` içindeki SLOTS düzenlenir ve script yeniden çalıştırılır; bu dosya ve `core-exercises.json` ondan üretilir.

## Göğüs · yatay itiş · ana (10: ev 6, yalnız salon 4)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Barbell Bench Press | Halter Bench Press | intermediate | barbell | gym |
| ★ | Dumbbell Bench Press | Dambıl Bench Press | beginner | dumbbell | home/gym |
|  | Machine Chest Press | Makine Göğüs Press | beginner | chest_press_machine | gym |
|  | Smith Machine Bench Press | Smith Makinede Bench Press | beginner | smith_machine | gym |
|  | Cable Chest Press | Kablo Göğüs Press | intermediate | cable | gym |
|  | Push-Up | Şınav | beginner | bodyweight | home/outdoor/gym |
|  | Knee Push Ups | Dizüstü Şınav | beginner | bodyweight | home/outdoor/gym |
|  | Dumbbell Floor Press | Dambıl Floor Press | beginner | dumbbell | home/gym |
|  | Band Chest Press | Bantlı Göğüs Press | beginner | resistance_band | home/gym |
|  | Wide Grip Push Ups | Geniş Şınav | beginner | bodyweight | home/outdoor/gym |

## Üst göğüs · eğimli itiş · yardımcı (5: ev 3, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Incline Dumbbell Press | Dambıl Eğimli Bench Press | intermediate | dumbbell | home/gym |
| ★ | Incline Barbell Bench Press | Halter Eğimli Bench Press | intermediate | barbell | gym |
|  | Smith Machine Incline Bench Press | Smith Makinede Eğimli Bench Press | beginner | smith_machine | gym |
|  | Incline Push-Up | Eğimli Şınav (Eller Yüksekte) | beginner | bodyweight | home/outdoor/gym |
|  | Decline Push-Up | Ayaklar Yüksekte Şınav | intermediate | bodyweight | home/outdoor/gym |

## Göğüs · açış (izolasyon) · yardımcı (7: ev 4, yalnız salon 3)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Fly | Dambıl Fly | beginner | dumbbell | home/gym |
| ★ | Cable Fly | Kablo Fly (Crossover) | intermediate | cable | gym |
|  | Machine Chest Fly | Makine Göğüs Fly | beginner | chest_fly_machine | gym |
|  | Pec Deck | Pec Deck (Makine Fly) | beginner | pec_deck | gym |
|  | Band Chest Fly | Bantlı Göğüs Fly | beginner | resistance_band | home/gym |
|  | Dumbbell Floor Fly | Dambıl Yerde Göğüs Fly | beginner | dumbbell | home/gym |
|  | Single Dumbbell Svend Press | Tek Dambıl Svend Press | beginner | dumbbell | home/gym |

## Omuz · dikey itiş · ana (9: ev 6, yalnız salon 3)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Shoulder Press | Dambıl Omuz Press | beginner | dumbbell | home/gym |
| ★ | Barbell Overhead Press | Halter Overhead Press | intermediate | barbell | gym |
|  | Seated Dumbbell Shoulder Press | Oturarak Dambıl Omuz Press | beginner | dumbbell | home/gym |
|  | Machine Shoulder Press | Makine Omuz Press | beginner | shoulder_press_machine | gym |
|  | Smith Machine Shoulder Press | Smith Makinede Omuz Press | beginner | smith_machine | gym |
|  | Arnold Press | Dambıl Arnold Press | intermediate | dumbbell | home/gym |
|  | Pike Push Ups | Pike Şınav | intermediate | bodyweight | home/outdoor/gym |
|  | Band Shoulder Press | Bantlı Omuz Press | beginner | resistance_band | home/gym |
|  | Dumbbell Push Press | Dambıl Push Press | intermediate | dumbbell | home/gym |

## Omuz · yan kaldırış · yardımcı (5: ev 3, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Lateral Raise | Dambıl Yana Kaldırma (Lateral Raise) | beginner | dumbbell | home/gym |
| ★ | Cable Lateral Raise | Kablo Yana Kaldırma | intermediate | cable | gym |
|  | Seated Dumbbell Lateral Raise | Oturarak Dambıl Yana Kaldırma | beginner | dumbbell | home/gym |
|  | Plate-Loaded Lateral Raise | Makine Yana Kaldırma | beginner | plate_loaded_lateral_raise_machine | gym |
|  | Band Lateral Raise | Bantlı Yan Omuz Kaldırış | beginner | resistance_band | home/gym |

## Arka omuz / üst sırt · yardımcı (8: ev 7, yalnız salon 1)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Cable Face Pull | Kablo Face Pull | beginner | cable | gym |
| ★ | Rear Delt Fly | Dambıl Arka Omuz Fly | beginner | dumbbell | home/gym |
|  | Dumbbell Reverse Fly | Dambıl Reverse Fly | intermediate | dumbbell | home/gym |
|  | Band Pull Apart | Bantla Pull-Apart | beginner | resistance_band | home/gym |
|  | TRX Face Pull | TRX Face Pull | beginner | suspension_trainer | home/gym |
|  | Band Rear Delt Fly | Bantlı Arka Omuz Fly | beginner | resistance_band | home/gym |
|  | Band Face Pull | Bantlı Face Pull | beginner | resistance_band | home/gym |
|  | Dumbbell Face Pull | Dambıl Face Pull | intermediate | dumbbell | home/gym |

## Trapez · yardımcı (4: ev 2, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Shrug | Dambıl Shrug | beginner | dumbbell | home/gym |
| ★ | Barbell Shrug | Halter Shrug | beginner | barbell | gym |
|  | Kettlebell Shrug | Girya Shrug | beginner | kettlebell | home/gym |
|  | Smith Machine Shrug | Smith Makinede Shrug | beginner | smith_machine | gym |

## Arka kol · bileşik · yardımcı (7: ev 4, yalnız salon 3)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Close-Grip Dumbbell Bench Press | Dar Tutuş Dambıl Bench Press | intermediate | dumbbell | home/gym |
| ★ | Close-Grip Bench Press | Dar Tutuş Halter Bench Press | intermediate | barbell | gym |
|  | Bench Dips | Sehpada Dips (Bench Dips) | intermediate | bodyweight | home/outdoor/gym |
|  | Diamond Push Ups | Elmas Şınav | intermediate | bodyweight | home/outdoor/gym |
|  | Machine Assisted Dips | Makine Destekli Dips | beginner | dip_machine | gym |
|  | Chest Dips | Paralel Barda Dips | intermediate | dip_station | gym |
|  | Close Grip Push Ups | Dar Şınav | intermediate | bodyweight | home/outdoor/gym |

## Arka kol · izolasyon · yardımcı (11: ev 7, yalnız salon 4)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Cable Tricep Pushdown | Kablo Triceps Pushdown | beginner | cable | gym |
| ★ | Overhead Tricep Extension | Tek Dambıl Baş Üstü Triceps Extension | beginner | dumbbell | home/gym |
|  | V-Bar Tricep Pushdown | V Tutamakla Kablo Triceps Pushdown | beginner | cable | gym |
|  | Dumbbell Skull Crusher | Dambıl Skull Crusher | intermediate | dumbbell | home/gym |
|  | Dumbbell Tricep Kickback | Dambıl Triceps Kickback | beginner | dumbbell | home/gym |
|  | Machine Triceps Extension | Makine Triceps Extension | beginner | tricep_extension_machine | gym |
|  | Single Arm Tricep Pushdown | Tek Kol Kablo Triceps Pushdown | intermediate | cable | gym |
|  | Band Triceps Pushdown | Bantlı Triceps Pushdown | beginner | resistance_band | home/gym |
|  | Band Overhead Triceps Extension | Bantlı Baş Üstü Triceps Uzatma | beginner | resistance_band | home/gym |
|  | Dumbbell Tricep Extension | Dambıl Baş Üstü Triceps Extension | beginner | dumbbell | home/gym |
|  | Single-Arm Dumbbell Overhead Tricep Extension | Tek Kol Dambıl Baş Üstü Triceps Extension | beginner | dumbbell | home/gym |

## Sırt · dikey çekiş · ana (11: ev 6, yalnız salon 5)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Lat Pulldown | Lat Pulldown | beginner | cable | gym |
| ★ | Pull-Up | Barfiks | intermediate | pull_up_bar | home/gym |
|  | V-Bar Lat Pulldown | V Tutamakla Lat Pulldown | beginner | lat_pulldown_machine | gym |
|  | Close Grip Lat Pulldown | Dar Tutuş Lat Pulldown | intermediate | cable | gym |
|  | Chin-Ups | Chin-Up (Ters Tutuş Barfiks) | intermediate | pull_up_bar | home/gym |
|  | Neutral Grip Pull Ups | Nötr Tutuş Barfiks | intermediate | pull_up_bar | home/gym |
|  | Assisted Pull Ups | Makine Destekli Barfiks | beginner | assisted_pullup_machine | gym |
|  | Band Assisted Pull Ups | Bant Destekli Barfiks | beginner | resistance_band | home/gym |
|  | Straight-Arm Pulldown | Kablo Düz Kol Pulldown | intermediate | cable | gym |
|  | Band Lat Pulldown | Bantlı Lat Pulldown | beginner | resistance_band | home/gym |
|  | Band Straight-Arm Pulldown | Bantlı Düz Kol Pulldown | beginner | resistance_band | home/gym |

## Sırt · yatay çekiş · ana (14: ev 8, yalnız salon 6)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Seated Cable Row | Oturarak Kablo Kürek Çekiş | beginner | cable | gym |
| ★ | Bent-Over Dumbbell Row | Dambılla Öne Eğilerek Kürek Çekiş | beginner | dumbbell | home/gym |
|  | Single-Arm Dumbbell Row | Tek Kol Dambıl Kürek Çekiş | beginner | dumbbell | home/gym |
|  | Bent-Over Barbell Row | Halterle Öne Eğilerek Kürek Çekiş | intermediate | barbell | gym |
|  | Chest-Supported Dumbbell Row | Eğimli Sehpada Dambıl Kürek Çekiş | intermediate | dumbbell | home/gym |
|  | T-Bar Row | T-Bar Row | intermediate | barbell | gym |
|  | Chest-Supported Smith Machine Row | Smith Makinede Göğüs Destekli Kürek Çekiş | beginner | smith_machine | gym |
|  | Kneeling Cable Row | Diz Üstü Kablo Kürek Çekiş | beginner | cable | gym |
|  | TRX Row | TRX Kürek Çekiş | beginner | suspension_trainer | home/gym |
|  | Inverted Row | Halterle Ters Kürek Çekiş (Inverted Row) | intermediate | barbell | gym |
|  | One Arm Kettlebell Row | Tek Kol Girya Kürek Çekiş | intermediate | kettlebell | home/gym |
|  | Band Seated Row | Bantlı Oturarak Kürek Çekişi | beginner | resistance_band | home/gym |
|  | Band Bent-Over Row | Bantlı Eğilerek Kürek Çekişi | beginner | resistance_band | home/gym |
|  | Band Single-Arm Row | Bantlı Tek Kol Kürek Çekişi | beginner | resistance_band | home/gym |

## Bacak · squat · ana (11: ev 7, yalnız salon 4)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Barbell Back Squat | Halter Squat | intermediate | barbell | gym |
| ★ | Goblet Squat | Goblet Squat | beginner | kettlebell | home/gym |
|  | Dumbbell Squat | Dambıl Squat | beginner | dumbbell | home/gym |
|  | Front Squat | Halter Front Squat | intermediate | barbell | gym |
|  | Leg Press | Leg Press | beginner | leg_press | gym |
|  | Hack Squat | Hack Squat Makinesi | intermediate | hack_squat | gym |
|  | Bodyweight Squat | Squat (Vücut Ağırlığı) | beginner | bodyweight | home/outdoor/gym |
|  | TRX Squat | TRX Squat | beginner | suspension_trainer | home/gym |
|  | Dumbbell Front Squat | Dambıl Front Squat | intermediate | dumbbell | home/gym |
|  | Dumbbell Sumo Squat | Dambıl Sumo Squat | beginner | dumbbell | home/gym |
|  | Banded Squat | Bantlı Squat | beginner | loop_band | home/gym |

## Ön bacak · izolasyon · yardımcı (5: ev 3, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Leg Extension | Leg Extension (Makine) | beginner | leg_extension | gym |
| ★ | Single Leg Extension | Tek Bacak Leg Extension (Makine) | intermediate | leg_extension | gym |
|  | Banded Terminal Knee Extension | Bantlı Diz Kilitleme (TKE) | beginner | loop_band | home/gym |
|  | Reverse Nordic Curl | Ters Nordic Curl | intermediate | bodyweight | home/outdoor/gym |
|  | Wall Sit | Duvarda Oturma (Wall Sit) | beginner | bodyweight | home/outdoor/gym |

## Arka bacak · kalça menteşesi · ana (10: ev 7, yalnız salon 3)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Romanian Deadlift | Halter Romanian Deadlift | intermediate | barbell | gym |
| ★ | Dumbbell Romanian Deadlift | Dambıl Romanian Deadlift | beginner | dumbbell | home/gym |
|  | Barbell Deadlift | Halter Deadlift | intermediate | barbell | gym |
|  | Dumbbell Deadlift | Dambıl Deadlift | beginner | dumbbell | home/gym |
|  | Kettlebell Deadlift | Girya Deadlift | beginner | kettlebell | home/gym |
|  | Smith Machine Romanian Deadlift | Smith Makinede Romanian Deadlift | beginner | smith_machine | gym |
|  | Single Leg Romanian Deadlift | Dambıl Tek Bacak Romanian Deadlift | intermediate | dumbbell | home/gym |
|  | Banded Romanian Deadlift | Bantlı Romanian Deadlift | beginner | loop_band | home/gym |
|  | Band Pull-Through | Bantlı Pull-Through | beginner | resistance_band | home/gym |
|  | Dumbbell Kickstand Deadlift | Dambıl Kickstand Deadlift | intermediate | dumbbell | home/gym |

## Arka bacak · curl · yardımcı (6: ev 4, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Lying Leg Curl | Yatarak Leg Curl (Makine) | beginner | leg_curl | gym |
| ★ | Seated Leg Curl | Oturarak Leg Curl (Makine) | beginner | leg_curl | gym |
|  | Stability Ball Leg Curl | Pilates Topuyla Leg Curl | intermediate | stability_ball | home/gym |
|  | TRX Hamstring Curl | TRX Hamstring Curl | intermediate | suspension_trainer | home/gym |
|  | Banded Standing Leg Curl | Bantlı Ayakta Leg Curl | beginner | loop_band | home/gym |
|  | Band Seated Hamstring Curl | Bantlı Oturarak Leg Curl | beginner | resistance_band | home/gym |

## Kalça · köprü / thrust · ana (6: ev 4, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Barbell Hip Thrust | Halter Hip Thrust | intermediate | barbell | gym |
| ★ | Glute Bridge | Kalça Köprüsü | beginner | bodyweight | home/outdoor/gym |
|  | Dumbbell Hip Thrust | Dambıl Hip Thrust | beginner | dumbbell | home/gym |
|  | Single Leg Glute Bridge | Tek Bacak Kalça Köprüsü | intermediate | bodyweight | home/outdoor/gym |
|  | Smith Machine Hip Thrust | Smith Makinede Hip Thrust | beginner | smith_machine | gym |
|  | Banded Hip Thrust | Bantlı Hip Thrust | beginner | loop_band | home/gym |

## Kalça · dış/iç bacak · yardımcı (7: ev 4, yalnız salon 3)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Cable Glute Kickback | Kablo Kalça Kickback | intermediate | cable | gym |
| ★ | Machine Hip Abduction | Makine Kalça Açma (Abductor) | beginner | hip_abduction_machine | gym |
|  | Banded Lateral Walk | Bantlı Yan Yürüyüş | beginner | loop_band | home/gym |
|  | Side-Lying Hip Abduction | Yan Yatarak Bacak Açma | beginner | bodyweight | home/outdoor/gym |
|  | Hip Adduction | Makine Kalça Kapama (Adductor) | intermediate | hip_adduction_machine | gym |
|  | Glute Kickback | Dört Ayak Kalça Kickback | beginner | bodyweight | home/outdoor/gym |
|  | Clamshells | Clamshell | beginner | bodyweight | home/outdoor/gym |

## Bacak · tek bacak / lunge · yardımcı (7: ev 7, yalnız salon 0)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Lunge | Dambıl Lunge | intermediate | dumbbell | home/gym |
| ★ | Walking Lunge | Yürüyerek Lunge | beginner | bodyweight | home/outdoor/gym |
|  | Reverse Lunge | Dambıl Geri Lunge | intermediate | dumbbell | home/gym |
|  | Bulgarian Split Squat | Dambıl Bulgar Split Squat | intermediate | dumbbell | home/gym |
|  | Bodyweight Reverse Lunge | Geri Lunge | beginner | bodyweight | home/outdoor/gym |
|  | Step Ups | Basamağa Çıkma (Step-Up) | beginner | bodyweight | home/outdoor/gym |
|  | Dumbbell Split Squat | Dambıl Split Squat | intermediate | dumbbell | home/gym |

## Baldır · yardımcı (6: ev 4, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Standing Calf Raise | Ayakta Baldır Makinesi | beginner | standing_calf_raise_machine | gym |
| ★ | Seated Calf Raise | Oturarak Baldır Makinesi | beginner | seated_calf_raise_machine | gym |
|  | Bodyweight Calf Raise | Baldır Kaldırma | beginner | bodyweight | home/outdoor/gym |
|  | Single Leg Calf Raise | Tek Bacak Baldır Kaldırma | beginner | bodyweight | home/outdoor/gym |
|  | Dumbbell Calf Raise | Dambılla Baldır Kaldırma | intermediate | dumbbell | home/gym |
|  | Band Calf Raise | Bantlı Baldır Kaldırış | beginner | resistance_band | home/gym |

## Ön kol · biceps curl · yardımcı (11: ev 6, yalnız salon 5)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Bicep Curl | Dambıl Biceps Curl | beginner | dumbbell | home/gym |
| ★ | Barbell Curl | Halter Biceps Curl | beginner | barbell | gym |
|  | EZ-Bar Curl | EZ Bar Curl | beginner | ez_bar | gym |
|  | Cable Curl | Kablo Biceps Curl | beginner | cable | gym |
|  | Machine Bicep Curl | Makine Biceps Curl | beginner | bicep_curl_machine | gym |
|  | Preacher Curl | EZ Bar Preacher Curl | beginner | ez_bar | gym |
|  | Incline Dumbbell Curl | Eğimli Sehpada Dambıl Curl | intermediate | dumbbell | home/gym |
|  | Concentration Curl | Dambıl Konsantrasyon Curl | beginner | dumbbell | home/gym |
|  | Band Biceps Curl | Bantlı Biceps Curl | beginner | resistance_band | home/gym |
|  | Seated Dumbbell Curl | Oturarak Dambıl Curl | beginner | dumbbell | home/gym |
|  | Zottman Curl | Dambıl Zottman Curl | intermediate | dumbbell | home/gym |

## Ön kol · hammer / brachialis · yardımcı (6: ev 4, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Hammer Curl | Dambıl Hammer Curl | beginner | dumbbell | home/gym |
| ★ | Cable Hammer Curl | Kablo Hammer Curl | intermediate | cable | gym |
|  | Cross Body Hammer Curl | Dambıl Çapraz Hammer Curl | intermediate | dumbbell | home/gym |
|  | EZ-Bar Reverse Curl | EZ Bar Ters Tutuş Curl | beginner | ez_bar | gym |
|  | Band Hammer Curl | Bantlı Hammer Curl | beginner | resistance_band | home/gym |
|  | Single-Arm Hammer Curl | Tek Kol Dambıl Hammer Curl | intermediate | dumbbell | home/gym |

## Önkol / kavrama · yardımcı (3: ev 3, yalnız salon 0)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Wrist Curl | Dambıl Bilek Curl | beginner | dumbbell | home/gym |
| ★ | Dead Hang | Barda Asılı Durma | beginner | pull_up_bar | home/gym |
|  | Dumbbell Reverse Wrist Curl | Dambıl Ters Bilek Curl | beginner | dumbbell | home/gym |

## Core · stabilite · core (7: ev 7, yalnız salon 0)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Plank | Plank | beginner | bodyweight | home/outdoor/gym |
| ★ | Dead Bug | Dead Bug (Ölü Böcek) | beginner | bodyweight | home/outdoor/gym |
|  | Bird-Dog | Bird Dog (Kuş-Köpek) | beginner | bodyweight | home/outdoor/gym |
|  | High Plank | Düz Kol Plank | beginner | bodyweight | home/outdoor/gym |
|  | Hollow Body Hold | Hollow Body Bekletme | intermediate | bodyweight | home/outdoor/gym |
|  | Ab Wheel Rollout | Karın Tekerleği (Ab Wheel) | intermediate | ab_wheel | home/gym |
|  | TRX Plank | TRX Plank | intermediate | suspension_trainer | home/gym |

## Core · karın bükme · core (5: ev 3, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Crunches | Crunch | beginner | bodyweight | home/outdoor/gym |
| ★ | Cable Crunch | Kablo Crunch | intermediate | cable | gym |
|  | Reverse Crunches | Ters Crunch | beginner | bodyweight | home/outdoor/gym |
|  | Machine Seated Crunch | Makine Crunch | beginner | ab_crunch_machine | gym |
|  | Bicycle Crunch | Bisiklet Crunch | beginner | bodyweight | home/outdoor/gym |

## Core · bacak kaldırma · core (5: ev 4, yalnız salon 1)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Hanging Knee Raise | Barda Asılı Diz Çekme | beginner | pull_up_bar | home/gym |
| ★ | Lying Leg Raise | Yatarak Bacak Kaldırma | beginner | bodyweight | home/outdoor/gym |
|  | Captain's Chair Knee Raise | Dips İstasyonunda Diz Çekme | beginner | bodyweight | gym |
|  | Hanging Leg Raise | Barda Asılı Bacak Kaldırma | intermediate | pull_up_bar | home/gym |
|  | Flutter Kicks | Flutter Kick (Çırpma) | beginner | bodyweight | home/outdoor/gym |

## Core · yan / rotasyon · core (6: ev 5, yalnız salon 1)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Side Plank | Yan Plank | beginner | bodyweight | home/outdoor/gym |
| ★ | Cable Pallof Press | Kablo Pallof Press | intermediate | cable | gym |
|  | Russian Twist | Russian Twist | beginner | bodyweight | home/outdoor/gym |
|  | Dumbbell Side Bend | Dambılla Yana Eğilme | beginner | dumbbell | home/gym |
|  | Cross-Body Crunch | Çapraz Crunch | beginner | bodyweight | home/outdoor/gym |
|  | Band Pallof Press | Bantlı Pallof Press | beginner | resistance_band | home/gym |

## Alt sırt · core (4: ev 2, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Back Extension | Hiperekstansiyon (Bel Sehpası) | beginner | bodyweight | gym |
| ★ | Superman | Superman | beginner | bodyweight | home/outdoor/gym |
|  | Reverse Plank | Ters Plank | beginner | bodyweight | home/outdoor/gym |
|  | Machine Back Extension | Makine Bel Extension | beginner | back_extension_machine | gym |

## Taşıma / kavrama · kondisyon (3: ev 3, yalnız salon 0)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Dumbbell Farmer's Walk | Dambıl Farmer Walk | beginner | dumbbell | home/gym |
| ★ | Kettlebell Farmer's Walk | Girya Farmer Walk | intermediate | kettlebell | home/gym |
|  | Suitcase Carry | Tek Taraflı Taşıma (Suitcase Carry) | beginner | kettlebell | home/gym |

## Kondisyon / tam vücut · kondisyon (7: ev 5, yalnız salon 2)

| | Hareket | Türkçe | Seviye | Ekipman | Ortam |
|---|---|---|---|---|---|
| ★ | Kettlebell Swing | Girya Swing | intermediate | kettlebell | home/gym |
| ★ | Burpees | Burpee | intermediate | bodyweight | home/outdoor/gym |
|  | Mountain Climbers | Mountain Climber (Dağ Tırmanışı) | beginner | bodyweight | home/outdoor/gym |
|  | Jump Rope | İp Atlama | beginner | jump_rope | home/gym |
|  | Medicine Ball Slam | Sağlık Topu Slam | beginner | slam_ball | gym |
|  | Battle Ropes | Battle Rope Dalgalar | intermediate | battle_rope | gym |
|  | Jumping Jacks | Jumping Jack | beginner | bodyweight | home/outdoor/gym |

## Isınma (dinamik)

Cat-Cow (Kedi-İnek) · Bird-Dog (Bird Dog (Kuş-Köpek)) · Jumping Jacks (Jumping Jack) · High Knees (Yüksek Diz Koşusu) · Downward Dog to Plank (Aşağı Bakan Köpekten Plank) · Thread the Needle (İğneye İplik Geçirme Esnemesi) · Half-Kneeling Hip Flexor Rock (Yarım Diz Kalça Önü Sallanma)

## Soğuma (statik)

Child's Pose (Çocuk Pozu) · Downward-Facing Dog (Aşağı Bakan Köpek) · Kneeling Hip Flexor Stretch (Diz Üstü Kalça Önü Esneme) · Pigeon Stretch (Güvercin Esneme) · Standing Calf Stretch (Ayakta Baldır Esneme) · Standing Quad Stretch (Ayakta Ön Bacak Esneme) · Doorway Chest Stretch (Kapı Aralığında Göğüs Esneme) · Cross-Body Shoulder Stretch (Çapraz Omuz Esneme) · Overhead Triceps Stretch (Baş Üstü Arka Kol Esneme) · Bench Hamstring Stretch (Sehpada Arka Bacak Esneme) · Supine Spinal Twist (Sırtüstü Omurga Dönüşü) · Knee-to-Chest Stretch (Dizi Göğse Çekme Esnemesi) · Neck Side Stretch (Boyun Yan Esneme)

## Kapsam uyarıları

- Görsel eksik: Band Chest Press
- Görsel eksik: Dumbbell Floor Fly
- Görsel eksik: Band Face Pull
- Görsel eksik: Band Triceps Pushdown
- Görsel eksik: Band Lat Pulldown
- Görsel eksik: Band Straight-Arm Pulldown
- Görsel eksik: Band Seated Row
- Görsel eksik: Band Bent-Over Row
- Görsel eksik: Band Single-Arm Row
- Görsel eksik: Band Biceps Curl
- Görsel eksik: Band Hammer Curl
- Görsel eksik: Band Pallof Press
- triceps_compound: başlangıç seviyesinde 1 hareket
