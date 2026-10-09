import { X509Certificate, createPublicKey, createVerify } from "node:crypto";
import { BillingVerificationError } from "./play.ts";

/**
 * App Store (StoreKit 2) işlem doğrulaması. İstemci cihazdaki imzalı işlemi (JWS) gönderir;
 * x5c zinciri Apple Root CA - G3'e kadar doğrulanır ve ES256 imzası kontrol edilir.
 * İstemcinin "satın aldım" beyanı tek başına kanıt sayılmaz.
 */
export const APPLE_PRODUCT_TIERS: Record<string, "plus" | "pro"> = {
  hedefit_plus_monthly: "plus",
  hedefit_plus_yearly: "plus",
  hedefit_premium_monthly: "pro",
  hedefit_premium_yearly: "pro",
};

/** Apple Root CA - G3 SHA-256 parmak izi (https://www.apple.com/certificateauthority/). */
const APPLE_ROOT_G3_SHA256 = "63:34:3A:BF:B8:9A:6A:03:EB:B5:7E:9B:3F:5F:A7:BE:7C:4F:5C:75:6F:30:17:B3:A8:C4:88:C3:65:3E:91:79";

export type AppleTransaction = {
  transactionId: string;
  originalTransactionId: string;
  productId: string;
  bundleId?: string;
  appAccountToken?: string;
  expiresDate?: number;
  revocationDate?: number;
  environment?: string;
  type?: string;
};

function b64urlToBuffer(value: string) {
  return Buffer.from(value.replace(/-/g, "+").replace(/_/g, "/"), "base64");
}

export function verifyAppleJws(jws: string, now = Date.now()): AppleTransaction {
  const parts = jws.split(".");
  if (parts.length !== 3) throw new BillingVerificationError("invalid_jws", "Geçersiz işlem imzası.");
  const header = JSON.parse(b64urlToBuffer(parts[0]).toString("utf8")) as { alg?: string; x5c?: string[] };
  if (header.alg !== "ES256" || !header.x5c || header.x5c.length < 2) throw new BillingVerificationError("invalid_jws", "Geçersiz işlem imzası.");
  const certs = header.x5c.map((der) => new X509Certificate(Buffer.from(der, "base64")));
  for (let i = 0; i < certs.length - 1; i++) {
    if (!certs[i].verify(certs[i + 1].publicKey)) throw new BillingVerificationError("invalid_chain", "Sertifika zinciri doğrulanamadı.");
  }
  const root = certs[certs.length - 1];
  if (root.fingerprint256 !== APPLE_ROOT_G3_SHA256) throw new BillingVerificationError("untrusted_root", "Güvenilmeyen sertifika.");
  for (const cert of certs) {
    if (Date.parse(cert.validFrom) > now || Date.parse(cert.validTo) < now) throw new BillingVerificationError("expired_cert", "Sertifika süresi dolmuş.");
  }
  const verifier = createVerify("SHA256");
  verifier.update(`${parts[0]}.${parts[1]}`);
  const ok = verifier.verify({ key: createPublicKey(certs[0].publicKey.export({ type: "spki", format: "pem" })), dsaEncoding: "ieee-p1363" }, b64urlToBuffer(parts[2]));
  if (!ok) throw new BillingVerificationError("bad_signature", "İşlem imzası geçersiz.");
  return JSON.parse(b64urlToBuffer(parts[1]).toString("utf8")) as AppleTransaction;
}

export function interpretAppleTransaction(tx: AppleTransaction, requestedProductId: string, userId: string, now = Date.now()) {
  if (tx.productId !== requestedProductId || !(tx.productId in APPLE_PRODUCT_TIERS)) throw new BillingVerificationError("unknown_product", "Satın alma bu ürünle eşleşmiyor.");
  const expectedBundle = process.env.APPLE_BUNDLE_ID ?? "com.hedefit.app";
  if (tx.bundleId && tx.bundleId !== expectedBundle) throw new BillingVerificationError("bundle_mismatch", "Satın alma bu uygulamaya ait değil.");
  if (!tx.appAccountToken || tx.appAccountToken.toLowerCase() !== userId.toLowerCase()) throw new BillingVerificationError("account_mismatch", "Satın alma bu hesaba ait değil.");
  const expiresAt = tx.expiresDate ? new Date(tx.expiresDate).toISOString() : null;
  const revoked = typeof tx.revocationDate === "number";
  const entitled = !revoked && expiresAt !== null && Date.parse(expiresAt) > now;
  return {
    productId: tx.productId,
    tier: APPLE_PRODUCT_TIERS[tx.productId],
    state: revoked ? "expired" : entitled ? "active" : "expired",
    expiresAt,
    basePlanId: tx.productId.endsWith("_yearly") ? "yearly" : "monthly",
    token: `apple:${tx.originalTransactionId}`,
    isTest: tx.environment === "Sandbox" || tx.environment === "Xcode",
    entitled,
  };
}
