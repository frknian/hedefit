"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Bell, BellOff, BellRing } from "lucide-react";
import { isNativeApp, mobileNotificationPermission, requestMobileNotificationPermission } from "@/lib/mobile";
import { useTranslations } from "@/lib/i18n/translate";

type Permission = NotificationPermission | "unsupported";

/**
 * Başlık çubuğundaki bildirim zili (Stitch tasarımındaki `notifications` ikonu).
 *
 * İzin akışı TEK yerden geçer (lib/mobile.ts) ve üç platformda da aynı
 * arayüzü sunar:
 *   • tarayıcı → Notification.requestPermission()
 *   • Android  → POST_NOTIFICATIONS çalışma zamanı izni (Capacitor
 *                LocalNotifications.requestPermissions üzerinden)
 *   • iOS      → UNUserNotificationCenter yetkilendirmesi (aynı çağrı)
 *
 * İzin YALNIZCA kullanıcı zile bastığında istenir. Açılışta kendiliğinden
 * sorulan izin, tarayıcıda kalıcı "engellendi" ile sonuçlanır ve o karar
 * uygulama içinden geri alınamaz.
 */
export function NotificationBell({ onOpenSettings }: { onOpenSettings: () => void }) {
  const t = useTranslations();
  const [permission, setPermission] = useState<Permission>("default");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const noticeTimer = useRef<number | null>(null);

  const refresh = useCallback(() => {
    void mobileNotificationPermission().then(setPermission).catch(() => setPermission("unsupported"));
  }, []);

  useEffect(() => {
    refresh();
    // Kullanıcı izni işletim sistemi ayarlarından değiştirip uygulamaya
    // dönebilir; zil o zaman hâlâ eski durumu göstermemeli.
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
      if (noticeTimer.current !== null) window.clearTimeout(noticeTimer.current);
    };
  }, [refresh]);

  function flash(message: string) {
    setNotice(message);
    if (noticeTimer.current !== null) window.clearTimeout(noticeTimer.current);
    noticeTimer.current = window.setTimeout(() => setNotice(""), 4000);
  }

  async function handleClick() {
    // İzin verilmişse zil bir kısayoldur: hatırlatma ayarları takvimde.
    if (permission === "granted") { onOpenSettings(); return; }
    if (permission === "denied") { flash(isNativeApp() ? t.notifications.deniedNative : t.notifications.deniedWeb); onOpenSettings(); return; }
    if (permission === "unsupported") { flash(t.notifications.unsupported); return; }

    setBusy(true);
    const result = await requestMobileNotificationPermission().catch(() => "unsupported" as Permission);
    setBusy(false);
    setPermission(result);
    if (result === "granted") flash(t.notifications.granted);
    else if (result === "denied") flash(isNativeApp() ? t.notifications.deniedNative : t.notifications.deniedWeb);
    else if (result === "unsupported") flash(t.notifications.unsupported);
  }

  const Icon = permission === "granted" ? BellRing : permission === "denied" || permission === "unsupported" ? BellOff : Bell;
  const label = permission === "granted" ? t.notifications.labelGranted
    : permission === "denied" ? t.notifications.labelDenied
    : permission === "unsupported" ? t.notifications.labelUnsupported
    : t.notifications.labelDefault;

  return (
    <div className="hf-bell-wrap">
      <button
        type="button"
        className={`hf-topbar-icon hf-bell hf-bell-${permission}`}
        aria-label={label}
        title={label}
        disabled={busy}
        onClick={() => void handleClick()}
      >
        <Icon className="size-5" aria-hidden />
        {/* İzin henüz istenmemişse küçük bir nokta dikkat çeker; istendikten
            sonra kaybolur, kalıcı bir uyarı rozeti değildir. */}
        {permission === "default" && <span className="hf-bell-dot" aria-hidden />}
      </button>
      {/* Mesaj gövdeye taşınır: başlık çubuğunda `backdrop-filter` var ve bu,
          içindeki `position:fixed` öğeler için yeni bir kapsayıcı blok kurup
          kutuyu çubuğun dar alanına hapsediyordu. */}
      {notice && createPortal(<p className="hf-bell-notice" role="status">{notice}</p>, document.body)}
    </div>
  );
}
