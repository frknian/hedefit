import { getExerciseById } from "@/lib/exercise-service";
import { clientKey, rateLimit, tooManyRequests } from "@/lib/rate-limit";

export function GET(request: Request, context: { params: Promise<{ id: string }> }) {
  // Liste uç noktasıyla (app/api/exercises/route.ts) aynı sınır: kimlik
  // gerektirmez ama toplu kazımaya karşı sınırlandırılır.
  const rateLimitResult = rateLimit(`exercises:${clientKey(request)}`, 120, 60_000);
  if (!rateLimitResult.ok) return tooManyRequests(rateLimitResult.retryAfterSeconds);

  return context.params.then(({ id }) => {
    const exercise = getExerciseById(id);
    return exercise ? Response.json(exercise) : Response.json({ error: "Egzersiz bulunamadı" }, { status: 404 });
  });
}
