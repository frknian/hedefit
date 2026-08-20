"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import { createStepRepository, type DailyStepEntry } from "@/lib/step-service";
import { computeStepAdvice, readStoredStepGoal, type StepAdvice } from "@/lib/step-counter";
import { localDateKey } from "@/lib/streak";
import { useTranslations } from "@/lib/i18n/translate";
import { useLocale } from "@/lib/i18n/locale";

function AdviceMessage({ advice }: { advice: StepAdvice }) {
  const t = useTranslations();
  if (advice.kind === "noData") return <p className="step-advice-empty">{t.stepHistory.adviceNoData}</p>;
  if (advice.kind === "goalReached") return <p className="step-advice goal">{t.stepHistory.adviceGoalReached(advice.steps.toLocaleString("tr-TR"))}</p>;
  if (advice.kind === "belowAverage") return <p className="step-advice low">{t.stepHistory.adviceBelowAverage(advice.yesterdaySteps.toLocaleString("tr-TR"), advice.averageSteps.toLocaleString("tr-TR"))}</p>;
  return <p className="step-advice">{t.stepHistory.adviceOnTrack(advice.yesterdaySteps.toLocaleString("tr-TR"))}</p>;
}

/**
 * İlerlemem sayfasındaki adım geçmişi: son 14 günün çubuk grafiği ve dünkü
 * performansa göre kısa bir tavsiye (bkz. lib/step-counter.ts computeStepAdvice —
 * karar mantığı orada, saf ve test edilebilir; burada yalnız çizim var).
 */
export function StepHistoryCard({ userId }: { userId?: string }) {
  const t = useTranslations();
  const locale = useLocale();
  const dateLocale = locale === "en" ? "en-US" : "tr-TR";
  const [entries, setEntries] = useState<DailyStepEntry[]>([]);
  const [loading, setLoading] = useState(Boolean(userId));
  const [goal] = useState(() => readStoredStepGoal());

  useEffect(() => {
    if (!userId) return undefined;
    let cancelled = false;
    async function load() {
      const supabase = createClient();
      if (!supabase) { setLoading(false); return; }
      try {
        const list = await createStepRepository(supabase, userId as string).list(14);
        if (!cancelled) setEntries(list);
      } catch {
        // Geçmiş yüklenemezse kart sessizce boş kalır; ana ekrandaki adım
        // sayar zaten kendi hata mesajını gösteriyor.
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [userId]);

  if (!userId) return null;

  const today = localDateKey();
  const byDate = new Map(entries.map((entry) => [entry.localDate, entry.steps]));
  const days = Array.from({ length: 14 }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - (13 - index));
    const key = localDateKey(date);
    return { dateKey: key, steps: byDate.get(key) || 0 };
  });
  const peak = Math.max(goal, ...days.map((day) => day.steps));
  const advice = computeStepAdvice(entries.map((entry) => ({ localDate: entry.localDate, steps: entry.steps })), goal, today);
  const shortDate = new Intl.DateTimeFormat(dateLocale, { day: "numeric", month: "numeric" });

  return <section className="step-history-card">
    <div className="section-title"><div><div className="eyebrow">{t.stepHistory.eyebrow}</div><h2>{t.stepHistory.title}</h2></div></div>
    {loading ? <p className="step-advice-empty">{t.stepHistory.loading}</p> : <>
      <AdviceMessage advice={advice} />
      <div className="step-history-chart" role="img" aria-label={t.stepHistory.chartLabel}>
        {days.map((day) => <div key={day.dateKey} className="step-history-bar-col">
          <div className="step-history-bar-track">
            <div className="step-history-bar" style={{ height: `${Math.max(2, Math.min(100, (day.steps / Math.max(1, peak)) * 100))}%` }} title={`${shortDate.format(new Date(`${day.dateKey}T12:00:00`))} · ${day.steps.toLocaleString("tr-TR")}`} />
          </div>
          <small>{shortDate.format(new Date(`${day.dateKey}T12:00:00`))}</small>
        </div>)}
      </div>
      <p className="step-history-goal-line">{t.stepHistory.goalLine(goal.toLocaleString("tr-TR"))}</p>
    </>}
  </section>;
}
