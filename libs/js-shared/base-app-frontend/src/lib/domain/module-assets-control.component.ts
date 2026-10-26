import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity, BaseEntityAttrDescriptor, BaseFormControlComponent, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { MODULE_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { ModuleAsset } from './module-asset';
import { ModuleAssetService } from './module-asset.service';

/** Key root of the control's own words. */
export const MODULE_ASSETS_I18N_SCOPE = `${MODULE_DEFINITION_I18N_SCOPE}.assets_panel`;

/**
 * The module form's Assets section. Like the translations section it is not a form field — nothing here is saved
 * with the module — so it is disabled and the form never submits it.
 */
export class ModuleAssetsAttrDescriptor extends BaseEntityAttrDescriptor {
  constructor() {
    super('assets', FormControlType.CUSTOM, 'Assets');
    this.component = ModuleAssetsControlComponent;
    this.disabled = true;
    this.hideInTable = true;
  }
}

/**
 * The files a module brings along — markdown pages, images — listed with their path, type and size, and added one
 * by one, never as an archive. A widget names one as `module:<path>`; a markdown file links the others relatively.
 *
 * Offered once the module is saved, since an asset belongs to the stored draft. Each upload goes to the folder the
 * user typed (empty: the module root), under the file's own name.
 */
@Component({
  selector: 'pp-module-assets-control',
  standalone: true,
  imports: [EntityLabelPipe, MatButton, TranslocoPipe],
  template: `
    <div class="base-entity-form-field pp-module-assets">
      <span class="pp-module-assets__label">{{ config().i18nKey() | ppLabel: config().label }}</span>
      @if (moduleKey(); as key) {
        <p class="pp-module-assets__hint">{{ scope + '.hint' | transloco }}</p>
        @if (assets().length) {
          <table class="pp-module-assets__list" data-testid="module-assets-list">
            <thead>
              <tr>
                <th>{{ scope + '.path' | transloco }}</th>
                <th>{{ scope + '.type' | transloco }}</th>
                <th>{{ scope + '.size' | transloco }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (asset of assets(); track asset.path) {
                <tr [attr.data-testid]="'module-asset-' + asset.path">
                  <td>
                    <code>module:{{ asset.path }}</code>
                  </td>
                  <td>{{ asset.mimeType }}</td>
                  <td>{{ sizeOf(asset) }}</td>
                  <td>
                    <button mat-button type="button" [attr.data-testid]="'module-asset-remove-' + asset.path" (click)="remove(key, asset)">{{ scope + '.remove' | transloco }}</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <p class="pp-module-assets__hint" data-testid="module-assets-none">{{ scope + '.none' | transloco }}</p>
        }
        <div class="pp-module-assets__actions">
          <label class="pp-module-assets__folder">
            <span>{{ scope + '.folder' | transloco }}</span>
            <input #folder type="text" placeholder="docs/img" data-testid="module-assets-folder" />
          </label>
          <button mat-stroked-button type="button" data-testid="module-assets-upload" (click)="fileInput.click()">{{ scope + '.upload' | transloco }}</button>
          <input #fileInput type="file" multiple hidden data-testid="module-assets-file" (change)="upload(key, fileInput, folder.value)" />
        </div>
        @if (error(); as message) {
          <p class="pp-module-assets__error" data-testid="module-assets-error">{{ message }}</p>
        }
      } @else {
        <p class="pp-module-assets__hint" data-testid="module-assets-save-first">{{ scope + '.save_first' | transloco }}</p>
      }
    </div>
  `,
  styles: [
    `
      .pp-module-assets {
        border: 1px solid var(--mat-sys-outline-variant);
        border-radius: 4px;
        display: flex;
        flex-direction: column;
        gap: 6px;
        padding: 8px 12px;
      }
      .pp-module-assets__label {
        font: var(--mat-sys-label-large);
      }
      .pp-module-assets__hint {
        margin: 0;
        opacity: 0.7;
        font-size: 12px;
      }
      .pp-module-assets__list {
        border-collapse: collapse;
        text-align: left;
      }
      .pp-module-assets__list th,
      .pp-module-assets__list td {
        padding: 2px 16px 2px 0;
      }
      .pp-module-assets__actions {
        display: flex;
        gap: 8px;
        align-items: center;
      }
      .pp-module-assets__folder {
        display: flex;
        gap: 6px;
        align-items: center;
      }
      .pp-module-assets__error {
        margin: 0;
        color: var(--mat-sys-error);
      }
    `,
  ],
})
export class ModuleAssetsControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  protected readonly scope = MODULE_ASSETS_I18N_SCOPE;
  private readonly service = inject(ModuleAssetService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly savedKey = signal<string | undefined>(undefined);

  /** The key of the stored module, once there is one. */
  protected readonly moduleKey = this.savedKey.asReadonly();
  protected readonly assets = signal<ModuleAsset[]>([]);
  protected readonly error = signal<string | undefined>(undefined);

  ngOnInit(): void {
    const id = this.formGroup?.get('id');
    const version = this.formGroup?.get('version');
    const refresh = () => this.updateKey(id?.value, version?.value);
    refresh();
    id?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
    version?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
  }

  protected sizeOf(asset: ModuleAsset): string {
    return asset.size < 1024 ? `${asset.size} B` : `${(asset.size / 1024).toFixed(1)} kB`;
  }

  /** Uploads each chosen file, one request apiece, into the folder typed beside the button. */
  protected upload(moduleKey: string, input: HTMLInputElement, folder: string): void {
    const files = Array.from(input.files ?? []);
    if (!files.length) return;
    this.error.set(undefined);
    const prefix = trimSlashes(folder.trim());
    let pending = files.length;
    for (const file of files) {
      this.service.upload(moduleKey, file, prefix ? `${prefix}/${file.name}` : undefined).subscribe({
        next: () => undefined,
        error: (failure: unknown) => {
          this.error.set(this.messageOf(failure));
          if (--pending === 0) this.reload(moduleKey);
        },
        complete: () => {
          if (--pending === 0) this.reload(moduleKey);
        },
      });
    }
    // Cleared so that choosing the same file again — after fixing it — still fires `change`.
    input.value = '';
  }

  protected remove(moduleKey: string, asset: ModuleAsset): void {
    this.error.set(undefined);
    this.service.remove(moduleKey, asset.path).subscribe({
      next: () => this.reload(moduleKey),
      error: (failure: unknown) => this.error.set(this.messageOf(failure)),
    });
  }

  /** Only a saved module has a draft to hold assets; a key typed into a new form is not one. */
  private updateKey(id: unknown, version: unknown): void {
    const key = typeof id === 'string' && id !== '' && version ? id : undefined;
    if (key === this.savedKey()) return;
    this.savedKey.set(key);
    this.assets.set([]);
    if (key) this.reload(key);
  }

  private reload(moduleKey: string): void {
    this.service.list(moduleKey).subscribe({
      next: (assets) => this.assets.set(assets),
      error: () => this.assets.set([]),
    });
  }

  private messageOf(failure: unknown): string {
    if (failure instanceof HttpErrorResponse) {
      const detail = (failure.error as { errorText?: string } | null)?.errorText;
      return detail ?? failure.message;
    }
    return String(failure);
  }
}

/** Without the slashes at either end, which a typed folder may carry and a module path may not. */
function trimSlashes(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && value[start] === '/') start++;
  while (end > start && value[end - 1] === '/') end--;
  return value.slice(start, end);
}
