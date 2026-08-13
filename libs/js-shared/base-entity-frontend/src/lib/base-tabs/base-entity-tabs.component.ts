import { Component, computed, effect, inject, input, OnDestroy, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { MatTabLink, MatTabNav, MatTabNavPanel } from '@angular/material/tabs';
import { BaseEntityDescriptor, EntityTabDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityToolbarComponent } from '../base-toolbar/base-entity-toolbar.component';
import { BaseFormNavigatorSingletonStore, RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { BaseEntityStatusbarComponent } from '../base-statusbar/base-entity-statusbar.component';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { translateLabel } from '../i18n/entity-label.pipe';

@Component({
  selector: 'base-entity-tabs',
  standalone: true,
  imports: [CommonModule, MatTabNav, MatTabLink, MatTabNavPanel, RouterOutlet, BaseEntityToolbarComponent, BaseEntityStatusbarComponent, TranslocoPipe],
  template: `
    <nav mat-tab-nav-bar [tabPanel]="tabPanel">
      <a mat-tab-link [attr.data-testid]="entityDescriptor().createTestId('show-list')" (click)="onShowList()" [active]="store.currentTab() === listTabName()"
        >{{ 'base_entity.tabs.list' | transloco: { entity: entityLabel() } }}</a
      >
      <a
        mat-tab-link
        [attr.data-testid]="entityDescriptor().createTestId('show-details')"
        [disabled]="store.currentEntity() === undefined"
        (click)="onShowDetails()"
        [active]="store.currentTab() === detailsTabName()"
        >{{ 'base_entity.tabs.details' | transloco: { entity: entityLabel() } }}</a
      >
      @for (tab of entityDescriptor().extraTabs; track tab.segment) {
        <a
          mat-tab-link
          [attr.data-testid]="entityDescriptor().createTestId(tab.testIdSuffix ?? 'show-' + tab.segment)"
          [disabled]="store.currentEntity() === undefined"
          (click)="onShowTab(tab)"
          [active]="store.currentTab() === tabName(tab)"
          >{{ tab.i18nKey | transloco: { entity: entityLabel() } }}</a
        >
      }
    </nav>

    <mat-tab-nav-panel #tabPanel>
      <base-entity-statusbar [entityDescriptor]="entityDescriptor()" />
      <base-entity-toolbar [entityDescriptor]="entityDescriptor()" />
      <router-outlet [routerOutletData]="entityDescriptor()" />
    </mat-tab-nav-panel>
  `,
})
export class BaseEntityTabsComponent implements OnDestroy, OnInit {
  store!: BaseEntityStoreApi<BaseEntity>;
  entityDescriptor = input.required<BaseEntityDescriptor>();
  selectedEntityId: Signal<string | undefined> = computed(() => (this.store.currentId() ? this.store.currentId() : BaseUrlSegments.NewEntity));
  detailsTabName: Signal<string> = computed(() => this.entityDescriptor().entityName + ' - details');
  listTabName: Signal<string> = computed(() => this.entityDescriptor().entityName + ' - list');
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly transloco = inject(TranslocoService);

  constructor() {
    this.registerEffects();
  }

  // region angular lifecycle hooks
  ngOnDestroy(): void {
    this.store.tabIsInactive(this.listTabName());
    this.store.tabIsInactive(this.detailsTabName());
    this.entityDescriptor().extraTabs.forEach((tab) => this.store.tabIsInactive(this.tabName(tab)));
  }

  ngOnInit() {
    const descriptor = this.entityDescriptor();
    // Named rather than left to fail on the first `this.store.…`: the store is bound onto the descriptor by
    // whoever builds it (a facade does it in its `descriptor` getter, a container by hand), and forgetting
    // it used to surface here as `Cannot read properties of undefined (reading 'tabIsActive')` from an
    // effect, several frames away from the descriptor that is actually incomplete.
    if (!descriptor.store) {
      throw new Error(`'${descriptor.entityName}' descriptor has no store; bind it (descriptor.store = <store>) before rendering base-entity-container.`);
    }
    this.store = descriptor.store as BaseEntityStoreApi<BaseEntity>;
    this.formNavigator.setEntityName(descriptor.entityName);
    this.formNavigator.registerTabSegments(descriptor.extraTabs.map((tab) => tab.segment));
  }
  // endregion

  // region event handlers
  async onShowDetails() {
    if (this.formNavigator.activeRouteSegment() != RouteSegments.DETAILS_ROUTE) {
      const id = this.selectedEntityId() ?? BaseUrlSegments.NewEntity;
      await this.formNavigator.navigateToDetails(this.entityDescriptor().entityName, id);
      this.store.tabIsActive(this.detailsTabName());
    }
  }

  async onShowList() {
    if (this.formNavigator.activeRouteSegment() != RouteSegments.LIST_ROUTE) {
      await this.formNavigator.navigateToList(this.entityDescriptor().entityName);
      this.store.tabIsActive(this.listTabName());
    }
  }

  async onShowTab(tab: EntityTabDescriptor) {
    if (this.formNavigator.activeTabSegment() === tab.segment) return;

    const id = this.selectedEntityId() ?? BaseUrlSegments.NewEntity;
    await this.formNavigator.navigateToTab(this.entityDescriptor().entityName, id, tab.segment);
    this.store.tabIsActive(this.tabName(tab));
  }
  // endregion

  // region protected, private helper methods
  /**
   * Translated entity name fed to the `base_entity.tabs.*` keys as the `entity` parameter. Plain method
   * rather than a computed: the impure `transloco` pipe wrapping it already re-renders on language
   * switch, and the translation is not a signal dependency.
   */
  protected entityLabel(): string {
    const descriptor = this.entityDescriptor();
    return translateLabel(this.transloco, descriptor.i18nKey(), descriptor.entityName);
  }

  /** Name this tab is tracked under in the store, in the same `<entity> - <what>` shape as the two generic ones. */
  protected tabName(tab: EntityTabDescriptor): string {
    return this.entityDescriptor().entityName + ' - ' + tab.segment;
  }

  private registerEffects() {
    effect(() => {
      const activeSegment = this.formNavigator.activeRouteSegment();
      if (activeSegment === RouteSegments.DETAILS_ROUTE) {
        this.store.tabIsActive(this.detailsTabName());
      } else if (activeSegment === RouteSegments.ENTITY_TAB_ROUTE) {
        // Three-way rather than "details or else list": an extra tab's URL is neither, and the old else
        // branch lit the List link up while the extra tab's own screen was on display.
        const openTab = this.entityDescriptor().extraTabs.find((tab) => tab.segment === this.formNavigator.activeTabSegment());
        if (openTab) this.store.tabIsActive(this.tabName(openTab));
      } else {
        this.store.tabIsActive(this.listTabName());
      }
    });
  }
}
