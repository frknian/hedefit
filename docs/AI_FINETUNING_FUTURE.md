# Gelecekte İnce Ayar (Fine-tuning) — Hazırlık Notu

**Bugün hiçbir eğitim YAPILMIYOR.** Bu belge yalnızca, toplanan verinin
ileride nasıl kullanılabileceğini ve hangi sınırların baştan konduğunu anlatır.

## Neden şimdi değil

- Taban modeli sıfırdan eğitmek bu ürünün ölçeğinde ne gerekli ne mümkün.
- Sürekli/çevrimiçi ağırlık güncellemesi (her sohbetten sonra öğrenen model)
  **bilerek reddedildi**: denetlenemez, geri alınamaz ve bir kullanıcının
  hatalı girdisi tüm kullanıcıları etkileyebilir.
- Kişiselleştirme bugün **veriyle** yapılıyor, ağırlıkla değil: deterministik
  motor + yapılandırılmış hafıza + bağlam getirimi. Bu yaklaşım anında etkili,
  tamamen denetlenebilir ve kullanıcı tarafından silinebilir.

## Bugün toplanan sinyaller

`db/migrations/20260819_ai_memory.sql`:

| Tablo | İçerik | İçermediği |
|---|---|---|
| `ai_feedback` | mesaj kimliği, 👍/👎, sağlayıcı, model, prompt sürümü, kategori | **mesaj metni yok** |
| `ai_provider_events` | kategori, sağlayıcı, model, sonuç, gecikme, token, hata sınıfı | **istek/yanıt metni yok** |

Bu ayrım kasıtlıdır. Bugün ölçmek istediğimiz şey **hangi yapılandırmanın daha
iyi çalıştığı** — bunun için sohbet içeriğini saklamak gerekmiyor. Sağlık
verisini ikinci bir tabloya kopyalamamak, sonradan geri alınamayacak bir
gizlilik borcundan kaçınmaktır.

Bu veriyle bugün bile şu sorular yanıtlanabilir:

- Yerel sağlayıcı yanıtları uzak modele göre ne kadar sık olumsuz oy alıyor?
- Hangi kategori en çok yedeğe düşüyor?
- Prompt v1 → v2 geçişi memnuniyeti artırdı mı?

## İleride veri kümesi kurulacaksa

Bunun için **ayrı ve açık bir kullanıcı onayı** gerekir; mevcut şema sohbet
metnini saklamadığı için bugünkü veriyle eğitim yapılamaz. Bu bir eksiklik
değil, tasarım kararıdır.

Onay alınırsa izlenecek yol:

```
açık rıza (opt-in)
      ↓
küratörlü veri kümesi          yüksek puanlı sohbetler, düzeltilmiş yanıtlar
      ↓
kalite filtresi                güvenlik katmanının engellediği her şey ELENİR
      ↓
gizlilik filtresi              isim, e-posta, konum, serbest metin sağlık notu
                               → anonimleştirme / kayıt tamamen düşürülür
      ↓
LoRA / adapter eğitimi         taban model DEĞİŞMEZ, yalnız adaptör
      ↓
çevrimdışı değerlendirme       sabit bir soru kümesinde v(n) ile karşılaştırma
      ↓
sürümlü dağıtım                geri alınabilir, prompt sürümüyle eşleştirilmiş
```

### Değişmez kurallar

1. **Filtrelenmemiş üretim sohbetleri üzerinde asla eğitim yapılmaz.**
2. Güvenlik katmanının engellediği hiçbir konuşma veri kümesine girmez.
3. Taban model ağırlıkları değiştirilmez; yalnızca adaptör eğitilir.
4. Her adaptör sürümü, çevrimdışı değerlendirmede bir öncekini geçmeden
   yayına alınmaz.
5. Kullanıcı verisini kaldırma talebi veri kümesini de kapsar.

## Mimarinin buna hazır olduğu noktalar

- Her yanıt `provider` + `model` + `promptVersion` ile etiketli → ölçümler
  karışmaz.
- `AIProvider` arayüzü sayesinde ince ayarlı bir model, mevcut sağlayıcıların
  yanına **tek satırla** eklenir (`providerRegistry.register`).
- Router `mode: "remote"` ile gölge değerlendirmeye izin verir.
