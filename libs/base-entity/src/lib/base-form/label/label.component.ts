import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgIf, NgStyle } from '@angular/common';

@Component({
  selector: 'base-label',
  standalone: true,
  templateUrl: './label.component.html',
  imports: [NgIf, NgStyle],
})
export class LabelComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
