import { inject, Injectable, Signal, Type, ViewContainerRef } from '@angular/core';
import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseFormControlComponent } from './base-form-control.component';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ArtifactComponent } from './artifact/artifact.component';
import { LabelComponent } from './label/label.component';
import { DatepickerComponent } from './datepicker/datepicker.component';
import { ForeignKeyComponent } from './foreign-key/foreign-key.component';
import { TextboxComponent } from './textbox/textbox.component';
import { DropdownComponent } from './dropdown/dropdown.component';
import { CheckboxComponent } from './checkbox/checkbox.component';
import { RadioComponent } from './radio/radio.component';
import { TextareaComponent } from './textarea/textarea.component';
import { FlexBoxComponent } from './flex-box/flex-box.component';
import { TagsComponent } from './tags/tags.component';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { FlexboxDescriptor } from '../base-entity/flexboxDescriptor';
import { EntityComponentsListComponent } from './components/entity-components-list.component';
import { NGXLogger } from 'ngx-logging-kit';
import { LookupComponent } from './lookup/lookup.component';

@Injectable({ providedIn: 'root' })
export class BaseEntityFormBuilder<Entity extends BaseEntity> {
  private readonly logger = inject(NGXLogger);

  // region public methods
  public buildForm(
    viewContainerRef: ViewContainerRef,
    baseEntityForm: FormGroup,
    store: any,
    attrDescriptors: AbstractAttrDescriptor[],
    entity: Signal<Entity>,
    initialValues?: Record<string, unknown>,
  ): void {
    this.logger.trace('Starting to build form for: ', { attrDescriptors: attrDescriptors });
    viewContainerRef.clear();
    attrDescriptors.forEach((column: AbstractAttrDescriptor) => {
      this.logger.trace('Processing column: ', column.attrName);
      const formControlType = this.createFormControl(column);
      if (formControlType) {
        if (column instanceof BaseEntityAttrDescriptor) {
          const hasSnapshotValue = initialValues != null && Object.prototype.hasOwnProperty.call(initialValues, column.attrName);
          const currentAttrValue = hasSnapshotValue ? initialValues![column.attrName] : Reflect.get(entity(), column.attrName);
          const formControl = new FormControl({ value: currentAttrValue, disabled: column.disabled }, column.required ? Validators.required : null);
          baseEntityForm.addControl(column.attrName, formControl);

          const componentRef = viewContainerRef.createComponent<BaseFormControlComponent<Entity>>(formControlType);
          componentRef.setInput('config', column);
          componentRef.setInput('entity', entity());
          componentRef.setInput('value', currentAttrValue);
          componentRef.instance.formGroup = baseEntityForm;
          componentRef.instance.store = store;
        } else if (column instanceof FlexboxDescriptor) {
          const componentRef = viewContainerRef.createComponent<BaseFormControlComponent<Entity>>(formControlType);
          componentRef.setInput('config', column as unknown as BaseEntityAttrDescriptor);
          componentRef.setInput('entity', entity());
          componentRef.instance.formGroup = baseEntityForm;
          componentRef.instance.store = store;
          this.buildForm((componentRef.instance as FlexBoxComponent<Entity>).flexBoxHost.viewContainerRef, baseEntityForm, store, column.attrDescriptors, entity, initialValues);
        } else throw Error('Undefined subclass of AbstractAttrDescriptor');
      }
    });
  }

  // endregion

  // region protected, private helper methods
  private createFormControl(column: AbstractAttrDescriptor): Type<BaseFormControlComponent<Entity>> {
    if (column.formControlType === FormControlType.ARTIFACT) {
      return ArtifactComponent<Entity>;
    } else if (column.formControlType === FormControlType.CHECKBOX) {
      return CheckboxComponent<Entity>;
    } else if (column.formControlType === FormControlType.COMPONENTS) {
      return EntityComponentsListComponent<Entity>;
    } else if (column.formControlType === FormControlType.DATE) {
      return DatepickerComponent<Entity>;
    } else if (column.formControlType === FormControlType.DROPDOWN) {
      return DropdownComponent<Entity>;
    } else if (column.formControlType === FormControlType.LABEL) {
      return LabelComponent<Entity>;
    } else if (column.formControlType === FormControlType.LOOKUP) {
      return LookupComponent<Entity>;
    } else if (column.formControlType === FormControlType.RADIO) {
      return RadioComponent<Entity>;
    } else if (column.formControlType === FormControlType.TEXTAREA) {
      return TextareaComponent<Entity>;
    } else if (column.formControlType === FormControlType.FLEX_BOX) {
      return FlexBoxComponent<Entity>;
    } else if (column.formControlType === FormControlType.FOREIGN_KEY) {
      return ForeignKeyComponent<Entity>;
    } else if (column.formControlType === FormControlType.TAGS) {
      return TagsComponent<Entity>;
    } else if (column.formControlType === FormControlType.TEXT_BOX) {
      return TextboxComponent<Entity>;
    } else throw Error('Undefined form control type');
  }

  // endregion
}
