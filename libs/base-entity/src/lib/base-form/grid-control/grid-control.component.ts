import { Component } from '@angular/core';
import { BaseEntity } from '../../base-entity/base-entity';
import { BaseFormControlComponent } from '../base-form-control.component';

@Component({
  selector: 'grid-control',
  imports: [],
  templateUrl: './grid-control.component.html',
})
export class GridControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
