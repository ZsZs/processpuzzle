import { Component, computed, inject, input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { BaseFormNavigatorSingletonStore, RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { LayoutService } from '@processpuzzle/util';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { BaseEntityQueryComponent } from '../base-query/base-entity-query.component';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'base-entity-toolbar',
  standalone: true,
  imports: [CommonModule, MatToolbarModule, MatIcon, MatFormField, MatInput, MatButton, MatIconButton, MatSuffix, MatMenu, MatMenuItem, MatMenuTrigger, BaseEntityQueryComponent, TranslocoDirective],
  templateUrl: './base-entity-toolbar.component.html',
  styles: [
    `
      .toolbar-inputs {
        display: flex;
        align-items: center;
        gap: 40px;
      }
      .filter-wrapper {
        display: inline-flex;
        align-items: center;
        gap: 8px;
      }
      .field-label {
        font-size: 14px;
        white-space: nowrap;
      }
    `,
  ],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
})
export class BaseEntityToolbarComponent<Entity extends BaseEntity> implements OnInit {
  entityDescriptor = input.required<BaseEntityDescriptor>();
  readonly layoutService = inject(LayoutService);
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  store!: BaseEntityStoreApi<Entity>;
  isDeleteEnabled = computed(() => this.store?.selectedEntities().length != 0 && !this.entityDescriptor().isAbstract);
  isEditEnabled = computed(() => this.store?.selectedEntities().length == 1 && !this.entityDescriptor().isAbstract);
  isNewEnabled = computed(() => !this.entityDescriptor().isAbstract);

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.entityDescriptor().store as BaseEntityStoreApi<Entity>;
    this.formNavigator.setEntityName(this.entityDescriptor().entityName);
  }

  // endregion

  // event handling methods
  onAddEntity() {
    this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, BaseUrlSegments.NewEntity);
  }

  onDeleteEntities() {
    this.store.selectedEntities().forEach((entity) => {
      if (entity.id) void this.store.delete(entity.id);
    });
  }

  onEditEntity() {
    const entityId = this.store.currentEntity()?.id;
    if (entityId) this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, entityId);
  }

  onDoFilter($event: KeyboardEvent) {
    const filterValue = ($event.target as HTMLInputElement).value;
    this.store.doFilter(filterValue);
  }

  onClearFilter(input: HTMLInputElement) {
    input.value = '';
    this.store.doFilter('');
    this.store.load({});
  }

  // endregion

  // public queries and mutators
  isSmallDevice(): boolean {
    return this.layoutService.isSmallDevice();
  }

  // endregion

  // protected, private helper methods
  protected readonly RouteSegments = RouteSegments;
  // endregion
}
