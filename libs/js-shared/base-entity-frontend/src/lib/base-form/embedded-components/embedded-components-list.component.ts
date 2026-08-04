import { Component, computed, effect, inject, OnInit, Signal, untracked } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { filter } from 'rxjs';
import { BaseEntity } from '../../base-entity/base-entity';
import { EmbeddedRow, rowId } from '../../base-entity-embedded/embedded-aggregate';
import { EmbeddedAggregateAccessor } from '../../base-entity-embedded/embedded-aggregate.accessor';
import { BaseEntityStoreApi } from '../../base-entity-store/base-entity.store';
import { BaseUrlSegments } from '../../base-form-navigator/base-url-segments';
import { DeleteConfirmationDialog, DeleteConfirmationDialogData } from '../../dialogs/delete-confirmation.dialog';
import { EntityLabelPipe, translateLabel } from '../../i18n/entity-label.pipe';
import { BaseFormControlComponent } from '../base-form-control.component';
import { EmbeddedComponentRefComponent } from './embedded-component-ref.component';

/**
 * To-many **containment** whose parts have no table of their own — the child's payload travels inside this
 * entity's payload, the way a sub-document does in a document store.
 *
 * The rows are only listed here; a row opens the child's own form, on a route nested below this one. That is
 * what keeps a parent with many children, or with children that have children of their own, readable — and
 * the child gets the full generated form rather than a cramped inline copy of it.
 *
 * The rows come from the child's own store, which reads them out of this entity's payload
 * (`EmbeddedEntityService`), so adding, editing and deleting a child all go through the same
 * List → Store → Service path as any other entity. What differs is only where that service writes: the
 * containing document, saved as a whole. For a part that is persisted through an endpoint of its own, see
 * `COMPONENTS`; for one that merely points at an independent entity, `RELATED_ENTITIES`.
 */
