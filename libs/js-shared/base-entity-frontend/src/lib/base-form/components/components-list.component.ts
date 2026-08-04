import { Component, computed, inject, OnInit } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { filter } from 'rxjs';
import { BaseEntity, PersistedEntity } from '../../base-entity/base-entity';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../../dialogs/delete-confirmation.dialog';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { BaseFormControlComponent } from '../base-form-control.component';
import { normalizeEntityReferences } from '../entity-references';
import { ComponentRefComponent } from './component-ref.component';

/** The slice of a component's store this control uses; the store itself is resolved by entity name. */
interface ComponentEntityStore {
  entities?: () => PersistedEntity<BaseEntity>[];
  load?: (query: Record<string, unknown>) => unknown;
  loadById?: (id: string) => PersistedEntity<BaseEntity> | undefined;
  delete?: (id: string) => Promise<void>;
  update?: (entity: PersistedEntity<BaseEntity>) => Promise<unknown>;
}

/**
 * To-many **containment** whose parts live in a table of their own — the child is persisted through its own
 * endpoint and points back at this entity through the foreign key named by
 * {@link BaseEntityDescriptor.parentReferenceAttrName}. This attribute therefore holds child **ids**, and the
 * row text is resolved from the child's store.
 *
 * The difference from `RELATED_ENTITIES` is ownership, and it shows in what the buttons do: attaching a child
 * also stamps this entity's id into the child's foreign key, and deleting a row **destroys the child**
 * instead of merely detaching it. For a part that has no table of its own, see `EMBEDDED_COMPONENTS`.
 */
