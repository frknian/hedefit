// RAG'a hazır bilgi tabanı soyutlaması.
//
// BİLEREK BASİT: bugün Hedefit'in bilgi tabanı birkaç düzine kısa parçadan
// oluşuyor. Bu ölçekte vektör veritabanı kurmak (embedding üretimi, ayrı
// servis, senkronizasyon) hiçbir kalite kazancı vermeden dağıtımı ve maliyeti
// büyütürdü. Bu yüzden retrieval şimdilik anahtar kelime + konu eşleşmesi.
//
// Arayüz (`KnowledgeRetriever`) buna rağmen asenkron ve sağlayıcıdan bağımsız:
// içerik büyüdüğünde `retrieve` gövdesini vektör aramasıyla değiştirmek
// yeterli, çağıran taraf (context-builder) hiç değişmez.
//
// PROVENANS: her parça kaynağını taşır. Model tarafından üretilmiş "tıbbi
// gerçekler" bu tabloya YAZILMAZ; buradaki içerik elle küratörlüdür ve
// genel sağlıklı yaşam bilgisiyle sınırlıdır (tanı/tedavi değil).

export type KnowledgeTopic =
  | "nutrition" | "weight_management" | "strength_training"
  | "walking" | "running" | "recovery" | "sleep" | "hydration" | "habit_building";

export type KnowledgeChunk = {
  id: string;
  title: string;
  topic: KnowledgeTopic;
  content: string;
  source: string;
  updatedAt: string;
};

export interface KnowledgeRetriever {
  retrieve(query: string, options?: { locale?: "tr" | "en"; limit?: number }): Promise<KnowledgeChunk[]>;
}

const UPDATED_AT = "2026-08-19";

// Başlangıç içeriği: yalnızca geniş kabul görmüş, tanı içermeyen ilkeler.
// Kaynak alanı boş bırakılmaz — provenansı olmayan parça eklenmez.
const CHUNKS: KnowledgeChunk[] = [
  {
    id: "protein-basics",
    title: "Günlük protein aralığı",
    topic: "nutrition",
    content: "Düzenli antrenman yapan yetişkinlerde günlük 1,6–2,2 g/kg protein, kas kütlesinin korunması ve geliştirilmesi için yaygın olarak önerilen aralıktır. Kalori açığındayken aralığın üst ucu kas kaybını azaltmaya yardımcı olur.",
    source: "ACSM/ISSN protein position stands (genel ilke)",
    updatedAt: UPDATED_AT,
  },
  {
    id: "deficit-rate",
    title: "Sürdürülebilir kilo verme hızı",
    topic: "weight_management",
    content: "Haftada vücut ağırlığının %0,5–1'i kadar kayıp genellikle sürdürülebilir kabul edilir. Daha hızlı kayıplarda kas ve performans kaybı payı büyür; tartıdaki günlük dalgalanma büyük ölçüde su ve glikojen kaynaklıdır.",
    source: "Genel ağırlık yönetimi ilkeleri",
    updatedAt: UPDATED_AT,
  },
  {
    id: "progressive-overload",
    title: "Kademeli yüklenme",
    topic: "strength_training",
    content: "Güç gelişimi için tek seferde tek değişken artırılır: 1–2 tekrar veya küçük bir ağırlık artışı. Aynı yükü iyi formla ve orta zorlukta (RPE 6–7) iki antrenman tamamladıysan artış zamanı gelmiştir.",
    source: "Genel kuvvet antrenmanı ilkeleri",
    updatedAt: UPDATED_AT,
  },
  {
    id: "step-targets",
    title: "Adım hedefleri",
    topic: "walking",
    content: "Günlük adım sayısını mevcut ortalamanın üzerine kademeli çıkarmak, sabit bir hedefe (ör. 10.000) zorlamaktan daha sürdürülebilirdir. Haftalık %10–20 artış makul bir tempodur.",
    source: "Genel fiziksel aktivite ilkeleri",
    updatedAt: UPDATED_AT,
  },
  {
    id: "recovery-sleep",
    title: "Toparlanma ve uyku",
    topic: "recovery",
    content: "Yetersiz uykuda algılanan zorluk artar, kuvvet ve teknik düşer. Uyku kısaldığında yükü artırmak yerine hacmi korumak veya azaltmak daha iyi sonuç verir.",
    source: "Genel toparlanma ilkeleri",
    updatedAt: UPDATED_AT,
  },
  {
    id: "hydration-basics",
    title: "Sıvı alımı",
    topic: "hydration",
    content: "Susama hissi çoğu kişi için yeterli bir rehberdir; antrenman uzun ve terleme yoğunsa alım artırılır. İdrar renginin açık sarı olması pratik bir göstergedir.",
    source: "Genel hidrasyon ilkeleri",
    updatedAt: UPDATED_AT,
  },
  {
    id: "habit-anchor",
    title: "Alışkanlık kurma",
    topic: "habit_building",
    content: "Yeni bir alışkanlığı var olan sabit bir rutine bağlamak (ör. akşam yemeğinden sonra 15 dakika yürüyüş) motivasyona dayanmaktan daha güvenilirdir. Küçük ve atlanması zor bir eşik seçmek süreklilik sağlar.",
    source: "Genel davranış değişikliği ilkeleri",
    updatedAt: UPDATED_AT,
  },
  {
    id: "running-progression",
    title: "Koşuya başlangıç",
    topic: "running",
    content: "Koşu/yürüyüş dönüşümlü ilerleme (ör. 2 dk koşu – 2 dk yürüyüş) eklem yüklenmesini kademeli artırır. Haftalık toplam mesafede %10'dan büyük sıçramalar aşırı yüklenme riskini büyütür.",
    source: "Genel dayanıklılık antrenmanı ilkeleri",
    updatedAt: UPDATED_AT,
  },
];

