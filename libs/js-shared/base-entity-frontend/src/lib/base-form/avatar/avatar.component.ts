import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass, NgStyle } from '@angular/common';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { ArtifactAttr } from '../artifact/artifact-attr';
import { ArtifactSelectorComponent } from '../artifact/artifact-selector.component';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { filter, switchMap } from 'rxjs';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../../dialogs/delete-confirmation.dialog';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

function toArtifact(value: unknown): ArtifactAttr | null {
  if (value == null) return null;
  if (Array.isArray(value)) {
    const item = value.find((v): v is ArtifactAttr => typeof v === 'object' && v !== null);
    return item ?? null;
  }
  return typeof value === 'object' ? (value as ArtifactAttr) : null;
}

@Component({
  selector: 'app-avatar',
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
            <div [id]="config().attrName" class="base-entity-form-avatar-container">
              @if (artifact(); as art) {
                <div class="avatar-item">
                  @if (thumbnailUrlOf(art); as thumbUri) {
                    <img class="avatar-thumbnail" [src]="thumbUri" [alt]="art.name" />
                  } @else if (isDefaultAvatarImage(defaultAvatar())) {
                    <img class="avatar-thumbnail" [src]="defaultAvatar()" [alt]="art.name" />
                  } @else {
                    <mat-icon class="avatar-icon">{{ defaultAvatar() }}</mat-icon>
                  }
                  <a href="" (click)="openArtifact($event, art)">{{ art.name }}</a>
                  @if (!config().disabled) {
                    <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete avatar reference" (click)="deleteArtifact(art)">
                      <mat-icon>cancel</mat-icon>
                    </button>
                  }
                </div>
              } @else {
                <div class="avatar-item avatar-placeholder">
                  @if (isDefaultAvatarImage(defaultAvatar())) {
                    <img class="avatar-thumbnail" [src]="defaultAvatar()" alt="Default avatar" />
                  } @else {
                    <mat-icon class="avatar-icon">{{ defaultAvatar() }}</mat-icon>
                  }
                </div>
              }
              @if (!config().disabled && canAdd()) {
                <app-artifact-selector [accept]="'image/*'" (artifactUploaded)="onArtifactUploaded($event)" />
              }
            </div>
          </fieldset>
        </div>
      }
    }
  `,
  styleUrls: ['../base-entity-form.css'],
  styles: [
    `
      .base-entity-form-avatar-container {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-top: 4px;
      }
      .avatar-item {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .avatar-thumbnail {
        width: 50px;
        height: 50px;
        object-fit: cover;
        border-radius: 4px;
        flex-shrink: 0;
      }
      .avatar-icon {
        width: 50px;
        height: 50px;
        font-size: 50px;
        line-height: 50px;
        flex-shrink: 0;
      }
    `,
  ],
})
export class AvatarComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  private readonly objectStoreService = inject(ObjectStoreService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly valueSignal = signal<unknown>(null);

  /** The single avatar artifact, or null if none uploaded. */
  readonly artifact = computed(() => toArtifact(this.valueSignal()));

  /** The fallback avatar (icon name or image URL) configured on the descriptor, defaulting to 'person'. */
  readonly defaultAvatar = computed(() => this.config().defaultAvatar || 'person');

  /** When not disabled, an upload can be performed to set or replace the avatar. */
  readonly canAdd = computed(() => !this.config().disabled);

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

  isDefaultAvatarImage(avatar: string | undefined): boolean {
    if (!avatar) return false;
    return avatar.includes('/') || avatar.includes('.') || avatar.startsWith('data:');
  }

  thumbnailUrlOf(artifact: ArtifactAttr): string | null {
    return this.thumbnailUrls()[artifact.objectId] ?? null;
  }

  private refreshThumbnails(): void {
    const art = this.artifact();
    if (!art || this.config().showThumbnail === false || !art.mimeType?.startsWith('image/')) {
      this.thumbnailUrls.set({});
      return;
    }

    const known = this.thumbnailUrls();
    if (art.objectId in known) {
      this.thumbnailUrls.set({ [art.objectId]: known[art.objectId] });
      return;
    }

    this.objectStoreService.getThumbnailUriByID(art.bucket, art.objectId).subscribe({
      next: (response) => this.setThumbnailUrl(art.objectId, response?.uri ?? null),
      error: () => this.setThumbnailUrl(art.objectId, null),
    });
  }

  private setThumbnailUrl(objectId: string, uri: string | null): void {
    const art = this.artifact();
    if (art?.objectId !== objectId) return;
    this.thumbnailUrls.set({ [objectId]: uri });
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

    if (!artifact.mimeType?.startsWith('image/')) {
      return;
    }

    this.writeValue(artifact);
  }

  deleteArtifact(artifact: ArtifactAttr | null = this.artifact()): void {
    if (this.config().disabled || !artifact) {
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
        next: () => this.writeValue(null),
      });
  }

  private writeValue(value: ArtifactAttr | null): void {
    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(value);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
