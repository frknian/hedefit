#!/usr/bin/env python3
"""site/*.html (Türkçe kaynak) → site/en/*.html (İngilizce). Çeviriler: scripts/i18n/en.json (Türkçe → İngilizce).

    python3 scripts/i18n/build-en.py           # üretir; çevirisi eksik metinleri listeler
    python3 scripts/i18n/extract.py            # yalnız eksikleri JSON olarak yazar

Türkçe sayfalarda metin değişince eksik çeviri uyarısı çıkar; o metin Türkçe kalır."""
import html, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / "site"
EN = json.loads((ROOT / "scripts/i18n/en.json").read_text(encoding="utf-8"))
LET = re.compile(r"[A-Za-zÇĞİÖŞÜçğıöşü]{2}")

# Türkçe dosya → İngilizce yol (uzantısız, Cloudflare'in temiz URL'leri)
PAGES = {"index.html": "/en/", "planlar.html": "/en/plans", "gizlilik.html": "/en/privacy",
         "destek.html": "/en/support", "hesap-silme.html": "/en/delete-account", "detay.html": "/en/detail"}
TR_PATH = {"index.html": "/", "planlar.html": "/planlar", "gizlilik.html": "/gizlilik",
           "destek.html": "/destek", "hesap-silme.html": "/hesap-silme", "detay.html": "/detay"}
OUT = {"index.html": "index.html", "planlar.html": "plans.html", "gizlilik.html": "privacy.html",
       "destek.html": "support.html", "hesap-silme.html": "delete-account.html", "detay.html": "detail.html"}
LINKS = {"index.html": "/en/", "planlar.html": "/en/plans", "gizlilik.html": "/en/privacy",
         "destek.html": "/en/support", "hesap-silme.html": "/en/delete-account", "detay.html": "/en/detail"}
ASSETS = ("styles.css", "main.js", "anim.js", "phone.js", "watch.js", "page.js", "tools.js", "bento.js", "embed.js")
missing = []
CURRENT = [None]

def tr(text):
    key = re.sub(r"\s+", " ", html.unescape(text)).strip()
    if not key or not LET.search(key): return None
    if key in EN: return EN[key]
    # gizlilik sayfasındaki İngilizce bölümler zaten İngilizce: Türkçe harf/kelime yoksa uyarı verme
    if CURRENT[0] == "gizlilik.html" and not re.search(r"[çğıöşüÇĞİÖŞÜ]", key) and key.lower() not in {"hukuki"}: return None
    missing.append(key); return None

def text_node(m):
    raw = m.group(1)
    lead = raw[:len(raw) - len(raw.lstrip())]; trail = raw[len(raw.rstrip()):]
    e = tr(raw)
    return ">" + lead + (html.escape(e, quote=False) if e is not None else raw.strip()) + trail + "<"

def attr(m):
    e = tr(m.group(2))
    return f'{m.group(1)}="{html.escape(e, quote=True) if e is not None else m.group(2)}"'

def meta(m):
    e = tr(m.group(3))
    return f'{m.group(1)}{m.group(2)}" content="{html.escape(e, quote=True) if e is not None else m.group(3)}"'

def links(h):
    def href(m):
        url = m.group(2)
        frag = ""
        if "#" in url: url, frag = url.split("#", 1); frag = "#" + frag
        if url in LINKS:
            if url == "gizlilik.html": frag = {"#bulten": "#newsletter", "#kvkk-tr": "#kvkk-en"}.get(frag, frag)
            return f'{m.group(1)}="{LINKS[url]}{frag}"'
        if url.startswith("assets/shots/") and (SITE / "assets/shots-en" / url.rsplit("/", 1)[1]).exists():
            return f'{m.group(1)}="/assets/shots-en/{url.rsplit("/", 1)[1]}{frag}"'      # İngilizce arayüzlü ekran görüntüsü
        if url.startswith("assets/") or url in ASSETS: return f'{m.group(1)}="/{url}{frag}"'
        return m.group(0)
    return re.sub(r'\b(href|src)="([^"]*)"', href, h)

def build(name):
    CURRENT[0] = name
    src = (SITE / name).read_text(encoding="utf-8")
    parts = re.split(r"(<script\b.*?</script>|<style\b.*?</style>)", src, flags=re.S)
    out = []
    for p in parts:
        if p.startswith(("<script", "<style")): out.append(p); continue
        if name == "gizlilik.html":                                  # yalnız İngilizce bölümler kalsın
            p = re.sub(r'\n\s*<section data-tr>.*?</section>', '', p, flags=re.S)
            p = re.sub(r'<div class="summary">.*?</div>\n', SUMMARY_EN, p, flags=re.S)
            p = re.sub(r'\n\s*<div class="langs".*?</div>', '', p, flags=re.S)
            p = p.replace('<section data-en hidden>', '<section>')
        p = re.sub(r">([^<>]+)<", text_node, p)
        p = re.sub(r'\b(alt|aria-label|placeholder|title)="([^"]*)"', attr, p)
        p = re.sub(r'(<meta (?:name|property)=")((?:description|og:title|og:description|twitter:title|twitter:description))" content="([^"]*)"', meta, p)
        out.append(p)
    h = "".join(out)
    h = h.replace('<html lang="tr">', '<html lang="en">').replace('content="tr_TR"', 'content="en_US"')
    # canonical ve og:url İngilizce adrese; hreflang alternatifleri (tr/en/x-default) olduğu gibi kalır
    h = re.sub(r'(<link rel="canonical" href="__SITE_URL__)[^"]*"', lambda m: m.group(1) + PAGES[name] + '"', h)
    h = re.sub(r'(<meta property="og:url" content="__SITE_URL__)[^"]*"', lambda m: m.group(1) + PAGES[name] + '"', h)
    # dil değiştirici: Türkçe kaynaktaki EN bağlantısı İngilizce sayfada TR'ye döner
    h = re.sub(r'<a class="lang-switch"[^>]*>EN</a>', f'<a class="lang-switch" href="{TR_PATH[name]}" hreflang="tr" lang="tr">TR</a>', h)
    h = links(h)
    return h

SUMMARY_EN = '''<div class="summary">
    <b>In short</b>
    <ul><li>We never sell your data.</li><li>We don't use health data for ads.</li><li>You can delete your account and data at any time.</li><li>Location is used only while you record a route.</li></ul>
  </div>
'''

if __name__ == "__main__":
    (SITE / "en").mkdir(exist_ok=True)
    for name in PAGES:
        (SITE / "en" / OUT[name]).write_text(build(name), encoding="utf-8")
    uniq = list(dict.fromkeys(missing))
    print(f"üretildi: {len(PAGES)} sayfa; çevirisi eksik {len(uniq)} metin")
    for m in uniq[:40]: print("  -", m[:110])
