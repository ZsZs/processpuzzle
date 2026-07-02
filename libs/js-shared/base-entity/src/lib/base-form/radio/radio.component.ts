import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'base-radio',
  standalone: true,
  templateUrl: './radio.component.html',
  imports: [NgClass, ReactiveFormsModule],
})
export class RadioComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
