import { Injectable, Signal, signal, Type, ViewContainerRef } from '@angular/core';
import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseFormControlComponent } from './base-form-control.component';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { LabelComponent } from './label/label.component';
import { DatepickerComponent } from './datepicker/datepicker.component';
import { ForeignKeyComponent } from './foreign-key/foreign-key.component';
import { TextboxComponent } from './textbox/textbox.component';
import { DropdownComponent } from './dropdown/dropdown.component';
import { CheckboxComponent } from './checkbox/checkbox.component';
import { RadioComponent } from './radio/radio.component';
import { TextareaComponent } from './textarea/textarea.component';
import { FlexBoxComponent } from './flex-box/flex-box.component';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FlexboxDescriptor } from '../base-entity/flexboxDescriptor';

@Injectable({ providedIn: 'root' })
export class BaseEntityFormBuilder<Entity extends BaseEntity> {
  // region public methods
  public buildForm(viewContainerRef: ViewContainerRef, baseEntityForm: FormGroup, store: any, attrDescriptors: AbstractAttrDescriptor[], entity: Signal<Entity>): void {
    viewContainerRef.clear();
    attrDescriptors.forEach((column: AbstractAttrDescriptor) => {
      const formControlType = this.createFormControl(column);
      if (formControlType) {
        const componentRef = viewContainerRef.createComponent<BaseFormControlComponent<Entity>>(formControlType);
        componentRef.instance.config = signal(column as BaseEntityAttrDescriptor);
        componentRef.instance.entity = entity;
        componentRef.instance.formGroup = baseEntityForm;
        componentRef.instance.store = store;
        if (column instanceof BaseEntityAttrDescriptor) {
          const currentAttrValue = Reflect.get(entity(), column.attrName);
          componentRef.instance.value = signal(currentAttrValue);
          const formControl = new FormControl({ value: currentAttrValue, disabled: column.disabled }, Validators.required);
          baseEntityForm.addControl(column.attrName, formControl);
        } else if (column instanceof FlexboxDescriptor) {
          this.buildForm((componentRef.instance as FlexBoxComponent<Entity>).flexBoxHost.viewContainerRef, baseEntityForm, store, column.attrDescriptors, entity);
        } else throw Error('Undefined subclass of AbstractAttrDescriptor');
      }
    });
  }

  // endregion

  // region protected, private helper methods
  private createFormControl(column: AbstractAttrDescriptor): Type<BaseFormControlComponent<Entity>> {
    if (column.formControlType === FormControlType.LABEL) {
      return LabelComponent<Entity>;
    } else if (column.formControlType === FormControlType.DATE) {
      return DatepickerComponent<Entity>;
    } else if (column.formControlType === FormControlType.FLEX_BOX) {
      return FlexBoxComponent<Entity>;
    } else if (column.formControlType === FormControlType.FOREIGN_KEY) {
      return ForeignKeyComponent<Entity>;
    } else if (column.formControlType === FormControlType.TEXT_BOX) {
      return TextboxComponent<Entity>;
    } else if (column.formControlType === FormControlType.DROPDOWN) {
      return DropdownComponent<Entity>;
    } else if (column.formControlType === FormControlType.CHECKBOX) {
      return CheckboxComponent<Entity>;
    } else if (column.formControlType === FormControlType.RADIO) {
      return RadioComponent<Entity>;
    } else if (column.formControlType === FormControlType.TEXTAREA) {
      return TextareaComponent<Entity>;
    } else throw Error('Undefined form control type');
  }

  // endregion
}
