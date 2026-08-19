package com.hedefit.app.localai

/**
 * Cihaz üstü model kataloğu.
 *
 * Buradaki her değer GERÇEK ve DOĞRULANMIŞTIR: dosya adları, byte boyutları ve
 * SHA-256 özetleri 2026-08-19'da Hugging Face API'sinden (`paths-info`)
 * okunmuştur. Uydurulmuş bir boyut/özet, indirmeyi ya hiç bitmeyen ya da
 * bozuk dosyayı "hazır" sayan bir sisteme çevirirdi.
 *
 * Katalog NEDEN native tarafta?
 * İndirme, bütünlük doğrulaması ve dosya yönetimi native katmanda yapılır;
 * JavaScript'e yalnızca "hangi modeller var, hangisi kurulu" bilgisi gider.
 * Böylece WebView'a keyfi bir indirme URL'si verme imkânı doğmaz — JS yalnız
 * BURADAKİ kimliklerden birini seçebilir (bkz. HedefitLocalAiPlugin).
 *
 * Modellerin tamamı Apache-2.0 lisanslı ve HF üzerinde gate'siz; indirme için
 * kullanıcı hesabı/lisans kabulü gerekmez.
 */
object LocalAiModelCatalog {

    /**
     * @param maxNumTokens Motorun toplam bağlam penceresi (giriş + çıkış).
     *   Mobilde modelin ilan ettiği maksimumu kullanmak bellek israfıdır;
     *   Hedefit istemleri bunun çok altında kalır (bkz. LOCAL_PROMPT_BUDGET).
     * @param minTotalRamMb Bu modeli yüklemeyi denemek için gereken TOPLAM cihaz
     *   RAM'i. Ölçüt keyfi değil: dosya boyutunun yaklaşık iki katı + işletim
     *   sistemi ve WebView payı. Altındaki cihazda yükleme denemesi süreci
     *   öldürür, bu yüzden hiç denenmez.
     */
    data class Entry(
        val id: String,
        val displayName: String,
        val fileName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val sha256: String,
        val maxNumTokens: Int,
        val minTotalRamMb: Int,
        val supportsThinking: Boolean,
    )

    private fun hf(repo: String, file: String) = "https://huggingface.co/$repo/resolve/main/$file?download=true"

    val entries: List<Entry> = listOf(
        Entry(
            id = "qwen3-0.6b-int4",
            displayName = "Hedefit Local AI (kompakt)",
            fileName = "qwen3_0_6b_mixed_int4.litertlm",
            downloadUrl = hf("litert-community/Qwen3-0.6B", "qwen3_0_6b_mixed_int4.litertlm"),
            sizeBytes = 497_664_000L,
            sha256 = "b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9",
            maxNumTokens = 2048,
            minTotalRamMb = 3_072,
            // Qwen3 "thinking" modunu destekler; Hedefit'te KAPALI kullanılır
            // (bkz. LocalAiEngine — görünür akıl yürütme kısa koçluk yanıtında
            // yalnızca gecikme ve token harcar).
            supportsThinking = true,
        ),
        Entry(
            id = "qwen2.5-1.5b-q8",
            displayName = "Hedefit Local AI (dengeli)",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = hf("litert-community/Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"),
            sizeBytes = 1_597_931_520L,
            sha256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
            maxNumTokens = 2048,
            minTotalRamMb = 4_096,
            supportsThinking = false,
        ),
        Entry(
            id = "gemma-4-e2b",
            displayName = "Hedefit Local AI (gelişmiş)",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = hf("litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm"),
            sizeBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            maxNumTokens = 2048,
            minTotalRamMb = 6_144,
            supportsThinking = false,
        ),
    )

    fun byId(id: String?): Entry? = entries.firstOrNull { it.id == id }

    /**
     * Karşılaştırma yapılmadan önceki varsayılan aday.
     *
     * En küçük modeli seçiyoruz çünkü karşılaştırma sonuçlanana kadar
     * "çalışmama" riski en düşük olan seçenek budur; nihai varsayılan
     * docs/AI_MODEL_DECISION.md içindeki ölçümlere göre belirlenir.
     */
    const val DEFAULT_MODEL_ID = "qwen3-0.6b-int4"
}
