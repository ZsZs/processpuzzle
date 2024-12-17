import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass, NgIf } from '@angular/common';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'base-textarea',
  standalone: true,
  templateUrl: './textarea.component.html',
  imports: [NgIf, NgClass, MatFormField, MatInput, MatLabel, ReactiveFormsModule],
})
export class TextareaComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
