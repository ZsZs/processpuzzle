import { ChangeDetectorRef, inject, OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { Subscription } from 'rxjs';

/**
 * Resolves `key` through Transloco, returning `fallback` when the key is absent or unresolved. Shared
 * by {@link EntityLabelPipe} and the components that need the resolved label as a translation
 * parameter rather than as rendered text.
 */
export function translateLabel(transloco: TranslocoService, key: string | undefined, fallback: string): string {
  if (!key) return fallback;
  const translated = transloco.translate<string>(key);
  return translated && translated !== key ? translated : fallback;
}

/**
 * Translates an entity/attribute name and falls back to the descriptor-provided name.
 *
 * Usage: `{{ descriptor.i18nKey() | ppLabel: descriptor.label }}`.
 *
 * `key` is the full transloco key (`<scope>.<entity>[.<attr>]`) produced by
 * {@link BaseEntityDescriptor.i18nKey} / {@link AbstractAttrDescriptor.i18nKey}. When it is
 * `undefined` (no i18nScope declared) or the key is missing from the loaded translations, the
 * `fallback` — the raw name held on the descriptor — is returned unchanged.
 *
 * Impure so it re-evaluates when the active language or a lazy scope finishes loading.
 */
@Pipe({ name: 'ppLabel', standalone: true, pure: false })
export class EntityLabelPipe implements PipeTransform, OnDestroy {
  private readonly transloco = inject(TranslocoService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);
  private readonly subscription: Subscription;

  constructor() {
    // Re-render on language switch or when a lazily-loaded consumer scope becomes available.
    this.subscription = this.transloco.events$.subscribe(() => this.changeDetectorRef.markForCheck());
  }

  transform(key: string | undefined, fallback: string): string {
    return translateLabel(this.transloco, key, fallback);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
