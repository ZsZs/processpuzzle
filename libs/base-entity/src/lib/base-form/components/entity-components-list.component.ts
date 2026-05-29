import { Component, OnInit } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../../base-entity/base-entity';
import { NgClass, NgStyle } from '@angular/common';
import { ComponentNameAttr, EntityComponentRefComponent } from './entity-component-ref.component';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';

@Component({
  selector: 'app-component-list',
  standalone: true,
  imports: [NgClass, NgStyle, EntityComponentRefComponent, MatButton, MatIcon],
  template: `
    @if (config().visible) {
      <div class="row">
        <fieldset class="base-entity-form-field" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list">
            @for (component of components(); track component.id) {
              <li>
                <app-entity-component-ref
                  [entity]="entity()"
                  [component]="component"
                  [componentNameAttr]="componentNameAttr()"
                  [disabled]="config().disabled"
                  [formGroup]="formGroup"
                  [linkedEntityType]="linkedEntityType().entityName"
                />
              </li>
            }
          </ul>
          @if (!config().disabled) {
            <button type="button" mat-button [title]="addComponentTitle()" [attr.aria-label]="addComponentTitle()" (click)="navigateToRelatedList()">
              <mat-icon>add</mat-icon>
              {{ addComponentTitle() }}
            </button>
          }
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
})
export class EntityComponentsListComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  ngOnInit(): void {
    this.addSelectedComponentFromNavigatorResponse();
  }

  componentNameAttr(): ComponentNameAttr {
    return {
      attrName: this.linkedEntityType().componentIdentification(),
      name: this.config().attrName,
    };
  }

  components(): PersistedEntity<BaseEntity>[] {
    const value = this.formGroup.get(this.config().attrName)?.value ?? this.value();
    if (!Array.isArray(value)) {
      return [];
    }

    value.forEach((component) => assertPersistedEntity(component as BaseEntity));
    return value as PersistedEntity<BaseEntity>[];
  }

  addComponentTitle(): string {
    return 'Add ' + this.linkedEntityType().entityName;
  }

  navigateToRelatedList(): void {
    if (this.config().disabled) {
      return;
    }

    this.formNavigator.navigateToRelatedList(this.linkedEntityType().entityName, this.formNavigator.determineCurrentUrl(), {
      command: NavigatorCommand.SELECT_OR_CREATE,
      attrName: this.config().attrName,
    });
  }

  private addSelectedComponentFromNavigatorResponse(): void {
    if (this.config().disabled) {
      return;
    }

    const responsePayload = this.formNavigator.popResponsePayload(this.config().attrName);
    if (responsePayload?.command !== NavigatorCommand.SELECT_OR_CREATE || !responsePayload.payload) {
      return;
    }

    const selectedComponent = responsePayload.payload as BaseEntity;
    assertPersistedEntity(selectedComponent);

    const components = [...this.components(), selectedComponent];
    const attrName = this.config().attrName;
    const entity = this.entity() as Record<string, unknown>;
    entity[attrName] = components;

    const control = this.formGroup.get(attrName);
    if (!control) {
      this.logger.warn('Unable to add selected component to form control because the control is missing.', this.describeFormState(attrName));
      return;
    }

    control.setValue(components);
    control.markAsDirty();
    control.markAsTouched();
    this.formGroup.markAsDirty();
    this.formGroup.markAsTouched();
    this.logger.info('Added selected component from navigator response.', this.describeFormState(attrName));
  }

  private describeFormState(attrName: string): Record<string, unknown> {
    const controls = Object.entries(this.formGroup.controls).map(([name, control]) => ({
      name,
      dirty: control.dirty,
      disabled: control.disabled,
      errors: control.errors,
      invalid: control.invalid,
      status: control.status,
      touched: control.touched,
    }));

    return {
      attrName,
      formDirty: this.formGroup.dirty,
      formInvalid: this.formGroup.invalid,
      formStatus: this.formGroup.status,
      targetControl: controls.find((control) => control.name === attrName),
      invalidControls: controls.filter((control) => control.invalid),
    };
  }
}
