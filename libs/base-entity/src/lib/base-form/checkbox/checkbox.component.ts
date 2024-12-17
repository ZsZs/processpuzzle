import { NgClass, NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatCheckbox } from '@angular/material/checkbox';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';

@Component({
  selector: 'base-checkbox',
  standalone: true,
  templateUrl: './checkbox.component.html',
  styles: `
    mat-checkbox {
      margin-bottom: 12px;
    }
  `,
  imports: [NgClass, NgIf, MatCheckbox, FormsModule, ReactiveFormsModule],
})
export class CheckboxComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
