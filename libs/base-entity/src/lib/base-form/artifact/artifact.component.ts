import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass, NgStyle } from '@angular/common';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { ArtifactAttr } from './artifact-attr';
import { ArtifactSelectorComponent } from './artifact-selector.component';

@Component({
  selector: 'app-artifact',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, NgClass, NgStyle, ArtifactSelectorComponent],
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
                </li>
              }
            </ul>
            <app-artifact-selector (artifactUploaded)="onArtifactUploaded($event)" />
          </fieldset>
        </div>
      }
    }
  `,
  styleUrls: ['../base-entit-form.css'],
})
export class ArtifactComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  private readonly objectStoreService = inject(ObjectStoreService);

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
    const control = this.formGroup.get(this.config().attrName);
    control?.setValue(artifact);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
