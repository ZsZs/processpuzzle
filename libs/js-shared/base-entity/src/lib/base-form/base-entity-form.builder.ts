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
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';

type AnyFormControlComponent = Type<BaseFormControlComponent<BaseEntity>>;

const FORM_CONTROL_COMPONENTS: Readonly<Partial<Record<FormControlType, AnyFormControlComponent>>> = {
  [FormControlType.ARTIFACT]: ArtifactComponent,
  [FormControlType.CHECKBOX]: CheckboxComponent,
  [FormControlType.COMPONENTS]: EntityComponentsListComponent,
  [FormControlType.DATE]: DatepickerComponent,
  [FormControlType.DROPDOWN]: DropdownComponent,
  [FormControlType.LABEL]: LabelComponent,
  [FormControlType.LOOKUP]: LookupComponent,
  [FormControlType.RADIO]: RadioComponent,
  [FormControlType.TEXTAREA]: TextareaComponent,
  [FormControlType.FLEX_BOX]: FlexBoxComponent,
  [FormControlType.FOREIGN_KEY]: ForeignKeyComponent,
  [FormControlType.TAGS]: TagsComponent,
  [FormControlType.TEXT_BOX]: TextboxComponent,
};

@Injectable({ providedIn: 'root' })
export class BaseEntityFormBuilder<Entity extends BaseEntity> {
  private readonly logger = inject(NGXLogger);

  // region public methods
  public buildForm(
    viewContainerRef: ViewContainerRef,
    baseEntityForm: FormGroup,
    store: BaseEntityStoreApi<Entity>,
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
          const currentAttrValue = initialValues != null && Object.hasOwn(initialValues, column.attrName) ? initialValues[column.attrName] : Reflect.get(entity(), column.attrName);
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
        } else throw new Error('Undefined subclass of AbstractAttrDescriptor');
      }
    });
  }

  // endregion

  // region protected, private helper methods
  private createFormControl(column: AbstractAttrDescriptor): Type<BaseFormControlComponent<Entity>> {
    const componentType = FORM_CONTROL_COMPONENTS[column.formControlType];
    if (!componentType) throw new Error('Undefined form control type');
    return componentType as unknown as Type<BaseFormControlComponent<Entity>>;
  }

  // endregion
}
