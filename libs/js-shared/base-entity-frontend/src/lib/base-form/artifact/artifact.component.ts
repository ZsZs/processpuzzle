import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
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
import { DeleteArtifactConfirmationDialog, DeleteArtifactConfirmationDialogData } from '../../dialogs/delete-artifact-confirmation.dialog';

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
  imports: [FormsModule, ReactiveFormsModule, NgClass, NgStyle, ArtifactSelectorComponent, MatIconButton, MatIcon],
  template: `
    @if (config().visible) {
      @if (config().isHeading) {
        <h3 [id]="config().attrName">{{ value() }}</h3>
      } @else {
        <div class="row">
          <fieldset class="base-entity-form-field" tabindex="0" [ngClass]="config().styleClass" [ngStyle]="config().style">
            <legend [ngClass]="config().labelClass">{{ config().label }}</legend>
            <ul [id]="config().attrName" class="base-entity-form-list">
              @if (artifact(); as artifact) {
                <li>
                  @if (thumbnailUrl(); as thumbUri) {
                    <img class="artifact-thumbnail" [src]="thumbUri" [alt]="artifact.name" />
                  } @else {
                    <mat-icon class="artifact-icon">{{ mimeIcon(artifact.mimeType) }}</mat-icon>
                  }
                  <a href="" (click)="openArtifact($event, artifact)">{{ artifact.name }}</a>
                  @if (!config().disabled) {
                    <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete artifact reference" (click)="deleteArtifact()">
                      <mat-icon>cancel</mat-icon>
                    </button>
                  }
                </li>
              }
            </ul>
            @if (!config().disabled) {
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
  private readonly artifactSignal = signal<ArtifactAttr | null>(null);
  readonly artifact = this.artifactSignal.asReadonly();
  private readonly thumbnailUrlSignal = signal<string | null>(null);
  readonly thumbnailUrl = this.thumbnailUrlSignal.asReadonly();

  ngOnInit(): void {
    const control = this.formGroup.get(this.config().attrName);
    if (control) {
      this.artifactSignal.set(control.value ?? null);
      control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
        this.artifactSignal.set(value ?? null);
        this.refreshThumbnail();
      });
    }
    this.refreshThumbnail();
  }

  private refreshThumbnail(): void {
    const art = this.artifact();
    const cfg = this.config();
    if (!art || cfg.showThumbnail === false || !art.mimeType?.startsWith('image/')) {
      this.thumbnailUrlSignal.set(null);
      return;
    }
    this.objectStoreService.getThumbnailUriByID(art.bucket, art.objectId).subscribe({
      next: (response) => this.thumbnailUrlSignal.set(response?.uri ?? null),
      error: () => this.thumbnailUrlSignal.set(null),
    });
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

    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(artifact);
    control?.markAsDirty();
    control?.markAsTouched();
  }

  deleteArtifact(): void {
    if (this.config().disabled) {
      return;
    }

    const artifact = this.artifact();
    if (!artifact) {
      return;
    }

    const dialogData: DeleteArtifactConfirmationDialogData = {
      titleKey: 'base_entity.delete_artifact_confirmation_dialog.title',
      contentKey: 'base_entity.delete_artifact_confirmation_dialog.content',
      contentParams: { artifactName: artifact.name },
      cancelButtonKey: 'base_entity.delete_artifact_confirmation_dialog.cancel_button',
      confirmButtonKey: 'base_entity.delete_artifact_confirmation_dialog.delete_button',
    };

    this.dialog
      .open(DeleteArtifactConfirmationDialog, { data: dialogData })
      .afterClosed()
      .pipe(
        filter((confirmed): confirmed is true => confirmed === true),
        switchMap(() => this.objectStoreService.deleteObjectByID(artifact.bucket, artifact.objectId)),
      )
      .subscribe({
        next: () => this.clearArtifact(),
      });
  }

  private clearArtifact(): void {
    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(null);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
