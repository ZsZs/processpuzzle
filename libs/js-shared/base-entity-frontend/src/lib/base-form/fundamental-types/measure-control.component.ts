import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { injectActiveLang } from '@processpuzzle/util';
import { BaseEntity } from '../../base-entity/base-entity';
import { BaseFormControlComponent } from '../base-form-control.component';
import { UNIT_CATALOG } from './unit-catalog.token';

/**
 * What the `QUANTITY`, `MONEY` and `DIMENSIONS` controls have in common: the attribute is one form control holding
 * an object, the amount fields and the unit or currency select are a form group of the component's own, and every
 * change publishes the group's value to that control — `null` while there is no complete, valid value, so a
 * required attribute blocks Save and an optional one saves nothing rather than a half-typed amount.
 *
 * The group's validity is the attribute's, as `ADDRESS` does it: a field the user left invalid blocks the Save
 * even though the control holds `null`.
 */
@Component({ standalone: true, template: `` })
export abstract class MeasureControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  abstract readonly fields: FormGroup;
  /** Whether changing the unit converts the amount (the default) or keeps the number as typed. */
  readonly convertOnUnitChange = signal(true);

  protected readonly catalog = inject(UNIT_CATALOG);
  protected readonly activeLang = injectActiveLang();
  protected readonly destroyRef = inject(DestroyRef);

  /** Puts the stored value, or the defaults, into {@link fields} — without emitting, nothing has changed yet. */
  protected abstract load(): void;
  /** The amount fields: required when the attribute is, and checked as decimals. */
  protected abstract amountControls(): AbstractControl[];
  /** What the fields hold as the attribute's value; `null` when empty, incomplete or invalid. */
  protected abstract currentValue(): unknown;

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.load();
    if (this.config().required) this.amountControls().forEach((control) => control.addValidators(Validators.required));
    this.fields.updateValueAndValidity({ emitEvent: false });

    const control = this.control();
    if (control) {
      control.addValidators(this.fieldsValidator);
      control.updateValueAndValidity({ emitEvent: false });
      if (control.disabled || this.config().disabled) this.fields.disable({ emitEvent: false });
    }
    this.fields.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.publish());
  }

  // endregion

  // region event handling methods
  /** A disabled attribute or control shows the value without anything to change. */
  isEditable(): boolean {
    return !this.config().disabled && this.control()?.enabled !== false;
  }

  toggleConvert(): void {
    this.convertOnUnitChange.update((converting) => !converting);
  }

  /** The most decimal places an amount may have; unlimited when the attribute sets none. */
  scaleLimit(): number | undefined {
    return this.config().scale;
  }

  /** The translation key suffix of the amount's error, for `base_entity.measure.error.<suffix>`. */
  amountErrorOf(control: AbstractControl): 'amount' | 'scale' | 'required' | undefined {
    if (control.hasError('amount')) return 'amount';
    if (control.hasError('scale')) return 'scale';
    return control.hasError('required') ? 'required' : undefined;
  }

  // endregion

  // region protected, private helper methods
  private readonly fieldsValidator = (): ValidationErrors | null => (this.fields.invalid ? { measure: true } : null);

  protected control(): AbstractControl | null {
    return this.formGroup?.get(this.config().attrName) ?? null;
  }

  protected publish(): void {
    const control = this.control();
    if (!control) return;
    control.setValue(this.currentValue());
    control.markAsDirty();
    control.markAsTouched();
  }

  // endregion
}
