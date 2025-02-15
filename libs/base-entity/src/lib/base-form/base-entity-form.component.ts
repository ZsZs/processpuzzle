import { BaseEntity } from '../base-entity/base-entity';
import { Component, computed, effect, inject, input, OnInit, Signal, untracked, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseFormHostDirective } from './base-form-host.directive';
import { MatCard, MatCardActions, MatCardContent } from '@angular/material/card';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MatButton } from '@angular/material/button';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { BaseEntityFormBuilder } from './base-entity-form.builder';

@Component({
  selector: 'base-form',
  standalone: true,
  templateUrl: 'base-entity-form.component.html',
  imports: [BaseFormHostDirective, ReactiveFormsModule, MatCard, MatCardContent, MatCardActions, MatButton],
  providers: [{ provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } }],
})
export class BaseEntityFormComponent<Entity extends BaseEntity> implements OnInit {
  baseEntityForm!: FormGroup;
  baseEntityListOptions = inject(ROUTER_OUTLET_DATA) as Signal<BaseEntityDescriptor>;
  entity: Signal<Entity> = computed(() => (this.isNewObject() ? this.store().createEntity() : this.store().loadById(this.entityId())));
  entityId: Signal<string> = input.required<string>();
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) componentHost!: BaseFormHostDirective;
  store: Signal<any> = computed(() => this.baseEntityListOptions().store);
  private entityFormBuilder = inject(BaseEntityFormBuilder<Entity>);
  private formBuilder = inject(FormBuilder);
  private readonly isNewObject = computed(() => this.entityId() === BaseUrlSegments.NewEntity);

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
  private registerEffects(): void {
    effect(() => {
      if (this.entityId() && this.baseEntityListOptions() && this.entity())
        untracked(() => {
          this.entityFormBuilder.buildForm(this.componentHost.viewContainerRef, this.baseEntityForm, this.store(), this.baseEntityListOptions().attrDescriptors, this.entity);
        });
    });

    effect(() => {
      if (this.entity()) this.store().setCurrentEntity(this.entity().id);
    });
  }

  // endregion
}
