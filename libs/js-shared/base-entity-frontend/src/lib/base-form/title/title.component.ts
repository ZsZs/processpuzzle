import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass, NgStyle } from '@angular/common';

@Component({
  selector: 'base-title',
  standalone: true,
  templateUrl: './title.component.html',
  imports: [NgClass, NgStyle],
})
export class TitleComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
