import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MAT_DATE_FORMATS, MAT_NATIVE_DATE_FORMATS, MatDateFormats } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTimepickerModule } from '@angular/material/timepicker';
import { injectActiveLang } from '@processpuzzle/util';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { effectiveDateFormat, formatDateValue, intlOptionsOf, toDateValue, toStoredDate } from '../../base-entity/date-format';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

/**
 * A DATE attribute's control: a date field, plus a time field when the attribute's `dateFormat` has a
 * `timeStyle` other than `none`. Both render in the attribute's style and the active language.
 *
 * The two inputs are not bound to the form control directly. Two accessors on one control do not see each
 * other's writes — a view change is pushed to the model only — so the time field would keep a stale date and
 * write it back. Both read {@link picked} instead, and every pick is committed to the form control in the
 * shape it is saved in: a local `YYYY-MM-DD` for a date-only format, so no UTC shift can move the day; the
 * instant otherwise. A field the entity holds as a `Date` keeps receiving one.
 */
@Component({
  selector: 'base-datepicker',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatTimepickerModule, ReactiveFormsModule, EntityLabelPipe],
  templateUrl: './datepicker.component.html',
  // Per component: the display options are this attribute's style, and the defaults are shared by every picker.
  providers: [{ provide: MAT_DATE_FORMATS, useFactory: (): MatDateFormats => ({ parse: { ...MAT_NATIVE_DATE_FORMATS.parse }, display: { ...MAT_NATIVE_DATE_FORMATS.display } }) }],
  styles: [
    `
      :host {
        display: block;
      }
      .date-time {
        display: flex;
        column-gap: 12px;
      }
      mat-form-field {
        flex: 1 1 0;
        width: 100%;
      }
    `,
  ],
})
export class DatepickerComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  readonly format = computed(() => effectiveDateFormat(this.config()));
  readonly showsDate = computed(() => this.format().dateStyle !== 'none');
  readonly showsTime = computed(() => this.format().timeStyle !== 'none');
  /** Today in the attribute's style, as the example the hint shows in place of a fixed pattern. */
  readonly dateHint = computed(() => formatDateValue(new Date(), { dateStyle: this.format().dateStyle, timeStyle: 'none' }, this.activeLang()));
  readonly timeHint = computed(() => formatDateValue(new Date(), { dateStyle: 'none', timeStyle: this.format().timeStyle }, this.activeLang()));
  readonly picked = signal<Date | null>(null);
  control!: AbstractControl;

  private readonly activeLang = injectActiveLang();
  private readonly dateFormats = inject(MAT_DATE_FORMATS);
  private readonly destroyRef = inject(DestroyRef);
  private holdsDate = false;

  ngOnInit(): void {
    const format = this.format();
    this.dateFormats.display.dateInput = intlOptionsOf({ dateStyle: format.dateStyle, timeStyle: 'none' });
    if (format.timeStyle !== 'none') {
      this.dateFormats.display.timeInput = intlOptionsOf({ dateStyle: 'none', timeStyle: format.timeStyle });
      this.dateFormats.display.timeOptionLabel = intlOptionsOf({ dateStyle: 'none', timeStyle: 'short' });
    }

    const attrName = this.config().attrName;
    const control = this.formGroup.get(attrName);
    if (!control) throw new Error(`Form control '${attrName}' is missing.`);
    this.control = control;
    this.holdsDate = this.control.value instanceof Date;
    this.picked.set(toDateValue(this.control.value) ?? null);
    this.control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      const date = toDateValue(value) ?? null;
      if (date?.getTime() !== this.picked()?.getTime()) this.picked.set(date);
    });
  }

  /** A new day keeps the time of day already picked: the date field parses to midnight. */
  onDateChange(date: Date | null): void {
    const previous = this.picked();
    if (date && previous && this.showsTime() && !Number.isNaN(date.getTime())) {
      date = new Date(date.getFullYear(), date.getMonth(), date.getDate(), previous.getHours(), previous.getMinutes(), previous.getSeconds());
    }
    this.commit(date);
  }

  onTimeChange(date: Date | null): void {
    this.commit(date);
  }

  onBlur(): void {
    this.control.markAsTouched();
  }

  private commit(date: Date | null): void {
    if (date && Number.isNaN(date.getTime())) {
      this.control.setErrors({ ...this.control.errors, matDatepickerParse: true });
      return;
    }
    this.picked.set(date);
    this.control.setValue(date && !this.holdsDate ? toStoredDate(date, this.format()) : date);
    this.control.markAsDirty();
  }
}
