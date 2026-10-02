#!/usr/bin/env python3
"""site/gizlilik.html ve site/destek.html dosyalarını üretir.

Gizlilik politikası ve KVKK metni uygulamadaki tek kaynaktan
(android/.../ui/screens/LegalTexts.kt) okunur; böylece web sayfası ile
uygulama içi metin ayrışmaz. LegalTexts.kt değişince şunu çalıştır:

    python3 scripts/build-legal-pages.py
"""
import html, re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = (ROOT / "android/app/src/main/java/com/hedefit/app/ui/screens/LegalTexts.kt").read_text(encoding="utf-8")
CONTROLLER = re.search(r'LEGAL_CONTROLLER = "([^"]+)"', SRC).group(1)
CONTACT = re.search(r'LEGAL_CONTACT = "([^"]+)"', SRC).group(1)
UPDATED_TR, UPDATED_EN = "1 Ekim 2026", "1 October 2026"


def unesc(s):
    return (s.replace('\\"', '"').replace("\\n", "\n")
            .replace("$LEGAL_CONTROLLER", CONTROLLER).replace("$LEGAL_CONTACT", CONTACT))


def parse(name):
    body = re.search(r"val " + name + r" = listOf\((.*?)\n\)\n", SRC, re.S).group(1)
    out = []
    for m in re.finditer(r"LegalSection\(\s*(.*?)\n    \),", body, re.S):
        lits = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
        out.append((unesc(lits[0]), unesc("".join(lits[1:]))))
    return out


def link(s):
    return s.replace(CONTACT, f'<a href="mailto:{CONTACT}">{CONTACT}</a>')


def render(secs):
    out = []
    for title, text in secs:
        parts, bullets = [], []
        for line in text.split("\n"):
            if line.startswith("• "):
                bullets.append(f"<li>{link(html.escape(line[2:]))}</li>")
                continue
            if bullets:
                parts.append("<ul>" + "".join(bullets) + "</ul>"); bullets = []
            if line.strip():
                parts.append(f"<p>{link(html.escape(line))}</p>")
        if bullets:
            parts.append("<ul>" + "".join(bullets) + "</ul>")
        out.append(f"<h3>{html.escape(title)}</h3>" + "".join(parts))
    return "\n".join(out)


HEAD = """<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title} — Hedefit</title>
<meta name="description" content="{desc}">
<meta name="theme-color" content="#0a0a0a">
<link rel="canonical" href="__SITE_URL__{path}">
<meta property="og:site_name" content="Hedefit">
<meta property="og:locale" content="tr_TR">
<meta property="og:type" content="website">
<meta property="og:title" content="{title} — Hedefit">
<meta property="og:description" content="{desc}">
<meta property="og:url" content="__SITE_URL__{path}">
<meta property="og:image" content="__SITE_URL__/assets/brand/og.png">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="/favicon.ico" sizes="48x48">
<link rel="icon" type="image/png" href="assets/brand/favicon.png">
<link rel="apple-touch-icon" href="assets/brand/apple-touch-icon.png">
<link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:ital,wght@1,800&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="styles.css">
</head>
<body class="legal">
<header class="nav"><a class="brand" href="index.html"><img src="assets/brand/icon.png" alt="" width="36" height="36"><span>HEDEFIT</span></a><a class="btn btn-sm" href="index.html">Ana sayfa</a></header>
"""
FOOT = """<footer class="footer"><div class="wrap foot">
<nav aria-label="Alt menü"><a href="index.html">Ana sayfa</a><a href="planlar.html">Planlar</a><a href="gizlilik.html">Gizlilik</a><a href="destek.html">Destek</a><a href="hesap-silme.html">Hesap silme</a></nav>
<p>© <span id="yr">2026</span> Hedefit</p></div></footer>
<script src="page.js"></script>
"""

