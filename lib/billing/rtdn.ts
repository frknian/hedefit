// Google Play Real-time Developer Notifications (Pub/Sub push) zarfı ve içeriği.

export type RtdnKind = "subscription" | "voided" | "test" | "other";

export type ParsedRtdn = {
  messageId: string;
  kind: RtdnKind;
  packageName: string | null;
  notificationType: string;
  purchaseToken: string | null;
};

type PushEnvelope = { message?: { data?: string; messageId?: string } };

type RtdnPayload = {
  packageName?: string;
  subscriptionNotification?: { notificationType?: number; purchaseToken?: string };
  voidedPurchaseNotification?: { purchaseToken?: string; productType?: number };
  testNotification?: unknown;
};

// subscriptionNotification.notificationType (Play belgeleri): ad yalnız günlük/teşhis içindir;
// karar HER ZAMAN Google'dan yeniden okunan gerçek abonelik durumuna göre verilir.
const SUBSCRIPTION_TYPE_NAMES: Record<number, string> = {
  1: "RECOVERED", 2: "RENEWED", 3: "CANCELED", 4: "PURCHASED", 5: "ON_HOLD", 6: "IN_GRACE_PERIOD",
  7: "RESTARTED", 8: "PRICE_CHANGE_CONFIRMED", 9: "DEFERRED", 10: "PAUSED", 11: "PAUSE_SCHEDULE_CHANGED",
  12: "REVOKED", 13: "EXPIRED", 17: "ITEM_REPLACED", 18: "ITEM_REPLACED_CANCELED", 20: "PENDING_PURCHASE_CANCELED",
};

/** Bozuk zarf/içerik için null (çağıran 200 ile onaylar: zehirli mesaj sonsuz yeniden denenmesin). */
export function parsePubSubPush(body: unknown): ParsedRtdn | null {
  const envelope = body as PushEnvelope | null;
  const message = envelope?.message;
  if (!message || typeof message.data !== "string" || typeof message.messageId !== "string" || !message.messageId) return null;

  let payload: RtdnPayload;
  try {
    const bytes = Uint8Array.from(atob(message.data), (char) => char.charCodeAt(0));
    payload = JSON.parse(new TextDecoder().decode(bytes)) as RtdnPayload;
  } catch {
    return null;
  }
  if (!payload || typeof payload !== "object") return null;
  const packageName = typeof payload.packageName === "string" ? payload.packageName : null;

  if (payload.testNotification) {
    return { messageId: message.messageId, kind: "test", packageName, notificationType: "TEST", purchaseToken: null };
  }
  const sub = payload.subscriptionNotification;
  if (sub && typeof sub.purchaseToken === "string" && sub.purchaseToken) {
    return {
      messageId: message.messageId,
      kind: "subscription",
      packageName,
      notificationType: SUBSCRIPTION_TYPE_NAMES[sub.notificationType ?? -1] ?? `TYPE_${sub.notificationType ?? "?"}`,
      purchaseToken: sub.purchaseToken,
    };
  }
  const voided = payload.voidedPurchaseNotification;
  // productType 1 = abonelik; tek seferlik ürünümüz yok.
  if (voided && typeof voided.purchaseToken === "string" && voided.purchaseToken && voided.productType !== 2) {
    return { messageId: message.messageId, kind: "voided", packageName, notificationType: "VOIDED", purchaseToken: voided.purchaseToken };
  }
  return { messageId: message.messageId, kind: "other", packageName, notificationType: "OTHER", purchaseToken: null };
}
