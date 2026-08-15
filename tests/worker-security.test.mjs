import assert from "node:assert/strict";
import test from "node:test";

// Gerçek derlenmiş Worker'ı çalıştırıp uçtan uca davranışı sınar (rendered-html.test.mjs'teki
// dispatch() ile aynı desen). npm test önce `npm run build` çalıştırdığı için dist/server/index.js
// güncel kalır; bu dosyayı doğrudan `node --test` ile çalıştırıyorsan önce `npm run build` gerekir.
async function dispatch(request, env = {}) {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    request,
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) }, ...env },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

const PROJECT_REF_HEADER = { "x-supabase-project-ref": "dqhsrabrbizwapkawmnf" };
const REJECTED_STATUSES = new Set([403, 404]); // allowlist/bilinmeyen proje reddi

// worker/supabase-proxy.ts: allowlist bypass — %2e%2e yol geçişi.
//
// ÖNEMLİ BULGU (bu testler yazılırken ortaya çıktı): WHATWG URL ayrıştırıcısı
// (new URL()), pathname'i okurken ".."/"%2e%2e" segmentlerini RFC 3986 "remove
// dot segments" algoritmasına göre ZATEN çözüyor — kodlanmış olsun ya da
// olmasın. Yani `incomingUrl.pathname` bu koda ulaştığında olası bir "../"
// dizisi zaten normalize edilmiş oluyor; worker/supabase-proxy.ts'teki
// decodedPathname kontrolü bu yüzden PRATİKTE hiç tetiklenmiyor (aşağıdaki
// "edge case" testi bunu kanıtlıyor: 401 dönüyor, yani istek allowlist'i
// GEÇİP gerçek Supabase'e ulaşıyor — decode edip "%2e%2e" gördüğü için değil,
// path zaten "rest/v1/profiles"a normalize olduğu için). Kontrol yine de
// zararsız bir savunma katmanı olarak kalıyor (bkz. üçüncü test: kodlanmamış
// "../" ile allowlist DIŞINA çıkma denemesi de normalize sonrası 404 alıyor).
// Bu iki test allowlist'i GEÇEN istekleri sınıyor; geçtikten sonra kod gerçek
// Supabase'e fetch atar. Canlı ağa bağımlı kalmamak için global fetch'i taklit
// ediyoruz — yalnız isteğin doğru (normalize edilmiş) hedefe gittiğini
// doğrulamak yeterli, gerçek bir yanıt gerekmiyor.
function withUpstreamFetchSpy() {
  const calls = [];
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async (input) => {
    calls.push(String(input));
    return new Response("{}", { status: 200, headers: { "content-type": "application/json" } });
  };
  return { calls, restore: () => { globalThis.fetch = previousFetch; } };
}

test("supabase-proxy: normal durum — izinli bir uç nokta allowlist'i geçer", async () => {
  const spy = withUpstreamFetchSpy();
  try {
    const response = await dispatch(new Request("http://localhost/api/supabase-proxy/rest/v1/profiles", {
      headers: PROJECT_REF_HEADER,
    }));
    assert.ok(!REJECTED_STATUSES.has(response.status), `beklenmedik ret: ${response.status}`);
    assert.ok(spy.calls.some((url) => url === "https://dqhsrabrbizwapkawmnf.supabase.co/rest/v1/profiles"));
  } finally {
    spy.restore();
  }
});

test("supabase-proxy: edge case — %2e%2e ile kodlanmış yol geçişi de, çözüldükten sonra hâlâ izinli bir uca düşer", async () => {
  const spy = withUpstreamFetchSpy();
  try {
    const response = await dispatch(new Request(
      "http://localhost/api/supabase-proxy/auth/v1/%2e%2e/%2e%2e/rest/v1/profiles",
      { headers: PROJECT_REF_HEADER },
    ));
    // new URL() bu path'i "/api/supabase-proxy/rest/v1/profiles"a normalize
    // ediyor (yukarıdaki not) — allowlist içinde kalıyor, proxy prefix'inin
    // DIŞINA çıkamıyor. Reddedilmemesi güvenlik açığı değil: normalize sonrası
    // yol hâlâ ALLOWED_PREFIXES içinde, upstream isteği de bunu kanıtlıyor.
    assert.ok(!REJECTED_STATUSES.has(response.status), `beklenmedik ret: ${response.status}`);
    assert.ok(spy.calls.some((url) => url === "https://dqhsrabrbizwapkawmnf.supabase.co/rest/v1/profiles"));
  } finally {
    spy.restore();
  }
});

