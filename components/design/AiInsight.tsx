import type { ReactNode } from "react";
import { Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { Chip, type ChipTone } from "./Chip";

/**
 * "AI Feedback Bubble" (DESIGN.md → Dynamic Elements): yapay zekâ üretimi
 * içerik, uygulamanın kendi verisinden buzlu cam + lime parıltıyla ayrışır.
 * Aynı bileşen plan gerekçesi, güvenlik notu ve haftalık değerlendirmede
 * kullanılır; üç yerde üç ayrı kutu çizmek yerine tek kaynak.
 */
export function AiInsight({
  title,
  status,
  statusTone = "success",
  children,
  className,
}: {
  title: string;
  status?: string;
  statusTone?: ChipTone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("hf-glass hf-glow flex items-start gap-4 rounded-hf-xl border border-hf-primary-container/50 p-4", className)}>
      <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-hf-md bg-hf-primary-container text-hf-on-primary-container">
        <Sparkles className="size-4" aria-hidden />
      </span>
      <div className="min-w-0">
        <h4 className="flex flex-wrap items-center gap-2 text-[14px] font-bold text-hf-on-surface">
          {title}
          {status && <Chip tone={statusTone} className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide">{status}</Chip>}
        </h4>
        <div className="mt-1 text-[13px] leading-relaxed text-hf-on-surface-variant">{children}</div>
      </div>
    </div>
  );
}
