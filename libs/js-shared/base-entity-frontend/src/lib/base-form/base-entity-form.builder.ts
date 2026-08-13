import { inject, Injectable, Signal, Type, ViewContainerRef } from '@angular/core';
import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseFormControlComponent } from './base-form-control.component';
import { AbstractControl, FormControl, FormGroup, Validators } from '@angular/forms';
import { AdditionalPropertiesComponent } from './additional-properties/additional-properties.component';
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
import { ComponentsListComponent } from './components/components-list.component';
import { EmbeddedComponentsListComponent } from './embedded-components/embedded-components-list.component';
import { RelatedEntitiesListComponent } from './related-entities/related-entities-list.component';
import { NGXLogger } from 'ngx-logging-kit';
import { LookupComponent } from './lookup/lookup.component';
import { TitleComponent } from './title/title.component';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';

type AnyFormControlComponent = Type<BaseFormControlComponent<BaseEntity>>;

const FORM_CONTROL_COMPONENTS: Readonly<Partial<Record<FormControlType, AnyFormControlComponent>>> = {
  [FormControlType.ADDITIONAL_PROPERTIES]: AdditionalPropertiesComponent,
  [FormControlType.ARTIFACT]: ArtifactComponent,
  [FormControlType.CHECKBOX]: CheckboxComponent,
  [FormControlType.COMPONENTS]: ComponentsListComponent,
  [FormControlType.DATE]: DatepickerComponent,
  [FormControlType.DROPDOWN]: DropdownComponent,
  [FormControlType.EMBEDDED_COMPONENTS]: EmbeddedComponentsListComponent,
  [FormControlType.LABEL]: LabelComponent,
  [FormControlType.LOOKUP]: LookupComponent,
  [FormControlType.RADIO]: RadioComponent,
  [FormControlType.RELATED_ENTITIES]: RelatedEntitiesListComponent,
  [FormControlType.TEXTAREA]: TextareaComponent,
  [FormControlType.FLEX_BOX]: FlexBoxComponent,
  [FormControlType.FOREIGN_KEY]: ForeignKeyComponent,
  [FormControlType.TAGS]: TagsComponent,
  [FormControlType.TEXT_BOX]: TextboxComponent,
  [FormControlType.TITLE]: TitleComponent,
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
    entityName: string,
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
          baseEntityForm.addControl(column.attrName, this.createFormControlFor(column, currentAttrValue));

          const componentRef = viewContainerRef.createComponent<BaseFormControlComponent<Entity>>(formControlType);
          componentRef.setInput('config', column);
          componentRef.setInput('entity', entity());
          componentRef.setInput('entityName', entityName);
          componentRef.setInput('value', currentAttrValue);
          componentRef.instance.formGroup = baseEntityForm;
          componentRef.instance.store = store;
          componentRef.instance.formBuilder = this;
        } else if (column instanceof FlexboxDescriptor) {
          const componentRef = viewContainerRef.createComponent<BaseFormControlComponent<Entity>>(formControlType);
          componentRef.setInput('config', column as unknown as BaseEntityAttrDescriptor);
          componentRef.setInput('entity', entity());
          componentRef.setInput('entityName', entityName);
          componentRef.instance.formGroup = baseEntityForm;
          componentRef.instance.store = store;
          componentRef.instance.formBuilder = this;
          this.buildForm((componentRef.instance as FlexBoxComponent<Entity>).flexBoxHost.viewContainerRef, baseEntityForm, store, column.attrDescriptors, entity, entityName, initialValues);
        } else throw new Error('Undefined subclass of AbstractAttrDescriptor');
      }
    });
  }

  // endregion

  // region protected, private helper methods
  private createFormControlFor(column: BaseEntityAttrDescriptor, currentAttrValue: unknown): AbstractControl {
    const validators = [];
    if (column.required) validators.push(Validators.required);
    // `Validators.pattern` on an empty value passes, so an optional patterned field stays optional — the two
    // validators compose rather than one implying the other.
    if (column.pattern) validators.push(Validators.pattern(column.pattern));

    return new FormControl({ value: currentAttrValue, disabled: column.disabled }, validators);
  }

  private createFormControl(column: AbstractAttrDescriptor): Type<BaseFormControlComponent<Entity>> {
    const componentType = FORM_CONTROL_COMPONENTS[column.formControlType];
    if (!componentType) throw new Error('Undefined form control type');
    return componentType as unknown as Type<BaseFormControlComponent<Entity>>;
  }

  // endregion
}
