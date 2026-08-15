import assert from "node:assert/strict";
import test from "node:test";
import { authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv } from "./helpers/auth.mjs";

// account/delete ve account/reset-progress geri alınamaz işlemler; öncesinde
// paylaşılan authenticateRequest()'i (e-posta doğrulama zorunluluğu dahil)
// tekrar yazan kendi bearerToken/getUser kopyalarını kullanıyorlardı ve hiçbir
// hız sınırı yoktu. Bu testler artık authenticateRequest + dar hız sınırının
// gerçekten devrede olduğunu davranışsal olarak doğruluyor.
//
// rateLimit anahtarları (`account-delete:${userId}`, `account-reset-progress:${userId}`)
// dosya boyunca paylaşılan tekil bir Map'te tutuluyor (bkz. lib/rate-limit.ts).
// Testler birbirinin sayacını etkilemesin diye HER TEST kendi tekil sahte
// kullanıcı kimliğini kullanır.
let nextUserId = 1;
function freshUserId() {
  nextUserId += 1;
  return `00000000-0000-4000-8000-${String(nextUserId).padStart(12, "0")}`;
}

function withSecretKeyEnv() {
  const previous = process.env.SUPABASE_SECRET_KEY;
  process.env.SUPABASE_SECRET_KEY = "test-secret-key";
  return () => { if (previous === undefined) delete process.env.SUPABASE_SECRET_KEY; else process.env.SUPABASE_SECRET_KEY = previous; };
}

