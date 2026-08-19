"use client";

import { useEffect, useRef, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import { createActivityRepository } from "@/lib/activity-service";
import { coreActivityCatalog, activityIntensityOptions, estimateActivityCalories, type ActivityIntensity } from "@/lib/sports";
import { localDateKey, userTimeZone, type ActivityType } from "@/lib/streak";
import { encodePolyline, routeDistanceKm, type LatLng } from "@/lib/polyline";
import { getCurrentPosition, isGpsTrackingAvailable, startGpsTracking, stopGpsTracking, type TrackedPoint } from "@/lib/gps-tracking";
import { connectHeartRateMonitor, type HeartRateMonitor } from "@/lib/ble-heart-rate";
import { useTranslations, translateIntensity } from "@/lib/i18n/translate";
import { GpsMapView } from "@/components/GpsMapView";

type Phase = "idle" | "unavailable" | "tracking" | "paused" | "summary";

// GPS'e uygun, mesafe bazlı aktiviteler; diğer sporlar manuel ActivityLogger'da kalır.
const TRACKABLE_ACTIVITIES = coreActivityCatalog.filter((activity) => ["walking", "running", "cycling"].includes(activity.key));

function formatDuration(ms: number) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
}

export function GpsActivityTracker({ userId, weightKg = 70, onClose }: { userId: string; weightKg?: number; onClose: () => void }) {
  const t = useTranslations();
  const [phase, setPhase] = useState<Phase>(isGpsTrackingAvailable() ? "idle" : "unavailable");
  const [activityKey, setActivityKey] = useState(TRACKABLE_ACTIVITIES[0]?.key || "walking");
  const [intensity, setIntensity] = useState<ActivityIntensity>("Orta");
  const [points, setPoints] = useState<TrackedPoint[]>([]);
  const [startedAt, setStartedAt] = useState<string | null>(null);
  const [endedAt, setEndedAt] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [currentBpm, setCurrentBpm] = useState<number | null>(null);
  const [hrConnecting, setHrConnecting] = useState(false);
  const [hrConnected, setHrConnected] = useState(false);
  const [hrSamples, setHrSamples] = useState<number[]>([]);
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  /** İlk GPS sinyali gelene kadar haritanın nereye bakacağı. */
  const [initialPosition, setInitialPosition] = useState<LatLng | null>(null);

  const watcherIdRef = useRef("");
  const hrMonitorRef = useRef<HeartRateMonitor | null>(null);

  useEffect(() => {
    if (phase !== "tracking") return undefined;
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, [phase]);

  useEffect(() => () => {
    if (watcherIdRef.current) void stopGpsTracking(watcherIdRef.current);
    if (hrMonitorRef.current) void hrMonitorRef.current.disconnect();
  }, []);

  // Takip başlamadan önce anlık konumu al: harita dünyanın neresinde olursa
  // olsun ilk karede doğru yere bakar, boş bir dünya görünümüyle açılmaz.
  useEffect(() => {
    let cancelled = false;
    void getCurrentPosition().then((point) => {
      if (!cancelled && point) setInitialPosition({ lat: point.lat, lng: point.lng });
    });
    return () => { cancelled = true; };
  }, []);

  const route: LatLng[] = points.map((p) => ({ lat: p.lat, lng: p.lng }));
  const distanceKm = routeDistanceKm(route);
  const elapsedMs = startedAt ? (endedAt ? new Date(endedAt).getTime() : now) - new Date(startedAt).getTime() : 0;
  const elapsedMinutes = elapsedMs / 60000;
  const lastPoint = points[points.length - 1];
  const currentSpeedKmh = lastPoint?.speedMps != null ? Math.max(0, lastPoint.speedMps) * 3.6 : 0;
  const avgSpeedKmh = elapsedMinutes > 0.05 ? distanceKm / (elapsedMinutes / 60) : 0;
  const maxSpeedKmh = points.reduce((max, p) => (p.speedMps != null ? Math.max(max, p.speedMps * 3.6) : max), 0);
  const selectedActivity = TRACKABLE_ACTIVITIES.find((activity) => activity.key === activityKey) || TRACKABLE_ACTIVITIES[0];
  const estimatedCalories = selectedActivity ? estimateActivityCalories(selectedActivity.key, Math.round(elapsedMinutes), weightKg, intensity) : 0;

  async function handleStart() {
    setMessage("");
    setPoints([]);
    setEndedAt(null);
    setHrSamples([]);
    const start = new Date().toISOString();
    setStartedAt(start);
    setPhase("tracking");
    watcherIdRef.current = await startGpsTracking(
      (point) => setPoints((current) => [...current, point]),
      (error) => setMessage(error),
    );
  }

  async function handlePause() {
    if (watcherIdRef.current) await stopGpsTracking(watcherIdRef.current);
    watcherIdRef.current = "";
    setPhase("paused");
  }

  async function handleResume() {
    setPhase("tracking");
    watcherIdRef.current = await startGpsTracking(
      (point) => setPoints((current) => [...current, point]),
      (error) => setMessage(error),
    );
  }

  async function handleStop() {
    if (watcherIdRef.current) await stopGpsTracking(watcherIdRef.current);
    watcherIdRef.current = "";
    if (hrMonitorRef.current) { await hrMonitorRef.current.disconnect(); hrMonitorRef.current = null; setHrConnected(false); }
    setEndedAt(new Date().toISOString());
    setPhase("summary");
  }

  async function handleConnectHeartRate() {
    setHrConnecting(true);
    setMessage("");
    try {
      const monitor = await connectHeartRateMonitor(
        (bpm) => { setCurrentBpm(bpm); setHrSamples((current) => [...current, bpm]); },
        () => { setCurrentBpm(null); setHrConnected(false); },
      );
      if (!monitor) { setMessage(t.gpsActivity.errorHeartRateUnavailable); return; }
      hrMonitorRef.current = monitor;
      setHrConnected(true);
    } catch {
      setMessage(t.gpsActivity.errorHeartRateConnect);
    } finally {
      setHrConnecting(false);
    }
  }

  async function handleSave() {
    if (!selectedActivity || !startedAt || !endedAt) return;
    setSaving(true);
    setMessage("");
    const client = createClient();
    if (!client) { setSaving(false); setMessage(t.gpsActivity.errorService); return; }
    const activityType: ActivityType = selectedActivity.key === "walking" ? "walk" : "sport";
    const avgHeartRate = hrSamples.length ? Math.round(hrSamples.reduce((sum, v) => sum + v, 0) / hrSamples.length) : null;
    const maxHeartRate = hrSamples.length ? Math.max(...hrSamples) : null;
    try {
      const entry = await createActivityRepository(client, userId).createWithRoute({
        activityType,
        activityKey: selectedActivity.key,
        activityName: selectedActivity.name,
        occurredAt: startedAt,
        localDate: localDateKey(new Date(startedAt), userTimeZone()),
        durationMinutes: Math.max(1, Math.round(elapsedMinutes)),
        distanceKm: Math.round(distanceKm * 100) / 100,
        estimatedCalories: estimatedCalories || null,
        steps: null,
        intensity: intensity.toLocaleLowerCase("tr-TR").replace("ü", "u") as "hafif" | "orta" | "yuksek",
        notes: notes.trim() || null,
        avgSpeedKmh: Math.round(avgSpeedKmh * 100) / 100,
        maxSpeedKmh: Math.round(maxSpeedKmh * 100) / 100,
        avgHeartRate,
        maxHeartRate,
        heartRateSource: hrSamples.length ? "ble" : null,
        encodedPolyline: encodePolyline(route),
        pointCount: route.length,
        startedAt,
        endedAt,
      });
      const { data, error: streakError } = await client.rpc("record_streak_activity", { p_activity_type: activityType, p_timezone: userTimeZone() });
      const row = Array.isArray(data) ? (data[0] as { current_streak?: number } | undefined) : (data as { current_streak?: number } | null);
      window.dispatchEvent(new CustomEvent("fit-ai-activity-recorded", { detail: { streak: Number(row?.current_streak) || undefined } }));
      setMessage(streakError ? t.gpsActivity.savedNoStreak(entry.activityName) : t.gpsActivity.savedWithStreak(entry.activityName, row?.current_streak || null));
      setTimeout(onClose, 1200);
    } catch {
      setMessage(t.gpsActivity.errorSave);
    } finally {
      setSaving(false);
    }
  }

  return <section className="gps-tracker" aria-labelledby="gps-tracker-title">
    <div className="gps-tracker-head">
      <div><div className="eyebrow">{t.gpsActivity.eyebrow}</div><h2 id="gps-tracker-title">{t.gpsActivity.title}</h2></div>
      <button type="button" className="activity-close" onClick={onClose} aria-label={t.gpsActivity.closeLabel}>×</button>
    </div>

    {phase === "unavailable" && <p className="gps-tracker-empty">{t.gpsActivity.errorUnavailable}</p>}

    {phase === "idle" && <div className="gps-tracker-setup">
      <div className="gps-tracker-activity-picker" role="group" aria-label={t.gpsActivity.activityPickerLabel}>
        {/* Yalnız adı yazar: katalogdaki iki harflik kısaltma ("YÜ Yürüyüş")
            etiketin önünde okunmayan bir gürültüydü. */}
        {TRACKABLE_ACTIVITIES.map((activity) => <button type="button" key={activity.key} aria-pressed={activityKey === activity.key} className={activityKey === activity.key ? "active" : ""} onClick={() => setActivityKey(activity.key)}>
          {activity.name}
        </button>)}
      </div>
      <button type="button" className="gps-tracker-start" onClick={() => void handleStart()}>{t.gpsActivity.start}</button>
    </div>}

    {(phase === "tracking" || phase === "paused") && <div className="gps-tracker-live">
      <GpsMapView route={route} currentPosition={route[route.length - 1] || initialPosition} className="gps-map-view live" />
      <div className="gps-tracker-stats">
        <div><span>{t.gpsActivity.statDuration}</span><strong>{formatDuration(elapsedMs)}</strong></div>
        <div><span>{t.gpsActivity.statDistance}</span><strong>{distanceKm.toFixed(2)} km</strong></div>
        <div><span>{t.gpsActivity.statSpeed}</span><strong>{currentSpeedKmh.toFixed(1)} km/s</strong></div>
        <div><span>{t.gpsActivity.statHeartRate}</span><strong>{currentBpm != null ? `${currentBpm} bpm` : "–"}</strong></div>
      </div>
      {!hrConnected && <button type="button" className="gps-tracker-hr-connect" disabled={hrConnecting} onClick={() => void handleConnectHeartRate()}>{hrConnecting ? t.gpsActivity.connectingHeartRate : t.gpsActivity.connectHeartRate}</button>}
      <div className="gps-tracker-controls">
        {phase === "tracking" && <button type="button" onClick={() => void handlePause()}>{t.gpsActivity.pause}</button>}
        {phase === "paused" && <button type="button" onClick={() => void handleResume()}>{t.gpsActivity.resume}</button>}
        <button type="button" className="gps-tracker-stop" onClick={() => void handleStop()}>{t.gpsActivity.stop}</button>
      </div>
    </div>}

    {phase === "summary" && <div className="gps-tracker-summary">
      <GpsMapView route={route} interactive className="gps-map-view summary" />
      <div className="gps-tracker-stats">
        <div><span>{t.gpsActivity.statDuration}</span><strong>{formatDuration(elapsedMs)}</strong></div>
        <div><span>{t.gpsActivity.statDistance}</span><strong>{distanceKm.toFixed(2)} km</strong></div>
        <div><span>{t.gpsActivity.statAvgSpeed}</span><strong>{avgSpeedKmh.toFixed(1)} km/s</strong></div>
        <div><span>{t.gpsActivity.statMaxSpeed}</span><strong>{maxSpeedKmh.toFixed(1)} km/s</strong></div>
        {hrSamples.length > 0 && <div><span>{t.gpsActivity.statAvgHeartRate}</span><strong>{Math.round(hrSamples.reduce((s, v) => s + v, 0) / hrSamples.length)} bpm</strong></div>}
        <div><span>{t.gpsActivity.statCalories}</span><strong>{estimatedCalories} kcal</strong></div>
      </div>
      <label className="gps-tracker-intensity">{t.gpsActivity.intensityLabel}
        <select value={intensity} onChange={(event) => setIntensity(event.target.value as ActivityIntensity)}>
          {activityIntensityOptions.map((option) => <option key={option} value={option}>{translateIntensity(t, option)}</option>)}
        </select>
      </label>
      <label className="gps-tracker-notes">{t.gpsActivity.noteLabel}<textarea maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder={t.gpsActivity.notePlaceholder} /></label>
      <button type="button" className="gps-tracker-save" disabled={saving} onClick={() => void handleSave()}>{saving ? t.gpsActivity.saving : t.gpsActivity.save}</button>
    </div>}

    {message && <p className="gps-tracker-message" role="status">{message}</p>}
  </section>;
}
