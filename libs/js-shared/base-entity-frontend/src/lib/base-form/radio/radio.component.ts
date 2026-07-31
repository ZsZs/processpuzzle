import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgClass } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

@Component({
  selector: 'base-radio',
  standalone: true,
  templateUrl: './radio.component.html',
  imports: [NgClass, ReactiveFormsModule, EntityLabelPipe],
})
export class RadioComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {}
