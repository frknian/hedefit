import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { Card } from "./Card";

/**
 * Tek bir sayıyı öne çıkaran kutucuk (VKİ, haftalık gün, kalan kalori…).
 * Sayı `display` ailesinden (Montserrat) gelir: DESIGN.md, sayısal veriyi
 * uzaktan okunacak kadar iri istiyor.
 */
export function StatTile({
  label,
  value,
  unit,
  hint,
  className,
}: {
  label: string;
  value: ReactNode;
  unit?: string;
  hint?: ReactNode;
  className?: string;
}) {
  return (
    <Card className={cn("flex flex-col gap-hf-base p-hf-md", className)}>
      <span className="text-[11px] font-medium uppercase tracking-wider text-hf-on-surface-variant">{label}</span>
      <strong className="font-hf-display text-[26px] leading-none font-bold text-hf-on-surface">
        {value}
        {unit && <small className="ml-1 text-[13px] font-medium text-hf-on-surface-variant">{unit}</small>}
      </strong>
      {hint && <small className="text-[11px] leading-snug text-hf-on-surface-variant">{hint}</small>}
    </Card>
  );
}
