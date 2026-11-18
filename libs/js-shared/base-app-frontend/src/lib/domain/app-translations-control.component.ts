import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, forkJoin, map, Observable, of, Subscription } from 'rxjs';
import { BaseEntity, BaseEntityAttrDescriptor, BaseFormControlComponent, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { AppTranslationUploadReport } from './app-translation';
import { AppTranslationService } from './app-translation.service';

/** Key root of the control's own words. */
export const APP_TRANSLATIONS_I18N_SCOPE = `${APP_DEFINITION_I18N_SCOPE}.translations_panel`;

/** One language the draft holds texts for, its keys sorted so the list reads the same every time. */
interface StoredLanguage {
  readonly locale: string;
  readonly texts: Record<string, string>;
  readonly keys: string[];
}

/**
 * The application form's Translations section. Not a form field — nothing here is saved with the application — so
 * it is disabled, like the module form's, and the form never submits it.
 */
export class AppTranslationsAttrDescriptor extends BaseEntityAttrDescriptor {
  constructor() {
    super('translations', FormControlType.CUSTOM, 'Translations');
    this.component = AppTranslationsControlComponent;
    this.disabled = true;
    this.hideInTable = true;
  }
}

/**
 * Where the user fetches and returns the texts of the application's own pages. *Download* gives one file per
 * supported language holding what is stored (an empty one for a language nothing is stored for), the user fills them
 * in off-line, and *Upload* takes them back. Unlike a module's, the keys are not derived from the definition: they are
 * whatever the application's widgets name under their `translocoPrefix`, so the section shows what is stored: each
 * language that holds texts, with its count, and — on a click — its keys and texts, read-only.
 *
 * There is no endpoint listing the stored languages, and none is needed: the draft bundle of a language answers an
 * empty object when nothing is stored, so each language the application offers is read and the empty ones are left
 * out. A failed read counts as empty — the list is a view, and Download still gives the authoritative files.
 *
 * Offered once the application is saved, since the texts belong to the stored draft.
 */
@Component({
  selector: 'pp-app-translations-control',
  standalone: true,
  imports: [EntityLabelPipe, MatButton, TranslocoPipe],
  template: `
    <div class="base-entity-form-field pp-app-translations">
      <span class="pp-app-translations__label">{{ config().i18nKey() | ppLabel: config().label }}</span>
      @if (appId(); as id) {
        <p class="pp-app-translations__hint">{{ scope + '.hint' | transloco }}</p>
        @if (stored(); as languages) {
          @if (languages.length) {
            <div class="pp-app-translations__languages" data-testid="app-translations-languages">
              @for (language of languages; track language.locale) {
                <button
                  mat-button
                  type="button"
                  [class.pp-app-translations__language--selected]="selected() === language.locale"
                  [attr.aria-pressed]="selected() === language.locale"
                  [attr.data-testid]="'app-translations-language-' + language.locale"
                  (click)="toggle(language.locale)">
                  {{ language.locale }} · {{ scope + '.stored' | transloco: { count: language.keys.length } }}
                </button>
              }
            </div>
            @if (selectedLanguage(); as language) {
              <table class="pp-app-translations__texts" data-testid="app-translations-texts">
                <thead>
                  <tr>
                    <th>{{ scope + '.key' | transloco }}</th>
                    <th>{{ scope + '.text' | transloco }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (key of language.keys; track key) {
                    <tr>
                      <td class="pp-app-translations__key">{{ key }}</td>
                      <td>{{ language.texts[key] }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          } @else {
            <p class="pp-app-translations__hint" data-testid="app-translations-none">{{ scope + '.none_stored' | transloco }}</p>
          }
        }
        <div class="pp-app-translations__actions">
          <button mat-stroked-button type="button" data-testid="app-translations-download" (click)="download(id)">{{ scope + '.download' | transloco }}</button>
          <button mat-stroked-button type="button" data-testid="app-translations-upload" (click)="fileInput.click()">{{ scope + '.upload' | transloco }}</button>
          <input #fileInput type="file" accept=".zip,.json" hidden data-testid="app-translations-file" (change)="upload(id, fileInput)" />
        </div>
        @if (report(); as uploaded) {
          <ul class="pp-app-translations__report" data-testid="app-translations-report">
            @for (language of uploaded.languages; track language.locale) {
              <li>
                <strong>{{ language.locale }}</strong
                >: {{ scope + '.stored' | transloco: { count: language.storedKeys } }}
              </li>
            }
          </ul>
        }
        @if (error(); as message) {
          <p class="pp-app-translations__error" data-testid="app-translations-error">{{ message }}</p>
        }
      } @else {
        <p class="pp-app-translations__hint" data-testid="app-translations-save-first">{{ scope + '.save_first' | transloco }}</p>
      }
    </div>
  `,
  styles: [
    `
      .pp-app-translations {
        border: 1px solid var(--mat-sys-outline-variant);
        border-radius: 4px;
        display: flex;
        flex-direction: column;
        gap: 6px;
        padding: 8px 12px;
      }
      .pp-app-translations__label {
        font: var(--mat-sys-label-large);
      }
      .pp-app-translations__hint {
        margin: 0;
        opacity: 0.7;
        font-size: 12px;
      }
      .pp-app-translations__actions {
        display: flex;
        gap: 8px;
      }
      .pp-app-translations__languages {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
      }
      .pp-app-translations__language--selected {
        background: var(--mat-sys-secondary-container);
      }
      .pp-app-translations__texts {
        border-collapse: collapse;
        display: block;
        font-size: 12px;
        max-height: 320px;
        overflow: auto;
      }
      .pp-app-translations__texts th,
      .pp-app-translations__texts td {
        border-bottom: 1px solid var(--mat-sys-outline-variant);
        padding: 2px 8px;
        text-align: left;
        vertical-align: top;
      }
      .pp-app-translations__key {
        font-family: monospace;
        white-space: nowrap;
      }
      .pp-app-translations__report {
        margin: 0;
        padding-left: 16px;
      }
      .pp-app-translations__error {
        margin: 0;
        color: var(--mat-sys-error);
      }
    `,
  ],
})
export class AppTranslationsControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  protected readonly scope = APP_TRANSLATIONS_I18N_SCOPE;
  private readonly translations = inject(AppTranslationService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly savedId = signal<string | undefined>(undefined);
  private reading?: Subscription;

  /** The id of the stored application, once there is one. */
  protected readonly appId = this.savedId.asReadonly();
  protected readonly report = signal<AppTranslationUploadReport | undefined>(undefined);
  protected readonly error = signal<string | undefined>(undefined);
  /** The languages holding texts; `undefined` until the first read answers. */
  protected readonly stored = signal<StoredLanguage[] | undefined>(undefined);
  protected readonly selected = signal<string | undefined>(undefined);
  protected readonly selectedLanguage = computed(() => this.stored()?.find((language) => language.locale === this.selected()));

  ngOnInit(): void {
    const id = this.formGroup?.get('id');
    const version = this.formGroup?.get('version');
    const refresh = () => this.updateId(id?.value, version?.value);
    refresh();
    id?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
    version?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
  }

  protected download(appId: string): void {
    this.error.set(undefined);
    this.translations.download(appId).subscribe({
      next: (zip) => this.save(zip, `${appId}-translations.zip`),
      error: (failure: unknown) => this.error.set(this.messageOf(failure)),
    });
  }

  protected upload(appId: string, input: HTMLInputElement): void {
    const file = input.files?.item(0);
    if (!file) return;
    this.error.set(undefined);
    this.translations.upload(appId, file).subscribe({
      next: (report) => {
        this.report.set(report);
        this.readStored(appId);
      },
      error: (failure: unknown) => this.error.set(this.messageOf(failure)),
    });
    // Cleared so that choosing the same file again — after fixing it — still fires `change`.
    input.value = '';
  }

  /** Only a saved application has a draft to hold the texts; an id typed into a new form is not one. */
  private updateId(id: unknown, version: unknown): void {
    const key = typeof id === 'string' && id !== '' && version ? id : undefined;
    if (key === this.savedId()) return;
    this.savedId.set(key);
    this.report.set(undefined);
    this.stored.set(undefined);
    this.selected.set(undefined);
    if (key) this.readStored(key);
  }

  /** Shows a language's texts, or hides them when it is the one already shown. */
  protected toggle(locale: string): void {
    this.selected.update((current) => (current === locale ? undefined : locale));
  }

  /** Reads the draft bundle of every offered language; a newer read replaces one still under way. */
  private readStored(appId: string): void {
    this.reading?.unsubscribe();
    const reads = this.languages().map((locale) => this.readLanguage(appId, locale));
    this.reading = forkJoin(reads)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((languages) => {
        const stored = languages.filter((language): language is StoredLanguage => !!language);
        this.stored.set(stored);
        if (!stored.some((language) => language.locale === this.selected())) this.selected.set(undefined);
      });
  }

  private readLanguage(appId: string, locale: string): Observable<StoredLanguage | undefined> {
    return this.translations.bundle(appId, locale, 'draft').pipe(
      map((texts) => {
        const keys = Object.keys(texts ?? {}).sort((left, right) => left.localeCompare(right));
        return keys.length ? { locale, texts, keys } : undefined;
      }),
      catchError(() => of(undefined)),
    );
  }

  /** The languages the application is offered in, the fallback one included, in the configured order. */
  private languages(): string[] {
    const available = this.transloco.getAvailableLangs().map((lang) => (typeof lang === 'string' ? lang : lang.id));
    return [...new Set([...available, 'en'])];
  }

  private save(zip: Blob, fileName: string): void {
    const url = URL.createObjectURL(zip);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  private messageOf(failure: unknown): string {
    if (failure instanceof HttpErrorResponse) {
      const detail = (failure.error as { errorText?: string } | null)?.errorText;
      return detail ?? failure.message;
    }
    return String(failure);
  }
}
