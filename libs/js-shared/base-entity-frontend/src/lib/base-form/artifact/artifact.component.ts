import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass, NgStyle } from '@angular/common';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { ArtifactAttr } from './artifact-attr';
import { ArtifactSelectorComponent } from './artifact-selector.component';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { filter, switchMap } from 'rxjs';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../../dialogs/delete-confirmation.dialog';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { isMultiValued, upperBound } from '../../base-entity/multiplicity';

/**
 * The control value as a list, whatever shape it arrived in: `null` is none, a lone object (a value saved
 * before the attribute became multi-valued) is one, and anything that is not an artifact object is dropped.
 */
function toArtifactList(value: unknown): ArtifactAttr[] {
  if (value == null) return [];
  const values = Array.isArray(value) ? value : [value];
  return values.filter((item): item is ArtifactAttr => typeof item === 'object' && item !== null);
}

const MIME_ICON_TABLE: Array<[RegExp | string, string]> = [
  ['application/pdf', 'picture_as_pdf'],
  [/^application\/(msword|vnd\.openxmlformats-officedocument\.wordprocessingml)/, 'description'],
  [/^application\/(vnd\.ms-excel|vnd\.openxmlformats-officedocument\.spreadsheetml)/, 'table_chart'],
  [/^application\/(vnd\.ms-powerpoint|vnd\.openxmlformats-officedocument\.presentationml)/, 'slideshow'],
  [/^application\/(zip|x-tar|x-7z-compressed|x-rar-compressed)/, 'folder_zip'],
  [/^audio\//, 'audiotrack'],
  [/^video\//, 'movie'],
  [/^text\//, 'article'],
  [/^image\//, 'image'],
];

@Component({
  selector: 'app-artifact',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, NgClass, NgStyle, ArtifactSelectorComponent, MatIconButton, MatIcon, EntityLabelPipe],
  template: `
    @if (config().visible) {
      @if (config().isHeading) {
        <h3 [id]="config().attrName">{{ value() }}</h3>
      } @else {
        <div class="row">
          <fieldset class="base-entity-form-field" tabindex="0" [ngClass]="config().styleClass" [ngStyle]="config().style">
            <legend [ngClass]="config().labelClass">{{ config().i18nKey() | ppLabel: config().label }}</legend>
            <ul [id]="config().attrName" class="base-entity-form-list">
              @for (artifact of artifacts(); track artifact.objectId) {
                <li>
                  @if (thumbnailUrlOf(artifact); as thumbUri) {
                    <img class="artifact-thumbnail" [src]="thumbUri" [alt]="artifact.name" />
                  } @else {
                    <mat-icon class="artifact-icon">{{ mimeIcon(artifact.mimeType) }}</mat-icon>
                  }
                  <a href="" (click)="openArtifact($event, artifact)">{{ artifact.name }}</a>
                  @if (!config().disabled) {
                    <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete artifact reference" (click)="deleteArtifact(artifact)">
                      <mat-icon>cancel</mat-icon>
                    </button>
                  }
                </li>
              }
            </ul>
            @if (!config().disabled && canAdd()) {
              <app-artifact-selector (artifactUploaded)="onArtifactUploaded($event)" />
            }
          </fieldset>
        </div>
      }
    }
  `,
  styleUrls: ['../base-entity-form.css'],
  styles: [
    `
      .base-entity-form-list li {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .artifact-thumbnail {
        width: 32px;
        height: 32px;
        object-fit: cover;
        border-radius: 4px;
        flex-shrink: 0;
      }
      .artifact-icon {
        width: 24px;
        height: 24px;
        font-size: 24px;
        line-height: 24px;
        flex-shrink: 0;
      }
    `,
  ],
})
export class ArtifactComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  private readonly objectStoreService = inject(ObjectStoreService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly valueSignal = signal<unknown>(null);
  /**
   * With an upper bound above 1 (see `multiplicity`) the value is an `ArtifactAttr[]`, an upload appends and
   * a delete removes one; otherwise it is a single `ArtifactAttr` and an upload replaces it.
   */
  readonly multiValued = computed(() => isMultiValued(this.config()));
  /** The artifacts shown, one row each — at most one when single-valued. */
  readonly artifacts = computed(() => {
    const artifacts = toArtifactList(this.valueSignal());
    return this.multiValued() ? artifacts : artifacts.slice(0, 1);
  });
  /** The single-valued view: the one artifact, or null. */
  readonly artifact = computed(() => this.artifacts()[0] ?? null);
  /** A single-valued control can always take an upload (it replaces); a multi-valued one until it is full. */
  readonly canAdd = computed(() => !this.multiValued() || this.artifacts().length < upperBound(this.config()));
  /** Signed thumbnail URIs by `objectId`; null where the store has none. */
  private readonly thumbnailUrls = signal<Readonly<Record<string, string | null>>>({});

  ngOnInit(): void {
    const control = this.formGroup.get(this.config().attrName);
    if (control) {
      this.valueSignal.set(control.value ?? null);
      control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
        this.valueSignal.set(value ?? null);
        this.refreshThumbnails();
      });
    }
    this.refreshThumbnails();
  }

  thumbnailUrlOf(artifact: ArtifactAttr): string | null {
    return this.thumbnailUrls()[artifact.objectId] ?? null;
  }

  /** Fetches a thumbnail once per image artifact and forgets those of artifacts no longer in the value. */
  private refreshThumbnails(): void {
    const known = this.thumbnailUrls();
    const kept: Record<string, string | null> = {};
    const missing: ArtifactAttr[] = [];
    for (const art of this.artifacts()) {
      if (this.config().showThumbnail === false || !art.mimeType?.startsWith('image/')) continue;
      if (art.objectId in known) kept[art.objectId] = known[art.objectId];
      else missing.push(art);
    }
    this.thumbnailUrls.set(kept);
    for (const art of missing) {
      this.objectStoreService.getThumbnailUriByID(art.bucket, art.objectId).subscribe({
        next: (response) => this.setThumbnailUrl(art.objectId, response?.uri ?? null),
        error: () => this.setThumbnailUrl(art.objectId, null),
      });
    }
  }

  private setThumbnailUrl(objectId: string, uri: string | null): void {
    if (!this.artifacts().some((art) => art.objectId === objectId)) return;
    this.thumbnailUrls.update((urls) => ({ ...urls, [objectId]: uri }));
  }

  mimeIcon(mimeType: string | undefined): string {
    if (!mimeType) return 'insert_drive_file';
    for (const [key, icon] of MIME_ICON_TABLE) {
      if (typeof key === 'string' ? mimeType === key : key.test(mimeType)) return icon;
    }
    return 'insert_drive_file';
  }

  openArtifact(event: Event, artifact: ArtifactAttr): void {
    event.preventDefault();

    this.objectStoreService.getObjectUriByID(artifact.bucket, artifact.objectId).subscribe({
      next: ({ uri }) => {
        if (uri) {
          window.open(uri, '_blank', 'noopener,noreferrer');
        }
      },
    });
  }

  onArtifactUploaded(artifact: ArtifactAttr): void {
    if (this.config().disabled) {
      return;
    }

    this.writeValue(this.multiValued() ? [...this.artifacts(), artifact] : artifact);
  }

  /** Deletes `artifact` — the single one when omitted — after the user confirms. */
  deleteArtifact(artifact: ArtifactAttr | null = this.artifact()): void {
    if (this.config().disabled) {
      return;
    }

    if (!artifact) {
      return;
    }

    const dialogData: DeleteConfirmationDialogData = {
      titleKey: 'base_entity.delete_artifact_confirmation_dialog.title',
      contentKey: 'base_entity.delete_artifact_confirmation_dialog.content',
      contentParams: { artifactName: artifact.name },
      cancelButtonKey: 'base_entity.delete_artifact_confirmation_dialog.cancel_button',
      confirmButtonKey: 'base_entity.delete_artifact_confirmation_dialog.delete_button',
    };

    this.dialog
      .open(DeleteConfirmationDialog, { data: dialogData })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        switchMap(() => this.objectStoreService.deleteObjectByID(artifact.bucket, artifact.objectId)),
      )
      .subscribe({
        next: () => this.removeArtifact(artifact),
      });
  }

  private removeArtifact(artifact: ArtifactAttr): void {
    this.writeValue(this.multiValued() ? this.artifacts().filter((art) => art.objectId !== artifact.objectId) : null);
  }

  private writeValue(value: ArtifactAttr | ArtifactAttr[] | null): void {
    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(value);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
