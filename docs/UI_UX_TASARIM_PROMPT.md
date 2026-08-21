# Hedefit — Sıfırdan UI/UX Tasarım Brief'i (Tasarımcı / AI Tasarım Aracı Prompt'u)

> Bu belge tek başına yeterlidir. Tasarımcıya ya da bir AI tasarım aracına
> (Stitch, Figma AI, v0, Lovable) olduğu gibi verilebilir. Uygulamanın kodunu
> görmeyen biri bu belgeyle uygulamanın tamamını yeniden tasarlayabilmelidir.

---

## 0. TL;DR — Tek paragraflık görev tanımı

Hedefit, kullanıcıyı ilk gün tanıyıp **her antrenmandan sonra kendini yeniden
ayarlayan** bir yapay zekâ antrenör uygulamasıdır. Türkçe ve İngilizce, açık ve
koyu temalı, telefon-öncelikli (Capacitor ile Android/iOS'ta native kabuk),
masaüstünde de çalışan bir web uygulaması. Görevin: mevcut marka renklerine
**birebir sadık kalarak**, kaydırma jesti gerektirmeyen, her şeyin dokunmayla
ulaşıldığı, açıklamaların ekranı boğmadığı, sade ve hatasız bir arayüz
tasarlamak.

---

## 1. Ürün özeti — Hedefit ne yapar?

**Marka sözü:** "Hedefin için fit plan."

Çoğu fitness uygulaması sabit bir program verip gerisini kullanıcıya bırakır.
Hedefit tersini yapar:

1. **Tanır** — 15 soruluk profil testi + yaş/boy/kilo/cinsiyet ölçüleri.
2. **Plan üretir** — ortam (ev/salon), ekipman, sakatlık ve hedefe göre.
3. **Dinler** — her seans sonrası "nasıldı?" geri bildirimi alır.
4. **Uyarlar** — zor geldiyse yükü düşürür, kolay geldiyse artırır, ağrı
   bildirilen bölgeyi programdan çıkarır. Ağrı veya yüksek yorgunluk
   bildirildiyse **yükü artırmayı reddeder**.

Yanında beslenme (AI kalori/makro hesabı), aktivite takibi (GPS rota, adım
sayar, nabız bandı), ilerleme grafikleri ve bir sohbet koçu vardır.

### Ana modüller
| Modül | Ne yapar |
|---|---|
| Kişisel plan | AI'nın ürettiği, zamanla uyarlanan antrenman programı |
| Antrenman oynatıcı | Set/dinlenme sayacı, ağırlık·tekrar·zorluk kaydı |
| Hareket kütüphanesi | ~106 hareket, animasyonlu başlangıç/bitiş kareleri, Türkçe adlar |
| Beslenme | Yazarak AI kalori/makro tahmini, barkod, öğün günlüğü, su takibi |
| Aktivite | GPS rota takibi, adım sayar, koşu/bisiklet/yüzme kayıtları |
| İlerleme | Vücut ölçümleri, tahmini 1RM rekorları, seri, haftalık AI değerlendirme |
| Takvim | Antrenman günleri, saat tercihi, hatırlatıcı, kaçırılan günü yeniden planlama |
| Fit Koç | Profilini ve planını bilen AI sohbet asistanı |
| Profil | Ölçüler, tercihler, veri indirme, hesap yönetimi, Premium |

---

## 2. Değişmez tasarım kuralları (İHLAL EDİLEMEZ)

Bunlar müşterinin açık talebidir; her ekran bu beş kuralla denetlenecek.

### K1 — Kaydırma jesti YOK
- **Yatay carousel yok.** Kartlar yan yana kayan şerit halinde sunulmaz.
- **Swipe-to-delete yok.** Silme her zaman görünür bir çöp kutusu ikonu veya
  "Sil" düğmesiyle yapılır.
- **Swipe ile sekme değiştirme yok.** Sekmeler yalnızca dokunmayla değişir.
- **Pull-to-refresh yok.** Yenileme, ilgili kartın üstündeki görünür bir
  "Yenile" düğmesiyle yapılır.
- **Sürükle-bırak sıralama yok.** Plan düzenleyicide hareket sırası "yukarı ▲ /
  aşağı ▼" düğmeleriyle değiştirilir.
- Tek izin verilen kaydırma: sayfanın kendi **dikey** akışı. Uzun listelerde
  yatay taşma olmaz; tablo/grafik gerekirse kendi içinde sığdırılır.

### K2 — Dokunulan yere gidilir (anchor + reset davranışı)
- Bir kısayola, karta veya özet satırına basıldığında hedef ekran açılır **ve
  ilgili bölüme kaydırılır** (yumuşak scroll, bölüm 1 saniye boyunca hafif lime
  halka ile vurgulanır).
- **Kaldığı yerden devam etmez.** Kullanıcı bir sekmeden çıkıp geri döndüğünde
  o sekme **en üstten** açılır; önceki scroll konumu geri yüklenmez.
- Seçenek seçildiğinde (ör. "Hazır programlar") kullanıcı o bölümün önüne
  bırakılır, sayfanın rastgele bir yerine değil.
- Logo/marka her zaman ana ekrana döner ve en üste kaydırır.
- Her kaplama (dialog) açıldığında odak başlığa gider; kapandığında odak onu
  açan düğmeye döner.

### K3 — Açıklamalar yer kaplamaz
Uygulama içinde uzun açıklama metni **asla doğrudan akışta durmaz**. İki kalıp
vardır, üçüncüsü yoktur:

- **Kalıp A — Açılır satır (accordion):** Kısa bir soru başlığı + sağda aşağı
  bakan ok (`⌄`). Basınca 200 ms'de açılır, ok 180° döner, içerik altında
  belirir. Örn: "Bu hedef nasıl hesaplandı?", "3 adımda nasıl yapılır?",
  "Planın neden böyle?". Aynı anda birden fazlası açık kalabilir; açılan içerik
  ekranın en fazla yarısını kaplar, daha uzunsa Kalıp B'ye geçilir.
- **Kalıp B — Ayrı açıklama sayfası:** Uzun içerik (hareket tekniği, güvenlik
  notları, hesaplama yöntemi, tıbbi uyarılar, gizlilik) tam sayfa detaya taşınır.
  Girişi tek satırlık bir "ⓘ Nasıl hesaplanır?" bağlantısıdır. Detay sayfasının
  üstünde geri oku, başlığı ve gerekiyorsa "Anladım" kapatma düğmesi bulunur.

**Kural:** Bir kartın gövde metni **iki satırı** geçiyorsa, fazlası Kalıp A veya
B'ye taşınır. Ekranda hiçbir yerde 3 satırdan uzun paragraf durmaz — Fit Koç
sohbet balonları ve haftalık AI değerlendirmesi hariç.

### K4 — Hatasız ve öngörülebilir
- Her etkileşimin dört durumu tasarlanır: **boş / yükleniyor / hata / dolu**.
- Yıkıcı işlem (hesap silme, ilerleme sıfırlama, kayıt silme) onay ister; hesap
  silme ve ilerleme sıfırlama **yazarak onay** (`SIFIRLA` gibi bir ifade) ister.
- Kayıt işlemleri iyimser değil, **gerçek durumlu**: "Kaydediliyor…" → "Kaydedildi"
  ya da "Kaydedilemedi, tekrar dene". Sessiz başarısızlık yok.
- Çevrimdışıyken kaydedilen şey "bu cihazda kaydedildi, hesabına eşitlenemedi"
  diye açıkça söylenir.
- Ham teknik hata mesajı hiçbir zaman kullanıcıya gösterilmez.

### K5 — Rahat kullanım (konfor)
- Minimum dokunma hedefi **48×48 px**; komşu hedefler arası en az 8 px.
- Ana eylemler ekranın alt üçte birinde, başparmak erişiminde.
- Güvenli alanlar (notch, gesture bar) her zaman hesaba katılır.
- Antrenman sırasında sayaç ve "Seti tamamla" düğmesi tek elle, ekrana bakmadan
  bulunabilecek kadar büyük olur (sayaç ≥ 64 px, düğme tam genişlik).
- Haptik geri bildirim: set bitişi, dinlenme bitişi, rekor kırma, kayıt silme.

---

## 3. Görsel dil — "High-Performance Kinetic"

Sportif, teknik, temiz. Spor salonu posteri değil, **iyi tasarlanmış bir cihaz
arayüzü** hissi. Gölge değil "parıltı". Ağır gradyan yok, dokusuz zeminler,
belirgin yuvarlaklık.

### 3.1 Renk — AÇIK TEMA (birebir korunacak)

| Rol | HEX | Kullanım |
|---|---|---|
| primary | `#536600` | Bağlantı, aktif ikon, vurgulu metin |
| on-primary | `#ffffff` | primary üstü yazı |
| **primary-container (marka lime)** | `#d9f76b` | Ana düğme zemini, aktif sekme, ilerleme dolgusu |
| on-primary-container | `#1c1c1a` | Lime üstü yazı — **beyaz değil, koyu mürekkep** |
| primary-strong | `#5d7100` | Basılı durum |
| primary-soft | `#eef7cf` | Seçili sakin zemin |
| secondary | `#a0410b` | İkincil vurgu |
| **secondary-container (turuncu)** | `#ff8850` | "Isı": antrenmanı bitir, yoğunluk uyarısı, seri alevi |
| on-secondary-container | `#6d2700` | Turuncu üstü yazı |
| secondary-soft | `#ffe6d9` | Yumuşak uyarı zemini |
| tertiary | `#496270` | Nötr bilgi |
| tertiary-container | `#d3eeff` | Bilgi rozeti zemini |
| surface | `#fcf9f5` | Gövde zemini |
| surface-lowest | `#ffffff` | En üst kart |
| surface-low | `#f6f3ef` | Girdi zemini |
| surface-container | `#f0edea` | Kart zemini |
| surface-high | `#ebe8e4` | Yükseltilmiş yüzey |
| surface-highest | `#e5e2de` | En yüksek yüzey |
| paper | `#f5f5f0` | Kâğıt bej — geniş bloklar |
| on-surface | `#1c1c1a` | Ana metin (dark ink) |
| on-surface-variant | `#454837` | İkincil metin |
| outline | `#767965` | Kenarlık |
| outline-variant | `#c6c8b2` | Sakin kenarlık |
| hairline | `rgba(28,28,26,0.08)` | Kart kenarı — 1 px |
| success | `#22c55e` · warning `#facc15` · error `#ef4444` | Durum |

### 3.2 Renk — KOYU TEMA (birebir korunacak)

| Rol | HEX |
|---|---|
| primary | `#d9f76b` |
| on-primary | `#2a3400` |
| primary-container | `#d1ef64` · on: `#2a3400` |
| primary-strong | `#b6d24b` · primary-soft `#303a23` |
| secondary | `#ffb596` · container `#ff8850` · on-container `#360f00` · soft `#3a241a` |
| tertiary | `#b9d079` · container `#3c4d02` · on-container `#d5ec92` |
| surface | `#131410` · lowest `#0d0f09` · low `#1a1c16` |
| surface-container | `#1e201a` · high `#292b24` · highest `#34362e` |
| paper (girdi zemini) | `#1a1c16` |
| on-surface | `#e3e3d9` · variant `#c6c8b2` |
| outline | `#90937e` · variant `#454837` |
| hairline | `rgba(227,227,217,0.12)` |
| error | `#ffb4ab` · container `#93000a` · on-container `#ffdad6` |

**Renk kuralları**
- Lime zemin üzerine **her zaman** koyu mürekkep (`#1c1c1a`) yazılır. Lime
  üstüne beyaz yazı yasak (kontrast 2.6:1).
- Turuncu yalnızca "ısı/yoğunluk/bitir/seri" anlamında; dekor amaçlı kullanılmaz.
- Kırmızı yalnızca hata ve yıkıcı işlem.
- Her ekran **iki temada da** teslim edilir. Koyu tema sonradan uyarlanan bir
  varyant değil, eşdeğer bir teslimattır.

### 3.3 Tipografi
- **Başlık:** Montserrat Variable (100–900). Güç ve ivme.
- **Gövde:** Inter Variable (100–900). Veri okunabilirliği.
- Ölçek:
  - Display 48 / 1.1 / 800 / -0.02em
  - Headline L 32 / 1.2 / 700
  - Headline M 24 / 1.3 / 600
  - Body L 18 / 1.6 · Body M 16 / 1.5
  - Label bold 14 / 700 · Label sm 12 / 500
- **Eyebrow (üst etiket):** 12 px, 700, harf aralığı +0.08em, BÜYÜK HARF, ikincil
  renk. Her bölümün üstünde durur: `BUGÜNÜN PLANI · 01`, `KİŞİSEL REKORLAR`,
  `BESLENME TAKİBİ`. Bu, markanın imzasıdır — korunacak.
- **Vurgu deseni:** Başlıklarda ikinci satır italik/renkli vurgu alır:
  "Yediklerini gör, **hedefine yaklaş.**" — bu kalıp korunur.
- Türkçe karakterler (ğ ş ı İ ç ö ü) tüm ağırlıklarda test edilir. Büyük harf
  dönüşümünde `i → İ` doğru olmalı (CSS `text-transform` değil, hazır metin).
- Sayılar tabular-nums; sayaçlarda rakam zıplaması olmaz.

### 3.4 Boşluk, yarıçap, derinlik
- 8 px tabanlı ölçek: 4 / 8 / 16 / 24 / 32 / 48. Gutter 16, kenar boşluğu 20.
- İçerik maksimum genişliği 1280 px, masaüstünde ortalanır.
- Yarıçap: 4 / 8 / 12 / 16 / 24 / tam yuvarlak. Kartlar 16–24, düğmeler tam
  yuvarlak veya 12, girdiler 12.
- Derinlik: ağır gölge yok, tonal katman + ortam gölgesi.
  - kart `0 4px 24px rgba(28,28,26,.04)`
  - yükseltilmiş `0 12px 32px rgba(28,28,26,.09)`
  - yüzen `0 24px 60px rgba(28,28,26,.16)`
  - lime parıltı `0 0 30px rgba(217,247,107,.35)`
  - düğme parıltısı `0 4px 20px rgba(217,247,107,.28)`
- Cam efekti (blur 12 px) **yalnızca** üst başlık çubuğu, alt sekme çubuğu ve
  yapışkan eylem çubuğunda. Kartlarda cam yok.

### 3.5 Hareket
- Standart easing `cubic-bezier(0.22,0.61,0.36,1)`.
- Süreler: mikro 120 ms, standart 200 ms, sayfa geçişi 260 ms.
- Sayfa geçişi: **fade + 8 px yukarı kayma**. Yandan sürükleme yok (K1).
- Accordion 200 ms yükseklik + ok dönüşü.
- Sayaç ve halka animasyonları saniyede bir kademe; sürekli pulsasyon yok.
- `prefers-reduced-motion` açıksa tüm hareket anlık geçişe düşer.

---

## 4. Bileşen kütüphanesi

Her bileşenin varsayılan / hover / basılı / odaklı / devre dışı / yükleniyor
durumları tasarlanır. Odak halkası her yerde görünür (2 px, primary).

1. **Düğmeler**
   - Birincil: lime dolgu, koyu yazı, alt parıltı, tam genişlik veya hap şekli.
   - İkincil: kenarlıklı, saydam zemin.
   - Metin düğmesi: yalnız yazı + ok.
   - Tehlike: kırmızı kenarlık, dolgu yok; onay adımından sonra dolu kırmızı.
   - Yükleniyor: etiket yerinde kalır, solda küçük dönen halka; genişlik değişmez.
2. **İkon düğmesi** — 48×48, yuvarlak, ipucu metni zorunlu (`aria-label`).
3. **Kart** — 1 px hairline, 16–24 yarıçap, 16 px iç boşluk. Başlık = eyebrow +
   başlık + isteğe bağlı sağ üst eylem.
4. **StatTile (istatistik karesi)** — üstte küçük etiket, ortada büyük sayı +
   birim, altta bağlam. Örn. `TOPLAM SÜRE / 248 dakika / tüm kayıtlar`.
5. **Chip / filtre** — hap şekli, seçili olan lime dolgu + koyu yazı. Çoklu seçim
   çipleri sol üstte ✓ alır.
6. **Segment kontrol** — 2–4 seçenek, tek satır, seçili olan lime kızak.
7. **Accordion (Kalıp A)** — bölüm 3'te tanımlı.
8. **Liste satırı** — sol ikon/görsel, orta başlık + alt bilgi, sağ değer veya
   `›`. Tıklanabilirse tamamı tıklanabilir.
9. **Halka göstergesi (ring)** — günlük enerji için. Ortada kalan kcal, altında
   "kaldı"/"aşıldı". Aşımda turuncu.
10. **İlerleme çubuğu** — makro ve antrenman ilerlemesi. Aşım kısmı turuncu.
11. **Sayaç (timer)** — mono/tabular, ≥64 px, altında durum etiketi
    (`DİNLENME SÜRÜYOR` / `AKTİF SET` / `HAZIR`).
12. **Dialog / kaplama** — ortalanmış, maks 560 px, üstte başlık + kapat.
    Telefonda tam yükseklik. **Sürükleyerek kapatma yok** — kapatma yalnız ✕ ve
    zemine dokunma.
13. **Bildirim şeridi (toast)** — üstte, 4 sn, tek satır, gerekiyorsa "Geri al".
14. **Boş durum** — ikon + tek satır açıklama + tek eylem düğmesi.
15. **Yükleme iskeletleri** — spinner değil, içerik biçiminde iskelet.
16. **SportyLoader** — AI plan üretirken kullanılan markalı yükleyici (bkz. 5.4).
17. **AiInsight kartı** — AI çıktısı taşıyan her kart: sol üstte ✦ işareti,
    altında kaynak notu ("Kimi K3 ile hazırlandı" / "Güvenli yerel kurallarla
    hazırlandı") ve sorumluluk reddi.
18. **Arama alanı** — üst çubukta; sonuçlar ekran/hareket/besin olarak gruplu.
19. **Alt sekme çubuğu** — 6 sekme, cam zemin, aktif olan lime ikon + etiket.
20. **Yan gezinme sütunu** — masaüstünde sol sabit, marka + profil + 8 giriş + CTA.

---

## 5. Bilgi mimarisi ve gezinme

### 5.1 Kabuk
- **Telefon:** üstte cam başlık çubuğu (marka · arama · takvim · kütüphane ·
  bildirim · tema), altta 6 sekmeli cam çubuk.
- **Masaüstü:** solda sabit gezinme sütunu (8 giriş), üstte arama ve eylemler.
- **Fit Koç** her ekranda sağ altta yüzen bir düğme (FAB). Antrenman oynatıcı
  ve tam ekran kaplamalarda gizlenir.

### 5.2 Sekmeler (alt çubuk)
| # | Sekme | İkon | İçerik |
|---|---|---|---|
| 1 | Ana Sayfa | ev | Bugünün planı, kısayollar, enerji, AI analizi |
| 2 | Aktivite | ayak izi | Adım, GPS rota, aktivite günlüğü, seri |
| 3 | Antrenman | dambıl | Plan listesi, hazır programlar, oynatıcı |
| 4 | Beslenme | çatal-bıçak | Öğün ekle, günlük özet, su, hedefler |
| 5 | İlerleme | grafik | İstatistik, ölçümler, rekorlar, haftalık AI |
| 6 | Profil | kişi | Ölçüler, tercihler, veri, hesap, Premium |

Başlık çubuğunda ikon olarak: **Takvim** ve **Hareket kütüphanesi**. Hiçbir ekran
erişilemez kalmaz.

### 5.3 Kısayollar (ana ekran) ve anchor haritası
Kullanıcı en fazla 6 kısayol seçebilir (varsayılan: antrenmana başla, aktiviteyi
başlat, öğün ekle, su ekle, takvim, ilerlemem). "Düzenle" moduna geçince
kısayollar açılıp kapatılır, en az biri açık kalır.

| Kısayol | Gider | Kaydırılan bölüm |
|---|---|---|
| Antrenmana başla | Antrenman | plan listesi |
| Hazır programlar | Antrenman | hazır programlar bölümü |
| Aktiviteyi başlat | Aktivite | GPS takip kaplaması açılır |
| Aktivite günlüğüm | Aktivite | günlük listesi |
| Öğün ekle | Beslenme | öğün giriş paneli |
| Su ekle | Beslenme | su/oruç kartı |
| İlerlemem | İlerleme | en üst |
| Takvim | Takvim | en üst |
| Hareket kütüphanesi | Kütüphane | en üst |

Aynı anchor mantığı ana ekrandaki tüm özet kartları için geçerlidir: enerji
kartına basınca beslenmedeki özet bölümüne, adım kartına basınca aktivitedeki
adım bölümüne gidilir.

### 5.4 Akışlar
```
Açılış → (oturum yok) Giriş ekranı
       → (oturum var, profil yok) Onboarding → Ölçüler → 15 soru → AI tarama → Ana Sayfa
       → (oturum var, profil var) Ana Sayfa
```
```
Ana Sayfa → Antrenman → Hareket seç → Oynatıcı → Bitir → Geri bildirim → Uyarlama özeti → Ana Sayfa
```

---

## 6. Ekran ekran tasarım

Her ekran için: amaç, bölüm sırası, etkileşimler, açıklamaların nereye
saklandığı, boş/hata durumları.

### 6.1 Giriş / Kayıt
- Marka kilidi ortada, altında tek satır slogan.
- Segment: **Giriş yap / Kayıt ol**. Alanlar: e-posta, şifre (göz ikonuyla
  göster/gizle), kayıtta ad.
- Sosyal giriş düğmeleri (varsa) altta, ayrıştırıcı çizgiyle.
- "Şifremi unuttum" metin düğmesi.
- Hata mesajı alanın hemen altında, kırmızı, tek satır, alan kırmızı kenarlık alır.
- E-posta doğrulama beklenirken: ayrı bir bilgi ekranı + "Tekrar gönder" (60 sn
  geri sayımlı).
- Gizlilik ve kullanım şartları tek satır bağlantı → **ayrı sayfa** (Kalıp B).

### 6.2 Onboarding — Ölçüler
- Üstte ilerleme çubuğu: `Adım 1/3`.
- Alanlar: ad, doğum tarihi (yaş otomatik hesaplanır ve satırın altında
  gösterilir), cinsiyet (segment), boy, kilo (kg/lb birim seçimi burada başlar).
- Her alan tek satır; klavye açıldığında aktif alan görünür kalır.
- "Neden soruyoruz?" → accordion (Kalıp A), tek paragraf.

### 6.3 Onboarding — 15 soruluk profil testi
Sıra: hedef, motivasyon (serbest metin), engel, deneyim, seviye, son 3 aydaki
sıklık, haftada ayırabileceği gün, seans süresi, ilgi duyulan antrenman türleri,
antrenman yeri, ekipman, sakatlık/ağrı bölgesi, gün içi hareket, uyku, serbest not.

- **Ekranda tek soru.** Üstte `Soru 4 / 15` + ince ilerleme çubuğu.
- Tek seçimli sorular (hedef, deneyim, seviye, sıklık, gün, süre, hareket, uyku):
  büyük dikey seçenek kartları; seçilince lime dolgu ve **otomatik olarak bir
  sonraki soruya geçilir** (120 ms gecikme, geçiş fade+kayma).
- Çok seçimli sorular (engel, ilgi alanı, ekipman, sakatlık): çipler; seçim
  bittiğinde alttaki "Devam" düğmesiyle ilerlenir.
- Serbest metin: çok satırlı alan + "isteğe bağlı" etiketi.
- Alt çubukta "← Geri" ve "Devam →". Geri, cevabı korur.
- **Atlanabilirlik:** isteğe bağlı sorular "Şimdilik geç" metin düğmesi taşır.
- Sakatlık sorusunda kırmızı değil, sakin bir bilgi kutusu: "Bildirdiğin bölgeyi
  program otomatik olarak koruyacak."

### 6.4 AI tarama (plan üretimi)
Tam ekran, markalı, **dört aşamalı** ilerleyen yükleyici. Her aşama sırayla
tikleniyor:
1. `Profil ölçüleri taranıyor` — yaş, boy, kilo, ortam ve ekipman değerlendiriliyor
2. `Spor geçmişi çözümleniyor` — 15 test cevabı, hedef ve ağrı bölgeleri okunuyor
3. `Program kişiselleştiriliyor` — hareket, set, tekrar ve dinlenme seçiliyor
4. `AI verileri taradı` — kişisel programın ve uyarlamaların hazır

AI'ya ulaşılamazsa dördüncü aşama sessizce `Veriler tarandı / Güvenli yerel plan
hazırlandı` olur — kullanıcı hata görmez, plan yine gelir. Bu ekran en fazla 12
saniye görünür; sonrasında plan ekranı açılır.

### 6.5 Ana Sayfa (Bugünün Planı)
Bölüm sırası (yukarıdan aşağı):

1. **Selamlama** — `BUGÜNÜN PLANI · 01` eyebrow, `Furkan, hazır mısın?`
   (ad normal, ikinci kısım vurgulu).
2. **Üç küçük künye** — Vücut kitle indeksi · Hedef · Ortam. Her biri değer +
   tek kelimelik bağlam.
3. **Kısayol ızgarası** — 2×3, ikon + etiket, sağ üstte "Düzenle".
4. **Günlük enerji halkası** — ortada kalan kcal, çevresinde alınan/harcanan/
   hedef. Aşımda turuncu. Karta basınca Beslenme'nin günlük özet bölümüne gider.
   Altında iki küçük satır: `BAZAL ENERJİ · BMR` ve `GÜNLÜK TOPLAM · TDEE`.
5. **Bugünün antrenmanı** — `BUGÜN` eyebrow, `Antrenmanım · Orta seviye`,
   hareket listesi (ilk 4 + "tümünü gör"), her satırda ad, set×tekrar, süre ve
   "Aç" düğmesi. Altında tam genişlikte **"Antrenmana başla"** birincil düğmesi.
   Her hareket satırının altında "3 adımda nasıl yapılır?" → **accordion**.
6. **Aşama göstergesi** — `AŞAMA 2/4` + tek satır açıklama
   ("Tekrar arttı · hacim bloğu") + "3 antrenman sonra bir sonraki aşama açılır."
7. **Planın neden böyle?** — **accordion**. Kapalıyken tek satır soru; açılınca
   AI'nın plan gerekçesi.
8. **Hedefit AI analizi** — durum rozeti (`Optimal`), varsa güvenlik notu.
9. **Fit Koç kartı** — `FORM AI` eyebrow, "Bugün senden **tek bir şey** istiyor:
   Hareketi mükemmel yapmak değil, devam etmek." + imza.
10. **Spor ekle** — kısa kart, "Aç" düğmesi aktivite günlüğü kaplamasını açar.

Boş durum (henüz plan yok): tek kart, "Profil testini tamamla, programını
oluşturalım" + düğme.

### 6.6 Antrenman sekmesi
1. **Bugünün planı** listesi (ana sayfadakinin tamamı, filtresiz).
2. **Plan düzenleyici girişi** — `PROGRAMINI KENDİNE GÖRE AYARLA` eyebrow,
   "Planı düzenle" düğmesi.
3. **Hazır programlar** (`ready-programs` anchor) — kişisel plana alternatif
   hazır programlar; her kart: ad, süre, seviye, "Kullan".
4. **Antrenmanıma eklenenler** — kütüphaneden eklenmiş hareketler.

**Plan düzenleyici (dialog):**
- Her hareket satırı: ad, `Set / Tekrar veya Süre / Dinlenme` sayısal alanlar,
  `▲ ▼` sıralama düğmeleri, "Hareketi değiştir", "Kaldır".
- "Tekrara çevir / Süreye çevir" geçişi.
- "Yeni hareket ekle" → arama alanı.
- Altta: "Otomatik plana dön" · "Vazgeç" · "Değişiklikleri kaydet".
- Üstte tek satır bilgi: "Bu değişiklikler sonraki antrenmana uygulanır."
- En az bir hareket kalmalı; ihlalde satır içi uyarı.

### 6.7 Antrenman oynatıcı (en kritik ekran)
Tam ekran, dikkat dağıtan hiçbir şey yok. Alt sekme çubuğu ve Fit Koç FAB'ı gizlenir.

Düzen (yukarıdan aşağı):
1. `← Plana dön` + üstte ince antrenman ilerleme çubuğu.
2. `HAREKET 3 / 7` etiketi, hareket adı (Headline L).
3. **Hareket animasyonu** — başlangıç/bitiş karelerinin döngüsü, kart içinde.
4. **Durum + sayaç** — `SET 2/4` veya `DİNLENME`; altında büyük sayaç ve durum
   etiketi (`AKTİF SET` / `DİNLENME SÜRÜYOR` / `HAZIR` / `HAREKET TAMAM`).
   Sayacın altında `Sonraki: set 3` ve `4×12 · yaklaşık 68 kcal`.
5. **Set kaydı** — `SETLER` başlığı altında her set için ağırlık ve tekrar
   girişi; tamamlanan setler ✓. Bir önceki performans gri olarak ipucu gösterir.
6. **Teknik rehberi** — `3 ADIMDA UYGULA` **accordion** (kapalı gelir). İçinde
   3 adım + `NEFES` + `SIK HATA` satırları. Uzun teknik anlatım "Hareketi
   ayrıntılı gör" bağlantısıyla **kütüphane detay sayfasına** taşınır (Kalıp B).
7. **Yapışkan alt eylem çubuğu** (cam zemin, güvenli alan destekli):
   - Birincil: `Seti tamamla` / `Dinlenmeyi başlat` / `Seti başlat` (bağlama göre)
   - İkincil: `Duraklat`, `Dinlenmeyi atla`
   - Yan: `← Önceki`, `Hareketi atla →`
   - Son harekette: `✓ Antrenmanı bitir ve kaydet` (turuncu).

Kurallar:
- Ekran açıkken cihaz uykuya geçmez.
- Dinlenme sayacı bitince haptik + kısa ses (ayarlardan kapatılabilir).
- Ağırlık/tekrar alanları sayısal klavye açar, artı/eksi düğmeleri de vardır.
- "Plana dön" basılınca "Antrenman kaydedilmeden çıkılsın mı?" onayı gelir.

### 6.8 Antrenman geri bildirimi (bitişte)
1. **Özet şeridi** — `SÜRE` · `YAKILAN` · `HAREKET` (3/7 biçiminde) ve
   `ÇALIŞILAN BÖLGELER` çipleri.
2. `ANTRENMAN TAMAMLANDI` eyebrow, "Programını bir sonraki **seviyeye
   uyarlayalım.**" ve tek satır gerekçe.
3. **Üç soru, üçü de tek dokunuş:**
   - "Antrenman nasıl hissettirdi?" → Kolay / Uygun / Zor
   - "Antrenman sonrası yorgunluk" → 1–5 ölçek (1 çok düşük, 3 orta, 5 çok yüksek)
   - "Ağrı veya rahatsızlık var mı?" → Yok / Bel / Diz / Omuz / Diğer (çoklu)
4. İsteğe bağlı not alanı.
5. **`SONRAKİ ADIM` özeti** — seçimlere göre anlık değişir:
   "Toparlanma ve güvenlik öncelikli plan" / "Kontrollü yük artışı için veri
   kaydı" / "Mevcut yükü değerlendiren dengeli plan".
6. `Kaydet ve programımı uyarla →`.

Kaydettikten sonra **uyarlama kartı** gösterilir: `ZAMANLA UYARLANAN PROGRAM`,
"Sonraki plan: +1 set · +2 tekrar · -15 sn dinlenme", varsa "Korunan bölgeler:
Diz", ve en fazla 3 gerekçe maddesi. Rekor kırıldıysa öncesinde kutlama kaplaması.

### 6.9 Aktivite sekmesi
1. **Adım kartı** — bugünkü adım, hedef halkası, hedefi düzenleme. Altında
   `Adım geçmişi` (7 gün, dikey bar listesi — yatay kaydırma yok).
2. **Aktif seri** — `AKTİF SERİ / 12 gün`, altında tek satır not.
3. **Hedefit Rota** — `Canlı aktivite takibi` kartı, "Aktiviteyi başlat" birincil
   düğmesi ve "Geçmiş rotalarım" metin düğmesi.
4. **Aktivite günlüğü** — son kayıtlar listesi; her satır: ikon, ad, tarih·süre,
   sağda kcal/adım.
5. **Aktivite ekle (manuel)** — koşu/yürüyüş/bisiklet/yüzme ve diğer sporlar;
   süre, mesafe, yoğunluk, not.
6. **Günlük özet grafiği** — süre/kalori arasında segment geçişi.

**GPS takip kaplaması (tam ekran):**
- Üstte aktivite türü seçimi (çip satırı, tek satıra sığar).
- Harita (rota canlı çizilir).
- Dört istatistik karesi: `SÜRE` · `MESAFE` · `TEMPO/HIZ` · `NABIZ`.
- Alt eylemler: `Aktiviteyi başlat` → `Duraklat / Devam et` → `Bitir`.
- Nabız bandı: "Nabız bandı bağla" düğmesi; yalnız mobilde etkin, web'de neden
  olmadığı tek satırla söylenir.
- **Bitişte özet:** harita önizlemesi, tüm istatistikler, yoğunluk seçimi, kısa
  not, `Aktiviteyi kaydet` ve `Paylaş` (markalı görsel üretir).
- **Kurtarma:** uygulama kapanıp açıldığında yarım kalan takip bulunursa üstte
  bilgi şeridi: "Yarım kalan bir takip bulundu ve geri yüklendi — devam edebilir
  ya da bitirip kaydedebilirsin." Takip ekranı otomatik açılır.
- Rota çok kısaysa paylaşım engellenir ve nedeni yazılır.

### 6.10 Beslenme sekmesi
1. **Gün seçici** — `← Önceki gün · BUGÜN · Sonraki gün →`. İleri tarih kapalı.
2. **Günlük özet** — `BUGÜN ALINAN` büyük kcal + hedefin yüzdesi; altında
   `KALAN` veya `AŞIM`. Aşımda ton turuncuya döner ve mesaj **suçlayıcı olmaz**:
   "Yarın telafi etmeye çalışmak yerine olağan düzenine dön; tek gün haftalık
   ortalamayı bozmaz."
3. **Makro çubukları** — Protein / Karbonhidrat / Yağ, her biri `alınan / hedef`.
4. **Öğün ekle paneli** (`food-entry-panel` anchor):
   - `ÖĞÜN` segmenti: Kahvaltı / Öğle / Akşam / Atıştırmalık
   - `BESİN / ÖĞÜN` metin alanı (örnek: "yoğurt, tavuk göğsü veya yulaf")
   - **Porsiyon birimi**: gram / ml / adet / porsiyon / çay bardağı / su bardağı /
     kupa / tabak / kase — seçilince altında tek satır dönüşüm ipucu
     ("1 su bardağı ≈ 200 g olarak alınıyor").
   - İki eylem: `AI analiz et ve ekle` (birincil) · `Barkod tara` (ikincil)
   - AI sonucu geldiğinde **AI BESİN ANALİZİ** kartı: kcal, protein, karb, yağ,
     lif, mikrolar (şeker, sodyum, kalsiyum, demir, potasyum, C vitamini) ve
     **güven seviyesi** (düşük/orta/yüksek) tek satır uyarıyla. Değerler
     düzenlenebilir; `Analizi günlüğe ekle` ile kaydedilir.
   - Barkod: kamera görünümü veya numara girişi; ürün bulununca değerler 100 g
     üzerinden gelir ve porsiyona göre düzenlenebileceği söylenir.
5. **Su ve oruç kartı** (`hydration-card` anchor) — bardak ikonlarıyla artırma,
   oruç penceresi sayacı.
6. **Günün özeti** — `Öğünlerin` listesi; her satırda ad, gramaj, kcal, kaynak
   rozeti (Barkod / Manuel / AI) ve **görünür sil ikonu** (swipe yok).
7. **AI öğün tavsiyesi** — `Bir sonraki öğünde neye odaklanmalı?` + "Yenile".
   Kaynak notu ve "Tıbbi beslenme tavsiyesi değildir." altta küçük.
8. **Hedefler paneli** — `Enerji ve makro planın`; düzenleme moduna geçilebilir.
   - Hedef türü: Kilo verme / Yağ kaybı / Kilo koruma / Kas kazanımı — her biri
     tek satır ipucu taşır.
   - `TDEE 2450 kcal → günlük hedef 2050 kcal` özeti.
   - **"Bu hedef nasıl hesaplandı?" → accordion**: BMR (Mifflin–St Jeor), TDEE,
     makro mantığı, tahmin notu.
   - **Sağlık notu** ayrı, sakin renkli kutu — teşhis/tedavi önerisi olmadığı,
     hamilelik/emzirme/yeme bozukluğu/diyabet/böbrek durumunda hekime danışılması.
   - BMR altı hedef seçilirse **engelleyici değil, uyarıcı** şerit çıkar.
9. **Haftalık kilo eğilimi** — iki ölçüm yoksa "2 ölçümden 1'i" ilerleme
   göstergesi ve ne zaman ikinci ölçümün ekleneceği.

### 6.11 İlerleme sekmesi
1. Başlık: `İLERLEMEM` / "Furkan, **ritmini gör.**"
2. **Dört istatistik karesi** — `BU HAFTA` tamamlanan antrenman · `TOPLAM SÜRE` ·
   `YAKILAN ENERJİ` (MET tabanlı tahmin) · tamamlama yüzdesi.
3. **4 haftalık sütun grafiği** — antrenman sayısı; sütunlar dikey, etiketler
   `1. hf … 4. hf`.
4. **Aylık rapor** — ay özeti + tek satır sorumluluk reddi.
5. **Kişisel rekorlar** — `Tahmini 1RM · en güçlü hareketlerin`; her satır:
   hareket, en iyi set (`60 kg × 8 tekrar · 6 gün`), tahmini 1RM. Altta tek satır:
   "Epley formülüyle tahmin edilir; gerçek maksimal test değildir." Boşsa ne
   yapılması gerektiğini söyleyen boş durum.
6. **Haftalık AI değerlendirmesi** — dört metrik (antrenman, tamamlama, süre,
   yorgunluk), başlık + özet, `İYİ GİDENLER` ✓ listesi, `DİKKAT` ! listesi,
   `GELECEK HAFTA` numaralı 3 öneri, güvenlik notu. Premium olmayanda "2 haftada
   1" limiti açıkça yazar.
7. **Vücut ölçümleri** — kilo/bel/göğüs/kalça/kol/bacak. Üstte 3 özet kart
   (son değer, fark, yüzde), altında çizgi grafik ve zaman aralığı segmenti
   (7 gün / 30 gün / 90 gün / tümü). `+ Ölçüm ekle` dialog açar.
8. **İlerleme günlüğü** — son antrenmanlar; her satır: tarih, ad, süre, hareket
   sayısı, yorgunluk rozeti.
9. **Aktivite günlüğüne git** bağlantısı.

### 6.12 Takvim
1. `ANTRENMAN TAKVİMİ` / "Haftanı **ritmine göre planla.**"
2. **Haftalık tercihler** — 7 gün çipi (çoklu seçim), antrenman saati seçimi.
3. **Ay görünümü** — gün hücrelerinde nokta: tamamlandı (lime), planlı (kontur),
   kaçırıldı (turuncu). Güne basınca alt kısımda o günün detayı açılır —
   **başka sayfaya atlamaz**, aynı ekranda detay bölümüne kaydırır.
4. **Kaçırılan gün** — "Yeniden planla" düğmesi ve gün seçimi.
5. **Hatırlatıcılar** — bildirim izni durumu, saat, açık/kapalı.

### 6.13 Hareket kütüphanesi
1. `HAREKET KÜTÜPHANESİ` / "Hareketi gör, **formunu öğren.**" + sonuç sayısı.
2. **Filtre bloğu** — `ARA` metin alanı; `KAS GRUBU`, `EKİPMAN`, `SEVİYE`,
   `KATEGORİ` için çip satırları (satırlar sarmalanır, yatay kaydırma yok);
   `Filtreleri temizle`.
3. **Izgara** — 2 sütun (telefon) / 3–4 (masaüstü). Kart: animasyon karesi,
   hareket adı (Türkçe), kas grubu, ekipman rozeti, favori kalbi.
4. `24 hareket daha göster →` düğmesi (sonsuz kaydırma yok).
5. **Hareket detayı — ayrı sayfa (Kalıp B):**
   - Büyük animasyon, hareket adı, `EGZERSİZ VERİ TABANI` künyesi
   - `YARDIMCI KASLAR`, `KATEGORİ`, `MEKANİK`, `KUVVET TÜRÜ` künye ızgarası
   - **`Adım adım nasıl yapılır?`** — burada accordion değil, tam metin; sayfanın
     asıl amacı bu.
   - Eylemler: `▶ Hareketi aç` (tek hareketlik oynatıcı) · `＋ Antrenmanıma ekle`
     · `♡ Favorilere ekle`
   - Alternatif hareket önerileri (ekipmansız / daha kolay varyant)
6. Boş sonuç: "Uygun egzersiz bulunamadı. Arama metnini veya filtrelerden birini
   değiştir."

### 6.14 Profil
Bölümler, her biri kart:
1. **Kimlik** — avatar (değiştir), ad, doğrulanmış e-posta rozeti, `Çıkış yap`.
2. **Ölçüler** — ad, doğum tarihi (yaş otomatik), cinsiyet, boy, kilo, hedef,
   ekipman, istenen hareketler, sakatlık notu. `Değişiklikleri kaydet`.
3. **Ortam** — Evde / Salon segmenti.
4. **Tercihler** — birim (kg/lb), dil (TR/EN), tema (Açık/Koyu/Sistem),
   bildirimler, kısayollar.
5. **Premium** — `PREMIUM` eyebrow; ücretsiz kullanıcıya "Premium'a Geç" ve
   limit karşılaştırması, üyeye "Premium Üye ✓".
6. **Cihazda AI** — `AI Koçu cihazda çalıştır`; model boyutu, indirme ilerlemesi,
   `Modeli indir` / `İndirmeyi durdur` / `Modeli sil`. Desteklenmeyen cihazda
   neden desteklenmediği tek satır. Uzun anlatım → accordion.
7. **Verilerim** — tüm verileri tek JSON dosyası olarak indir.
8. **Planı yenile** — profil değişikliklerini plana uygula.
9. **Testi yeniden çöz** — 15 soruyu baştan.
10. **İlerlemeyi sıfırla** — kırmızı bölge; onay dialogunda yazarak onay + kutucuk.
11. **Hesap yönetimi** — hesabı dondur / hesabı sil; ikisi de ayrı onay akışı,
    silme geri alınamaz uyarısı ve yazarak onay.
12. **Yasal** — Gizlilik, Kullanım şartları, Lisanslar → **ayrı sayfalar**.

### 6.15 Fit Koç (AI sohbet)
- **Giriş:** sağ alt FAB, `Fit Koç` etiketiyle. Basınca alttan yükselen panel
  (telefonda tam ekran, masaüstünde sağda 420 px sütun).
- **Başlık:** `Fit Koç` + alt satır "Programını bilen asistan" + kapat.
- **Boş durum:** "Sana nasıl yardımcı olayım?" + 3 öneri çipi:
  "Bugünkü programımı özetle" · "Bu hareketi nasıl kolaylaştırırım?" ·
  "Dinlenme sürem uygun mu?"
- **Mesajlar:** kullanıcı sağda lime-soft balon, koç solda yüzey balonu.
  Markdown desteklenir (liste, kalın). Yazarken "Koç düşünüyor" animasyonu ve
  `Yanıtı durdur` düğmesi.
- **Her koç yanıtının altında:** 👍 / 👎 geri bildirim ve gerekiyorsa kaynak notu.
- **Kullanım sayacı:** "Bugün 3/5 soru kullanıldı". Limit dolunca iki seçenek:
  `Reklam izle, +1 hak kazan` ve `Premium'a Geç`.
- **Yeni mesaj geldiğinde** otomatik en alta gitmez; bunun yerine `Son mesaja git`
  düğmesi belirir (kullanıcı okuma yerini kaybetmesin).
- **Hafıza:** koç sohbetten kalıcı tercih öğrenir (sevmediğin hareket, ekipman,
  antrenman saati). Bunun için ayrı bir **"Koçun senin hakkında bildikleri"**
  sayfası vardır: her kayıt bir satır (tür, içerik, ne zaman öğrenildi) ve
  **tek tek silinebilir**. Girişi Profil > Tercihler altındadır.
- **Altbilgi:** "Tıbbi tanı yerine geçmez. Ağrı veya yaralanmada sağlık uzmanına
  danış." — küçük, sabit.
- **Hata:** "Fit Koç'a ulaşılamıyor." + `Tekrar dene`. Sağlayıcı çökerse koç
  yine de verilere dayanan güvenli yerel bir öneri döner ve günlük hak iade edilir
  — bu kullanıcıya tek satırla söylenir.

### 6.16 Premium
- İki sütunlu karşılaştırma tablosu: Ücretsiz / Premium.
  - AI koç mesajı: 5/gün → 20/gün
  - Yazarak AI besin tahmini: 3/gün → 30/gün
  - Haftalık AI değerlendirme: 2 haftada 1 → her hafta
  - AI beslenme önerisi: 5/gün → 20/gün
- Fiyat segmenti: ₺89/ay · ₺799/yıl (yıllıkta tasarruf rozeti).
- `Premium'a Geç` birincil düğme. Ödeme henüz aktif değilse bunu dürüstçe
  söyleyen bilgi şeridi.

### 6.17 Genel arama
- Üst çubuktaki arama alanına basınca tam ekran arama açılır.
- Sonuçlar gruplanır: **Ekranlar** (sekme ve bölümler), **Hareketler**,
  **Besinler**.
- Bir sonuca basınca doğrudan o ekrana ve **o bölüme** gidilir (K2). Hareket
  sonucu kütüphanede o hareketin detayını açar.
- Boşken son aramalar ve öneriler.

### 6.18 Bildirimler
- Üst çubukta zil ikonu, okunmamışta lime nokta.
- Panelde: antrenman hatırlatıcısı, seri uyarısı, haftalık değerlendirme hazır,
  hedefe ulaşma. Her satır tıklanınca ilgili ekrana ve bölüme gider.
- `Bildirim ayarları` bağlantısı.

---

## 7. Durumlar, hatalar, limitler

| Durum | Tasarım |
|---|---|
| İlk açılış, veri yok | Her bölümün kendi boş durumu: ikon + tek satır + tek eylem |
| Yükleniyor | İçerik biçiminde iskelet; AI işlemlerinde markalı yükleyici |
| AI limiti doldu | Sayaç kırmızıya döner + iki çıkış: reklam izle / Premium |
| Çevrimdışı | Üstte ince şerit: "Çevrimdışısın — kayıtların cihazda tutuluyor" |
| Eşitlenemedi | Kayıt satırında küçük bulut-çarpı ikonu + "Tekrar dene" |
| Sağlayıcı hatası | Kullanıcıya nazik tek cümle + yerel güvenli sonuç; hak iade edilir |
| İzin reddedildi (konum/kamera/bildirim) | Neden gerektiği tek satır + "Ayarları aç" |
| Düşük bellekli cihaz | "Cihazda AI" kapalı gösterilir, nedeni yazılır |

---

## 8. Erişilebilirlik ve yerelleştirme

- WCAG AA: normal metin ≥ 4.5:1, büyük metin ≥ 3:1. Lime üstü yazı daima koyu.
- Renk tek başına anlam taşımaz: aşım hem turuncu hem "AŞIM" etiketi taşır.
- Tüm ikon düğmelerinde erişilebilir ad; sayaç ve halka için canlı bölge metni
  ("Günlük kalori hedefinden 420 kcal kaldı").
- Klavye ile tam gezinme; dialoglarda odak tuzağı; ESC ile kapanma.
- Dinamik yazı boyutuna (200 %'e kadar) uyum; hiçbir yerde metin kırpılmaz.
- TR/EN: Türkçe metinler İngilizceden **ortalama %25 uzundur**. Tüm düğme ve
  etiketler en uzun Türkçe metinle test edilir; iki satıra düşen düğme kabul
  edilir, kırpılan metin edilmez.
- Tarih, sayı ve birim biçimleri yerel ayara göre (kg/lb, virgül/nokta).

---

## 9. Platform notları

- **Telefon (birincil):** 360–430 px genişlik referans. Alt sekme çubuğu güvenli
  alan üstünde. Klavye açıldığında yapışkan eylem çubuğu klavyenin üstüne çıkar.
- **Tablet / masaüstü:** iki sütunlu düzen — solda ana içerik, sağda özet
  kartları (enerji, seri, koç). Maks 1280 px.
- **Native kabuk:** durum çubuğu rengi temaya uyar; geri tuşu (Android) her zaman
  bir önceki ekrana döner, uygulamayı beklenmedik şekilde kapatmaz.
- Uygulama içi tüm görseller ve yazı tipleri **yereldir** (çevrimdışı çalışır);
  dış CDN'e bağımlılık yok.

---

## 10. Teslim edilecekler

1. Tasarım jetonları dosyası (renk, tipografi, boşluk, yarıçap, gölge) — bölüm 3
   değerleriyle birebir.
2. Bileşen kütüphanesi — bölüm 4'teki 20 bileşen, tüm durumlarıyla, açık + koyu.
3. Ekran tasarımları — bölüm 6'daki her ekran, açık + koyu, telefon + masaüstü.
4. Durum varyantları — bölüm 7'deki her durum en az bir ekranda gösterilmiş.
5. Akış diyagramları — onboarding, antrenman, beslenme kaydı, GPS aktivite.
6. Etkileşim notları — accordion, anchor kaydırma, sayfa geçişi, haptik anları.
7. Erişilebilirlik kontrol listesi — bölüm 8 maddelerinin karşılanma kanıtı.

---

## 11. Kabul kriterleri (tasarım onaydan önce bunlarla denetlenir)

- [ ] Hiçbir ekranda yatay kaydırma, carousel, swipe jesti veya sürükle-bırak yok.
- [ ] Her kısayol ve özet kart, hedef ekranın **doğru bölümüne** gidiyor.
- [ ] Sekmeye geri dönüldüğünde ekran **en üstten** açılıyor.
- [ ] Akışta 3 satırdan uzun paragraf yok; uzun açıklamalar accordion veya ayrı
      sayfada.
- [ ] Her accordion başlığı bir soru ve sağında aşağı ok taşıyor.
- [ ] Lime zemin üzerinde beyaz yazı hiçbir yerde yok.
- [ ] Tüm ekranlar açık ve koyu temada teslim edildi.
- [ ] Yıkıcı her işlem onay adımı taşıyor; ikisi yazarak onay istiyor.
- [ ] Tüm dokunma hedefleri ≥ 48×48 px.
- [ ] Boş / yükleniyor / hata / dolu durumları eksiksiz.
- [ ] En uzun Türkçe metinle hiçbir etiket kırpılmıyor.
