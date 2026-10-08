// Tanıtım sitesi Worker'ı: statik dosyaları sunar; "yayınlanınca haber ver" formunu karşılar.
// Depolama: Workers KV (SUBSCRIBERS). Yalnız e-posta, kayıt zamanı ve rastgele çıkış kodu saklanır.

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" },
});
const page = (title, text) => new Response(
  `<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex"><title>${title}</title></head><body style="font-family:system-ui,sans-serif;background:#0a0a0a;color:#f4f6f8;display:grid;place-items:center;min-height:100vh;margin:0;text-align:center"><main><h1 style="margin:0 0 8px">${title}</h1><p style="color:#97a1ae">${text}</p><p><a style="color:#6fe02a" href="/">Ana sayfa</a></p></main></body></html>`,
  { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" } },
);

const EMAIL = /^[^\s@]{1,64}@[^\s@]{1,255}\.[^\s@]{2,}$/;
const hex = (bytes) => [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");

async function sha256(text) {
  return hex(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))));
}

async function subscribe(request, env, url) {
  if (request.method !== "POST") return json({ error: "method" }, 405);
  const origin = request.headers.get("origin");
  if (origin && new URL(origin).origin !== url.origin) return json({ error: "origin" }, 403);
  if (!(request.headers.get("content-type") || "").includes("application/json")) return json({ error: "type" }, 415);
  const text = await request.text();
  if (text.length > 2048) return json({ error: "size" }, 413);
  let body;
  try { body = JSON.parse(text); } catch { return json({ error: "json" }, 400); }

  if (typeof body.website === "string" && body.website.trim()) return json({ ok: true }); // bot tuzağı: sessizce yut
  if (body.consent !== true) return json({ error: "consent" }, 400);
  const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
  if (email.length > 254 || !EMAIL.test(email)) return json({ error: "email" }, 400);

  // Basit hız sınırı: IP'nin özeti (ham IP saklanmaz), 60 sn içinde en çok 5 deneme.
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  const rlKey = `rl:${(await sha256(ip)).slice(0, 24)}`;
  const tries = parseInt((await env.SUBSCRIBERS.get(rlKey)) || "0", 10);
  if (tries >= 5) return json({ error: "rate" }, 429);
  await env.SUBSCRIBERS.put(rlKey, String(tries + 1), { expirationTtl: 60 });

  const key = `sub:${email}`;
  if (!(await env.SUBSCRIBERS.get(key))) {
    const token = hex(crypto.getRandomValues(new Uint8Array(16)));
    await env.SUBSCRIBERS.put(key, JSON.stringify({ at: new Date().toISOString(), t: token, src: "site" }));
  }
  return json({ ok: true }); // zaten kayıtlı olsa da aynı yanıt: listeyi sızdırmaz
}

async function unsubscribe(request, env, url) {
  const email = (url.searchParams.get("e") || "").trim().toLowerCase();
  const token = url.searchParams.get("t") || "";
  const raw = email && token ? await env.SUBSCRIBERS.get(`sub:${email}`) : null;
  if (raw) {
    try {
      if (JSON.parse(raw).t === token) await env.SUBSCRIBERS.delete(`sub:${email}`);
      else return page("Bağlantı geçersiz", "Çıkış bağlantısı doğrulanamadı.");
    } catch { return page("Bağlantı geçersiz", "Çıkış bağlantısı doğrulanamadı."); }
  }
  return page("Listeden çıkarıldın", "Bu e-posta adresine yayın bildirimi gönderilmeyecek.");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/api/subscribe") return subscribe(request, env, url);
    if (url.pathname === "/api/unsubscribe") return unsubscribe(request, env, url);
    return env.ASSETS.fetch(request);
  },
};