privacy = f"""{HEAD.format(path="/gizlilik", title="Gizlilik politikası", desc="Hedefit gizlilik politikası ve KVKK aydınlatma metni: hangi verileri işliyoruz, kimlerle paylaşıyoruz, haklarını nasıl kullanırsın.")}
<main class="doc">
  <p class="eyebrow">Hukuki</p>
  <h1>Gizlilik <span class="hl">politikası</span></h1>
  <div class="langs" role="group" aria-label="Dil"><button type="button" data-lang="tr" aria-pressed="true">Türkçe</button><button type="button" data-lang="en" aria-pressed="false">English</button></div>

  <div class="summary">
    <b data-tr>Kısaca</b><b data-en hidden>In short</b>
    <ul data-tr><li>Verilerini satmayız.</li><li>Sağlık verilerini reklam için kullanmayız.</li><li>İstediğin an hesabını ve verilerini silebilirsin.</li><li>Konum yalnızca rota kaydı sırasında kullanılır.</li></ul>
    <ul data-en hidden><li>We never sell your data.</li><li>We don't use health data for ads.</li><li>You can delete your account and data at any time.</li><li>Location is used only while you record a route.</li></ul>
  </div>

  <section data-tr>
    <p class="meta">Son güncelleme: {UPDATED_TR} · <a href="#kvkk-tr">KVKK Aydınlatma Metni</a></p>
    <h2>Gizlilik politikası</h2>
    {render(parse("PRIVACY_POLICY"))}
    <h2 id="kvkk-tr">KVKK Aydınlatma Metni</h2>
    {render(parse("KVKK_NOTICE"))}
    <h2 id="bulten">Web sitesi: yayın bildirimi listesi</h2>
    <p>Sitedeki “Yayınlanınca haber ver” formunu doldurursan, yalnızca <b>e-posta adresin</b>, kayıt zamanın ve rastgele bir çıkış kodun saklanır. Amaç: Hedefit mağazalarda yayınlandığında sana tek bir e-posta ile haber vermek. Hukuki sebep: açık rızan.</p>
    <p>Veri, Cloudflare altyapısında (Workers KV) tutulur ve başka kimseyle paylaşılmaz; başka bir amaçla kullanılmaz. Gönderim denemelerini sınırlamak için IP adresinin yalnızca özeti ve yalnızca 60 saniye süreyle kullanılır. Veriyi, bildirimi gönderene veya senin çıkmana kadar saklarız. Çıkmak için bildirim e-postasındaki bağlantıyı kullanabilir ya da <a href="mailto:{CONTACT}">{CONTACT}</a> adresine yazabilirsin.</p>
  </section>

  <section data-en hidden>
    <p class="meta">Last updated: {UPDATED_EN} · <a href="#kvkk-en">KVKK Privacy Notice</a></p>
    <h2>Privacy policy</h2>
    {render(parse("PRIVACY_POLICY_EN"))}
    <h2 id="kvkk-en">KVKK Privacy Notice</h2>
    {render(parse("KVKK_NOTICE_EN"))}
    <h2 id="newsletter">Website: launch notification list</h2>
    <p>If you submit the “Notify me at launch” form on the website, only your <b>email address</b>, the time of signup and a random unsubscribe code are stored, to email you once when Hedefit is published on the app stores. Legal basis: your explicit consent. The data is kept on Cloudflare (Workers KV), is not shared and is not used for anything else. To limit abuse, only a hash of your IP address is used, for 60 seconds. We keep it until the notification is sent or you opt out, via the link in the email or by writing to <a href="mailto:{CONTACT}">{CONTACT}</a>.</p>
  </section>
</main>
{FOOT}
</body></html>
"""

