import { NgClass } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatCheckbox } from '@angular/material/checkbox';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

@Component({
  selector: 'base-checkbox',
  standalone: true,
  templateUrl: './checkbox.component.html',
  styleUrls: ['./checkbox.component.css'],
  imports: [NgClass, MatCheckbox, FormsModule, ReactiveFormsModule, EntityLabelPipe],
})
export class CheckboxComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
