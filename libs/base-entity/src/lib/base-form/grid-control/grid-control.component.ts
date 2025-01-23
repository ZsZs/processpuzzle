import { Component } from '@angular/core';
import { BaseEntity } from '../../base-entity/base-entity';
import { BaseFormControlComponent } from '../base-form-control.component';

@Component({
  selector: 'flex-control',
  imports: [],
  templateUrl: './flex-control.component.html',
  styleUrl: './flex-control.component.css',
})
export class GridControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
