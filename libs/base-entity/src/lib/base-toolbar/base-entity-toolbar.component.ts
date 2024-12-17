import { Component, inject, input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { MatButton } from '@angular/material/button';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { LayoutService } from '@processpuzzle/util';

@Component({
  selector: 'base-entity-toolbar',
  standalone: true,
  imports: [CommonModule, MatToolbar, MatIcon, MatFormField, MatInput, MatButton, MatMenu, MatMenuItem, MatMenuTrigger],
  templateUrl: './base-entity-toolbar.component.html',
})
export class BaseEntityToolbarComponent<Entity extends BaseEntity> implements OnInit {
  baseEntityListOptions = input.required<BaseEntityDescriptor>();
  layoutService = inject(LayoutService);
  store: any;

  // region Angular lifecycle hooks
  ngOnInit(): void {
    this.store = this.baseEntityListOptions().store;
  }
  // endregion

  // event handling methods
  onAddEntity() {
    this.store.navigateToDetails(BaseUrlSegments.NewEntity);
  }

  onDeleteEntities() {
    this.store.selectedEntities().forEach((entity: Entity) => this.store.delete(entity.id));
  }

  onDoFilter($event: KeyboardEvent) {
    const filterValue = ($event.target as HTMLInputElement).value;
    this.store.doFilter(filterValue);
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
