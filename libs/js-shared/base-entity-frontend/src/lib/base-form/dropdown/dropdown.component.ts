import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { BaseEntity } from '../../base-entity/base-entity';

@Component({
  selector: 'base-dropdown',
  standalone: true,
  templateUrl: './dropdown.component.html',
  imports: [ReactiveFormsModule, MatLabel, MatFormField, MatSelectModule],
})
export class DropdownComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
