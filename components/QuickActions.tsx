"use client";

import { useState, type ComponentType } from "react";
import { CalendarDays, Droplet, History, LibraryBig, ListChecks, LineChart, Play, Plus, Utensils } from "lucide-react";
import { QUICK_ACTIONS, setStoredQuickActionIds, toggleQuickActionId, useQuickActionIds, type AppView } from "@/lib/quick-actions";
import { useTranslations, type Dictionary } from "@/lib/i18n/translate";

function actionLabel(t: Dictionary, id: string) {
  const labels = t.quickActions as unknown as Record<string, string>;
  return labels[id] ?? id;
}

// Kısayol ikonları: Stitch tasarımındaki "lime/turuncu vurgulu ikon + etiket"
// kartını eşlemek için. Isı taşıyan eylemler (aktivite başlat, su ekle)
// turuncu; geri kalanı marka lime'ı alır.
const ACTION_ICONS: Record<string, ComponentType<{ className?: string }>> = {
  startWorkout: Play,
  readyPrograms: ListChecks,
  startActivity: Plus,
  activityLog: History,
  addMeal: Utensils,
  water: Droplet,
  progress: LineChart,
  calendar: CalendarDays,
  library: LibraryBig,
};
const HEAT_ACTIONS = new Set(["startActivity", "water"]);

/**
 * Ana ekrandaki kısayol şeridi. Hedef görünüme geçer, gerekiyorsa oradaki
 * bölüme kaydırır ve hangi kısayolların görüneceği kullanıcı tarafından
 * değiştirilebilir.
 */
export function QuickActions({ onNavigate }: { onNavigate: (view: AppView, anchor?: string, overlay?: "gpsTracker") => void }) {
  const t = useTranslations();
  const selected = useQuickActionIds();
  const [editing, setEditing] = useState(false);

  const visible = QUICK_ACTIONS.filter((action) => selected.includes(action.id));

  return <section className="quick-actions">
    <div className="quick-actions-head">
      <div className="eyebrow">{t.quickActions.eyebrow}</div>
      <button type="button" className="quick-actions-edit" aria-pressed={editing} onClick={() => setEditing((open) => !open)}>
        {editing ? t.quickActions.done : t.quickActions.customize}
      </button>
    </div>

    {editing ? <>
      <p className="quick-actions-hint">{t.quickActions.hint}</p>
      <div className="quick-actions-picker">{QUICK_ACTIONS.map((action) => {
        const on = selected.includes(action.id);
        return <button
          type="button"
          key={action.id}
          aria-pressed={on}
          className={on ? "active" : ""}
          onClick={() => setStoredQuickActionIds(toggleQuickActionId(selected, action.id))}
        >{actionLabel(t, action.id)}</button>;
      })}</div>
    </> : <div className="quick-actions-list">{visible.map((action) => {
      const Icon = ACTION_ICONS[action.id];
      return <button
        type="button"
        key={action.id}
        className={HEAT_ACTIONS.has(action.id) ? "heat" : ""}
        onClick={() => onNavigate(action.view, action.anchor, action.overlay)}
      >{Icon && <Icon className="quick-action-icon" />}<span>{actionLabel(t, action.id)}</span></button>;
    })}</div>}
  </section>;
}