function withAccountDeleteFetch(userId, { avatarNames = [] } = {}) {
  let listCalls = 0;
  return withAuthenticatedFetch((url, init) => {
    const href = String(url);
    if (href.includes("/storage/v1/object/list/")) {
      listCalls += 1;
      return Response.json(listCalls === 1 ? avatarNames.map((name) => ({ name })) : []);
    }
    if (href.includes("/storage/v1/object/profile-avatars")) return Response.json({ message: "ok" });
    if (href.includes("/auth/v1/admin/users/")) return Response.json({ user: { id: userId } });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
}

function withResetProgressFetch(userId) {
  return withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rest/v1/")) return new Response(null, { status: 204 });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
}

// ---- account/delete -------------------------------------------------------

test("account/delete: normal durum — doğru onay ve e-posta ile hesap silinir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAccountDeleteFetch(freshUserId());
  try {
    const { POST } = await import(`../app/api/account/delete/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/account/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "HESABIMI SİL", email: "test@example.com" }),
    }));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { deleted: true });
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/delete: normal durum — 100'den fazla avatar sayfalanarak tamamen silinir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  const userId = freshUserId();
  const removedBatches = [];
  let listCalls = 0;
  globalThis.fetch = withAuthenticatedFetch((url, init) => {
    const href = String(url);
    if (href.includes("/storage/v1/object/list/")) {
      listCalls += 1;
      // İlk sayfa tam AVATAR_LIST_PAGE_SIZE (100) döner, ikinci sayfa 1 tane
      // daha, üçüncüsü boş — sayfalamanın gerçekten devam ettiğini kanıtlar.
      if (listCalls === 1) return Response.json(Array.from({ length: 100 }, (_, i) => ({ name: `avatar-${i}.png` })));
      if (listCalls === 2) return Response.json([{ name: "avatar-100.png" }]);
      return Response.json([]);
    }
    if (href.includes("/storage/v1/object/profile-avatars")) {
      removedBatches.push(JSON.parse(String(init.body)).prefixes.length);
      return Response.json({ message: "ok" });
    }
    if (href.includes("/auth/v1/admin/users/")) return Response.json({ user: { id: userId } });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { POST } = await import(`../app/api/account/delete/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/account/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "HESABIMI SİL", email: "test@example.com" }),
    }));
    assert.equal(response.status, 200);
    assert.deepEqual(removedBatches, [100, 1], "iki sayfa da (100 + 1) silinmeli, ilk sayfada durulmamalı");
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/delete: edge case — avatar temizliği kalıcı olarak başarısız olsa bile hesap yine de silinir", async () => {
  // Regresyon testi: eskiden list()/remove() hatası tüm silme isteğini
  // 500 ile reddediyordu — geri alınamaz, yasal olarak önemli bir hakkı
  // ilgisiz bir depolama hatasına bağımlı kılıyordu. Şimdi bir kaç deneme
  // sonrası hâlâ başarısızsa loglanıp deleteUser'a yine de devam edilmeli.
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  const userId = freshUserId();
  let deleteUserCalled = false;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/storage/v1/object/list/")) return Response.json({ message: "internal error" }, { status: 500 });
    if (href.includes("/auth/v1/admin/users/")) { deleteUserCalled = true; return Response.json({ user: { id: userId } }); }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { POST } = await import(`../app/api/account/delete/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/account/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "HESABIMI SİL", email: "test@example.com" }),
    }));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { deleted: true });
    assert.equal(deleteUserCalled, true, "avatar temizliği başarısız olsa da deleteUser çağrılmalı");
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/delete: edge case — saatte 3 denemeden fazlası hız sınırına takılır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  const userId = freshUserId();
  globalThis.fetch = withAccountDeleteFetch(userId);
  try {
    const { POST } = await import(`../app/api/account/delete/route.ts?test=${Date.now()}`);
    const makeRequest = () => POST(authorizedRequest("http://localhost/api/account/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "yanlış" }), // onay yanlış olsa da hız sınırı SAYAÇTAN önce çalışır
    }));
    const statuses = [];
    for (let i = 0; i < 4; i += 1) statuses.push((await makeRequest()).status);
    assert.deepEqual(statuses, [400, 400, 400, 429], "4. denemede hız sınırı devreye girmeli");
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/delete: hatalı input — yanlış onay ifadesi veya eşleşmeyen e-posta reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAccountDeleteFetch(freshUserId());
  try {
    const { POST } = await import(`../app/api/account/delete/route.ts?test=${Date.now()}`);
    const wrongPhrase = await POST(authorizedRequest("http://localhost/api/account/delete", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "hesabımı sil", email: "test@example.com" }),
    }));
    assert.equal(wrongPhrase.status, 400);

    const wrongEmail = await POST(authorizedRequest("http://localhost/api/account/delete", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "HESABIMI SİL", email: "baskasi@example.com" }),
    }));
    assert.equal(wrongEmail.status, 400);
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/delete: hatalı input — kimliği doğrulanmamış istek Supabase'e hiç gitmeden reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new TypeError("beklenmeyen ağ isteği"); };
  try {
    const { POST } = await import(`../app/api/account/delete/route.ts?test=${Date.now()}`);
    const response = await POST(new Request("http://localhost/api/account/delete", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "HESABIMI SİL", email: "test@example.com" }),
    }));
    assert.equal(response.status, 401);
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

// ---- account/reset-progress -------------------------------------------------------

test("account/reset-progress: normal durum — takvim bağı silme döngüsünden ÖNCE çözülür", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  const userId = freshUserId();
  const calls = [];
  globalThis.fetch = withAuthenticatedFetch((url) => {
    calls.push(String(url));
    return new Response(null, { status: 204 });
  }, userId);
  try {
    const { POST } = await import(`../app/api/account/reset-progress/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/account/reset-progress", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "RESET_PROGRESS" }),
    }));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { reset: true });
    const scheduleIndex = calls.findIndex((url) => url.includes("/workout_schedule"));
    const firstTableIndex = calls.findIndex((url) => url.includes("/food_entries"));
    assert.ok(scheduleIndex !== -1 && firstTableIndex !== -1 && scheduleIndex < firstTableIndex, "workout_schedule güncellemesi tablo silmelerinden önce gitmeli");
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/reset-progress: edge case — saatte 3 denemeden fazlası hız sınırına takılır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  const userId = freshUserId();
  globalThis.fetch = withResetProgressFetch(userId);
  try {
    const { POST } = await import(`../app/api/account/reset-progress/route.ts?test=${Date.now()}`);
    const makeRequest = () => POST(authorizedRequest("http://localhost/api/account/reset-progress", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "RESET_PROGRESS" }),
    }));
    const statuses = [];
    for (let i = 0; i < 4; i += 1) statuses.push((await makeRequest()).status);
    assert.deepEqual(statuses, [200, 200, 200, 429], "4. denemede hız sınırı devreye girmeli");
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/reset-progress: hatalı input — yanlış onay ifadesi reddedilir, hiçbir tabloya dokunulmaz", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  const userId = freshUserId();
  let restCalled = false;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    if (String(url).includes("/rest/v1/")) restCalled = true;
    return new Response(null, { status: 204 });
  }, userId);
  try {
    const { POST } = await import(`../app/api/account/reset-progress/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/account/reset-progress", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "reset_progress" }),
    }));
    assert.equal(response.status, 400);
    assert.equal(restCalled, false, "yanlış onayda hiçbir veri değişikliği tetiklenmemeli");
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});

test("account/reset-progress: hatalı input — kimliği doğrulanmamış istek Supabase'e hiç gitmeden reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const restoreSecret = withSecretKeyEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new TypeError("beklenmeyen ağ isteği"); };
  try {
    const { POST } = await import(`../app/api/account/reset-progress/route.ts?test=${Date.now()}`);
    const response = await POST(new Request("http://localhost/api/account/reset-progress", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmation: "RESET_PROGRESS" }),
    }));
    assert.equal(response.status, 401);
  } finally {
    globalThis.fetch = previousFetch;
    restoreSecret();
    restoreEnv();
  }
});
