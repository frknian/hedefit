"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import { createActivityRepository, type ActivityEntry, type ActivityRoute } from "@/lib/activity-service";
import { decodePolyline } from "@/lib/polyline";
import { formatDuration, formatPace } from "@/lib/activity-format";
import { useTranslations } from "@/lib/i18n/translate";
import { useLocale } from "@/lib/i18n/locale";
import { RoutePreviewThumbnail } from "@/components/RoutePreviewThumbnail";

const MAX_SHOWN = 8;

function formatEntryDate(value: string, locale: string) {
  return new Intl.DateTimeFormat(locale, { day: "numeric", month: "short" }).format(new Date(`${value}T12:00:00`));
}

/**
 * İlerlemem sayfasındaki Hedefit Rota özeti: yürüyüş/koşu/bisiklet
 * rotalarının küçük haritaları, süreleri ve mesafeleri. Aktivite günlüğünün
 * (ActivityLog) küçültülmüş bir önizlemesi — tam listeye "Tümünü gör" ile gidilir.
 */
export function RouteHistoryCard({ userId, onOpenAll }: { userId?: string; onOpenAll: () => void }) {
  const t = useTranslations();
  const dateLocale = useLocale() === "en" ? "en-US" : "tr-TR";
  const [entries, setEntries] = useState<ActivityEntry[]>([]);
  const [routes, setRoutes] = useState<Record<string, ActivityRoute | null>>({});
  const [loading, setLoading] = useState(Boolean(userId));

  useEffect(() => {
    if (!userId) return undefined;
    let cancelled = false;
    async function load() {
      const supabase = createClient();
      if (!supabase) { setLoading(false); return; }
      const repo = createActivityRepository(supabase, userId as string);
      try {
        const list = (await repo.list(40)).filter((entry) => entry.source === "gps").slice(0, MAX_SHOWN);
        if (cancelled) return;
        setEntries(list);
        const withRoutes = await Promise.all(list.map(async (entry) => [entry.id, await repo.getRoute(entry.id).catch(() => null)] as const));
        if (!cancelled) setRoutes(Object.fromEntries(withRoutes));
      } catch {
        // Rota geçmişi yüklenemezse kart sessizce boş kalır.
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    function refresh() { void load(); }
    window.addEventListener("fit-ai-activity-recorded", refresh);
    return () => { cancelled = true; window.removeEventListener("fit-ai-activity-recorded", refresh); };
  }, [userId]);

  if (!userId) return null;

  return <section className="route-history-card">
    <div className="section-title"><div><div className="eyebrow">{t.routeHistory.eyebrow}</div><h2>{t.routeHistory.title}</h2></div>{entries.length > 0 && <button type="button" className="route-history-open-all" onClick={onOpenAll}>{t.routeHistory.openAll} →</button>}</div>
    {loading ? <p className="step-advice-empty">{t.routeHistory.loading}</p>
      : entries.length === 0 ? <p className="step-advice-empty">{t.routeHistory.empty}</p>
      : <div className="route-history-list">{entries.map((entry) => {
          const route = routes[entry.id];
          const points = route ? decodePolyline(route.encodedPolyline) : [];
          const durationMs = entry.durationMinutes * 60_000;
          return <article key={entry.id} className="route-history-row" onClick={onOpenAll}>
            {points.length > 1 ? <RoutePreviewThumbnail route={points} width={64} height={64} /> : <div className="route-preview-thumb route-preview-thumb-placeholder" style={{ width: 64, height: 64 }} aria-hidden="true" />}
            <div>
              <strong>{entry.activityName}</strong>
              <small>{formatEntryDate(entry.localDate, dateLocale)} · {formatDuration(durationMs)}{entry.distanceKm ? ` · ${entry.distanceKm.toFixed(2)} km` : ""}</small>
            </div>
            <b>{entry.distanceKm ? formatPace(entry.distanceKm, durationMs) : entry.estimatedCalories ? `${entry.estimatedCalories} kcal` : ""}</b>
          </article>;
        })}</div>}
  </section>;
}
