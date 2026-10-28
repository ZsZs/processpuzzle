import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BaseEntity, BaseFormControlComponent } from '@processpuzzle/base-entity';
import { Observable } from 'rxjs';

/** One file an owner brings along, as the list endpoint reports it. */
export interface OwnedAsset {
  readonly path: string;
  readonly mimeType: string;
  readonly size: number;
  readonly sha256: string;
}

/** What the Assets section needs of the service behind it: a module's or an application's. */
export interface OwnedAssetService {
  list(ownerKey: string): Observable<OwnedAsset[]>;
  upload(ownerKey: string, file: File, path?: string): Observable<OwnedAsset>;
  remove(ownerKey: string, path: string): Observable<void>;
}

/** The markup the module and the application form share; the two differ in words, scheme and test ids only. */
export const ASSETS_CONTROL_TEMPLATE = `
    <div class="base-entity-form-field pp-assets">
      <span class="pp-assets__label">{{ config().i18nKey() | ppLabel: config().label }}</span>
      @if (ownerKey(); as key) {
        <p class="pp-assets__hint">{{ scope + '.hint' | transloco }}</p>
        @if (assets().length) {
          <table class="pp-assets__list" [attr.data-testid]="testIdPrefix + '-assets-list'">
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
                <tr [attr.data-testid]="testIdPrefix + '-asset-' + asset.path">
                  <td>
                    <code>{{ scheme }}:{{ asset.path }}</code>
                  </td>
                  <td>{{ asset.mimeType }}</td>
                  <td>{{ sizeOf(asset) }}</td>
                  <td>
                    <button mat-button type="button" [attr.data-testid]="testIdPrefix + '-asset-remove-' + asset.path" (click)="remove(key, asset)">{{ scope + '.remove' | transloco }}</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <p class="pp-assets__hint" [attr.data-testid]="testIdPrefix + '-assets-none'">{{ scope + '.none' | transloco }}</p>
        }
        <div class="pp-assets__actions">
          <label class="pp-assets__folder">
            <span>{{ scope + '.folder' | transloco }}</span>
            <input #folder type="text" placeholder="docs/img" [attr.data-testid]="testIdPrefix + '-assets-folder'" />
          </label>
          <button mat-stroked-button type="button" [attr.data-testid]="testIdPrefix + '-assets-upload'" (click)="fileInput.click()">{{ scope + '.upload' | transloco }}</button>
          <input #fileInput type="file" multiple hidden [attr.data-testid]="testIdPrefix + '-assets-file'" (change)="upload(key, fileInput, folder.value)" />
        </div>
        @if (error(); as message) {
          <p class="pp-assets__error" [attr.data-testid]="testIdPrefix + '-assets-error'">{{ message }}</p>
        }
      } @else {
        <p class="pp-assets__hint" [attr.data-testid]="testIdPrefix + '-assets-save-first'">{{ scope + '.save_first' | transloco }}</p>
      }
    </div>
  `;

export const ASSETS_CONTROL_STYLES = `
      .pp-assets {
        border: 1px solid var(--mat-sys-outline-variant);
        border-radius: 4px;
        display: flex;
        flex-direction: column;
        gap: 6px;
        padding: 8px 12px;
      }
      .pp-assets__label {
        font: var(--mat-sys-label-large);
      }
      .pp-assets__hint {
        margin: 0;
        opacity: 0.7;
        font-size: 12px;
      }
      .pp-assets__list {
        border-collapse: collapse;
        text-align: left;
      }
      .pp-assets__list th,
      .pp-assets__list td {
        padding: 2px 16px 2px 0;
      }
      .pp-assets__actions {
        display: flex;
        gap: 8px;
        align-items: center;
      }
      .pp-assets__folder {
        display: flex;
        gap: 6px;
        align-items: center;
      }
      .pp-assets__error {
        margin: 0;
        color: var(--mat-sys-error);
      }
    `;

/**
 * The logic of an Assets section: the files an owner — a module or an application — brings along, listed with
 * their path, type and size, and added one by one, never as an archive. A widget names one as `<scheme>:<path>`.
 *
 * Offered once the owner is saved, since an asset belongs to the stored draft. Each upload goes to the folder the
 * user typed (empty: the owner's root), under the file's own name.
 */
// A component rather than a directive, because it extends one; the subclasses bring the markup.
@Component({ template: '' })
export abstract class AssetsControlBase<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  /** Key root of the section's own words. */
  protected abstract readonly scope: string;
  /** What a widget writes before the path: `module` or `app`. */
  protected abstract readonly scheme: string;
  /** Prefix of the section's test ids. */
  protected abstract readonly testIdPrefix: string;
  protected abstract readonly service: OwnedAssetService;

  private readonly destroyRef = inject(DestroyRef);
  private readonly savedKey = signal<string | undefined>(undefined);

  /** The key of the stored owner, once there is one. */
  protected readonly ownerKey = this.savedKey.asReadonly();
  protected readonly assets = signal<OwnedAsset[]>([]);
  protected readonly error = signal<string | undefined>(undefined);

  ngOnInit(): void {
    const id = this.formGroup?.get('id');
    const version = this.formGroup?.get('version');
    const refresh = () => this.updateKey(id?.value, version?.value);
    refresh();
    id?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
    version?.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(refresh);
  }

  protected sizeOf(asset: OwnedAsset): string {
    return asset.size < 1024 ? `${asset.size} B` : `${(asset.size / 1024).toFixed(1)} kB`;
  }

  /** Uploads each chosen file, one request apiece, into the folder typed beside the button. */
  protected upload(ownerKey: string, input: HTMLInputElement, folder: string): void {
    const files = Array.from(input.files ?? []);
    if (!files.length) return;
    this.error.set(undefined);
    const prefix = trimSlashes(folder.trim());
    let pending = files.length;
    for (const file of files) {
      this.service.upload(ownerKey, file, prefix ? `${prefix}/${file.name}` : undefined).subscribe({
        next: () => undefined,
        error: (failure: unknown) => {
          this.error.set(this.messageOf(failure));
          if (--pending === 0) this.reload(ownerKey);
        },
        complete: () => {
          if (--pending === 0) this.reload(ownerKey);
        },
      });
    }
    // Cleared so that choosing the same file again — after fixing it — still fires `change`.
    input.value = '';
  }

  protected remove(ownerKey: string, asset: OwnedAsset): void {
    this.error.set(undefined);
    this.service.remove(ownerKey, asset.path).subscribe({
      next: () => this.reload(ownerKey),
      error: (failure: unknown) => this.error.set(this.messageOf(failure)),
    });
  }

  /** Only a saved owner has a draft to hold assets; a key typed into a new form is not one. */
  private updateKey(id: unknown, version: unknown): void {
    const key = typeof id === 'string' && id !== '' && version ? id : undefined;
    if (key === this.savedKey()) return;
    this.savedKey.set(key);
    this.assets.set([]);
    if (key) this.reload(key);
  }

  private reload(ownerKey: string): void {
    this.service.list(ownerKey).subscribe({
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

/** Without the slashes at either end, which a typed folder may carry and an asset path may not. */
function trimSlashes(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && value[start] === '/') start++;
  while (end > start && value[end - 1] === '/') end--;
  return value.slice(start, end);
}
