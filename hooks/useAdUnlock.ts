"use client";

import { useState } from "react";
import { authorizedFetch } from "@/lib/api-client";
import { isNativeApp, showRewardedAd } from "@/lib/mobile";
import type { UsageFeature } from "@/lib/usage-limits";

const REWARDED_AD_UNIT_ID = process.env.NEXT_PUBLIC_ADMOB_REWARDED_AD_UNIT_ID || "ca-app-pub-3940256099942544/5224354917";

export type AdUnlockStatus = "idle" | "granted" | "failed";

/**
 * Ödüllü reklam izleyip ilgili özellik için +1 günlük hak kazandırır (bkz.
 * app/api/ads/reward/route.ts, lib/usage-limits.ts grantAdBonus). Yalnızca
 * native uygulamada anlamlıdır; web'de showButton hep false döner.
 */
export function useAdUnlock(feature: UsageFeature) {
  const [watching, setWatching] = useState(false);
  const [status, setStatus] = useState<AdUnlockStatus>("idle");

  async function watchAd(): Promise<boolean> {
    setWatching(true);
    setStatus("idle");
    try {
      const { rewarded } = await showRewardedAd(REWARDED_AD_UNIT_ID);
      if (!rewarded) {
        setStatus("failed");
        return false;
      }
      const response = await authorizedFetch("/api/ads/reward", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ feature }),
      });
      if (!response.ok) {
        setStatus("failed");
        return false;
      }
      setStatus("granted");
      return true;
    } catch {
      setStatus("failed");
      return false;
    } finally {
      setWatching(false);
    }
  }

  return { watchAd, watching, status, showButton: isNativeApp() };
}