support = f"""{HEAD.format(path="/destek", title="Destek", desc="Hedefit destek: hesap, giriş, izinler, Fit Koç hakları ve veri talepleri için sık sorulanlar ve iletişim.")}
<main class="doc">
  <p class="eyebrow">Destek</p>
  <h1>Size nasıl <span class="hl">yardımcı</span> olalım?</h1>
  <p class="lead">Aşağıdaki sık sorulanlarda cevabını bulamazsan bize yaz.</p>

  <div class="contact">
    <div><small>E-POSTA</small><a href="mailto:{CONTACT}?subject=Hedefit%20destek">{CONTACT}</a></div>
    <p>Yazarken hesabına kayıtlı e-posta adresini kullan ve mümkünse uygulama sürümünü, cihaz modelini ve sorunun ne olduğunu ekle. Ekran görüntüsü çok yardımcı olur.</p>
  </div>

  <h2>Sık sorulanlar</h2>
  <details open><summary>Giriş yapamıyorum</summary>
    <p>E-posta adresini ve şifreni kontrol et. Google ile kayıt olduysan yine <b>Google ile devam et</b> seçeneğini kullan. Şifreni hatırlamıyorsan ya da hesabına erişemiyorsan hesabına kayıtlı e-posta adresinden bize yaz.</p></details>
  <details><summary>Hesabımı nasıl silerim?</summary>
    <p>Uygulamada <b>Profil ve ayarlar → Hesabı kalıcı sil</b> yolunu izle. Ayrıntılar için <a href="hesap-silme.html">hesap silme sayfasına</a> bak. İstersen önce hesabını dondurabilir ya da yalnızca ilerleme verilerini sıfırlayabilirsin.</p></details>
  <details><summary>Verilerimi öğrenmek, düzeltmek veya silmek istiyorum (KVKK)</summary>
    <p>Bu talepleri hesabına kayıtlı e-posta adresinden <a href="mailto:{CONTACT}?subject=KVKK%20ba%C5%9Fvurusu">{CONTACT}</a> adresine iletebilirsin. Başvurular en geç 30 gün içinde ücretsiz sonuçlandırılır. Ayrıntılar için <a href="gizlilik.html#kvkk-tr">KVKK Aydınlatma Metni</a>.</p></details>
  <details><summary>Fit Koç’a soru hakkım bitti</summary>
    <p>Fit Koç’un günlük soru hakkı planına göre değişir (Misafir 3, Free 5, Plus 20, Premium 40) ve her gün yenilenir. Ücretsiz hesaplarda görevlerden kazandığın XP bu hakkı artırır (300 XP’de +1, 500 XP’de +2, sonrasında her 250 XP’de bir ek hak). Daha yüksek hak için <a href=\"planlar.html\">Plus ve Premium planlarına</a> bak.</p></details>
  <details><summary>Rota kaydı veya GPS çalışmıyor</summary>
    <p>Konum iznini <b>her zaman</b> ya da <b>uygulama kullanılırken</b> olarak ver ve cihazının pil tasarrufu ayarında Hedefit’i kısıtlama. Rota kaydı arka planda sürer; kaydı bitirene kadar bildirimi kapatma. Kapalı alanda ya da zayıf sinyalde nokta sayısı azalabilir.</p></details>
  <details><summary>Adım, uyku veya kilo verilerim görünmüyor</summary>
    <p>Hedefit bu verileri Health Connect üzerinden okur. Telefonunda Health Connect’in kurulu olduğundan ve <b>Ayarlar → Health Connect</b> bölümünde Hedefit’e adım, uyku, kilo, aktif kalori ve nabız izinlerini verdiğinden emin ol. İzni istediğin zaman kapatabilirsin.</p></details>
  <details><summary>Bildirim almıyorum</summary>
    <p>Telefonun ayarlarında Hedefit için bildirimlere izin verildiğini ve uygulama içindeki hatırlatma tercihlerinin açık olduğunu kontrol et.</p></details>
  <details><summary>Üyeliğimi nasıl iptal ederim?</summary>
    <p>Üyelikler Google Play ya da App Store üzerinden yönetilir. İptal için ilgili mağazanın <b>Abonelikler</b> bölümünden Hedefit’i seçebilirsin. Hesabı silmek aboneliği otomatik iptal etmeyebilir; önce mağazadan iptal et.</p></details>
  <details><summary>Bir hata buldum ya da öneride bulunmak istiyorum</summary>
    <p>Bize e-posta ile yaz; hatanın nasıl oluştuğunu adım adım anlat. Her öneriyi okuruz.</p></details>

  <p class="note">Hedefit tıbbi tavsiye vermez. Bir sağlık sorunun varsa programa başlamadan önce uzmana danış.</p>
</main>
{FOOT}
</body></html>
"""

(ROOT / "site/gizlilik.html").write_text(privacy, encoding="utf-8")
(ROOT / "site/destek.html").write_text(support, encoding="utf-8")
print("ok")
