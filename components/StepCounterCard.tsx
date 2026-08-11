"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import { createStepRepository } from "@/lib/step-service";
import { localDateKey } from "@/lib/streak";
import { fetchTodaySteps, isNativeApp, isStepCounterAvailable, requestStepPermission } from "@/lib/mobile";
import { useTranslations } from "@/lib/i18n/translate";

const DEFAULT_GOAL = 8000;
const CIRCUMFERENCE = 2 * Math.PI * 42;
const CONNECTED_KEY = "fit-ai-step-counter-connected";

type Status = "idle" | "checking" | "connect" | "denied" | "unavailable" | "ready";

/**
 * Ana ekranın adım sayar kartı: HealthKit/Health Connect'ten bugünün adımını
 * çeker ve `daily_steps` tablosuna senkronize eder. Yalnızca native uygulamada
 * çalışır (web'de hiçbir şey render etmez).
 */
export function StepCounterCard({ userId, goal = DEFAULT_GOAL }: { userId?: string; goal?: number }) {
  const t = useTranslations();
  const [status, setStatus] = useState<Status>("idle");
  const [steps, setSteps] = useState<number | null>(null);

  useEffect(() => {
    if (!isNativeApp()) return;
    let cancelled = false;

    async function sync() {
      const value = await fetchTodaySteps();
      if (cancelled || value === null) return;
      setSteps(value);
      setStatus("ready");
      if (!userId) return;
      const supabase = createClient();
      if (!supabase) return;
      await createStepRepository(supabase, userId).upsertToday(localDateKey(), value).catch(() => undefined);
    }

    async function init() {
      setStatus("checking");
      const available = await isStepCounterAvailable();
      if (cancelled) return;
      if (!available) { setStatus("unavailable"); return; }
      const alreadyConnected = window.localStorage.getItem(CONNECTED_KEY) === "1";
      if (!alreadyConnected) { setStatus("connect"); return; }
      await sync();
    }

    void init();
    const interval = window.setInterval(() => { if (window.localStorage.getItem(CONNECTED_KEY) === "1") void sync(); }, 5 * 60_000);
    function onFocus() { if (window.localStorage.getItem(CONNECTED_KEY) === "1") void sync(); }
    window.addEventListener("focus", onFocus);
    return () => { cancelled = true; window.clearInterval(interval); window.removeEventListener("focus", onFocus); };
  }, [userId]);

  async function connect() {
    setStatus("checking");
    const granted = await requestStepPermission();
    if (!granted) { setStatus("denied"); return; }
    window.localStorage.setItem(CONNECTED_KEY, "1");
    const value = await fetchTodaySteps();
    setSteps(value);
    setStatus("ready");
    if (value !== null && userId) {
      const supabase = createClient();
      if (supabase) await createStepRepository(supabase, userId).upsertToday(localDateKey(), value).catch(() => undefined);
    }
  }

  if (!isNativeApp() || status === "idle" || status === "unavailable") return null;

  const current = steps ?? 0;
  const ratio = Math.min(1, current / Math.max(1, goal));
  const dash = CIRCUMFERENCE * ratio;
  const remaining = Math.max(0, goal - current);

  return <div className="step-ring-card">
    {status === "connect" || status === "denied" ? (
      <div className="step-ring-connect">
        <span>{t.stepCounter.eyebrow}</span>
        <p>{status === "denied" ? t.stepCounter.permissionDenied : t.stepCounter.connectHint}</p>
        <button type="button" onClick={() => void connect()}>{t.stepCounter.connect}</button>
      </div>
    ) : (
      <>
        <div className="step-ring" role="img" aria-label={t.stepCounter.ariaLabel(current)}>
          <svg viewBox="0 0 100 100" aria-hidden="true">
            <circle className="step-ring-track" cx="50" cy="50" r="42" />
            <circle className="step-ring-fill" cx="50" cy="50" r="42" strokeDasharray={`${dash} ${CIRCUMFERENCE}`} />
          </svg>
          <div className="step-ring-center">
            <strong>{status === "checking" ? "…" : current.toLocaleString("tr-TR")}</strong>
            <small>{t.stepCounter.unit}</small>
          </div>
        </div>
        <div className="step-ring-legend">
          <span>{t.stepCounter.eyebrow}</span>
          <strong>{ratio >= 1 ? t.stepCounter.goalReached : t.stepCounter.remaining(remaining)}</strong>
          <small>{t.stepCounter.goalLabel}: {goal.toLocaleString("tr-TR")} {t.stepCounter.unit}</small>
        </div>
      </>
    )}
  </div>;
}
