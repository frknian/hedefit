import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";

/**
 * Hap (pill) biçimli etiket ve filtre düğmesi. DESIGN.md: çipler her zaman
 * tam yuvarlak ve `label-sm` tipografisiyle; böylece tıklanabilir KARTLARDAN
 * görsel olarak ayrışırlar.
 *
 * `as="button"` verilmediğinde statik bir rozettir (ör. "Göğüs · Compound").
 */
export type ChipTone = "neutral" | "primary" | "secondary" | "success" | "tertiary";

const toneClass: Record<ChipTone, string> = {
  neutral: "bg-hf-surface-highest text-hf-on-surface-variant",
  primary: "bg-hf-primary-container text-hf-on-primary-container",
  secondary: "bg-hf-secondary-container/15 text-hf-secondary",
  success: "bg-hf-success/20 text-hf-success",
  tertiary: "bg-hf-tertiary-container text-hf-on-tertiary-container",
};

const base = "inline-flex items-center gap-1.5 rounded-hf-full px-3 py-1 text-[12px] font-medium leading-none whitespace-nowrap";

export function Chip({ tone = "neutral", className, ...props }: ComponentProps<"span"> & { tone?: ChipTone }) {
  return <span className={cn(base, toneClass[tone], className)} {...props} />;
}

export function ChipButton({
  tone = "neutral",
  selected = false,
  className,
  type = "button",
  ...props
}: ComponentProps<"button"> & { tone?: ChipTone; selected?: boolean }) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        base,
        "min-h-11 cursor-pointer px-4 transition-colors",
        selected ? toneClass.primary : cn(toneClass[tone], "hover:bg-hf-surface-high"),
        className,
      )}
      {...props}
    />
  );
}
