import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";

/**
 * Başlık çubuğundaki dairesel ikon düğmesi (bildirim, ayar, takvim…).
 * Dokunma hedefi 44px'in altına inmez; ikon görsel olarak daha küçüktür.
 */
export function IconButton({ className, type = "button", ...props }: ComponentProps<"button">) {
  return (
    <button
      type={type}
      className={cn(
        "grid size-11 shrink-0 cursor-pointer place-items-center rounded-hf-full text-hf-on-surface-variant transition-colors",
        "hover:bg-hf-surface-highest hover:text-hf-primary aria-pressed:bg-hf-primary-container aria-pressed:text-hf-on-primary-container",
        className,
      )}
      {...props}
    />
  );
}