@Component({
  selector: 'app-embedded-components-list',
  standalone: true,
  imports: [NgClass, NgStyle, EmbeddedComponentRefComponent, MatButton, MatIcon, EntityLabelPipe, TranslocoPipe],
  template: `
    @if (config().visible) {
      <div class="row">
        <fieldset class="base-entity-form-field" tabindex="0" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().i18nKey() | ppLabel: config().label }}</legend>
          <ul [id]="config().attrName" class="base-entity-form-list">
            @for (row of rows(); track rowKey(row); let index = $index) {
              <li>
                <app-embedded-component-ref [displayName]="displayName(row, index)" [disabled]="config().disabled" (openRequested)="openComponent(row)" (deleteRequested)="deleteComponent(row)" />
              </li>
            }
          </ul>
          @if (!config().disabled) {
            @if (ownerIsPersisted()) {
              <button type="button" mat-button class="base-entity-form-focus-action" [title]="addComponentTitle()" [attr.aria-label]="addComponentTitle()" (click)="addComponent()">
                <mat-icon>add</mat-icon>
                {{ addComponentTitle() }}
              </button>
            } @else {
              <p class="base-entity-form-hint">{{ 'base_entity.embedded_components.save_owner_first' | transloco: { entity: linkedEntityLabel() } }}</p>
            }
          }
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
})
export class EmbeddedComponentsListComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly transloco = inject(TranslocoService);
  private readonly aggregateAccessor = inject(EmbeddedAggregateAccessor);
  private readonly componentStore = computed<BaseEntityStoreApi<BaseEntity> | undefined>(() => this.descriptorRegistry.getStore<BaseEntityStoreApi<BaseEntity>>(this.linkedEntityName()));

  /**
   * The child's store holds exactly this attribute's rows: it resolves them from the containing document
   * through the route, so it is already scoped to the entity this form is editing.
   */
  readonly rows: Signal<EmbeddedRow[]> = computed(() => this.componentStore()?.entities() ?? []);

  constructor() {
    super();
    this.registerRowReload();
    this.registerFormControlSync();
  }

  ngOnInit(): void {
    this.assertContainmentContract();
  }

  rowKey(row: EmbeddedRow): string {
    return rowId(row, this.config().referenceIdField);
  }

  /** The child's identifying text, falling back to whatever identifies it at all, then to its position. */
  displayName(row: EmbeddedRow, index: number): string {
    const attrName = this.linkedEntityDescriptor()?.componentIdentification();
    const own = attrName ? (row as Record<string, unknown>)[attrName] : undefined;
    if (own !== undefined && own !== null && String(own).length > 0) return String(own);

    return this.rowKey(row) || `${this.linkedEntityName()} ${index + 1}`;
  }

  linkedEntityLabel(): string {
    const descriptor = this.linkedEntityDescriptor();
    return descriptor ? translateLabel(this.transloco, descriptor.i18nKey(), descriptor.entityName) : this.linkedEntityName();
  }

  addComponentTitle(): string {
    return 'Add ' + this.linkedEntityName();
  }

  /**
   * An embedded row is stored inside its owner, so there is nowhere to put one until the owner exists.
   *
   * Read off the route rather than off the entity: an id is no evidence of a document, because an entity type
   * may well assign itself one at construction (`new TestEntity()` mints a uuid), which would make an unsaved
   * owner look persisted and offer an add button writing into a document that is not there. The URL is what
   * the child's own store resolves the rows through anyway, and it says `new` while the owner is unsaved.
   * The entity's id remains the fallback for an application whose routes carry no entity name.
   */
  ownerIsPersisted(): boolean {
    const ownerLevel = this.formNavigator.breadcrumb().find((level) => level.entityName === this.entityName());
    if (!ownerLevel) return Boolean(this.entity()?.id);

    return ownerLevel.entityId !== undefined && ownerLevel.entityId !== BaseUrlSegments.NewEntity;
  }

  openComponent(row: EmbeddedRow): void {
    void this.formNavigator.navigateToEmbedded(this.linkedEntityName(), this.rowKey(row));
  }

  /** A new child is created on its own form, the same way an existing one is edited. */
  addComponent(): void {
    if (this.config().disabled || !this.ownerIsPersisted()) return;

    void this.formNavigator.navigateToEmbedded(this.linkedEntityName(), BaseUrlSegments.NewEntity);
  }

  /** Destroys the child — an embedded component has no existence outside this payload — after confirmation. */
  deleteComponent(row: EmbeddedRow): void {
    if (this.config().disabled) return;

    const dialogData: DeleteConfirmationDialogData = {
      titleKey: 'base_entity.delete_component_confirmation_dialog.title',
      contentKey: 'base_entity.delete_component_confirmation_dialog.content',
      contentParams: { componentName: this.displayName(row, this.rows().indexOf(row)) },
      cancelButtonKey: 'base_entity.delete_component_confirmation_dialog.cancel_button',
      confirmButtonKey: 'base_entity.delete_component_confirmation_dialog.delete_button',
    };

    this.dialog
      .open(DeleteConfirmationDialog, { data: dialogData })
      .afterClosed()
      .pipe(filter((confirmed): confirmed is true => confirmed === true))
      .subscribe(() => void this.componentStore()?.delete(this.rowKey(row)));
  }

  // region protected, private helper methods
  /**
   * Both declarations — this attribute's control type and the child's own descriptor — are written by hand
   * and have to agree; checking at first render names both sides instead of silently rendering a list whose
   * rows go nowhere on save.
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
    if (!descriptor.isEmbedded) {
      throw new Error(`'${linkedEntityName}' is not an embedded component, so '${attrName}' has to use FormControlType.COMPONENTS.`);
    }
  }

  /**
   * Re-reads the rows whenever the containing document changes.
   *
   * The child's store holds a projection of that document, and it is a root singleton shared by every owner
   * of this child type — so its rows are stale both before the document has loaded (a form opened directly on
   * a URL) and after the user moves to a different owner. Depending on the document itself covers both, and
   * cannot loop: the reload writes to the child's store, never to the root's.
   */
  private registerRowReload(): void {
    effect(() => {
      this.aggregateAccessor.rootStoreFor(this.linkedEntityName())?.entities();
      untracked(() => this.componentStore()?.load({}));
    });
  }

  /**
   * Mirrors the rows into this attribute's control. A child's save writes the containing document itself, so
   * the control would otherwise still hold the array as it looked when the form was built — and the owner's
   * own save, which merges `form.value` over the entity, would write that stale array back.
   */
  private registerFormControlSync(): void {
    effect(() => {
      const rows = this.rows();
      const control = this.formGroup?.get(this.config().attrName);
      if (!control) return;

      // Not `markAsDirty`: the aggregate on the server already has these rows, so this is not an edit the
      // owner's Save still has to carry.
      control.setValue(rows, { emitEvent: false });
    });
  }
  // endregion
}
