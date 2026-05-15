import { Component, inject, input, OnInit } from '@angular/core';
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

@Component({
  selector: 'base-entity-toolbar',
  standalone: true,
  imports: [CommonModule, MatToolbarModule, MatIcon, MatFormField, MatInput, MatButton, MatIconButton, MatSuffix, MatMenu, MatMenuItem, MatMenuTrigger],
  templateUrl: './base-entity-toolbar.component.html',
})
export class BaseEntityToolbarComponent<Entity extends BaseEntity> implements OnInit {
  entityDescriptor = input.required<BaseEntityDescriptor>();
  readonly layoutService = inject(LayoutService);
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  store: any;

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.entityDescriptor().store;
    this.formNavigator.setEntityName(this.entityDescriptor().entityName);
  }

  // endregion

  // event handling methods
  onAddEntity() {
    this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, BaseUrlSegments.NewEntity);
  }

  onDeleteEntities() {
    this.store.selectedEntities().forEach((entity: Entity) => this.store.delete(entity.id));
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
