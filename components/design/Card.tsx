import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";

/**
 * Tasarım sisteminin taban yüzeyi (DESIGN.md "Elevation & Depth").
 *
 * Derinlik ağır gölgeyle değil, TONAL KATMANLA kurulur:
 *   plain  → Seviye 1: beyaz/koyu mürekkep yüzey + 1px saç teli kenar
 *   raised → Seviye 2: aynı yüzey + geniş, düşük opaklıkta ortam gölgesi
 *   glass  → yalnız gezinme ve yapışkan eylemler için buzlu cam
 *   accent → yapay zekâ çıktısı gibi vurgulanan içerik (lime tonlu parıltı)
 */
export type CardTone = "plain" | "raised" | "glass" | "accent";

const toneClass: Record<CardTone, string> = {
  plain: "bg-hf-surface-lowest border-hf-hairline shadow-hf-card",
  raised: "bg-hf-surface-lowest border-hf-hairline shadow-hf-raised",
  glass: "hf-glass border-hf-hairline",
  accent: "hf-glass hf-glow border-hf-primary-container/50",
};

export function Card({ tone = "plain", className, ...props }: ComponentProps<"div"> & { tone?: CardTone }) {
  return <div className={cn("rounded-hf-xl border", toneClass[tone], className)} {...props} />;
}
