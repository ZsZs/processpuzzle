import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity, BaseEntityAttrDescriptor, BaseFormControlComponent, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { MODULE_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { ModuleTranslationCoverage, ModuleTranslationUploadReport } from './module-translation';
import { ModuleTranslationService } from './module-translation.service';

/** Key root of the control's own words. */
export const MODULE_TRANSLATIONS_I18N_SCOPE = `${MODULE_DEFINITION_I18N_SCOPE}.translations_panel`;

/**
 * The module form's Translations section. Not a form field — nothing here is saved with the module — so it
 * is disabled, like the event parameters list of base-workflow, and the form never submits it.
 */
export class ModuleTranslationsAttrDescriptor extends BaseEntityAttrDescriptor {
  constructor() {
    super('translations', FormControlType.CUSTOM, 'Translations');
    this.component = ModuleTranslationsControlComponent;
    this.disabled = true;
    this.hideInTable = true;
  }
}

/**
 * Where the user fetches and returns the module's translation files. The app is deliberately not a
 * translation editor: *Download* prepares one file per supported language holding every translatable item
 * of the module, the user translates them off-line, and *Upload* takes them back. What the section shows is
 * how far each language is covered, and — after an upload — what was stored and what was not understood.
 *
 * Offered once the module is saved: the files are prepared from the stored draft, so for a module that has
 * only been typed into the form there is nothing yet to prepare them from.
 */
@Component({
  selector: 'pp-module-translations-control',
  standalone: true,
  imports: [EntityLabelPipe, MatButton, TranslocoPipe],
  template: `
    <div class="base-entity-form-field pp-module-translations">
      <span class="pp-module-translations__label">{{ config().i18nKey() | ppLabel: config().label }}</span>
      @if (moduleKey(); as key) {
        <p class="pp-module-translations__hint">{{ scope + '.hint' | transloco }}</p>
        @if (coverage().length) {
          <table class="pp-module-translations__coverage" data-testid="module-translations-coverage">
            <thead>
              <tr>
                <th>{{ scope + '.language' | transloco }}</th>
                <th>{{ scope + '.translated' | transloco }}</th>
              </tr>
            </thead>
            <tbody>
              @for (language of coverage(); track language.locale) {
                <tr [attr.data-testid]="'module-translations-' + language.locale">
                  <td>{{ language.locale }}</td>
                  <td [title]="language.missingKeys.join(', ')">{{ language.translatedCount }} / {{ language.keyCount }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
        <div class="pp-module-translations__actions">
          <button mat-stroked-button type="button" data-testid="module-translations-download" (click)="download(key)">{{ scope + '.download' | transloco }}</button>
          <button mat-stroked-button type="button" data-testid="module-translations-upload" (click)="fileInput.click()">{{ scope + '.upload' | transloco }}</button>
          <input #fileInput type="file" accept=".zip,.json" hidden data-testid="module-translations-file" (change)="upload(key, fileInput)" />
        </div>
        @if (report(); as uploaded) {
          <ul class="pp-module-translations__report" data-testid="module-translations-report">
            @for (language of uploaded.languages; track language.locale) {
              <li>
                <strong>{{ language.locale }}</strong
                >: {{ scope + '.stored' | transloco: { count: language.storedKeys } }}
                @if (language.unknownKeys.length) {
                  <span class="pp-module-translations__warning" [title]="language.unknownKeys.join(', ')">{{ scope + '.unknown' | transloco: { count: language.unknownKeys.length } }}</span>
                }
                @if (language.missingKeys.length) {
                  <span class="pp-module-translations__warning" [title]="language.missingKeys.join(', ')">{{ scope + '.missing' | transloco: { count: language.missingKeys.length } }}</span>
                }
                @if (language.untranslatedKeys.length) {
                  <span class="pp-module-translations__warning" [title]="language.untranslatedKeys.join(', ')">{{
                    scope + '.untranslated' | transloco: { count: language.untranslatedKeys.length }
                  }}</span>
                }
              </li>
            }
          </ul>
        }
        @if (error(); as message) {
          <p class="pp-module-translations__error" data-testid="module-translations-error">{{ message }}</p>
        }
      } @else {
        <p class="pp-module-translations__hint" data-testid="module-translations-save-first">{{ scope + '.save_first' | transloco }}</p>
      }
    </div>
  `,
  styles: [
    `
      .pp-module-translations {
        border: 1px solid var(--mat-sys-outline-variant);
        border-radius: 4px;
        display: flex;
        flex-direction: column;
        gap: 6px;
        padding: 8px 12px;
      }
      .pp-module-translations__label {
        font: var(--mat-sys-label-large);
      }
      .pp-module-translations__hint {
        margin: 0;
        opacity: 0.7;
        font-size: 12px;
      }
      .pp-module-translations__coverage {
        border-collapse: collapse;
        text-align: left;
      }
      .pp-module-translations__coverage th,
      .pp-module-translations__coverage td {
        padding: 2px 16px 2px 0;
      }
      .pp-module-translations__actions {
        display: flex;
        gap: 8px;
      }
      .pp-module-translations__report {
        margin: 0;
        padding-left: 16px;
      }
      .pp-module-translations__warning {
        margin-left: 8px;
        font-size: 12px;
        color: var(--mat-sys-tertiary);
      }
      .pp-module-translations__error {
        margin: 0;
        color: var(--mat-sys-error);
      }
    `,
  ],
})
export class ModuleTranslationsControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  protected readonly scope = MODULE_TRANSLATIONS_I18N_SCOPE;
  private readonly translations = inject(ModuleTranslationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly savedKey = signal<string | undefined>(undefined);

  /** The key of the stored module, once there is one. */
  protected readonly moduleKey = this.savedKey.asReadonly();
  protected readonly coverage = signal<ModuleTranslationCoverage[]>([]);
  protected readonly report = signal<ModuleTranslationUploadReport | undefined>(undefined);
  protected readonly error = signal<string | undefined>(undefined);

  ngOnInit(): void {
    const id = this.formGroup?.get('id');
    const version = this.formGroup?.get('version');
    const refresh = () => this.updateKey(id?.value, version?.value);
    refresh();
    id?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
    version?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
  }

  protected download(moduleKey: string): void {
    this.error.set(undefined);
    this.translations.download(moduleKey).subscribe({
      next: (zip) => this.save(zip, `${moduleKey}-translations.zip`),
      error: (failure: unknown) => this.error.set(this.messageOf(failure)),
    });
  }

  protected upload(moduleKey: string, input: HTMLInputElement): void {
    const file = input.files?.item(0);
    if (!file) return;
    this.error.set(undefined);
    this.translations.upload(moduleKey, file).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loadCoverage(moduleKey);
      },
      error: (failure: unknown) => this.error.set(this.messageOf(failure)),
    });
    // Cleared so that choosing the same file again — after fixing it — still fires `change`.
    input.value = '';
  }

  /** Only a saved module has a draft to prepare the files from; a key typed into a new form is not one. */
  private updateKey(id: unknown, version: unknown): void {
    const key = typeof id === 'string' && id !== '' && version ? id : undefined;
    if (key === this.savedKey()) return;
    this.savedKey.set(key);
    this.report.set(undefined);
    this.coverage.set([]);
    if (key) this.loadCoverage(key);
  }

  private loadCoverage(moduleKey: string): void {
    this.translations.coverage(moduleKey).subscribe({
      next: (coverage) => this.coverage.set(coverage),
      error: () => this.coverage.set([]),
    });
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
