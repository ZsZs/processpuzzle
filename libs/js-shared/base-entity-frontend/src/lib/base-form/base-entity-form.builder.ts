import { DirectiveWithBindings, inject, Injectable, inputBinding, Signal, Type, ViewContainerRef } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoService } from '@jsverse/transloco';
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
import { translateLabel } from '../i18n/entity-label.pipe';
import { ENTITY_STATE_CONTROL } from './state/entity-state-control';

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
  // Optional so a host without transloco still gets a working form — its tooltips fall back to `description`.
  private readonly transloco = inject(TranslocoService, { optional: true });
  private readonly stateControl = inject(ENTITY_STATE_CONTROL, { optional: true });

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

          // Inputs as bindings rather than `setInput`: Angular refuses `setInput` on a component created with
          // binding functions, and the tooltip directive needs those. Each is a snapshot taken here, as before.
          const currentEntity = entity();
          const componentRef = viewContainerRef.createComponent<BaseFormControlComponent<Entity>>(formControlType, {
            bindings: [inputBinding('config', () => column), inputBinding('entity', () => currentEntity), inputBinding('entityName', () => entityName), inputBinding('value', () => currentAttrValue)],
            directives: this.tooltipFor(column),
          });
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
  /**
   * The tooltip of one control, attached to the control's host element so that every control type — the
   * ones base-entity ships and the CUSTOM ones it cannot know — gets it without a template change.
   *
   * The message is read from {@link AbstractAttrDescriptor.tooltipI18nKey}, falling back to the
   * descriptor's `description` (which is also what an entity authored as metadata carries). The binding is a
   * getter, re-read on every change detection, so a language switch or a lazily-loaded scope shows up without
   * rebuilding the form. An attribute with neither a key nor a description gets no tooltip directive at all;
   * an empty message is one MatTooltip never opens.
   */
  private tooltipFor(column: BaseEntityAttrDescriptor): DirectiveWithBindings<MatTooltip>[] {
    const key = column.tooltipI18nKey();
    const fallback = column.description ?? '';
    if (!key && !fallback) return [];
    const message = () => (this.transloco ? translateLabel(this.transloco, key, fallback) : fallback);
    return [{ type: MatTooltip, bindings: [inputBinding('matTooltip', message), inputBinding('matTooltipPosition', () => 'above'), inputBinding('matTooltipShowDelay', () => 500)] }];
  }

  private createFormControlFor(column: BaseEntityAttrDescriptor, currentAttrValue: unknown): AbstractControl {
    const validators = [];
    if (column.required) validators.push(Validators.required);
    // `Validators.pattern` on an empty value passes, so an optional patterned field stays optional — the two
    // validators compose rather than one implying the other.
    if (column.pattern) validators.push(Validators.pattern(column.pattern));

    // A STATE control is always disabled, and that is what keeps it out of the PUT: `BaseEntityFormComponent` saves
    // `form.value`, which leaves disabled controls out. The state changes only by firing a transition.
    const disabled = column.disabled || column.formControlType === FormControlType.STATE;
    return new FormControl({ value: currentAttrValue, disabled }, validators);
  }

  private createFormControl(column: AbstractAttrDescriptor): Type<BaseFormControlComponent<Entity>> {
    const componentType = column.formControlType === FormControlType.CUSTOM ? (column as BaseEntityAttrDescriptor).component : this.componentOf(column.formControlType);
    if (!componentType) throw new Error('Undefined form control type');
    return componentType as unknown as Type<BaseFormControlComponent<Entity>>;
  }

  private componentOf(formControlType: FormControlType): AnyFormControlComponent | undefined {
    if (formControlType === FormControlType.STATE) return (this.stateControl?.component as AnyFormControlComponent | undefined) ?? LabelComponent;
    return FORM_CONTROL_COMPONENTS[formControlType];
  }

  // endregion
}
