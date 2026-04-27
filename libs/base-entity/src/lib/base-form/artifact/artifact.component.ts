import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgStyle } from '@angular/common';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { ArtifactAttr } from './artifact-attr';
import { ArtifactSelectorComponent } from './artifact-selector.component';

@Component({
  selector: 'app-artifact',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, NgStyle, ArtifactSelectorComponent],
  template: `
    @if (config().visible) {
      @if (config().isHeading) {
        <h3 [id]="config().attrName">{{ value() }}</h3>
      } @else {
        @if (artifacts().length) {
          <ul [id]="config().attrName" [ngStyle]="config().style">
            @for (artifact of artifacts(); track artifact.objectId) {
              <li>
                <a href="" (click)="openArtifact($event, artifact)">{{ artifact.name }}</a>
              </li>
            }
          </ul>
        }
        <app-artifact-selector (artifactUploaded)="onArtifactUploaded($event)" />
      }
    }
  `,
  styles: ``,
})
export class ArtifactComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  private readonly objectStoreService = inject(ObjectStoreService);

  artifacts(): ArtifactAttr[] {
    const value = this.formGroup.get(this.config().attrName)?.value;
    return Array.isArray(value) ? value : [];
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
    control?.setValue([...this.artifacts(), artifact]);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
