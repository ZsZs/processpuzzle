import { TestEntity, TestEnum } from '../test-entity';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestEntityService } from '../base-entity-service/test-entity.service';
import { TestEntityStore } from '../test-entity.store';
import { provideRouter } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { BaseEntityLoadResponse, BaseEntityQueryCondition } from '../base-entity-service/base-entity-load-response';
import { DummyComponent, MOCK_API_RESPONSE, MOCK_PAGED_RESPONSE, newTestEntity, setupMockService, testEntity_1, testEntity_2 } from '../../test-setup';
import { describe, expect, it, vi } from 'vitest';
import { signalStore } from '@ngrx/signals';
import { Observable, of, Subject, throwError } from 'rxjs';
import { mock } from 'vitest-mock-extended';
import { entityNameFromType } from '../base-entity/base-entity-utility';
import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { BaseEntityStore } from './base-entity.store';

describe('BaseEntityStore', () => {
  function setup({ isApiFailed = false, payload = MOCK_API_RESPONSE }: { isApiFailed?: boolean; payload?: TestEntity[] | BaseEntityLoadResponse<TestEntity> } = {}) {
    const mockService = setupMockService({ isApiFailed, payload });
    TestBed.configureTestingModule({
      imports: [DummyComponent],
      providers: [provideHttpClient(), provideRouter([]), TestEntityStore, { provide: TestEntityService, useValue: mockService }],
    });
    const fixture = TestBed.createComponent(DummyComponent);
    const store = TestBed.inject(TestEntityStore);
    fixture.detectChanges();
    return { mockService, store };
  }

  it('derives the entity name from the entity type', () => {
    expect(entityNameFromType(TestEntity)).toEqual('TestEntity');
  });

  it('createEntity() instantiate a new Object from the the given entityType argument', () => {
    const { store } = setup();
    expect(store.createEntity()).toEqual({
      boolean: true,
      date: expect.any(Date),
      description: '',
      enumValue: 0,
      id: expect.any(String),
      name: 'TestEntity',
      number: 1,
    });
  });

  describe('delete(), load(), save(), update()', () => {
    it('add() will call API and adds entity to entities[]', async () => {
      const { store, mockService } = setup();
      await store.add(newTestEntity);

      expect(mockService.add).toHaveBeenCalledTimes(1);
      const expectedEntities = [...MOCK_API_RESPONSE, ...[newTestEntity]];
      expect(store.entities()).toStrictEqual(expectedEntities);
    });

    it('add() will verify if API is failed', async () => {
      const { store, mockService } = setup({ isApiFailed: true });

      await store.add(newTestEntity);

      expect(mockService.add).toHaveBeenCalledTimes(1);
      expect(store.error()).toStrictEqual('API Failed');
    });

    it('delete() calls service and removes the entity from entities[].', async () => {
      const { store, mockService } = setup();

      await store.delete('1');
      expect(mockService.delete).toHaveBeenCalledTimes(1);
      expect(store.entities()).toStrictEqual([testEntity_2]);
    });

    it('delete() will verify if API is failed', async () => {
      const { store, mockService } = setup({ isApiFailed: true });

      await store.delete('2');
      expect(mockService.delete).toHaveBeenCalledTimes(1);
      expect(store.error()).toStrictEqual('API Failed');
    });

    it('load() will call API and add returned entities to entities[]', () => {
      const { store, mockService } = setup();
      expect(mockService.findByQuery).toHaveBeenCalledTimes(1);
      expect(store.entities()).toStrictEqual(MOCK_API_RESPONSE);
    });

    it('load(), can handle paged response', () => {
      const { store, mockService } = setup({ payload: MOCK_PAGED_RESPONSE });
      expect(mockService.findByQuery).toHaveBeenCalledTimes(1);
      expect(store.entities()).toStrictEqual(MOCK_API_RESPONSE);
      expect(store.number()).toEqual(33);
      expect(store.size()).toEqual(2);
      expect(store.totalElements()).toEqual(666);
      expect(store.totalPages()).toEqual(333);
    });

    it('load() will verify if API is failed', () => {
      const { store, mockService } = setup({ isApiFailed: true });

      expect(mockService.findByQuery).toHaveBeenCalledTimes(1);
      expect(store.error()).toStrictEqual('API Failed');
    });

    it('deleteAll() calls service and removes all entities.', () => {
      const { store, mockService } = setup();

      store.deleteAll();
      expect(mockService.deleteAll).toHaveBeenCalledTimes(1);
      expect(store.entities()).toStrictEqual([]);
    });

    it('deleteAll() will verify if API is failed', () => {
      const { store, mockService } = setup({ isApiFailed: true });

      store.deleteAll();

      expect(mockService.deleteAll).toHaveBeenCalledTimes(1);
      expect(store.error()).toStrictEqual('API Failed');
    });

    it('update() will call API and updates entity in entities[]', async () => {
      const { store, mockService } = setup();
      Reflect.set(testEntity_1, 'name', 'changed');

      await store.update(testEntity_1);

      expect(mockService.update).toHaveBeenCalledTimes(1);
      expect(store.loadById('1')).toStrictEqual(testEntity_1);
    });

    it('update() will verify if API is failed', async () => {
      const { store, mockService } = setup({ isApiFailed: true });

      await store.update(testEntity_1);

      expect(mockService.update).toHaveBeenCalledTimes(1);
      expect(store.error()).toStrictEqual('API Failed');
    });
  });

  describe('reload()', () => {
    it('replaces the row the repository answers for with the fresh one', async () => {
      const { store, mockService } = setup();
      const fresh = new TestEntity('1', 'reloaded', 'fresh', true, 101, new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_ONE);
      mockService.findById.mockReturnValue(of(fresh));

      const reloaded = await store.reload('1');

      expect(mockService.findById).toHaveBeenCalledWith('1');
      expect(reloaded).toBe(fresh);
      expect(store.entities()).toHaveLength(2);
      expect(store.loadById('1')).toBe(fresh);
      expect(store.loadById('2')).toBe(testEntity_2);
    });

    it('appends a row the store does not hold yet', async () => {
      const { store, mockService } = setup();
      const missing = new TestEntity('3', 'third', 'new', false, 300, new Date('2024-03-18T20:02:27.000Z'), TestEnum.VALUE_ONE);
      mockService.findById.mockReturnValue(of(missing));

      await store.reload('3');

      expect(store.entities().map((entity) => entity.id)).toEqual(['1', '2', '3']);
    });

    it('returns undefined and leaves the rows alone when the repository answers nothing', async () => {
      const { store, mockService } = setup();
      mockService.findById.mockReturnValue(of(undefined as unknown as PersistedEntity<TestEntity>));

      expect(await store.reload('1')).toBeUndefined();
      expect(store.entities()).toStrictEqual(MOCK_API_RESPONSE);
    });

    it('sets the error state when the repository fails', async () => {
      const { store, mockService } = setup();
      mockService.findById.mockReturnValue(throwError(() => ({ message: 'API Failed' })));

      expect(await store.reload('1')).toBeUndefined();
      expect(store.error()).toStrictEqual('API Failed');
      expect(store.entities()).toStrictEqual(MOCK_API_RESPONSE);
    });
  });

  /** What an embedded child's Save does to its owner's document — see `EmbeddedAggregateAccessor.stageRoot`. */
  describe('stage()', () => {
    const staged = (): TestEntity => new TestEntity('1', 'staged', 'unsaved', true, 7, new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_ONE);

    it('replaces the row without calling the repository, and marks it unsaved', () => {
      const { store, mockService } = setup();
      const entity = staged();

      store.stage(entity);

      expect(store.loadById('1')).toBe(entity);
      expect(store.hasStagedChanges('1')).toBe(true);
      expect(store.hasStagedChanges('2')).toBe(false);
      expect(mockService.update).not.toHaveBeenCalled();
    });

    it('marks a row once, however often it is staged', () => {
      const { store } = setup();

      store.stage(staged());
      store.stage(staged());

      expect(store.stagedIds()).toEqual(['1']);
    });

    it('ignores a row the store does not hold', () => {
      const { store } = setup();

      store.stage(new TestEntity('9', 'unknown', '', true, 0, new Date(), TestEnum.VALUE_ONE));

      expect(store.entities()).toStrictEqual(MOCK_API_RESPONSE);
      expect(store.stagedIds()).toEqual([]);
    });

    it('update() persists the staged row and clears the mark', async () => {
      const { store, mockService } = setup();
      const entity = staged();
      store.stage(entity);

      await store.update(entity);

      expect(mockService.update).toHaveBeenCalledWith(entity);
      expect(store.hasStagedChanges('1')).toBe(false);
    });

    it('keeps the mark when update() is refused, so the edits are not mistaken for saved ones', async () => {
      const { store, mockService } = setup();
      store.stage(staged());
      mockService.update.mockReturnValue(throwError(() => ({ message: 'Order total does not match the sum of line item subtotals.' })));

      await store.update(staged());

      expect(store.hasStagedChanges('1')).toBe(true);
    });

    it('reload() discards the staged row', async () => {
      const { store, mockService } = setup();
      store.stage(staged());
      mockService.findById.mockReturnValue(of(testEntity_1));

      await store.reload('1');

      expect(store.loadById('1')).toBe(testEntity_1);
      expect(store.hasStagedChanges('1')).toBe(false);
    });
  });

  describe('select(), deselect(), currentEntity()', () => {
    it('selectEntity(), deselectEntity() handles the selectedEntities array.', () => {
      const { store } = setup();
      store.selectEntity('1');
      store.selectEntity('2');
      expect(store.selectedEntities()).toStrictEqual([testEntity_1, testEntity_2]);

      store.deselectEntity('1');
      store.deselectEntity('2');
      expect(store.selectedEntities()).toEqual([]);

      store.selectEntity('1');
      store.selectEntity('2');
      store.deselectAll();
      expect(store.selectedEntities()).toEqual([]);
    });

    it('setCurrentEntity() stores the given entity.', () => {
      const { store } = setup();
      store.setCurrentEntity('1');
      expect(store.currentEntity()).toEqual(testEntity_1);
      expect(store.currentId()).toEqual('1');

      store.setCurrentEntity('2');
      expect(store.currentEntity()).toEqual(testEntity_2);
      expect(store.currentId()).toEqual('2');
    });
  });

  describe('setCurrentEntity() for a row the loaded rows do not hold', () => {
    // The backend lists a page at a time, so the row a link names may be on another page, or the list may not have
    // arrived when the screen asks.
    const beyondThePage = new TestEntity('21', 'twenty-first', 'on page two', false, 21, new Date('2024-03-18T20:02:27.000Z'), TestEnum.VALUE_ONE);

    it('asks for the row on its own and makes it current', async () => {
      const { store, mockService } = setup();
      mockService.findById.mockReturnValue(of(beyondThePage));

      store.setCurrentEntity('21');

      await vi.waitFor(() => expect(store.currentEntity()).toBe(beyondThePage));
      expect(mockService.findById).toHaveBeenCalledWith('21');
      expect(store.currentId()).toEqual('21');
      expect(store.entities().map((entity) => entity.id)).toEqual(['1', '2', '21']);
    });

    it('clears the current row at once, rather than leaving the previous one while the new one is fetched', () => {
      const { store, mockService } = setup();
      store.setCurrentEntity('1');
      mockService.findById.mockReturnValue(of(beyondThePage));

      store.setCurrentEntity('21');

      expect(store.currentEntity()).toBeUndefined();
    });

    it('does not ask for a row the loaded rows hold', () => {
      const { store, mockService } = setup();

      store.setCurrentEntity('2');

      expect(store.currentEntity()).toBe(testEntity_2);
      expect(mockService.findById).not.toHaveBeenCalled();
    });

    it('leaves the current row unset when the repository has no such row', async () => {
      const { store, mockService } = setup();
      mockService.findById.mockReturnValue(of(undefined as unknown as PersistedEntity<TestEntity>));

      store.setCurrentEntity('999');

      await vi.waitFor(() => expect(mockService.findById).toHaveBeenCalledWith('999'));
      expect(store.currentEntity()).toBeUndefined();
    });

    describe('while the list is still on its way', () => {
      /** A store whose first page has not arrived yet; `arrive` delivers it. */
      function setupWithPendingList() {
        const mockService = setupMockService({ payload: MOCK_API_RESPONSE });
        const firstPage = new Subject<TestEntity[]>();
        mockService.findByQuery.mockReturnValue(firstPage.asObservable() as never);
        TestBed.configureTestingModule({
          imports: [DummyComponent],
          providers: [provideHttpClient(), provideRouter([]), TestEntityStore, { provide: TestEntityService, useValue: mockService }],
        });
        TestBed.createComponent(DummyComponent).detectChanges();
        const store = TestBed.inject(TestEntityStore);
        const arrive = (rows: TestEntity[]) => {
          firstPage.next(rows);
          firstPage.complete();
        };
        return { store, mockService, arrive };
      }

      it('takes the row from the list when the list has it, and asks for nothing', () => {
        const { store, mockService, arrive } = setupWithPendingList();

        store.setCurrentEntity('2');
        expect(store.currentEntity()).toBeUndefined();
        arrive([testEntity_1, testEntity_2]);

        expect(store.currentEntity()).toBe(testEntity_2);
        expect(mockService.findById).not.toHaveBeenCalled();
      });

      it('asks for the row on its own when the list that arrives does not have it', async () => {
        const { store, mockService, arrive } = setupWithPendingList();
        mockService.findById.mockReturnValue(of(beyondThePage));

        store.setCurrentEntity('21');
        expect(mockService.findById).not.toHaveBeenCalled();
        arrive([testEntity_1, testEntity_2]);

        await vi.waitFor(() => expect(store.currentEntity()).toBe(beyondThePage));
        expect(mockService.findById).toHaveBeenCalledWith('21');
      });

      it('does not take the row for the current one again when a later list is loaded', () => {
        const { store, arrive } = setupWithPendingList();
        store.setCurrentEntity('2');
        arrive([testEntity_1, testEntity_2]);
        store.setCurrentEntity('1');

        store.load({ page: 2 });

        expect(store.currentEntity()).toBe(testEntity_1);
      });
    });

    it('drops an answer that arrives after another row was asked for', async () => {
      const { store, mockService } = setup();
      const late = new Subject<PersistedEntity<TestEntity>>();
      mockService.findById.mockReturnValueOnce(late.asObservable());

      store.setCurrentEntity('21');
      await vi.waitFor(() => expect(mockService.findById).toHaveBeenCalledWith('21'));
      store.setCurrentEntity('1');
      late.next(beyondThePage);
      late.complete();

      // The row is kept (it was fetched), but it is no longer the current one.
      await vi.waitFor(() => expect(store.entities().map((entity) => entity.id)).toContain('21'));
      expect(store.currentEntity()).toBe(testEntity_1);
    });
  });

  describe('load() of every page', () => {
    const row = (id: string) => new TestEntity(id, `row ${id}`, 'd', true, Number(id), new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_ONE) as PersistedEntity<TestEntity>;
    const pages = [[row('1'), row('2')], [row('3')], [row('4')]];
    const answerPage = (query: BaseEntityQueryCondition): Observable<BaseEntityLoadResponse<PersistedEntity<TestEntity>>> => {
      const page = query.page ?? 0;
      return of({ content: pages[page] ?? [], number: page, size: 2, totalElements: 4, totalPages: pages.length });
    };

    it('reads every page, one after another, and holds the rows together in order', () => {
      const { store, mockService } = setup();
      mockService.findByQuery.mockClear();
      mockService.findByQuery.mockImplementation(answerPage);

      store.load({ allPages: true, query: 'status==ACTIVE' });

      expect(store.entities().map((entity) => entity.id)).toEqual(['1', '2', '3', '4']);
      expect(mockService.findByQuery.mock.calls.map(([query]) => [query.page, query.pageSize, query.allPages, query.query])).toEqual([
        [0, 200, false, 'status==ACTIVE'],
        [1, 200, false, 'status==ACTIVE'],
        [2, 200, false, 'status==ACTIVE'],
      ]);
      expect(store.totalElements()).toBe(4);
      expect(store.isLoading()).toBe(false);
    });

    it('asks for the page size it is given', () => {
      const { store, mockService } = setup();
      mockService.findByQuery.mockClear();
      mockService.findByQuery.mockImplementation(answerPage);

      store.load({ allPages: true, pageSize: 2 });

      expect(mockService.findByQuery.mock.calls.every(([query]) => query.pageSize === 2)).toBe(true);
    });

    it('takes a bare array answer as it is: there are no pages to follow', () => {
      const { store, mockService } = setup();
      mockService.findByQuery.mockClear();
      mockService.findByQuery.mockReturnValue(of([row('1'), row('2')]));

      store.load({ allPages: true });

      expect(store.entities().map((entity) => entity.id)).toEqual(['1', '2']);
      expect(mockService.findByQuery).toHaveBeenCalledTimes(1);
    });

    it('follows no more than a hundred pages', () => {
      const { store, mockService } = setup();
      mockService.findByQuery.mockClear();
      mockService.findByQuery.mockImplementation((query) => of({ content: [row(String(query.page ?? 0))], number: query.page ?? 0, size: 1, totalElements: 5000, totalPages: 5000 }));

      store.load({ allPages: true });

      expect(mockService.findByQuery).toHaveBeenCalledTimes(100);
      expect(store.entities()).toHaveLength(100);
    });

    it('reads a single page when the option is not given, as before', () => {
      const { store, mockService } = setup();
      mockService.findByQuery.mockClear();
      mockService.findByQuery.mockImplementation(answerPage);

      store.load({});

      expect(mockService.findByQuery).toHaveBeenCalledTimes(1);
      expect(store.entities().map((entity) => entity.id)).toEqual(['1', '2']);
    });

    it('leaves the error in the state, and stops loading, when a page fails', () => {
      const { store, mockService } = setup();
      mockService.findByQuery.mockClear();
      mockService.findByQuery.mockImplementation((query) => ((query.page ?? 0) === 1 ? throwError(() => new Error('page two failed')) : answerPage(query)));

      store.load({ allPages: true });

      expect(store.error()).toContain('page two failed');
      expect(store.isLoading()).toBe(false);
    });
  });

  describe('error state', () => {
    it('resetErrorState() sets previous error to undefined.', () => {
      const { store } = setup({ isApiFailed: true });
      expect(store.error()).toStrictEqual('API Failed');

      store.resetErrorState();
      expect(store.error()).toBeUndefined();
    });
  });

  describe('computed values', () => {
    it('countOfEntities() returns the size of entities[] array', () => {
      const { store } = setup();
      expect(store.countOfEntities()).toEqual(2);
    });

    it('matTableDataSource() instantiates a MatTableDataSource with the entities[] array', () => {
      const { store } = setup();
      expect(store.matTableDataSource()).toBeInstanceOf(MatTableDataSource);
      expect(store.matTableDataSource().data).toEqual(store.entities());
    });
  });
});

