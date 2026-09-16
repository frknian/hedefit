export function foodSearchQueries(query: string): string[] {
  const clean = query.trim().replace(/\s+/g, " ").slice(0, 80);
  // Kullanıcının yazdığı Türkçe adı İngilizce sağlayıcı terimine dönüştürmek,
  // arayüzde "yumurta" yerine "egg" gibi sonuçlar üretiyordu. Katalog
  // araması artık her besin için kullanıcının dili ve yazdığı adla sınırlı.
  return clean.length >= 2 ? [clean] : [];
}
