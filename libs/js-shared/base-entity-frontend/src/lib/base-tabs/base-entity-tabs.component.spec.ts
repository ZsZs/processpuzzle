import { BaseEntityTabsComponent } from './base-entity-tabs.component';
import { DummyComponent, HOSTED_SCREENS_SEGMENT, setupContainerComponentTest, TEST_ENTITY_TAB_SEGMENT } from '../../test-setup';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { EntityTabDescriptor } from '../base-entity/base-entity.descriptor';

describe('BaseEntityTabsComponent', () => {
  describe('component sanity', () => {
    it('should create', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityTabsComponent);
      expect(component).toBeTruthy();
    });
  });

  describe('template structure contains:', () => {
    it('nav with mat-tab-link', async () => {
      const { fixture } = await setupContainerComponentTest(BaseEntityTabsComponent);
      const nav = fixture.debugElement.query(By.css('nav')).nativeElement;
      expect(nav).toBeTruthy();
    });

    it('mat-tab-nav-panel', async () => {
      const { fixture } = await setupContainerComponentTest(BaseEntityTabsComponent);
      const tabPanel = fixture.debugElement.query(By.css('mat-tab-nav-panel')).nativeElement;
      expect(tabPanel).toBeTruthy();

      const statusBar = fixture.debugElement.query(By.css('mat-tab-nav-panel base-entity-statusbar')).nativeElement;
      expect(statusBar).toBeTruthy();

      const toolBar = fixture.debugElement.query(By.css('mat-tab-nav-panel base-entity-toolbar')).nativeElement;
      expect(toolBar).toBeTruthy();
    });
  });

  describe('tab labels:', () => {
    async function renderWithTranslations(translations: Record<string, string>) {
      const { fixture } = await setupContainerComponentTest(BaseEntityTabsComponent, { en: translations });
      const [listLink, detailsLink] = fixture.debugElement.queryAll(By.css('a[mat-tab-link]'));

      return { list: listLink.nativeElement.textContent.trim(), details: detailsLink.nativeElement.textContent.trim() };
    }

    it('interpolates the translated entity name into the tab keys', async () => {
      const labels = await renderWithTranslations({
        'base_entity.tabs.list': '{{ entity }} - lista',
        'base_entity.tabs.details': '{{ entity }} - részletek',
        'test_entity._self': 'Teszt entitás',
      });

      expect(labels.list).toBe('Teszt entitás - lista');
      expect(labels.details).toBe('Teszt entitás - részletek');
    });

    it('falls back to the raw entity name when the entity key is not translated', async () => {
      const labels = await renderWithTranslations({
        'base_entity.tabs.list': '{{ entity }} - list',
        'base_entity.tabs.details': '{{ entity }} - details',
      });

      expect(labels.list).toBe('TestEntity - list');
      expect(labels.details).toBe('TestEntity - details');
    });
  });

  describe('angular lifecycle hooks:', () => {
    it('onDestroy() deregisters tabs in store', async () => {
      // SETUP:
      const { component } = await setupContainerComponentTest(BaseEntityTabsComponent);
      vi.spyOn(component.store, 'tabIsInactive');

      // EXERCISE:
      (component as BaseEntityTabsComponent).ngOnDestroy();

      // VERIFY:
      expect(component.store.tabIsInactive).toHaveBeenCalled();
    });

    it('onInit() takes reference to store from BaseEntityListOptions', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityTabsComponent);
      expect(component.store).toBeTruthy();
    });
  });

  describe('component actions:', () => {
    it('onShowDetails()', async () => {
      // SETUP:
      const { component, formNavigator } = await setupContainerComponentTest(BaseEntityTabsComponent);
      vi.spyOn(formNavigator, 'navigateToDetails');

      // EXERCISE:
      await (component as BaseEntityTabsComponent).onShowDetails();

      // VERIFY:
      expect(formNavigator.navigateToDetails).toHaveBeenCalled();
    });

    it('onShowList()', async () => {
      // SETUP:
      const { fixture, component, formNavigator } = await setupContainerComponentTest(BaseEntityTabsComponent);
      await formNavigator.navigateToDetails('TestEntity', '1');
      fixture.detectChanges();
      vi.spyOn(formNavigator, 'navigateToList');

      // EXERCISE:
      await (component as BaseEntityTabsComponent).onShowList();

      // VERIFY:
      expect(formNavigator.navigateToList).toHaveBeenCalled();
    });
  });

  /**
   * A feature contributes a screen of its own through `BaseEntityDescriptor.extraTabs` — `Document` its
   * content editor. The framework knows only how to render the link and route to the segment; what the
   * screen is for stays with the feature.
   */
  describe('extra tabs:', () => {
    const previewTab: EntityTabDescriptor = { segment: TEST_ENTITY_TAB_SEGMENT, i18nKey: 'test_entity.tabs.preview', component: DummyComponent };

    async function setupWithPreviewTab(translations: Record<string, string> = {}) {
      return setupContainerComponentTest(BaseEntityTabsComponent, { en: translations }, [], [previewTab]);
    }

    function tabLinks(fixture: { debugElement: DebugElement }) {
      return fixture.debugElement.queryAll(By.css('a[mat-tab-link]'));
    }

    it('renders one link per declared tab, after the two generic ones', async () => {
      const { fixture } = await setupWithPreviewTab({ 'test_entity.tabs.preview': '{{ entity }} - preview' });

      const links = tabLinks(fixture);
      expect(links).toHaveLength(3);
      expect(links[2].nativeElement.textContent.trim()).toBe('TestEntity - preview');
      // Same shape createTestId gives the generic links, so an e2e locator reads the same either way.
      expect(links[2].attributes['data-testid']).toBe('testEntity-show-preview');
    });

    it('declares no extra link when the descriptor declares no tab', async () => {
      const { fixture } = await setupContainerComponentTest(BaseEntityTabsComponent);
      expect(tabLinks(fixture)).toHaveLength(2);
    });

    /** Same rule as Details: an entity-scoped screen has nothing to show until a row is selected. */
    it('is disabled while no entity is current', async () => {
      const { fixture, component, store } = await setupWithPreviewTab();
      expect(component.store.currentEntity()).toBeUndefined();
      expect(tabLinks(fixture)[2].nativeElement.getAttribute('aria-disabled')).toBe('true');

      store.setCurrentEntity('1');
      fixture.detectChanges();

      expect(tabLinks(fixture)[2].nativeElement.getAttribute('aria-disabled')).toBe('false');
    });

    it('navigates to <entity>/<id>/<segment>', async () => {
      const { component, store, formNavigator } = await setupWithPreviewTab();
      store.setCurrentEntity('1');

      await (component as BaseEntityTabsComponent).onShowTab(previewTab);

      expect(formNavigator.determineCurrentUrl()).toBe('/test-entity/1/' + TEST_ENTITY_TAB_SEGMENT);
    });

    /**
     * The reason the active-tab effect is a three-way branch: an extra tab's URL is neither the list nor the
     * details route, and the earlier "details or else list" shape lit the List link up while the extra tab's
     * own screen was on display.
     */
    it('marks itself active on its own route, leaving the list link alone', async () => {
      const { fixture, component, store, formNavigator } = await setupWithPreviewTab();
      store.setCurrentEntity('1');

      await formNavigator.navigateToTab('TestEntity', '1', TEST_ENTITY_TAB_SEGMENT);
      fixture.detectChanges();

      expect(component.store.currentTab()).toBe('TestEntity - ' + TEST_ENTITY_TAB_SEGMENT);
      expect(tabLinks(fixture)[0].nativeElement.getAttribute('aria-selected')).toBe('false');
      expect(tabLinks(fixture)[2].nativeElement.getAttribute('aria-selected')).toBe('true');
    });

    /**
     * `BaseFormNavigatorSingletonStore` is a singleton whose `activeRouteSegment` names the **innermost**
     * screen in the URL. When a container tab hosts another entity's screens — base-app's Preview tab — that
     * innermost screen is not this entity's, and reading it lit this tab bar's own List link up while the
     * container tab was the thing on display.
     */
    it('stays active while another entity’s screens are open inside it', async () => {
      const { fixture, component, store, formNavigator } = await setupWithPreviewTab();
      store.setCurrentEntity('1');

      await formNavigator.navigateToUrl(`/test-entity/1/${TEST_ENTITY_TAB_SEGMENT}/${HOSTED_SCREENS_SEGMENT}/hosted-entity/list`);
      fixture.detectChanges();

      expect(component.store.currentTab()).toBe('TestEntity - ' + TEST_ENTITY_TAB_SEGMENT);
      expect(tabLinks(fixture)[0].nativeElement.getAttribute('aria-selected')).toBe('false');
      expect(tabLinks(fixture)[2].nativeElement.getAttribute('aria-selected')).toBe('true');
    });

    /**
     * The other side of the same rule: an embedded child *is* part of this entity's aggregate, so its form
     * keeps the owner's Details tab active — which is where the user came from and goes back to.
     */
    it('follows an embedded child of its own aggregate onto the Details tab', async () => {
      const { fixture, component, store, formNavigator } = await setupWithPreviewTab();
      store.setCurrentEntity('1');

      await formNavigator.navigateToUrl('/test-entity/1/details/embedded-component/embedded_1_1/details');
      fixture.detectChanges();

      expect(component.store.currentTab()).toBe('TestEntity - details');
    });

    it('deregisters its tab on destroy', async () => {
      const { component } = await setupWithPreviewTab();
      vi.spyOn(component.store, 'tabIsInactive');

      (component as BaseEntityTabsComponent).ngOnDestroy();

      expect(component.store.tabIsInactive).toHaveBeenCalledWith('TestEntity - ' + TEST_ENTITY_TAB_SEGMENT);
    });
  });

  /**
   * The store is bound onto the descriptor by whoever builds it, and forgetting it used to surface from an
   * effect as `Cannot read properties of undefined (reading 'tabIsActive')`, several frames from the
   * descriptor that was actually incomplete.
   */
  describe('unbound store:', () => {
    it('names the descriptor that has no store', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityTabsComponent);
      (component as BaseEntityTabsComponent).entityDescriptor().store = undefined;

      expect(() => (component as BaseEntityTabsComponent).ngOnInit()).toThrowError(/TestEntity.*no store/);
    });
  });
});
