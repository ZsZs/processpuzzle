import { BaseEntityToolbarComponent } from './base-entity-toolbar.component';
import { setupContainerComponentTest } from '../../test-setup';
import { By } from '@angular/platform-browser';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { TestEntity } from '../test-entity';

describe('BaseEntityToolbarComponent', () => {
  describe('sanity tests', () => {
    it('should create', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      expect(component).toBeTruthy();
    });
  });

  describe('template structure contain:', () => {
    it('when small device menu, otherwise buttons', async () => {
      const { fixture, breakpointObserver } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      const matToolbar = fixture.debugElement.query(By.css('mat-toolbar')).nativeElement;
      expect(matToolbar).toBeTruthy();

      let addButton = fixture.debugElement.query(By.css('mat-toolbar button[test-id=add]')).nativeElement;
      expect(addButton).toBeTruthy();
      let deleteButton = fixture.debugElement.query(By.css('mat-toolbar button[test-id=delete]')).nativeElement;
      expect(deleteButton).toBeTruthy();

      breakpointObserver.resize('handset');
      fixture.detectChanges();
      const menu = fixture.debugElement.query(By.css('mat-toolbar mat-menu')).nativeElement;
      expect(menu).toBeTruthy();

      const menuButton = fixture.debugElement.query(By.css('button[test-id=menu-button]')).nativeElement;
      menuButton.click(); // make menu list visible
      addButton = fixture.debugElement.query(By.css('button[test-id=add]')).nativeElement;
      expect(addButton).toBeTruthy();
      deleteButton = fixture.debugElement.query(By.css('button[test-id=delete]')).nativeElement;
      expect(deleteButton).toBeTruthy();
    });

    it('when activated route aint RouteSegments.LIST_ROUTE toolbar is not displayed', async () => {
      const { fixture, store } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      await store.navigateToDetails('1');
      fixture.detectChanges();
      const matToolbar = fixture.debugElement.query(By.css('mat-toolbar'));
      expect(matToolbar).toBeFalsy();
    });
  });

  describe('angular lifecycle hooks:', () => {
    it('onInit() takes reference to store from BaseEntityListOptions', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      expect(component.store).toBeTruthy();
    });
  });

  describe('component actions:', () => {
    it('onAddEntity() calls store.navigateToDetails() with id=new', async () => {
      const { component } = await setupContainerComponentTest(BaseEntityToolbarComponent<TestEntity>);
      jest.spyOn(component.store, 'navigateToDetails');

      (component as BaseEntityToolbarComponent<TestEntity>).onAddEntity();

      expect(component.store.navigateToDetails).toHaveBeenCalledWith(BaseUrlSegments.NewEntity);
    });

    it('onDeleteEntities()', async() => {
      // SETUP:
      const { fixture, component } = await setupContainerComponentTest( BaseEntityToolbarComponent );
      component.store.selectEntity( '1' );
      component.store.selectEntity( '2' );
      jest.spyOn( component.store, 'delete' );
      fixture.detectChanges();

      // EXERCISE:
      (component as BaseEntityToolbarComponent<TestEntity>).onDeleteEntities();

      // VERIFY:
      expect( component.store.delete ).toHaveBeenNthCalledWith( 1, '1' );
      expect( component.store.delete ).toHaveBeenNthCalledWith( 2, '2' );
    });

    it('onDoFilter()', async () => {
      // SETUP:
      const { fixture, component } = await setupContainerComponentTest(BaseEntityToolbarComponent);
      jest.spyOn(component.store, 'doFilter');

      //EXERCISE:
      const filterInput = fixture.debugElement.query(By.css('input[test-id=filter-input]')).nativeElement;
      filterInput.value = 'A';
      filterInput.dispatchEvent(new KeyboardEvent('keyup', { code: 'A' }));
      fixture.detectChanges();

      // VERIFY:
      expect(component.store.doFilter).toHaveBeenCalledWith('A');
    });
  });
});
