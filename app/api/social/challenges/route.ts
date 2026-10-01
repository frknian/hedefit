import { authenticateRequest } from "../../../../lib/api-auth.ts";
import { rateLimit, tooManyRequests } from "../../../../lib/rate-limit.ts";
import { isUuid, socialUserClient } from "../../../../lib/social.ts";

export const runtime = "edge";

const METRICS = ["xp", "workouts", "distance_km"] as const;

export async function GET(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-challenges-list:${auth.user.id}`, 60, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_list_challenges");
  if (error) return Response.json({ error: "Meydan okumalar yüklenemedi." }, { status: 500 });

  const rows = (data ?? []) as { id: string; title: string; metric: string; target_value: number; starts_at: string; ends_at: string; creator_id: string; is_creator: boolean; my_status: string; participant_count: number }[];
  const challenges = rows.map((row) => ({
    id: row.id,
    title: row.title,
    metric: row.metric,
    targetValue: row.target_value,
    startsAt: row.starts_at,
    endsAt: row.ends_at,
    creatorId: row.creator_id,
    isCreator: row.is_creator,
    myStatus: row.my_status,
    participantCount: row.participant_count,
  }));
  return Response.json({ challenges });
}

export async function POST(request: Request) {
  const auth = await authenticateRequest(request);
  if ("error" in auth) return auth.error;
  const limited = rateLimit(`social-challenges-create:${auth.user.id}`, 10, 60_000);
  if (!limited.ok) return tooManyRequests(limited.retryAfterSeconds);

  const payload = await request.json().catch(() => null) as Record<string, unknown> | null;
  const title = typeof payload?.title === "string" ? payload.title.trim() : "";
  const metric = typeof payload?.metric === "string" ? payload.metric : "";
  const targetValue = Number(payload?.targetValue);
  const days = Number(payload?.days);
  const friendIds = Array.isArray(payload?.friendIds) ? payload.friendIds.filter((id): id is string => typeof id === "string" && isUuid(id)) : [];

  if (!title || title.length > 60) return Response.json({ error: "Geçersiz başlık." }, { status: 400 });
  if (!METRICS.includes(metric as (typeof METRICS)[number])) return Response.json({ error: "Geçersiz metrik." }, { status: 400 });
  if (!Number.isFinite(targetValue) || targetValue <= 0) return Response.json({ error: "Geçersiz hedef." }, { status: 400 });
  if (!Number.isInteger(days) || days < 1 || days > 30) return Response.json({ error: "Süre 1-30 gün arasında olmalı." }, { status: 400 });

  const client = socialUserClient(request);
  if (!client) return Response.json({ error: "Servis yapılandırılmamış." }, { status: 503 });

  const { data, error } = await client.rpc("hedefit_create_challenge", {
    p_title: title,
    p_metric: metric,
    p_target: targetValue,
    p_days: days,
    p_friend_ids: friendIds,
  });
  if (error) return Response.json({ error: "Meydan okuma oluşturulamadı." }, { status: 500 });
  return Response.json({ id: data }, { status: 201 });
}
