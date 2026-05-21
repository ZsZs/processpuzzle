import { TestEntity, TestEnum } from '../test-entity';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { setupListComponentTest } from '../../test-setup';
import { By } from '@angular/platform-browser';
import { RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { MatHeaderCell, MatTable, MatTableDataSource } from '@angular/material/table';
import { describe, expect, it, vi } from 'vitest';
import { NavigatorCommand, type NavigationPayload } from '../base-form-navigator/navigation-payload';

describe('EntityListComponent', () => {
  const textboxConfig = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Project Name');
  const labelConfig = new BaseEntityAttrDescriptor('number', FormControlType.LABEL);
  labelConfig.linkedEntityType = new BaseEntityDescriptor({
    attrDescriptors: [],
    entityName: 'TestEntityComponent',
    entityTitle: 'Test Entity Component',
    store: {},
  });
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
      const table = fixture.debugElement.query(By.directive(MatTable));
      expect(table).toBeTruthy();
    });

    it('table header element', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      const tableHeader = fixture.debugElement.query(By.directive(MatHeaderCell));
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
      const { formNavigator } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      expect(formNavigator.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
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
      vi.spyOn(store, 'selectEntity');
      vi.spyOn(store, 'deselectEntity');
      vi.spyOn(store, 'setCurrentEntity');
      vi.spyOn(store, 'clearCurrentEntity');

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
      expect(store.deselectEntity).toHaveBeenNthCalledWith(1, testEntity_1.id);
      // there is only 1 entity is selected, so it will be also in store selected
      expect(store.setCurrentEntity).toHaveBeenNthCalledWith(2, testEntity_2.id);
    });

    it('navigateToDetails() calls form navigator and sets current entity.', async () => {
      const { component, store, formNavigator } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      vi.spyOn(formNavigator, 'navigateToDetails');
      vi.spyOn(store, 'setCurrentEntity');

      component.onNavigateToDetails(testEntity_2);

      expect(formNavigator.navigateToDetails).toHaveBeenCalledWith('TestEntity', testEntity_2.id);
      expect(store.setCurrentEntity).toHaveBeenCalledWith(testEntity_2.id);
    });

    it('navigateToRelated() calls form navigator to navigate to.', async () => {
      const { component, formNavigator } = await setupListComponentTest([textboxConfig, textareaConfig, labelConfig], MOCK_STORE_RESPONSE);
      vi.spyOn(formNavigator, 'navigateToRelated');

      component.onNavigateToRelated(labelConfig, testEntity_2);

      expect(formNavigator.navigateToRelated).toHaveBeenCalledWith('TestEntityComponent', testEntity_2.number);
    });

    it('onRowClick() ', async () => {
      const { component } = await setupListComponentTest([textboxConfig, textareaConfig, labelConfig], MOCK_STORE_RESPONSE);
      vi.spyOn(component, 'onChangeSelection');

      component.onRowClick(testEntity_2);

      expect(component.selection.isSelected(testEntity_2)).toBeTruthy();
      expect(component.onChangeSelection).toHaveBeenCalledWith(testEntity_2);
    });

    it('onToggleAllRows() ', async () => {
      // SETUP:
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig, labelConfig], MOCK_STORE_RESPONSE);
      vi.spyOn(store, 'selectEntity');
      vi.spyOn(store, 'deselectAll');

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

    it('shows Select and Cancel actions for SELECT_OR_CREATE requests and enables Select for one selected entity.', async () => {
      const requestPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, payload: { entityName: 'TestEntity' } };
      const { fixture, component } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE, requestPayload);

      const cancelButton = fixture.debugElement.query(By.css('button#cancel')).nativeElement as HTMLButtonElement;
      const selectButton = fixture.debugElement.query(By.css('button#select')).nativeElement as HTMLButtonElement;

      expect(cancelButton).toBeTruthy();
      expect(selectButton).toBeTruthy();
      expect(selectButton.disabled).toBeTruthy();

      component.selection.select(testEntity_1);
      fixture.detectChanges();

      expect(selectButton.disabled).toBeFalsy();
    });

    it('onSelectEntity() pushes the selected entity as SELECT_OR_CREATE response payload.', async () => {
      const requestPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, payload: { entityName: 'TestEntity' } };
      const { component, formNavigator } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE, requestPayload);
      vi.spyOn(formNavigator, 'navigateBack').mockResolvedValue(undefined);

      component.selection.select(testEntity_2);
      await component.onSelectEntity();

      expect(formNavigator.responsePayloads().peek()).toEqual({ command: NavigatorCommand.SELECT_OR_CREATE, payload: testEntity_2 });
      expect(formNavigator.navigateBack).toHaveBeenCalled();
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
