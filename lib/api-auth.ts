import { createClient } from "@supabase/supabase-js";
import { normalizeSupabaseUrl } from "./supabase/url.ts";
import { isVerifiedAuthUser } from "./auth.ts";

export type AuthenticatedUser = { id: string; email: string | null };

export function bearerToken(request: Request) {
  const authorization = request.headers.get("authorization") || "";
  return authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
}

/**
 * Supabase erişim jetonunu sunucu tarafında doğrular (OWASP A01 – Broken Access Control).
 * Yapılandırma eksikse kapalı tarafa düşer: kimlik doğrulanamıyorsa istek reddedilir.
 */
export async function authenticateRequest(request: Request): Promise<{ user: AuthenticatedUser } | { error: Response }> {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anonKey) {
    return { error: Response.json({ error: "Kimlik doğrulama servisi yapılandırılmamış." }, { status: 503 }) };
  }

  const token = bearerToken(request);
  if (!token) {
    return { error: Response.json({ error: "Bu işlem için giriş yapmalısın." }, { status: 401 }) };
  }

  const client = createClient(url, anonKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await client.auth.getUser(token);
  if (error || !data.user) {
    return { error: Response.json({ error: "Oturum doğrulanamadı." }, { status: 401 }) };
  }
  // isVerifiedAuthUser: yalnız email_confirmed_at değil, Google OAuth için
  // kimlik sağlayıcının email_verified bayrağını da kabul eder — bazı Google
  // dönüşlerinde email_confirmed_at oturum yenilenene kadar boş kalabiliyor
  // (bkz. lib/auth.ts). Bu kontrolü client-side isVerifiedAuthUser ile
  // AYNI tutmazsak, istemci zaten doğrulanmış saydığı bir kullanıcıyı sunucu
  // reddedebilir (ör. hesap silme/ilerleme sıfırlama az önce Google ile giriş
  // yapmış birinde 403 döndürüyordu).
  if (!isVerifiedAuthUser(data.user)) {
    return { error: Response.json({ error: "Bu işlem için e-posta adresini doğrulamalısın." }, { status: 403 }) };
  }
  return { user: { id: data.user.id, email: data.user.email ?? null } };
}
