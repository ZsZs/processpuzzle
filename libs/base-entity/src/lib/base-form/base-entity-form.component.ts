import { BaseEntity } from '../base-entity/base-entity';
import { Component, computed, effect, inject, input, OnInit, signal, Signal, Type, untracked, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseFormHostDirective } from './base-form-host.directive';
import { BaseEntityAttrDescriptor, FormControlType } from '../base-entity/base-entity-attr.descriptor';
import { LabelComponent } from './label/label.component';
import { TextboxComponent } from './textbox/textbox.component';
import { DropdownComponent } from './dropdown/dropdown.component';
import { CheckboxComponent } from './checkbox/checkbox.component';
import { RadioComponent } from './radio/radio.component';
import { TextareaComponent } from './textarea/textarea.component';
import { BaseFormControlComponent } from './base-form-control.component';
import { MatCard, MatCardActions, MatCardContent } from '@angular/material/card';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { DatepickerComponent } from './datepicker/datepicker.component';
import { MatButton } from '@angular/material/button';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { ForeignKeyComponent } from './foreign-key/foreign-key.component';

@Component({
  selector: 'base-form',
  standalone: true,
  template: `
    <mat-card appearance="outlined" class="dense-3">
      <mat-card-content>
        <form [formGroup]="baseEntityForm" (ngSubmit)="onSubmit()">
          <ng-template baseFormHost></ng-template>
        </form>
      </mat-card-content>
      <mat-card-actions>
        <button id="cancel" mat-raised-button color="secondary" (click)="onCancel()">Cancel</button>
        <button id="submit" mat-raised-button color="primary" type="submit" [disabled]="baseEntityForm.invalid || !baseEntityForm.dirty" (click)="onSubmit()">Save</button>
      </mat-card-actions>
    </mat-card>
  `,
  imports: [BaseFormHostDirective, ReactiveFormsModule, MatCard, MatCardContent, MatCardActions, MatButton],
  providers: [{ provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } }],
})
export class BaseEntityFormComponent<Entity extends BaseEntity> implements OnInit {
  baseEntityForm!: FormGroup;
  baseEntityListOptions = inject(ROUTER_OUTLET_DATA) as Signal<BaseEntityDescriptor>;
  entityId: Signal<string> = input.required<string>();
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) componentHost!: BaseFormHostDirective;
  protected formBuilder = inject(FormBuilder);
  store: Signal<any> = computed(() => this.baseEntityListOptions().store);
  private isNewObject = computed(() => this.entityId() === BaseUrlSegments.NewEntity);
  entity: Signal<Entity> = computed(() => (this.isNewObject() ? this.store().createEntity() : this.store().loadById(this.entityId())));

  constructor() {
    this.registerEffects();
  }

  // region angular lifecycle hooks
  ngOnInit(): void {
    this.store().determineActiveRouteSegment();
    this.baseEntityForm = this.formBuilder.group({});
  }
  // endregion

  // region event handlers
  async onCancel() {
    this.store().setCurrentEntity(undefined);
    await this.store().navigateBack();
  }

  async onSubmit() {
    const objectToSave = { ...this.entity(), ...this.baseEntityForm.value };
    if (this.isNewObject()) {
      this.store().save(objectToSave);
    } else {
      this.store().update(objectToSave, objectToSave.id);
    }
    this.store().setCurrentEntity(undefined);
    await this.store().navigateBack();
  }
  // endregion

  // region protected, private helper methods
  private buildForm(): void {
    this.componentHost.viewContainerRef.clear();
    const viewContainerRef = this.componentHost.viewContainerRef;
    this.baseEntityListOptions().columnDescriptors.forEach((column: BaseEntityAttrDescriptor<Entity>) => {
      const formControlType = this.createFormControl(column);
      const currentAttrValue = Reflect.get(this.entity(), column.attrName);
      if (formControlType) {
        const componentRef = viewContainerRef.createComponent<BaseFormControlComponent<Entity>>(formControlType);
        componentRef.instance.config = signal(column);
        componentRef.instance.entity = this.entity;
        componentRef.instance.formGroup = this.baseEntityForm;
        componentRef.instance.store = this.store();
        componentRef.instance.value = signal(currentAttrValue);
        const formControl = new FormControl({ value: currentAttrValue, disabled: column.disabled }, Validators.required);
        this.baseEntityForm.addControl(column.attrName, formControl);
      }
    });
  }

  private createFormControl(column: BaseEntityAttrDescriptor<Entity>): Type<BaseFormControlComponent<Entity>> {
    if (column.formControlType === FormControlType.LABEL) {
      return LabelComponent<Entity>;
    } else if (column.formControlType === FormControlType.DATE) {
      return DatepickerComponent<Entity>;
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

  private registerEffects(): void {
    effect(() => {
      if (this.entityId() && this.baseEntityListOptions() && this.entity())
        untracked(() => {
          this.buildForm();
        });
    });

    effect(() => {
      if (this.entity()) this.store().setCurrentEntity(this.entity().id);
    });
  }
  // endregion
}
