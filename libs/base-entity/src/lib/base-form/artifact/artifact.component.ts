import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgStyle } from '@angular/common';
import { ObjectStoreService } from '../../object-store/object-store.service';
import { ArtifactAttr } from './artifact-attr';

@Component({
  selector: 'app-artifact',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, NgStyle],
  template: `
    @if (config().visible) {
      @if (config().isHeading) {
        <h3 [id]="config().attrName">{{ value() }}</h3>
      } @else {
        @if (value()?.length) {
          <ul [id]="config().attrName" [ngStyle]="config().style">
            @for (artifact of value(); track artifact.objectId) {
              <li>
                <a href="" (click)="openArtifact($event, artifact)">{{ artifact.name }}</a>
              </li>
            }
          </ul>
        }
      }
    }
  `,
  styles: ``,
})
export class ArtifactComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  private readonly objectStoreService = inject(ObjectStoreService);

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
}