/**
 * A row that carries no `id` at all, the way `App Region` does not: the contract identifies it by the
 * slot it fills. Keyed by `id` alone every lookup below would miss it, and `update` would match the
 * first row of the list, `undefined === undefined`.
 */
class SlotRow implements BaseEntity {
  /** Declared, never assigned — see the same note on `RegionDefinition`. */
  declare readonly id?: string;

  slot: string;
  label: string;

  constructor(slot = '', label = '') {
    this.slot = slot;
    this.label = label;
  }
}

describe('BaseEntityStore, keyed by something other than `id`', () => {
  function setup() {
    const rows = [new SlotRow('header', 'Header'), new SlotRow('sidenav', 'Sidenav'), new SlotRow('footer', 'Footer')];
    const repository = mock<BaseEntityService<SlotRow>>();
    repository.findByQuery.mockReturnValue(of(rows as PersistedEntity<SlotRow>[]));
    repository.delete.mockReturnValue(of(undefined));
    repository.update.mockImplementation((entity) => of(entity));

    const SlotRowStore = signalStore(
      { providedIn: 'root' },
      BaseEntityStore<SlotRow>(
        SlotRow,
        () => repository,
        (row) => row.slot || undefined,
      ),
    );
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });

    return { store: TestBed.inject(SlotRowStore), repository, rows };
  }

  it('finds the row the details form is opened on', () => {
    const { store } = setup();

    expect(store.loadById('sidenav')?.label).toEqual('Sidenav');
    expect(store.loadById('nothing')).toBeUndefined();
  });

  it('makes the row current under the key the URL names it by', () => {
    const { store } = setup();

    store.setCurrentEntity('footer');

    expect(store.currentEntity()?.label).toEqual('Footer');
    expect(store.currentId()).toEqual('footer');
  });

  // The row lives in its owner's document: there is no endpoint to ask for it.
  it('does not ask the repository for a row it does not hold', async () => {
    const { store, repository } = setup();

    store.setCurrentEntity('nowhere');
    await Promise.resolve();

    expect(store.currentEntity()).toBeUndefined();
    expect(repository.findById).not.toHaveBeenCalled();
  });

  it('replaces the row that was edited rather than the first unkeyed one', async () => {
    const { store } = setup();

    await store.update(new SlotRow('footer', 'changed') as PersistedEntity<SlotRow>);

    expect(store.entities().map((row) => row.label)).toEqual(['Header', 'Sidenav', 'changed']);
  });

  it('removes only the deleted row', async () => {
    const { store } = setup();

    await store.delete('header');

    expect(store.entities().map((row) => row.slot)).toEqual(['sidenav', 'footer']);
  });

  it('selects and deselects by the same key', () => {
    const { store } = setup();

    store.selectEntity('sidenav');
    expect(store.selectedEntities().map((row) => row.slot)).toEqual(['sidenav']);

    store.deselectEntity('sidenav');
    expect(store.selectedEntities()).toEqual([]);
  });
});
