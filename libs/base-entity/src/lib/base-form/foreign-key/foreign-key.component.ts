import { Component } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { NgIf } from '@angular/common';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ReactiveFormsModule } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'base-foreign-key',
  standalone: true,
  templateUrl: './foreign-key.component.html',
  imports: [NgIf, MatIcon, MatLabel, MatFormField, MatInput, ReactiveFormsModule],
})
export class ForeignKeyComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  // region event handling methods
  navigateToRelated() {
    this.store.navigateToRelated(this.config().linkedEntityType, this.value());
  }
  // endregion
}
