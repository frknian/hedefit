import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/** Bölüm başlığı: küçük büyük harfli üst etiket + Montserrat başlık + sağda eylem. */
export function SectionHeader({
  eyebrow,
  title,
  action,
  className,
}: {
  eyebrow?: string;
  title: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex items-end justify-between gap-hf-md", className)}>
      <div className="min-w-0">
        {eyebrow && <div className="mb-1 text-[10px] font-bold uppercase tracking-[2px] text-hf-on-surface-variant">{eyebrow}</div>}
        <h2 className="font-hf-display text-[24px] leading-tight font-semibold text-hf-on-surface">{title}</h2>
      </div>
      {action}
    </div>
  );
}