@Component({
  selector: 'app-components-list',
  standalone: true,
  imports: [NgClass, NgStyle, ComponentRefComponent, MatButton, MatIcon, EntityLabelPipe],
  template: `
    @if (config().visible) {
      <div class="row">
        <fieldset class="base-entity-form-field" tabindex="0" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().i18nKey() | ppLabel: config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list">
            @for (componentEntity of componentEntities(); track componentEntity.id) {
              <li>
                <app-component-ref
                  [componentEntity]="componentEntity"
                  [displayName]="displayName(componentEntity)"
                  [disabled]="config().disabled"
                  [linkedEntityType]="linkedEntityName()"
                  (deleteRequested)="deleteComponent(componentEntity)"
                />
              </li>
            }
          </ul>
          @if (!config().disabled) {
            <button type="button" mat-button class="base-entity-form-focus-action" [title]="addComponentTitle()" [attr.aria-label]="addComponentTitle()" (click)="navigateToComponentList()">
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
export class ComponentsListComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly componentStore = computed<ComponentEntityStore | undefined>(() => this.descriptorRegistry.getStore<ComponentEntityStore>(this.config().linkedEntityType));

  ngOnInit(): void {
    this.assertContainmentContract();
    this.loadComponentEntities();
    this.attachSelectedComponentFromNavigatorResponse();
  }

  componentEntities(): PersistedEntity<BaseEntity>[] {
    const value = this.formGroup.get(this.config().attrName)?.value ?? this.value();
    return normalizeEntityReferences(value, this.config().referenceIdField);
  }

  /**
   * The child's identifying text. A reference may still be stored as a whole object (seed data, a form
   * snapshot), so its own attribute wins; otherwise the child is looked up in its store, and the id is what
   * remains when the store has not loaded it.
   */
  displayName(componentEntity: PersistedEntity<BaseEntity>): string {
    const attrName = this.linkedEntityDescriptor()?.componentIdentification() ?? '';
    if (!attrName) return componentEntity.id;

    const own = (componentEntity as unknown as Record<string, unknown>)[attrName];
    const resolved = own ?? (this.storedComponent(componentEntity.id) as Record<string, unknown> | undefined)?.[attrName];
    return resolved === undefined || resolved === null ? componentEntity.id : String(resolved);
  }

  addComponentTitle(): string {
    return 'Add ' + this.linkedEntityName();
  }

  navigateToComponentList(): void {
    if (this.config().disabled) {
      return;
    }

    this.formNavigator.captureFormSnapshot(this.formGroup.getRawValue());
    this.formNavigator.navigateToRelatedList(this.linkedEntityName(), this.formNavigator.determineCurrentUrl(), {
      command: NavigatorCommand.SELECT_OR_CREATE,
      attrName: this.config().attrName,
      context: this.componentEntities(),
    });
  }

  /** Destroys the child — a component has no life outside its parent — after the user confirms. */
  deleteComponent(componentEntity: PersistedEntity<BaseEntity>): void {
    if (this.config().disabled) {
      return;
    }

    const dialogData: DeleteConfirmationDialogData = {
      titleKey: 'base_entity.delete_component_confirmation_dialog.title',
      contentKey: 'base_entity.delete_component_confirmation_dialog.content',
      contentParams: { componentName: this.displayName(componentEntity) },
      cancelButtonKey: 'base_entity.delete_component_confirmation_dialog.cancel_button',
      confirmButtonKey: 'base_entity.delete_component_confirmation_dialog.delete_button',
    };

    this.dialog
      .open(DeleteConfirmationDialog, { data: dialogData })
      .afterClosed()
      .pipe(filter((confirmed): confirmed is true => confirmed === true))
      .subscribe(() => {
        void this.componentStore()?.delete?.(componentEntity.id);
        this.writeReferences(this.componentEntities().filter((reference) => reference.id !== componentEntity.id));
      });
  }

  // region protected, private helper methods
  /**
   * The two declarations — this attribute's control type and the child's own descriptor — have to agree, and
   * they are both authored by hand. Checking at first render turns a silent mis-declaration (a list that
   * deletes what it should detach, or a child whose payload goes nowhere) into an error naming both sides.
   */
  private assertContainmentContract(): void {
    const linkedEntityName = this.linkedEntityName();
    const descriptor = this.linkedEntityDescriptor();
    const attrName = this.config().attrName;

    if (!descriptor) {
      throw new Error(`'${attrName}' references '${linkedEntityName}', which is not registered in BASE_ENTITY_FACADE_REGISTRY.`);
    }
    if (!descriptor.isComponentOf(this.entityName())) {
      throw new Error(`'${attrName}' declares '${linkedEntityName}' a component, but that entity does not name '${this.entityName()}' as its componentParent.`);
    }
    if (descriptor.isEmbedded) {
      throw new Error(`'${linkedEntityName}' is an embedded component, so '${attrName}' has to use FormControlType.EMBEDDED_COMPONENTS.`);
    }
  }

  /**
   * The rows arrive as ids, so the child store has to hold the children for them to render as names. The
   * parent's form may well be the first screen the user opens, which is why the load happens here rather
   * than being left to the child's list page (`LookupComponent` loads its table the same way).
   */
  private loadComponentEntities(): void {
    const componentStore = this.componentStore();
    if (!componentStore?.load || (componentStore.entities?.() ?? []).length > 0) {
      return;
    }

    componentStore.load({});
  }

  private attachSelectedComponentFromNavigatorResponse(): void {
    if (this.config().disabled) {
      return;
    }

    const responsePayload = this.formNavigator.popResponsePayload(this.config().attrName);
    if (responsePayload?.command !== NavigatorCommand.SELECT_OR_CREATE || !responsePayload.payload) {
      return;
    }

    const selectedComponent = responsePayload.payload as PersistedEntity<BaseEntity>;
    const previousReferences = Array.isArray(responsePayload.context) ? normalizeEntityReferences(responsePayload.context) : this.componentEntities();

    this.writeReferences([...previousReferences, selectedComponent]);
    this.adoptComponent(selectedComponent);
  }

  /** Stamps this entity's id into the child's foreign key, so the containment holds from the child's side too. */
  private adoptComponent(componentEntity: PersistedEntity<BaseEntity>): void {
    const parentReferenceAttrName = this.linkedEntityDescriptor()?.parentReferenceAttrName();
    const parentId = this.entity().id;
    if (!parentReferenceAttrName || !parentId) {
      return;
    }

    const stored = this.storedComponent(componentEntity.id) ?? componentEntity;
    if (Reflect.get(stored, parentReferenceAttrName) === parentId) {
      return;
    }

    void this.componentStore()?.update?.({ ...stored, [parentReferenceAttrName]: parentId });
  }

  private storedComponent(id: string): PersistedEntity<BaseEntity> | undefined {
    const componentStore = this.componentStore();
    return componentStore?.loadById?.(id) ?? componentStore?.entities?.().find((componentEntity) => componentEntity.id === id);
  }

  /** Writes the references back as ids — the child's payload belongs to the child's own endpoint, not here. */
  private writeReferences(references: PersistedEntity<BaseEntity>[]): void {
    const attrName = this.config().attrName;
    const ids = references.map((reference) => reference.id);
    Reflect.set(this.entity() as Record<string, unknown>, attrName, ids);

    const control = this.formGroup.get(attrName);
    if (!control) {
      this.logger.warn('Unable to update the components of the form because the control is missing.', { attrName });
      return;
    }

    control.setValue(ids);
    control.markAsDirty();
    control.markAsTouched();
    this.formGroup.markAsDirty();
    this.formGroup.markAsTouched();
  }
  // endregion
}
