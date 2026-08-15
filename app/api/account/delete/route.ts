import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "../../../../lib/supabase/url.ts";
import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";

export const runtime = "nodejs";

const AVATAR_LIST_PAGE_SIZE = 100;

export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  // Geri alınamaz bir işlem: paylaşılan izolat başına dahi olsa cömert bir
  // limit gerekmiyor. Yanlışlıkla/kötü niyetle tekrar tekrar denemeyi engeller.
  const rateLimitResult = rateLimit(`account-delete:${auth.user.id}`, 3, 3_600_000);
  if (!rateLimitResult.ok) return tooManyRequests(rateLimitResult.retryAfterSeconds);

  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !anonKey || !secretKey) return Response.json({ error: "Hesap silme servisi yapılandırılmamış." }, { status: 503 });

  let payload: { confirmation?: string; email?: string };
  try {
    payload = await request.json() as { confirmation?: string; email?: string };
  } catch {
    return Response.json({ error: "Silme onayı okunamadı." }, { status: 400 });
  }
  if (payload.confirmation !== "HESABIMI SİL") return Response.json({ error: "Silme onayı eşleşmiyor." }, { status: 400 });
  if (!auth.user.email || payload.email?.trim().toLocaleLowerCase("tr-TR") !== auth.user.email.toLocaleLowerCase("tr-TR")) {
    return Response.json({ error: "E-posta onayı hesapla eşleşmiyor." }, { status: 400 });
  }

  const admin = createClient(url, secretKey, { auth: { persistSession: false, autoRefreshToken: false } });

  // list() sayfa başına en fazla AVATAR_LIST_PAGE_SIZE döner; 100'den fazla
  // yüklenmiş avatarı olan bir kullanıcıda tek sayfa fazlasını atlayıp
  // sahipsiz dosya bırakıyordu. Boş sayfa dönene kadar ilerleriz.
  //
  // list()/remove() geçici bir hatayla dönerse hesap silme isteğinin
  // TAMAMINI reddetmek (önceki sürümde böyleydi) yasal olarak önemli, geri
  // alınamaz bir hakkı (hesabını silme) ilgisiz bir depolama alt sistemine
  // bağımlı kılıp saatte 3 denemeyle daha da kırılgan hâle getiriyordu. Bunun
  // yerine kısa bir yeniden deneme yapıp hâlâ başarısızsa açıkça LOGLAYIP asıl
  // silme işlemine (deleteUser) devam ediyoruz — eski davranışın aksine bu
  // sessiz değil, izlenebilir bir en-iyi-çaba: sahipsiz kalan dosya varsa
  // loglardan görülebilir ve elle temizlenebilir, ama kullanıcının hesabını
  // silme hakkını engellemez.
  const AVATAR_CLEANUP_ATTEMPTS = 2;
  let offset = 0;
  let avatarCleanupFailed = false;
  pagesLoop: for (;;) {
    let listResult: { data: { name: string }[] | null; error: { message: string } | null } | null = null;
    for (let attempt = 1; attempt <= AVATAR_CLEANUP_ATTEMPTS; attempt += 1) {
      listResult = await admin.storage.from("profile-avatars").list(auth.user.id, { limit: AVATAR_LIST_PAGE_SIZE, offset });
      if (!listResult.error) break;
      if (attempt < AVATAR_CLEANUP_ATTEMPTS) await new Promise((resolve) => setTimeout(resolve, 300));
    }
    if (!listResult || listResult.error) {
      console.error("[account-delete] avatar list failed after retries, proceeding with account deletion anyway", { userId: auth.user.id, message: listResult?.error?.message });
      avatarCleanupFailed = true;
      break pagesLoop;
    }
    const avatarObjects = listResult.data;
    if (!avatarObjects?.length) break;

    let removeError: { message: string } | null = null;
    for (let attempt = 1; attempt <= AVATAR_CLEANUP_ATTEMPTS; attempt += 1) {
      ({ error: removeError } = await admin.storage.from("profile-avatars").remove(avatarObjects.map((item) => `${auth.user.id}/${item.name}`)));
      if (!removeError) break;
      if (attempt < AVATAR_CLEANUP_ATTEMPTS) await new Promise((resolve) => setTimeout(resolve, 300));
    }
    if (removeError) {
      console.error("[account-delete] avatar removal failed after retries, proceeding with account deletion anyway", { userId: auth.user.id, message: removeError.message });
      avatarCleanupFailed = true;
      break pagesLoop;
    }
    if (avatarObjects.length < AVATAR_LIST_PAGE_SIZE) break;
    offset += AVATAR_LIST_PAGE_SIZE;
  }
  if (avatarCleanupFailed) {
    console.warn("[account-delete] proceeding without full avatar cleanup; orphaned files may remain in profile-avatars", { userId: auth.user.id });
  }

  const { error: deleteError } = await admin.auth.admin.deleteUser(auth.user.id);
  if (deleteError) {
    console.error("[account-delete] user deletion failed", { userId: auth.user.id, code: deleteError.code });
    return Response.json({ error: "Hesap verileri silinemedi. Lütfen yeniden dene." }, { status: 500 });
  }
  return Response.json({ deleted: true });
}
