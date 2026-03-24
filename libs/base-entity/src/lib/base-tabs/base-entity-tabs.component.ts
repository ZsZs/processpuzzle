import { Component, computed, effect, input, OnDestroy, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { MatTabLink, MatTabNav, MatTabNavPanel } from '@angular/material/tabs';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityToolbarComponent } from '../base-toolbar/base-entity-toolbar.component';
import { RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { BaseEntityStatusbarComponent } from '../base-statusbar/base-entity-statusbar.component';

@Component({
  selector: 'base-entity-tabs',
  standalone: true,
  imports: [CommonModule, MatTabNav, MatTabLink, MatTabNavPanel, RouterOutlet, BaseEntityToolbarComponent, BaseEntityStatusbarComponent],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="tabPanel">
      <a mat-tab-link (click)="onShowList()" [active]="store.currentTab() === listTabName()">{{ listTabName() }}</a>
      <a mat-tab-link [disabled]="store.currentEntity() === undefined" (click)="onShowDetails()" [active]="store.currentTab() === detailsTabName()">{{ detailsTabName() }}</a>
    </nav>

    <mat-tab-nav-panel #tabPanel>
      <base-entity-statusbar [entityDescriptor]="entityDescriptor()" />
      <base-entity-toolbar [entityDescriptor]="entityDescriptor()" />
      <router-outlet [routerOutletData]="entityDescriptor()" />
    </mat-tab-nav-panel>
  `,
})
export class BaseEntityTabsComponent implements OnDestroy, OnInit {
  store: any;
  entityDescriptor = input.required<BaseEntityDescriptor>();
  selectedEntityId: Signal<string | undefined> = computed(() => (this.store.currentId() ? this.store.currentId() : BaseUrlSegments.NewEntity));
  detailsTabName: Signal<string> = computed(() => this.entityDescriptor().entityName + ' - details');
  listTabName: Signal<string> = computed(() => this.entityDescriptor().entityName + ' - list');

  constructor() {
    this.registerEffects();
  }

  // region angular lifecycle hooks
  ngOnDestroy(): void {
    this.store.tabIsInactive(this.listTabName());
    this.store.tabIsInactive(this.detailsTabName());
  }

  ngOnInit() {
    this.store = this.entityDescriptor().store;
  }
  // endregion

  // region event handlers
  async onShowDetails() {
    if (this.store.activeRouteSegment() != RouteSegments.DETAILS_ROUTE) {
      const id = this.selectedEntityId() ? this.selectedEntityId() : BaseUrlSegments.NewEntity;
      await this.store.navigateToDetails(id);
      this.store.tabIsActive(this.detailsTabName());
    }
  }

  async onShowList() {
    if (this.store.activeRouteSegment() != RouteSegments.LIST_ROUTE) {
      await this.store.navigateToList(this.entityDescriptor().entityName);
      this.store.tabIsActive(this.listTabName());
    }
  }
  // endregion

  // region protected, private helper methods
  private registerEffects() {
    effect(() => {
      if (this.store.activeRouteSegment() === RouteSegments.DETAILS_ROUTE) {
        this.store.tabIsActive(this.detailsTabName());
      } else {
        this.store.tabIsActive(this.listTabName());
      }
    });
  }
}
