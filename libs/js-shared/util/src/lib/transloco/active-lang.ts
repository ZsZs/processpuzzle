import { inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';

/**
 * The active Transloco language as a signal, for code that renders locale-sensitive output (dates, numbers)
 * and has to follow a language switch without a reload. Must be called in an injection context.
 */
export function injectActiveLang(): Signal<string> {
  const transloco = inject(TranslocoService);
  return toSignal(transloco.langChanges$, { initialValue: transloco.getActiveLang() });
}
