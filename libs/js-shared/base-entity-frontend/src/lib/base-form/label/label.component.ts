import { Component, computed } from '@angular/core';
import { NgStyle } from '@angular/common';
import { injectActiveLang } from '@processpuzzle/util';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { effectiveDateFormat, formatDateValue, toDateValue } from '../../base-entity/date-format';

@Component({
  selector: 'base-label',
  standalone: true,
  templateUrl: './label.component.html',
  imports: [NgStyle],
})
export class LabelComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  private readonly activeLang = injectActiveLang();
  /** The value as shown: a date in the attribute's `dateFormat` (or the date default) and the active language. */
  readonly text = computed(() => {
    const value = this.value();
    return toDateValue(value) ? formatDateValue(value, effectiveDateFormat(this.config()), this.activeLang()) : value;
  });
}
