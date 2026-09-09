import { TestEntity } from '../test-entity';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestEntityService } from '../base-entity-service/test-entity.service';
import { TestEntityStore } from '../test-entity.store';
import { provideRouter } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { BaseEntityLoadResponse } from '../base-entity-service/base-entity-load-response';
import { DummyComponent, MOCK_API_RESPONSE, MOCK_PAGED_RESPONSE, newTestEntity, setupMockService, testEntity_1, testEntity_2 } from '../../test-setup';
import { describe, expect, it } from 'vitest';
import { signalStore } from '@ngrx/signals';
import { of } from 'rxjs';
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

    const SlotRowStore = signalStore({ providedIn: 'root' }, BaseEntityStore<SlotRow>(SlotRow, () => repository, (row) => row.slot || undefined));
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
