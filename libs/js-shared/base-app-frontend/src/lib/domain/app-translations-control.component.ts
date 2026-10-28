import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity, BaseEntityAttrDescriptor, BaseFormControlComponent, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { AppTranslationUploadReport } from './app-translation';
import { AppTranslationService } from './app-translation.service';

/** Key root of the control's own words. */
export const APP_TRANSLATIONS_I18N_SCOPE = `${APP_DEFINITION_I18N_SCOPE}.translations_panel`;

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
 * whatever the application's widgets name under their `translocoPrefix`, so the section only says what was stored.
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
  private readonly destroyRef = inject(DestroyRef);
  private readonly savedId = signal<string | undefined>(undefined);

  /** The id of the stored application, once there is one. */
  protected readonly appId = this.savedId.asReadonly();
  protected readonly report = signal<AppTranslationUploadReport | undefined>(undefined);
  protected readonly error = signal<string | undefined>(undefined);

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
      next: (report) => this.report.set(report),
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
