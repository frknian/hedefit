#!/usr/bin/env python3
"""site/*.html içindeki çevrilecek Türkçe metinleri (metin düğümleri + alt/aria-label/placeholder/title/meta) çıkarır.
   python3 scripts/i18n/extract.py            → eksik çeviri listesi (en.json'da olmayanlar)"""
import json, re, sys
from html.parser import HTMLParser
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
PAGES = ["index.html", "detay.html", "planlar.html", "destek.html", "hesap-silme.html"]
KEEP_ATTR = {"alt", "aria-label", "placeholder", "title"}
META = {("name", "description"), ("property", "og:title"), ("property", "og:description"), ("name", "twitter:title"), ("name", "twitter:description")}
LET = r"[A-Za-zÇĞİÖŞÜçğıöşü]{2}"

class P(HTMLParser):
    def __init__(s): super().__init__(convert_charrefs=True); s.t = []; s.skip = 0
    def handle_starttag(s, tag, attrs):
        if tag in ("script", "style"): s.skip += 1
        d = dict(attrs)
        for k, v in attrs:
            if v and k in KEEP_ATTR and re.search(LET, v): s.t.append(v)
        if tag == "meta" and "content" in d and any((k, d.get(k)) in META for k in ("name", "property")) : s.t.append(d["content"])
    def handle_endtag(s, tag):
        if tag in ("script", "style"): s.skip -= 1
    def handle_data(s, data):
        if s.skip: return
        v = data.strip()
        if v and re.search(LET, v): s.t.append(re.sub(r"\s+", " ", v))

def strings():
    out = []
    for f in PAGES:
        p = P(); p.feed((ROOT / "site" / f).read_text(encoding="utf-8")); out += p.t
    return list(dict.fromkeys(out))

if __name__ == "__main__":
    en = json.loads((ROOT / "scripts/i18n/en.json").read_text(encoding="utf-8")) if (ROOT / "scripts/i18n/en.json").exists() else {}
    missing = [s for s in strings() if s not in en]
    print(json.dumps(missing, ensure_ascii=False, indent=0))
    print(f"# toplam {len(strings())}, çevrilmiş {len(strings()) - len(missing)}, eksik {len(missing)}", file=sys.stderr)
