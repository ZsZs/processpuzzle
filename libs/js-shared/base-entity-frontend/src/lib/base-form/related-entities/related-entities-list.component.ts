import { Component, OnInit } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../../base-entity/base-entity';
import { normalizeEntityReferences } from '../entity-references';
import { NgClass, NgStyle } from '@angular/common';
import { RelatedEntityNameAttr, RelatedEntityRefComponent } from './related-entity-ref.component';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

/**
 * To-many **association**: the rows point at entities that exist independently of this one, so adding
 * picks an existing entity from the related list and deleting only detaches the reference. Containment —
 * where the related entity has exactly one owner and dies with the reference — is a different control type
 * (`COMPONENTS`), so do not extend this one with delete-the-entity behavior.
 */
@Component({
  selector: 'app-related-entities-list',
  standalone: true,
  imports: [NgClass, NgStyle, RelatedEntityRefComponent, MatButton, MatIcon, EntityLabelPipe],
  template: `
    @if (config().visible) {
      <div class="row">
        <fieldset class="base-entity-form-field" tabindex="0" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().i18nKey() | ppLabel: config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list">
            @for (relatedEntity of relatedEntities(); track relatedEntity.id) {
              <li>
                <app-related-entity-ref
                  [entity]="entity()"
                  [relatedEntity]="relatedEntity"
                  [relatedEntityNameAttr]="relatedEntityNameAttr()"
                  [disabled]="config().disabled"
                  [formGroup]="formGroup"
                  [linkedEntityType]="linkedEntityName()"
                />
              </li>
            }
          </ul>
          @if (!config().disabled) {
            <button type="button" mat-button class="base-entity-form-focus-action" [title]="addRelatedEntityTitle()" [attr.aria-label]="addRelatedEntityTitle()" (click)="navigateToRelatedList()">
              <mat-icon>add</mat-icon>
              {{ addRelatedEntityTitle() }}
            </button>
          }
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
})
export class RelatedEntitiesListComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  ngOnInit(): void {
    this.addSelectedEntityFromNavigatorResponse();
  }

  relatedEntityNameAttr(): RelatedEntityNameAttr {
    return {
      attrName: this.linkedEntityDescriptor()?.componentIdentification() ?? '',
      name: this.config().attrName,
    };
  }

  relatedEntities(): PersistedEntity<BaseEntity>[] {
    const value = this.formGroup.get(this.config().attrName)?.value ?? this.value();
    return normalizeEntityReferences(value, this.config().referenceIdField);
  }

  addRelatedEntityTitle(): string {
    return 'Add ' + this.linkedEntityName();
  }

  navigateToRelatedList(): void {
    if (this.config().disabled) {
      return;
    }

    this.formNavigator.captureFormSnapshot(this.formGroup.getRawValue());
    this.formNavigator.navigateToRelatedList(this.linkedEntityName(), this.formNavigator.determineCurrentUrl(), {
      command: NavigatorCommand.SELECT_OR_CREATE,
      attrName: this.config().attrName,
      context: this.relatedEntities(),
    });
  }

  private addSelectedEntityFromNavigatorResponse(): void {
    if (this.config().disabled) {
      return;
    }

    const responsePayload = this.formNavigator.popResponsePayload(this.config().attrName);
    if (responsePayload?.command !== NavigatorCommand.SELECT_OR_CREATE || !responsePayload.payload) {
      return;
    }

    const selectedEntity = responsePayload.payload as BaseEntity;
    assertPersistedEntity(selectedEntity);

    const previousEntities = Array.isArray(responsePayload.context) ? (responsePayload.context as PersistedEntity<BaseEntity>[]) : this.relatedEntities();
    const relatedEntities = [...previousEntities, selectedEntity];
    const attrName = this.config().attrName;
    const entity = this.entity() as Record<string, unknown>;
    entity[attrName] = relatedEntities;

    const control = this.formGroup.get(attrName);
    if (!control) {
      this.logger.warn('Unable to add selected entity to form control because the control is missing.', this.describeFormState(attrName));
      return;
    }

    control.setValue(relatedEntities);
    control.markAsDirty();
    control.markAsTouched();
    this.formGroup.markAsDirty();
    this.formGroup.markAsTouched();
    this.logger.info('Added selected entity from navigator response.', this.describeFormState(attrName));
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
