import { authenticateRequest } from "../../../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../../../lib/rate-limit.ts";
import { isUuid, socialUserClient } from "../../../../../../lib/social.ts";

export const runtime = "edge";

export async function GET(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-challenges-progress:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "Geçersiz meydan okuma kimliği." }, { status: 400 });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_challenge_progress", { p_challenge_id: id });
  if (error) {
    if (error.code === "P0002") return Response.json({ error: "Meydan okuma bulunamadı." }, { status: 404 });
    if (error.code === "42501") return Response.json({ error: "Bu meydan okumaya katılmıyorsun." }, { status: 403 });
    return Response.json({ error: "İlerleme yüklenemedi." }, { status: 500 });
  }

  const rows = (data ?? []) as { user_id: string; username: string | null; display_name: string | null; avatar_path: string | null; progress_value: number }[];
  const entries = rows.map((row, index) => ({
    rank: index + 1,
    progressValue: row.progress_value,
    isCurrentUser: row.user_id === auth.user.id,
    user: { id: row.user_id, username: row.username, displayName: row.display_name, avatarPath: row.avatar_path },
  }));
  return Response.json({ entries });
}
