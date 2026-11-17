import { TestEntity, TestEnum } from '../test-entity';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { setupListComponentTest } from '../../test-setup';
import { By } from '@angular/platform-browser';
import { RouteSegments } from '../base-form-navigator/base-form-navigator.store';
import { MatHeaderCell, MatTable, MatTableDataSource } from '@angular/material/table';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { NavigatorCommand, type NavigationPayload } from '../base-form-navigator/navigation-payload';
import { TestBed } from '@angular/core/testing';
import { WorkingContextService } from '../working-context/working-context.service';

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
      const table = fixture.debugElement.query(By.directive(MatTable));
      expect(table).toBeTruthy();
    });

    it('table header element', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      const tableHeader = fixture.debugElement.query(By.directive(MatHeaderCell));
      expect(tableHeader).toBeTruthy();
    });

    it('autosize class on the header and the cells of an autosized column only', async () => {
      const autosizedConfig = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Project Name');
      autosizedConfig.autosizeColumn = true;
      const { fixture } = await setupListComponentTest([autosizedConfig, textareaConfig], MOCK_STORE_RESPONSE);
      const autosized = (column: string) => fixture.debugElement.queryAll(By.css(`.mat-column-${column}`)).map((cell) => cell.nativeElement.classList.contains('pp-col-autosize'));

      expect(autosized('name')).toEqual([true, true, true]);
      expect(autosized('description')).toEqual([false, false, false]);
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

    it('onChangeSelection() without a row only re-reads how many are selected', async () => {
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      vi.spyOn(store, 'selectEntity');
      vi.spyOn(store, 'deselectEntity');
      vi.spyOn(store, 'clearCurrentEntity');

      component.onChangeSelection();

      expect(store.selectEntity).not.toHaveBeenCalled();
      expect(store.deselectEntity).not.toHaveBeenCalled();
      expect(store.clearCurrentEntity).toHaveBeenCalled();
    });

    it('identifies a row by the key its store names — an embedded row is not keyed by id', async () => {
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      store.keyOf = (entity) => `key-${entity.name}`;
      vi.spyOn(store, 'selectEntity');

      component.selection.select(testEntity_1);
      component.onChangeSelection(testEntity_1);

      expect(store.selectEntity).toHaveBeenCalledWith('key-hello');
    });

    it('falls back to id for a store that names no key', async () => {
      const { component, store } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      (store as { keyOf?: unknown }).keyOf = undefined;
      vi.spyOn(store, 'selectEntity');

      component.selection.select(testEntity_1);
      component.onChangeSelection(testEntity_1);

      expect(store.selectEntity).toHaveBeenCalledWith(testEntity_1.id);
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

    it('artifactsOf() reads an ARTIFACT cell as a list, single object or array', async () => {
      const { component } = await setupListComponentTest([textboxConfig], MOCK_STORE_RESPONSE);
      const one = { bucket: 'b', objectId: 'o1', name: 'one.pdf', mimeType: 'application/pdf' };
      const two = { ...one, objectId: 'o2', name: 'two.pdf' };

      expect(component.artifactsOf(null)).toEqual([]);
      expect(component.artifactsOf(one)).toEqual([one]);
      expect(component.artifactsOf([one, null, two])).toEqual([one, two]);
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

    it('onSelectEntity() pushes the selected entity as SELECT_OR_CREATE response payload echoing the request attrName.', async () => {
      const requestPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'component', payload: { entityName: 'TestEntity' } };
      const { component, formNavigator } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE, requestPayload);
      vi.spyOn(formNavigator, 'navigateBack').mockResolvedValue(undefined);

      component.selection.select(testEntity_2);
      await component.onSelectEntity();

      expect(formNavigator.responsePayloads().get('component')).toEqual({ command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'component', payload: testEntity_2 });
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

  describe('working context button', () => {
    async function setupWithContext() {
      const setup = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      const workingContext = TestBed.inject(WorkingContextService);
      workingContext.register([{ name: 'test', entityName: 'TestEntity' }]);
      const select = vi.spyOn(workingContext, 'select').mockResolvedValue(true);
      setup.fixture.detectChanges();
      return { ...setup, select };
    }

    it('is absent when the entity is the root of no context', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig, textareaConfig], MOCK_STORE_RESPONSE);
      expect(fixture.nativeElement.querySelector('[data-testid="testEntity-set-active-context"]')).toBeNull();
    });

    it('has no action on the rows, but a button below the list that waits for exactly one selected row', async () => {
      const { fixture, select } = await setupWithContext();
      const cells = () => fixture.nativeElement.querySelectorAll('td.mat-mdc-cell:not(.mat-column-select)') as NodeListOf<HTMLElement>;
      const button = fixture.nativeElement.querySelector('[data-testid="testEntity-set-active-context"]') as HTMLButtonElement;
      expect(fixture.nativeElement.querySelectorAll('td [data-testid="testEntity-set-active-context"]')).toHaveLength(0);
      expect(button.disabled).toBe(true);

      cells()[2].click();
      fixture.detectChanges();
      expect(button.disabled).toBe(false);

      button.click();
      expect(select).toHaveBeenCalledWith('test', '2');

      cells()[0].click();
      fixture.detectChanges();
      expect(button.disabled).toBe(true);
    });

    it('sets the row active on double-click and on Enter', async () => {
      const { fixture, select } = await setupWithContext();
      const cell = fixture.nativeElement.querySelector('td.mat-mdc-cell:not(.mat-column-select)') as HTMLElement;

      cell.dispatchEvent(new MouseEvent('dblclick'));
      cell.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

      expect(select).toHaveBeenCalledTimes(2);
      expect(select).toHaveBeenCalledWith('test', '1');
    });
  });

  describe('retired records of a shared type:', () => {
    const retired = Object.assign(new TestEntity('3', 'gone', 'no longer used', false, 1, new Date('2024-01-01T00:00:00.000Z'), TestEnum.VALUE_ONE), { retiredAt: '2026-11-16T10:00:00Z' });

    it('offers no toggle for an entity whose records are never retired', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig], MOCK_STORE_RESPONSE);

      expect(fixture.debugElement.query(By.css('[data-testid="testEntity-show-retired"]'))).toBeNull();
    });

    it('lists retired records only when asked, and reloads when the toggle is flipped', async () => {
      const { fixture, component, store } = await setupListComponentTest([textboxConfig], [...MOCK_STORE_RESPONSE, retired], undefined, (descriptor) => {
        descriptor.reinstate = () => of({});
      });
      fixture.detectChanges();
      expect(fixture.debugElement.query(By.css('[data-testid="testEntity-show-retired"]'))).toBeTruthy();
      expect(store.load).toHaveBeenLastCalledWith(expect.objectContaining({ includeRetired: false }));

      (component as unknown as { onToggleRetired(on: boolean): void }).onToggleRetired(true);
      fixture.detectChanges();
      TestBed.flushEffects();

      expect(store.load).toHaveBeenLastCalledWith(expect.objectContaining({ includeRetired: true }));
    });

    it('marks a retired row as retired, and no other', async () => {
      const { fixture } = await setupListComponentTest([textboxConfig], [...MOCK_STORE_RESPONSE, retired], undefined, (descriptor) => {
        descriptor.reinstate = () => of({});
      });
      await fixture.whenStable();
      fixture.detectChanges();

      const rows = fixture.debugElement.queryAll(By.css('tr[mat-row]'));
      expect(rows.filter((row) => row.nativeElement.getAttribute('data-retired') === 'true')).toHaveLength(1);
      expect(rows.some((row) => row.nativeElement.classList.contains('base-entity-list-row--retired'))).toBe(true);
    });
  });
});
