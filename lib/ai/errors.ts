// AI katmanının hata sınıfları.
//
// Router'ın "bu sağlayıcı bu işi yapamaz" (atla, yedeğe geç) ile "bu sağlayıcı
// bozuldu" (hata olarak say, telemetriye yaz) durumlarını ayırt edebilmesi
// için ayrı bir tip gerekiyor; mesaj metnine bakarak ayrım yapmak kırılgan.

export class AiUnsupportedRequestError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AiUnsupportedRequestError";
  }
}

/** Zincirdeki HER sağlayıcı başarısız oldu. Kullanıcıya ham hata gösterilmez. */
export class AiAllProvidersFailedError extends Error {
  readonly failures: Array<{ provider: string; message: string }>;
  constructor(failures: Array<{ provider: string; message: string }>) {
    super(`all AI providers failed: ${failures.map((failure) => failure.provider).join(", ") || "none available"}`);
    this.name = "AiAllProvidersFailedError";
    this.failures = failures;
  }
}
