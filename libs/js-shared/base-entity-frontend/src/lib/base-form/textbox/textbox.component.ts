import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

@Component({
  selector: 'base-textbox',
  standalone: true,
  templateUrl: './textbox.component.html',
  imports: [MatFormField, MatInput, MatLabel, FormsModule, ReactiveFormsModule, EntityLabelPipe],
  styles: [
    `
      :host {
        display: block;
      }
      mat-form-field {
        width: 100%;
      }
    `,
  ],
})
export class TextboxComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
