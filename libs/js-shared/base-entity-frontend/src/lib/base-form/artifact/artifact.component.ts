import { Component, inject } from '@angular/core';
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
          <fieldset class="base-entity-form-field" [ngClass]="config().styleClass" [ngStyle]="config().style">
            <legend [ngClass]="config().labelClass">{{ config().label }}</legend>
            <ul [id]="config().attrName" class="base-entity-form-list">
              @if (artifact(); as artifact) {
                <li>
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
})
export class ArtifactComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  private readonly objectStoreService = inject(ObjectStoreService);
  private readonly dialog = inject(MatDialog);

  artifact(): ArtifactAttr | null {
    return this.formGroup.get(this.config().attrName)?.value ?? null;
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
      titleKey: 'base-entity.delete_artifact_confirmation_dialog.title',
      contentKey: 'base-entity.delete_artifact_confirmation_dialog.content',
      contentParams: { artifactName: artifact.name },
      cancelButtonKey: 'base-entity.delete_artifact_confirmation_dialog.cancel_button',
      confirmButtonKey: 'base-entity.delete_artifact_confirmation_dialog.delete_button',
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
