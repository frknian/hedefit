import assert from "node:assert/strict";
import test from "node:test";
import { isValidUsername } from "../lib/social.ts";
import { authorizedRequest, withAuthenticatedFetch, withSupabaseAuthEnv, TEST_USER_ID } from "./helpers/auth.mjs";

// Sosyal katman v1: arkadaşlık istekleri + haftalık lider tablosu.
// supabase/migrations/20260929000000_friendships.sql içindeki RPC'ler
// (hedefit_list_friendships, hedefit_send_friend_request,
// hedefit_search_users, hedefit_weekly_leaderboard) burada taklit edilir;
// testler yalnızca route.ts'in auth/hız sınırı/gövde doğrulamasını ve
// Supabase yanıtının doğru JSON şekline dönüştüğünü kontrol eder.

let nextUserId = 100;
function freshUserId() {
  nextUserId += 1;
  return `00000000-0000-4000-8000-${String(nextUserId).padStart(12, "0")}`;
}

test("isValidUsername: format kontrolü", () => {
  assert.equal(isValidUsername("furkan.k"), true);
  assert.equal(isValidUsername("ab"), false);
  assert.equal(isValidUsername("Has Space"), false);
  assert.equal(isValidUsername(123), false);
});

test("social/users/search: normal durum — sonuçlar camelCase'e dönüştürülür", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_search_users")) {
      return Response.json([{ id: "u1", username: "aysekoc", display_name: "Ayşe", avatar_path: null }]);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { GET } = await import(`../app/api/social/users/search/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/users/search?q=ayse"));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { users: [{ id: "u1", username: "aysekoc", displayName: "Ayşe", avatarPath: null }] });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/users/search: hatalı input — iki karakterden kısa sorgu boş liste döner, ağa gitmez", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch(() => { throw new TypeError("ağa gitmemeliydi"); }, freshUserId());
  try {
    const { GET } = await import(`../app/api/social/users/search/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/users/search?q=a"));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { users: [] });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/friends GET: normal durum — kabul/gelen/giden istekler ayrıştırılır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_list_friendships")) {
      return Response.json([
        { id: "f1", status: "accepted", created_at: "2026-01-01T00:00:00Z", responded_at: "2026-01-02T00:00:00Z", is_incoming: false, friend_id: "u1", username: "a", display_name: "A", avatar_path: null },
        { id: "f2", status: "pending", created_at: "2026-01-03T00:00:00Z", responded_at: null, is_incoming: true, friend_id: "u2", username: "b", display_name: "B", avatar_path: null },
        { id: "f3", status: "pending", created_at: "2026-01-04T00:00:00Z", responded_at: null, is_incoming: false, friend_id: "u3", username: "c", display_name: "C", avatar_path: null },
      ]);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { GET } = await import(`../app/api/social/friends/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/friends"));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.friends.length, 1);
    assert.equal(body.friends[0].user.id, "u1");
    assert.equal(body.incomingRequests.length, 1);
    assert.equal(body.incomingRequests[0].user.id, "u2");
    assert.equal(body.outgoingRequests.length, 1);
    assert.equal(body.outgoingRequests[0].user.id, "u3");
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/friends POST: normal durum — geçerli kullanıcı adına istek gönderilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_send_friend_request")) {
      return Response.json({ id: "f9", status: "pending", created_at: "2026-01-05T00:00:00Z" });
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { POST } = await import(`../app/api/social/friends/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/social/friends", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "aysekoc" }),
    }));
    assert.equal(response.status, 201);
    assert.deepEqual(await response.json(), { request: { id: "f9", status: "pending", createdAt: "2026-01-05T00:00:00Z" } });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/friends POST: hatalı input — geçersiz kullanıcı adı Supabase'e hiç gitmeden reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch(() => { throw new TypeError("ağa gitmemeliydi"); }, freshUserId());
  try {
    const { POST } = await import(`../app/api/social/friends/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/social/friends", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "a" }),
    }));
    assert.equal(response.status, 400);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/friends POST: edge case — zaten var olan kayıt 409 döner", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_send_friend_request")) {
      return Response.json({ code: "23505", message: "already_exists" }, { status: 409 });
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { POST } = await import(`../app/api/social/friends/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/social/friends", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "aysekoc" }),
    }));
    assert.equal(response.status, 409);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/friends/[id] PATCH: normal durum — bekleyen istek kabul edilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rest/v1/friendships")) {
      return Response.json({ id: "00000000-0000-4000-8000-000000000f01", status: "accepted", responded_at: "2026-01-06T00:00:00Z" });
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { PATCH } = await import(`../app/api/social/friends/[id]/route.ts?test=${Date.now()}`);
    const response = await PATCH(authorizedRequest("http://localhost/api/social/friends/00000000-0000-4000-8000-000000000f01", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "accepted" }),
    }), { params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000f01" }) });
    assert.equal(response.status, 200);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/friends/[id] PATCH: hatalı input — geçersiz uuid ve geçersiz durum reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch(() => { throw new TypeError("ağa gitmemeliydi"); }, freshUserId());
  try {
    const { PATCH } = await import(`../app/api/social/friends/[id]/route.ts?test=${Date.now()}`);
    const badId = await PATCH(authorizedRequest("http://localhost/api/social/friends/not-a-uuid", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "accepted" }),
    }), { params: Promise.resolve({ id: "not-a-uuid" }) });
    assert.equal(badId.status, 400);

    const badStatus = await PATCH(authorizedRequest("http://localhost/api/social/friends/00000000-0000-4000-8000-000000000f01", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "blocked" }),
    }), { params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000f01" }) });
    assert.equal(badStatus.status, 400);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/friends/[id] DELETE: normal durum — arkadaşlık sonlandırılır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rest/v1/friendships")) return Response.json({ id: "00000000-0000-4000-8000-000000000f02" });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { DELETE } = await import(`../app/api/social/friends/[id]/route.ts?test=${Date.now()}`);
    const response = await DELETE(authorizedRequest("http://localhost/api/social/friends/00000000-0000-4000-8000-000000000f02", { method: "DELETE" }), {
      params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000f02" }),
    });
    assert.equal(response.status, 204);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/leaderboard GET: normal durum — sıra ve isCurrentUser hesaplanır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_weekly_leaderboard")) {
      return Response.json([
        { user_id: "other-user", username: "b", display_name: "B", avatar_path: null, weekly_xp: 500 },
        { user_id: TEST_USER_ID, username: "me", display_name: "Ben", avatar_path: null, weekly_xp: 300 },
      ]);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  });
  try {
    const { GET } = await import(`../app/api/social/leaderboard/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/leaderboard"));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.entries[0].rank, 1);
    assert.equal(body.entries[0].isCurrentUser, false);
    assert.equal(body.entries[1].rank, 2);
    assert.equal(body.entries[1].isCurrentUser, true);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social rotaları: hatalı input — kimliği doğrulanmamış istek Supabase'e hiç gitmeden 401 döner", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new TypeError("ağa gitmemeliydi"); };
  try {
    const { GET } = await import(`../app/api/social/leaderboard/route.ts?test=${Date.now()}`);
    const response = await GET(new Request("http://localhost/api/social/leaderboard"));
    assert.equal(response.status, 401);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

// ---- Faz 2: aktivite akışı -------------------------------------------------

test("social/feed GET: normal durum — snake_case satırlar camelCase'e dönüştürülür", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_friend_activity_feed")) {
      return Response.json([{ id: "e1", user_id: "u1", username: "a", display_name: "A", avatar_path: null, source: "WORKOUT_COMPLETED", source_id: "w1", amount: 50, occurred_at: "2026-02-01T00:00:00Z" }]);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { GET } = await import(`../app/api/social/feed/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/feed"));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.deepEqual(body.items[0], { id: "e1", source: "WORKOUT_COMPLETED", sourceId: "w1", amount: 50, occurredAt: "2026-02-01T00:00:00Z", user: { id: "u1", username: "a", displayName: "A", avatarPath: null } });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

// ---- Faz 3: ortak meydan okumalar -----------------------------------------

test("social/challenges POST: normal durum — geçerli gövdeyle meydan okuma oluşturulur", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_create_challenge")) return Response.json("00000000-0000-4000-8000-000000000c01");
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { POST } = await import(`../app/api/social/challenges/route.ts?test=${Date.now()}`);
    const response = await POST(authorizedRequest("http://localhost/api/social/challenges", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: "50km birlikte", metric: "distance_km", targetValue: 50, days: 7, friendIds: ["00000000-0000-4000-8000-000000000f01"] }),
    }));
    assert.equal(response.status, 201);
    assert.deepEqual(await response.json(), { id: "00000000-0000-4000-8000-000000000c01" });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/challenges POST: hatalı input — geçersiz metrik ve süre Supabase'e hiç gitmeden reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch(() => { throw new TypeError("ağa gitmemeliydi"); }, freshUserId());
  try {
    const { POST } = await import(`../app/api/social/challenges/route.ts?test=${Date.now()}`);
    const badMetric = await POST(authorizedRequest("http://localhost/api/social/challenges", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: "x", metric: "calories", targetValue: 10, days: 7, friendIds: [] }),
    }));
    assert.equal(badMetric.status, 400);

    const badDays = await POST(authorizedRequest("http://localhost/api/social/challenges", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title: "x", metric: "xp", targetValue: 10, days: 90, friendIds: [] }),
    }));
    assert.equal(badDays.status, 400);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/challenges GET: normal durum — liste camelCase'e dönüştürülür", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_list_challenges")) {
      return Response.json([{ id: "c1", title: "50km", metric: "distance_km", target_value: 50, starts_at: "2026-02-01T00:00:00Z", ends_at: "2026-02-08T00:00:00Z", creator_id: userId, is_creator: true, my_status: "joined", participant_count: 2 }]);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { GET } = await import(`../app/api/social/challenges/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/challenges"));
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.challenges[0].targetValue, 50);
    assert.equal(body.challenges[0].participantCount, 2);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/challenges/[id] PATCH: normal durum — davet kabul edilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rest/v1/challenge_participants")) return Response.json({ challenge_id: "00000000-0000-4000-8000-000000000c02", status: "joined" });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { PATCH } = await import(`../app/api/social/challenges/[id]/route.ts?test=${Date.now()}`);
    const response = await PATCH(authorizedRequest("http://localhost/api/social/challenges/00000000-0000-4000-8000-000000000c02", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status: "joined" }),
    }), { params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000c02" }) });
    assert.equal(response.status, 200);
    // Katalog bağlantısı olmayan (eski tip) davette challenge planı başlatılmaz.
    assert.deepEqual(await response.json(), { challengeId: "00000000-0000-4000-8000-000000000c02", status: "joined", userChallengeId: null });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/challenges/[id] DELETE: normal durum — yaratıcı meydan okumayı iptal eder", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rest/v1/challenges")) return Response.json({ id: "00000000-0000-4000-8000-000000000c03" });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { DELETE } = await import(`../app/api/social/challenges/[id]/route.ts?test=${Date.now()}`);
    const response = await DELETE(authorizedRequest("http://localhost/api/social/challenges/00000000-0000-4000-8000-000000000c03", { method: "DELETE" }), {
      params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000c03" }),
    });
    assert.equal(response.status, 204);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/challenges/[id] DELETE: edge case — yaratıcı değilse katılımcı kaydı silinerek ayrılınır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const userId = freshUserId();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rest/v1/challenges")) return Response.json(null);
    if (href.includes("/rest/v1/challenge_participants")) return Response.json({ challenge_id: "00000000-0000-4000-8000-000000000c04" });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, userId);
  try {
    const { DELETE } = await import(`../app/api/social/challenges/[id]/route.ts?test=${Date.now()}`);
    const response = await DELETE(authorizedRequest("http://localhost/api/social/challenges/00000000-0000-4000-8000-000000000c04", { method: "DELETE" }), {
      params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000c04" }),
    });
    assert.equal(response.status, 204);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/challenges/[id]/progress GET: normal durum — sıra ve isCurrentUser hesaplanır", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_challenge_progress")) {
      return Response.json([
        { user_id: "other-user", username: "b", display_name: "B", avatar_path: null, progress_value: 32.5 },
        { user_id: TEST_USER_ID, username: "me", display_name: "Ben", avatar_path: null, progress_value: 18 },
      ]);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  });
  try {
    const { GET } = await import(`../app/api/social/challenges/[id]/progress/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/challenges/00000000-0000-4000-8000-000000000c05/progress"), {
      params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000c05" }),
    });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.entries[0].rank, 1);
    assert.equal(body.entries[0].isCurrentUser, false);
    assert.equal(body.entries[1].isCurrentUser, true);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/challenges/[id]/progress GET: edge case — katılımcı değilse 403 döner", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_challenge_progress")) return Response.json({ code: "42501", message: "not_a_participant" }, { status: 403 });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  });
  try {
    const { GET } = await import(`../app/api/social/challenges/[id]/progress/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/challenges/00000000-0000-4000-8000-000000000c06/progress"), {
      params: Promise.resolve({ id: "00000000-0000-4000-8000-000000000c06" }),
    });
    assert.equal(response.status, 403);
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/settings GET: normal durum — aranabilirlik tercihi döner", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch((url) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_get_discoverable")) return Response.json(false);
    if (href.includes("/rest/v1/profiles")) return Response.json({ share_progress_with_friends: false });
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, freshUserId());
  try {
    const { GET } = await import(`../app/api/social/settings/route.ts?test=${Date.now()}`);
    const response = await GET(authorizedRequest("http://localhost/api/social/settings"));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { discoverable: false, shareProgress: false });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/settings PATCH: normal durum — değer RPC'ye iletilir ve yeni durum döner", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  let sentBody = null;
  globalThis.fetch = withAuthenticatedFetch(async (url, init) => {
    const href = String(url);
    if (href.includes("/rpc/hedefit_set_discoverable")) {
      sentBody = JSON.parse(String(init?.body ?? "{}"));
      return Response.json(false);
    }
    throw new TypeError(`beklenmeyen ağ isteği: ${href}`);
  }, freshUserId());
  try {
    const { PATCH } = await import(`../app/api/social/settings/route.ts?test=${Date.now()}`);
    const response = await PATCH(authorizedRequest("http://localhost/api/social/settings", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ discoverable: false }),
    }));
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { discoverable: false });
    assert.deepEqual(sentBody, { p_value: false });
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});

test("social/settings PATCH: hatalı input — boolean olmayan değer Supabase'e gitmeden reddedilir", async () => {
  const restoreEnv = withSupabaseAuthEnv();
  const previousFetch = globalThis.fetch;
  globalThis.fetch = withAuthenticatedFetch(() => { throw new TypeError("ağa gitmemeliydi"); }, freshUserId());
  try {
    const { PATCH } = await import(`../app/api/social/settings/route.ts?test=${Date.now()}`);
    for (const value of ["false", 0, null, undefined]) {
      const response = await PATCH(authorizedRequest("http://localhost/api/social/settings", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ discoverable: value }),
      }));
      assert.equal(response.status, 400);
    }
  } finally {
    globalThis.fetch = previousFetch;
    restoreEnv();
  }
});
