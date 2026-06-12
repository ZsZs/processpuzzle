import { BaseEntityTabsComponent } from './base-entity-tabs.component';
import { setupContainerComponentTest } from '../../test-setup';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';

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
});
