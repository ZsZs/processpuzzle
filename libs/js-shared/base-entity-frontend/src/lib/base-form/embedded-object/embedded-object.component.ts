import { Component, OnInit, ViewChild, ViewContainerRef, input, signal } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { AbstractAttrDescriptor } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityFormBuilder } from '../base-entity-form.builder';
import { BaseEntityStoreApi } from '../../base-entity-store/base-entity.store';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

/**
 * Hosts dynamic child form controls for {@link EmbeddedObjectComponent}.
 */
@Component({
  selector: 'app-embedded-object-body',
  standalone: true,
  template: `<ng-container #host />`,
})
export class EmbeddedObjectBodyComponent<Entity extends BaseEntity> implements OnInit {
  @ViewChild('host', { static: true, read: ViewContainerRef }) host!: ViewContainerRef;

  readonly childGroup = input.required<FormGroup>();
  readonly item = input<Record<string, unknown>>({});
  readonly childDescriptors = input.required<AbstractAttrDescriptor[]>();
  readonly store = input.required<BaseEntityStoreApi<Entity>>();
  readonly formBuilder = input.required<BaseEntityFormBuilder<Entity>>();
  readonly entityName = input.required<string>();
  readonly disabled = input<boolean>(false);

  ngOnInit(): void {
    const initial = this.item() ?? {};
    this.formBuilder().buildForm(
      this.host,
      this.childGroup(),
      this.store(),
      this.childDescriptors(),
      signal(initial as Entity),
      this.entityName(),
      initial,
    );
    if (this.disabled()) {
      this.childGroup().disable();
    }
  }
}

/**
 * Renders an embedded object as an inline fieldset whose child controls are built from
 * `config().childDescriptors`.
 *
 * Unlike {@link EmbeddedComponentsListComponent}, this operates on a pure value structure rather than
 * an entity with its own route, store, and identity: edits live directly in a nested `FormGroup` within
 * the owner form, and validity bubbles up naturally.
 */
@Component({
  selector: 'app-embedded-object',
  standalone: true,
  template: `
    @if (config().visible) {
      <div class="row" [formGroup]="formGroup">
        <fieldset class="base-entity-form-field" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().i18nKey() | ppLabel: config().label }}</legend>
          <app-embedded-object-body
            [childGroup]="childGroup"
            [item]="childInitialValues()"
            [childDescriptors]="config().childDescriptors ?? []"
            [store]="store"
            [formBuilder]="formBuilder"
            [entityName]="entityName()"
            [disabled]="config().disabled"
          />
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
  imports: [NgClass, NgStyle, ReactiveFormsModule, EntityLabelPipe, EmbeddedObjectBodyComponent],
})
export class EmbeddedObjectComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  childGroup!: FormGroup;
  readonly childInitialValues = signal<Record<string, unknown>>({});

  ngOnInit(): void {
    const rawGroup = this.formGroup.get(this.config().attrName);
    this.childGroup = rawGroup instanceof FormGroup ? rawGroup : new FormGroup({});
    if (this.childGroup !== rawGroup) {
      this.formGroup.setControl(this.config().attrName, this.childGroup);
    }

    const raw = this.value();
    const initial = (raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : {}) as Record<string, unknown>;
    this.childInitialValues.set(initial);

    if (this.config().disabled) {
      this.childGroup.disable();
    }
  }
}