test("supabase-proxy: hatalı input — kodlanmamış '../' ile allowlist dışına (pg/) çıkma denemesi reddedilir", async () => {
  const response = await dispatch(new Request(
    "http://localhost/api/supabase-proxy/auth/v1/../../pg/query",
    { headers: PROJECT_REF_HEADER },
  ));
  // Normalize sonrası pathname "/api/supabase-proxy/pg/query" olur — "pg/"
  // ALLOWED_PREFIXES'te yok, 404 beklenir.
  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { error: "Unsupported Supabase endpoint." });
});

test("supabase-proxy: hatalı input — bozuk yüzde kodlaması (%) Worker'ı çökertmeden reddedilir", async () => {
  // decodeURIComponent("%") RangeError/URIError fırlatır; safeDecodeURIComponent
  // bunu yutup null döndürmeli. new URL() bu geçersiz kodlamayı pathname'e
  // olduğu gibi bırakır (normalize edemez), bu yüzden burada gerçekten
  // decodedPathname kontrolü devreye girer.
  const response = await dispatch(new Request(
    "http://localhost/api/supabase-proxy/rest/v1/%",
    { headers: PROJECT_REF_HEADER },
  ));
  assert.equal(response.status, 404);
});

test("supabase-proxy: hatalı input — bilinmeyen proje referansı her zaman reddedilir", async () => {
  const response = await dispatch(new Request("http://localhost/api/supabase-proxy/rest/v1/profiles", {
    headers: { "x-supabase-project-ref": "baska-bir-proje" },
  }));
  assert.equal(response.status, 403);
});

// worker/index.ts: /_vinext/image dönüşümü başarısız olunca (bu test ortamında
// env.IMAGES hiç bağlı değil) orijinal dosyayı servis eden yedek yol.
//
// BULGU: vinext'in handleImageOptimization() fonksiyonu "url" parametresini
// KENDİSİ doğruluyor ve protokol-göreli/eksik değerleri 400 ile doğrudan
// reddediyor — worker/index.ts'teki catch bloğuna (ve oradaki
// source.startsWith("//") kontrolüne) hiç ulaşılmıyor. Yani bu istismar yolu
// zaten üst kütüphane tarafından kapatılmış; worker/index.ts'teki kontrol şu an
// için erişilemeyen ama zararsız bir ikinci savunma katmanı.
test("image fallback: normal durum — aynı kaynaklı (/ ile başlayan) yol, dönüşüm başarısız olunca yedek yoldan servis edilir", async () => {
  const response = await dispatch(new Request("http://localhost/_vinext/image?url=%2Flogo.png&w=64&q=75"));
  // env.ASSETS.fetch stub'ı 404 döndürüyor; önemli olan isteğin reddedilmeden
  // (400 almadan) fetchAsset'e kadar ulaşması.
  assert.equal(response.status, 404);
});

test("image fallback: hatalı input — protokol-göreli (//host/...) url parametresi reddedilir", async () => {
  const response = await dispatch(new Request("http://localhost/_vinext/image?url=%2F%2Fevil.example.com%2Fx.png&w=64&q=75"));
  // 400: vinext'in kendi doğrulaması. Kritik olan davranış — yabancı bir
  // origin'e ASLA proxy yapılmaması — her iki durumda da (400 ya da
  // worker/index.ts'teki 404) korunuyor.
  assert.equal(response.status, 400);
});

test("image fallback: edge case — kaynak parametresi hiç yoksa da güvenle reddedilir", async () => {
  const response = await dispatch(new Request("http://localhost/_vinext/image?w=64&q=75"));
  assert.equal(response.status, 400);
});
