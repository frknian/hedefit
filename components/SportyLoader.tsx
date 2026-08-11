"use client";

import { OnboardingIcon } from "./onboarding/OnboardingIcon";

/**
 * Açılış / bekleme ekranı (Stitch "Giriş ve Yükleme Ekranı").
 *
 * Ortada nabız gibi genişleyen iki halka, içinde dambıl işaretli dairesel
 * çekirdek; altında marka adı, durum metni ve zıplayan üç nokta. Bekleme
 * süresi değişmez, ama ekran uygulamanın ne olduğunu anlatır.
 *
 * role="status" + aria-live: ekran okuyucu bekleme durumunu duyurur; tüm
 * animasyonlar `prefers-reduced-motion` altında durur (bkz. globals.css).
 */
export function SportyLoader({ title, body }: { title: string; body: string }) {
  return (
    <main className="auth-shell auth-loading">
      <section className="auth-status-card sport-loader-card" role="status" aria-live="polite">
        <div className="sport-loader">
          {/* Dışa doğru sönerek büyüyen nabız halkaları. */}
          <span className="sport-loader-pulse" aria-hidden="true" />
          <span className="sport-loader-pulse" aria-hidden="true" />
          <svg viewBox="0 0 100 100" aria-hidden="true">
            <circle className="sport-loader-track" cx="50" cy="50" r="42" />
            <circle className="sport-loader-arc" cx="50" cy="50" r="42" />
          </svg>
          <span className="sport-loader-core" aria-hidden="true"><OnboardingIcon name="strength" /></span>
        </div>
        <h1>{title}</h1>
        <p>{body}</p>
        <div className="sport-loader-bars" aria-hidden="true"><i /><i /><i /></div>
      </section>
    </main>
  );
}
