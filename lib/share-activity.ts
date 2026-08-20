import { isNativeApp } from "./mobile.ts";

// Aktivite görselini sosyal ağlara veren katman. Üç yol var ve hepsi
// gerekiyor:
//   - Native: Android WebView'da navigator.share HİÇ yoktur, o yüzden
//     @capacitor/share zorunlu; eklenti dosya paylaşmak için gerçek bir
//     dosya yolu istediğinden görsel önce Filesystem'e yazılır.
//   - Mobil tarayıcı: Web Share API Level 2 (dosyalı) varsa doğrudan.
//   - Masaüstü: paylaşım yoksa görsel indirilir; kullanıcı elle yükler.

export type ShareOutcome = "shared" | "downloaded" | "cancelled" | "failed";

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const result = String(reader.result || "");
      // "data:image/png;base64,AAAA..." → yalnız base64 gövdesi
      const comma = result.indexOf(",");
      if (comma < 0) { reject(new Error("görsel okunamadı")); return; }
      resolve(result.slice(comma + 1));
    };
    reader.onerror = () => reject(new Error("görsel okunamadı"));
    reader.readAsDataURL(blob);
  });
}

/** Kullanıcının iptal etmesi hata değildir; ayrı ele alınır. */
function isCancellation(error: unknown): boolean {
  const name = (error as { name?: string } | null)?.name;
  const message = String((error as { message?: string } | null)?.message || "");
  return name === "AbortError" || /abort|cancel|dismiss/i.test(message);
}

function downloadFallback(blob: Blob, fileName: string): ShareOutcome {
  try {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    // Nesne URL'i hemen serbest bırakılırsa indirme yarıda kalabiliyor.
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
    return "downloaded";
  } catch {
    return "failed";
  }
}

export async function shareActivityImage(blob: Blob, options: { title: string; text: string; fileName?: string }): Promise<ShareOutcome> {
  const fileName = options.fileName || `hedefit-rota-${Date.now()}.png`;

  if (isNativeApp()) {
    try {
      const [{ Share }, { Filesystem, Directory }] = await Promise.all([
        import("@capacitor/share"),
        import("@capacitor/filesystem"),
      ]);
      const written = await Filesystem.writeFile({
        path: fileName,
        data: await blobToBase64(blob),
        directory: Directory.Cache,
      });
      await Share.share({ title: options.title, text: options.text, files: [written.uri] });
      return "shared";
    } catch (error) {
      return isCancellation(error) ? "cancelled" : "failed";
    }
  }

  const file = new File([blob], fileName, { type: "image/png" });
  if (typeof navigator !== "undefined" && navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ title: options.title, text: options.text, files: [file] });
      return "shared";
    } catch (error) {
      if (isCancellation(error)) return "cancelled";
      return downloadFallback(blob, fileName);
    }
  }

  return downloadFallback(blob, fileName);
}
