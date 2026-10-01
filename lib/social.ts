import { createClient } from "@supabase/supabase-js";
import { bearerToken } from "./api-auth.ts";
import { normalizeSupabaseUrl } from "./supabase/url.ts";
import { isUuid } from "./nutrition-log.ts";

export { isUuid };

const USERNAME_PATTERN = /^[a-z0-9._]{3,20}$/;

export function isValidUsername(value: unknown): value is string {
  return typeof value === "string" && USERNAME_PATTERN.test(value.toLowerCase());
}

/** app/api/social/* rotalarının paylaştığı, kullanıcının kendi jetonuyla RLS'e tabi Supabase client'ı. */
export function socialUserClient(request: Request) {
  const url = normalizeSupabaseUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anonKey) return null;
  return createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${bearerToken(request)}` } },
  });
}

export type FriendshipRow = {
  id: string;
  status: "pending" | "accepted" | "declined";
  created_at: string;
  responded_at: string | null;
  is_incoming: boolean;
  friend_id: string;
  username: string | null;
  display_name: string | null;
  avatar_path: string | null;
};

export function toFriendJson(row: FriendshipRow) {
  return {
    id: row.id,
    status: row.status,
    createdAt: row.created_at,
    respondedAt: row.responded_at,
    isIncoming: row.is_incoming,
    user: {
      id: row.friend_id,
      username: row.username,
      displayName: row.display_name,
      avatarPath: row.avatar_path,
    },
  };
}
