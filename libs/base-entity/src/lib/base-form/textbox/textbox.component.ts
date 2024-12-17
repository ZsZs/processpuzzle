import { NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'base-textbox',
  standalone: true,
  templateUrl: './textbox.component.html',
  imports: [NgIf, MatFormField, MatInput, MatLabel, FormsModule, ReactiveFormsModule],
})
export class TextboxComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
