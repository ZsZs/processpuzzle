import { Component, inject, Injector, OnInit, signal } from '@angular/core';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ReactiveFormsModule } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';
import { BASE_ENTITY_STORE_REGISTRY } from '../../base-entity-store/base-entity-store-registry';

@Component({
  selector: 'base-foreign-key',
  standalone: true,
  template: `
    @if (config().visible) {
      <div class="row" [formGroup]="formGroup" (focusin)="hasFocus.set(true)" (focusout)="onFocusOut($event)">
        <div class="foreign-key-control">
          <mat-form-field class="foreign-key-field">
            <mat-label>{{ foreignKeyLabel() }}</mat-label>
            <input matInput [value]="foreignKeyDisplayName()" [disabled]="config().disabled" readonly />
            <button type="button" mat-icon-button matSuffix (click)="navigateToRelated()" aria-label="Related entity">
              <mat-icon>link</mat-icon>
            </button>
          </mat-form-field>
          @if (showSelectEntityButton()) {
            <div class="foreign-key-select-row">
              <button
                type="button"
                class="foreign-key-select-button"
                mat-button
                [title]="selectEntityTitle()"
                [attr.aria-label]="selectEntityTitle()"
                (click)="navigateToRelatedList()"
              >
                <mat-icon>add</mat-icon>
                {{ selectEntityTitle() }}
              </button>
            </div>
          }
        </div>
        <input type="hidden" [formControlName]="config().attrName" />
      </div>
    }
  `,
  styles: [
    `
      .foreign-key-control {
        display: inline-flex;
        flex-direction: column;
        align-items: stretch;
      }

      .foreign-key-field {
        width: 100%;
      }

      .foreign-key-select-row {
        display: flex;
        min-height: 28px;
        margin-top: -18px;
        padding: 0 12px 0 16px;
        justify-content: flex-start;
      }

      .foreign-key-select-button {
        height: 28px;
        min-width: max-content;
        padding: 0 8px;
        line-height: 28px;
        white-space: nowrap;
      }

      .foreign-key-select-button mat-icon {
        width: 18px;
        height: 18px;
        margin-right: 4px;
        font-size: 18px;
        line-height: 18px;
      }
    `,
  ],
  imports: [MatButton, MatIconButton, MatIcon, MatLabel, MatFormField, MatInput, MatSuffix, ReactiveFormsModule],
})
export class ForeignKeyComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  hasFocus = signal(false);
  private readonly injector = inject(Injector);
  private readonly storeRegistry = inject(BASE_ENTITY_STORE_REGISTRY);
  private readonly selectedEntity = signal<BaseEntity | undefined>(undefined);

  ngOnInit(): void {
    this.addSelectedComponentFromNavigatorResponse();
  }

  // region event handling methods
  onFocusOut(event: FocusEvent): void {
    const currentTarget = event.currentTarget as HTMLElement | null;
    const relatedTarget = event.relatedTarget as Node | null;
    this.hasFocus.set(!!currentTarget && !!relatedTarget && currentTarget.contains(relatedTarget));
  }

  navigateToRelated() {
    const relatedEntityName = this.config().linkedEntityType?.entityName;
    if (!relatedEntityName) return;

    this.formNavigator.navigateToRelated(relatedEntityName, this.foreignKeyId(), this.formNavigator.determineCurrentUrl());
  }

  navigateToRelatedList(): void {
    const relatedEntityName = this.config().linkedEntityType?.entityName;
    if (this.config().disabled || !relatedEntityName) {
      return;
    }

    this.formNavigator.navigateToRelatedList(relatedEntityName, this.formNavigator.determineCurrentUrl(), {
      command: NavigatorCommand.SELECT_OR_CREATE,
    });
  }
  // endregion

  selectEntityTitle(): string {
    return 'Select ' + (this.config().linkedEntityType?.entityName ?? 'Entity');
  }

  foreignKeyLabel(): string {
    return this.config().linkedEntityType?.entityName ?? this.config().label;
  }

  showSelectEntityButton(): boolean {
    return !this.config().disabled && this.hasFocus() && this.config().linkedEntityType !== undefined;
  }

  foreignKeyDisplayName(): string {
    const relatedEntity = this.selectedEntity() ?? this.relatedEntityFromLinkedStore();
    if (!relatedEntity) {
      return this.foreignKeyId();
    }

    const attrName = this.config().linkedEntityType?.componentIdentification();
    const componentName = attrName ? (relatedEntity as unknown as Record<string, unknown>)[attrName] : undefined;

    return componentName === undefined || componentName === null ? relatedEntity.id : String(componentName);
  }

  private addSelectedComponentFromNavigatorResponse(): void {
    if (this.config().disabled) {
      return;
    }

    const responsePayload = this.formNavigator.popResponsePayload(NavigatorCommand.SELECT_OR_CREATE);
    const selectedEntity = responsePayload?.payload as BaseEntity | undefined;
    const selectedEntityId = selectedEntity?.id;
    if (!selectedEntityId) {
      return;
    }

    this.selectedEntity.set(selectedEntity);
    const attrName = this.config().attrName;
    const entity = this.entity() as Record<string, unknown>;
    entity[attrName] = selectedEntityId;

    const control = this.formGroup.get(attrName);
    if (!control) {
      return;
    }

    control.setValue(selectedEntityId);
    control.markAsDirty();
    control.markAsTouched();
    this.formGroup.markAsDirty();
    this.formGroup.markAsTouched();
  }

  private foreignKeyId(): string {
    const attrName = this.config().attrName;
    const controlValue = this.formGroup?.get(attrName)?.value;
    const value = controlValue ?? this.value();
    return value === undefined || value === null ? '' : String(value);
  }

  private relatedEntityFromLinkedStore(): BaseEntity | undefined {
    const id = this.foreignKeyId();
    const linkedStore = this.resolveLinkedStore();
    if (!id || !linkedStore) {
      return undefined;
    }

    if (typeof linkedStore.loadById === 'function') {
      return linkedStore.loadById(id);
    }

    if (typeof linkedStore.entities === 'function') {
      return linkedStore.entities().find((entity: BaseEntity) => entity.id === id);
    }

    return undefined;
  }

  private resolveLinkedStore(): any {
    const entityName = this.config().linkedEntityType?.entityName;
    const registryStoreToken = entityName ? this.storeRegistry[entityName] : undefined;
    if (registryStoreToken) {
      return this.injector.get(registryStoreToken, undefined);
    }

    const store = this.config().linkedEntityType?.store;
    if (!store) {
      return undefined;
    }

    if (typeof store.loadById === 'function' || typeof store.entities === 'function') {
      return store;
    }

    try {
      return this.injector.get(store);
    } catch {
      return undefined;
    }
  }
}