// Konu başına anahtar kelimeler. Sorguda geçen kelime bir konuyu işaret
// ediyorsa o konunun parçaları öne çıkar.
const TOPIC_KEYWORDS: Record<KnowledgeTopic, RegExp> = {
  nutrition: /protein|besin|makro|öğün|beslen|yemek|nutrition|meal|macro/i,
  weight_management: /kilo|zayıf|yağ|açık|deficit|weight|fat loss/i,
  strength_training: /ağırlık|kuvvet|set|tekrar|kas|strength|rep|lift|muscle/i,
  walking: /yürü|adım|walk|step/i,
  running: /koş|tempo|maraton|run|jog|pace/i,
  recovery: /dinlen|toparlan|yorgun|ağrı|recover|rest|sore|fatigue/i,
  sleep: /uyku|uyu|sleep/i,
  hydration: /su|sıvı|hidrasyon|water|hydrat/i,
  habit_building: /alışkanlık|süreklilik|motivasyon|habit|consisten|streak/i,
};

function scoreChunk(chunk: KnowledgeChunk, query: string): number {
  let score = 0;
  if (TOPIC_KEYWORDS[chunk.topic].test(query)) score += 2;
  // Başlıktaki kelimelerin doğrudan geçmesi ek puan; kısa kelimeler elenir
  // ("ve", "bir" her sorguda eşleşir ve sıralamayı anlamsızlaştırır.)
  for (const word of chunk.title.toLocaleLowerCase("tr-TR").split(/\W+/)) {
    if (word.length > 3 && query.toLocaleLowerCase("tr-TR").includes(word)) score += 1;
  }
  return score;
}

export const staticKnowledgeRetriever: KnowledgeRetriever = {
  async retrieve(query, options = {}) {
    const limit = options.limit ?? 2;
    return CHUNKS
      .map((chunk) => ({ chunk, score: scoreChunk(chunk, query || "") }))
      // Puanı 0 olan parça alakasızdır; alakasız bilgi bağlamı şişirir ve
      // modelin asıl soruyu kaçırmasına yol açar.
      .filter((entry) => entry.score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, limit)
      .map((entry) => entry.chunk);
  },
};

export function formatKnowledge(chunks: KnowledgeChunk[]): string[] {
  return chunks.map((chunk) => `${chunk.title}: ${chunk.content} (kaynak: ${chunk.source})`);
}
