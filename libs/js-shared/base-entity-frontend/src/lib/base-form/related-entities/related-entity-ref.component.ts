import { Component, inject, input, InputSignal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BaseEntity, PersistedEntity } from '../../base-entity/base-entity';
import { referenceIdOf } from '../entity-references';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';

export interface RelatedEntityNameAttr {
  attrName: string;
  name: string;
}

@Component({
  selector: 'app-related-entity-ref',
  standalone: true,
  imports: [MatIconButton, MatIcon],
  template: `
    <a href="" (click)="navigateToRelated($event)">{{ relatedEntityName() }}</a>
    @if (!disabled()) {
      <button type="button" mat-icon-button class="base-entity-form-delete-button" aria-label="Delete related entity reference" (click)="removeRelatedEntity()">
        <mat-icon>cancel</mat-icon>
      </button>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
  styles: [
    `
      :host {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
      }
      :host .base-entity-form-delete-button {
        margin-left: auto;
      }
    `,
  ],
})
export class RelatedEntityRefComponent<Entity extends BaseEntity, RelatedEntity extends BaseEntity> {
  entity: InputSignal<Entity> = input.required<Entity>();
  relatedEntity: InputSignal<PersistedEntity<RelatedEntity>> = input.required<PersistedEntity<RelatedEntity>>();
  relatedEntityNameAttr: InputSignal<RelatedEntityNameAttr> = input.required<RelatedEntityNameAttr>();
  disabled: InputSignal<boolean> = input(false);
  /**
   * Which field of a raw row carries the id, when it is not `id` — the owning attribute's
   * `referenceIdField`. Needed on this component and not only on the list, because removal filters the
   * attribute's raw value and has to resolve each item's id the same way the rendering did.
   */
  referenceIdField: InputSignal<string | undefined> = input<string | undefined>(undefined);
  formGroup: InputSignal<FormGroup> = input.required<FormGroup>();
  linkedEntityType: InputSignal<string> = input.required<string>();
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);

  relatedEntityName(): string {
    const attrName = this.relatedEntityNameAttr().attrName;
    const relatedEntity = this.relatedEntity();
    const name = attrName ? (relatedEntity as unknown as Record<string, unknown>)[attrName] : undefined;
    if (typeof name === 'string') return name;
    if (typeof name === 'number' || typeof name === 'boolean' || typeof name === 'bigint') return String(name);
    return relatedEntity.id;
  }

  navigateToRelated(event: Event): void {
    event.preventDefault();
    this.formNavigator.navigateToRelated(this.linkedEntityType(), this.relatedEntity().id, this.formNavigator.determineCurrentUrl());
  }

  removeRelatedEntity(): void {
    if (this.disabled()) {
      return;
    }

    const relatedEntitiesAttrName = this.relatedEntityNameAttr().name;
    const entity = this.entity() as Record<string, unknown>;
    const relatedEntities = entity[relatedEntitiesAttrName];

    if (!Array.isArray(relatedEntities)) {
      return;
    }

    // Resolved through `referenceIdOf` rather than read off `.id`, because what is filtered here is the
    // attribute's **raw** value: an attribute the contract declares as `string[]` holds bare ids, whose
    // `.id` is `undefined`, so comparing it matched nothing and no row could ever be detached. Filtering
    // the raw array rather than the normalized copy is deliberate — it keeps ids as ids and whole
    // entities as whole entities, so the payload shape survives the removal.
    const targetId = this.relatedEntity().id;
    const remainingEntities = relatedEntities.filter((relatedEntity) => referenceIdOf(relatedEntity, this.referenceIdField()) !== targetId);
    entity[relatedEntitiesAttrName] = remainingEntities;

    const control = this.formGroup().get(relatedEntitiesAttrName);
    control?.setValue(remainingEntities);
    control?.markAsDirty();
    control?.markAsTouched();
  }
}
