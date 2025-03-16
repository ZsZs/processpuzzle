import { TestEntity } from '../test-entity';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestEntityService } from '../base-entity-service/test-entity.service';
import { TestEntityStore } from '../test-entity.store';
import { provideRouter } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { BaseEntityLoadResponse } from '../base-entity-service/base-entity-load-response';
import { DummyComponent, MOCK_API_RESPONSE, MOCK_PAGED_RESPONSE, mockService, newTestEntity, setupMockService, testEntity_1, testEntity_2 } from '../../test-setup';

describe('BaseEntityStore', () => {
  function setup({ isApiFailed = false, payload = MOCK_API_RESPONSE }: { isApiFailed?: boolean; payload?: TestEntity[] | BaseEntityLoadResponse<TestEntity> } = {}) {
    setupMockService({ isApiFailed, payload });
    TestBed.configureTestingModule({
      imports: [DummyComponent],
      providers: [provideHttpClient(), provideRouter([]), TestEntityStore, { provide: TestEntityService, useValue: mockService }],
    });
    const fixture = TestBed.createComponent(DummyComponent);
    const store = TestBed.inject(TestEntityStore);
    fixture.detectChanges();
    return { mockService, store };
  }

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
    it('add() will call API and adds entity to entities[]', () => {
      const { store, mockService } = setup();
      store.add(newTestEntity);

      expect(mockService.add).toHaveBeenCalledTimes(1);
      const expectedEntities = [...MOCK_API_RESPONSE, ...[newTestEntity]];
      expect(store.entities()).toStrictEqual(expectedEntities);
    });

    it('add() will verify if API is failed', () => {
      const { store, mockService } = setup({ isApiFailed: true });

      store.add(newTestEntity);

      expect(mockService.findByQuery).toHaveBeenCalledTimes(1);
      expect(store.error()).toStrictEqual('API Failed');
    });

    it('delete() calls service and removes the entity from entities[].', () => {
      const { store, mockService } = setup();

      store.delete('1');
      expect(mockService.delete).toHaveBeenCalledTimes(1);
      expect(store.entities()).toStrictEqual([testEntity_2]);
    });

    it('delete() will verify if API is failed', () => {
      const { store, mockService } = setup({ isApiFailed: true });

      store.delete('2');
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
      expect(store.page()).toEqual(33);
      expect(store.pageSize()).toEqual(2);
      expect(store.totalPageCount()).toEqual(333);
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

    it('update() will call API and updates entity in entities[]', () => {
      const { store, mockService } = setup();
      Reflect.set(testEntity_1, 'name', 'changed');

      store.update(testEntity_1);

      expect(mockService.update).toHaveBeenCalledTimes(1);
      expect(store.loadById('1')).toStrictEqual(testEntity_1);
    });

    it('update() will verify if API is failed', () => {
      const { store, mockService } = setup({ isApiFailed: true });

      store.update(testEntity_1);

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
