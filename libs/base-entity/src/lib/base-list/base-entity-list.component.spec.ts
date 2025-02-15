import { TestEntity, TestEnum } from '../test-entity';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { setupListComponentTest } from '../../test-setup';
import { By } from '@angular/platform-browser';
import { RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { MatTableDataSource } from '@angular/material/table';

describe('EntityListComponent', () => {
  const textboxConfig = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Project Name');
  const labelConfig = new BaseEntityAttrDescriptor('number', FormControlType.LABEL);
  labelConfig.linkedEntityType = 'TestEntityComponent';
  const textareaConfig = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Project feedback', undefined, false, { inputType: 'text' });
  const testEntity_1 = new TestEntity('1', 'hello', 'anything', false, 100, new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_ONE);
  const testEntity_2 = new TestEntity('2', 'bella', 'something', true, 200, new Date('2023-02-18T20:02:27.000Z'), TestEnum.VALUE_TWO);
  const MOCK_STORE_RESPONSE: TestEntity[] = [testEntity_1, testEntity_2];

  describe('sanity tests', () => {
    it('should create', async () => {
      const { component } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      expect(component).toBeTruthy();
    });
  });

  describe('template structure contains:', () => {
    it('table element', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      const table = fixture.debugElement.query(By.css('table')).nativeElement;
      expect(table).toBeTruthy();
    });

    it('table header element', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      const tableHeader = fixture.debugElement.query(By.css('table th')).nativeElement;
      expect(tableHeader).toBeTruthy();
    });

    it('paginator element', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      const matPaginator = fixture.debugElement.query(By.css('mat-paginator')).nativeElement;
      expect(matPaginator).toBeTruthy();
    });
  });

  describe('angular lifecycle hooks:', () => {
    it('onInit() determines active route segment', async () => {
      const { store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      expect(store.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
    });

    it('onAfterViewInit() connects data source and loads data', async () => {
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);

      expect(component.dataSource).toBeInstanceOf(MatTableDataSource);
      expect(component.dataSource.sort).toEqual(component.sort);
      expect(component.paginator).toEqual(component.paginator);
      expect(store.load).toHaveBeenCalled(); // spyOn was configured in setup
    });
  });

  describe('component actions:', () => {
    it('onChangeSelection(), sets/clears store.currentEntity() and adds/removes store.selectedEntities()', async () => {
      // SETUP:
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig, labelConfig], MOCK_STORE_RESPONSE);
      jest.spyOn(store, 'selectEntity');
      jest.spyOn(store, 'deselectEntity');
      jest.spyOn(store, 'setCurrentEntity');
      jest.spyOn(store, 'clearCurrentEntity');

      // EXERCISE:
      component.selection.select(testEntity_1);
      component.onChangeSelection(testEntity_1);

      // VERIFY:
      expect(store.selectEntity).toHaveBeenNthCalledWith(1, testEntity_1.id);
      expect(store.setCurrentEntity).toHaveBeenNthCalledWith(1, testEntity_1.id);

      // EXERCISE:
      component.selection.select(testEntity_2);
      component.onChangeSelection(testEntity_2);

      // VERIFY:
      expect(store.selectEntity).toHaveBeenNthCalledWith(2, testEntity_2.id);
      expect(store.clearCurrentEntity).toHaveBeenNthCalledWith(1);

      // EXERCISE:
      component.selection.deselect(testEntity_1);
      component.onChangeSelection(testEntity_1);

      // VERIFY:
      expect(store.deselectEntity).toHaveBeenNthCalledWith(1, testEntity_1);
      // there is only 1 entity is selected, so it will be also in store selected
      expect(store.setCurrentEntity).toHaveBeenNthCalledWith(2, testEntity_2.id);
    });

    it('navigateToDetails() calls store to navigate and set current entity.', async () => {
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      jest.spyOn(store, 'navigateToDetails');
      jest.spyOn(store, 'setCurrentEntity');

      component.onNavigateToDetails(testEntity_2);

      expect(store.navigateToDetails).toHaveBeenCalledWith(testEntity_2.id);
      expect(store.setCurrentEntity).toHaveBeenCalledWith(testEntity_2.id);
    });

    it('navigateToRelated() calls store to navigate to.', async () => {
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig, labelConfig], MOCK_STORE_RESPONSE);
      jest.spyOn(store, 'navigateToRelated');

      component.onNavigateToRelated(labelConfig, testEntity_2);

      expect(store.navigateToRelated).toHaveBeenCalledWith('TestEntityComponent', testEntity_2.number);
    });

    it('onRowClick() ', async () => {
      const { component } = await setupListComponentTest([textboxConfig, textareaConfig, labelConfig], MOCK_STORE_RESPONSE);
      jest.spyOn(component, 'onChangeSelection');

      component.onRowClick(testEntity_2);

      expect(component.selection.isSelected(testEntity_2)).toBeTruthy();
      expect(component.onChangeSelection).toHaveBeenCalledWith(testEntity_2);
    });

    it('onToggleAllRows() ', async () => {
      // SETUP:
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig, labelConfig], MOCK_STORE_RESPONSE);
      jest.spyOn(store, 'selectEntity');
      jest.spyOn(store, 'deselectAll');

      // EXERCISE:
      component.onToggleAllRows();

      // VERIFY:
      expect(component.selection.isSelected(testEntity_1)).toBeTruthy();
      expect(component.selection.isSelected(testEntity_2)).toBeTruthy();
      expect(store.selectEntity).toHaveBeenNthCalledWith(1, testEntity_1.id);
      expect(store.selectEntity).toHaveBeenNthCalledWith(2, testEntity_2.id);

      // EXERCISE:
      component.onToggleAllRows();

      // VERIFY:
      expect(component.selection.isSelected(testEntity_1)).toBeFalsy();
      expect(component.selection.isSelected(testEntity_2)).toBeFalsy();
      expect(store.deselectAll).toHaveBeenNthCalledWith(1);
    });
  });

  describe('triggers from store:', () => {
    it('when store.entities() changed, datasource.data is updated.', async () => {
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      expect(component.dataSource.data).toEqual(store.entities());
    });

    it('when store.filterKey() changed, this.dataSource.filter is updated.', async () => {
      const { fixture, component, store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      store.doFilter('A');
      fixture.detectChanges();
      expect(component.dataSource.filter).toEqual('a');
    });
  });
});
