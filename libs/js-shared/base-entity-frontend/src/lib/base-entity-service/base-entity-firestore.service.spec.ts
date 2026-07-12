import { TestBed } from '@angular/core/testing';
import { TestEntityMapper } from '../test-entity.mapper';
import { TestEntityFirestoreService } from './test-entity-firestore.service';
import {
  collection,
  deleteDoc,
  doc,
  DocumentReference,
  DocumentSnapshot,
  Firestore,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  QuerySnapshot,
  setDoc,
  updateDoc,
  where,
} from '@angular/fire/firestore';
import { TestEntity, TestEnum } from '../test-entity';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { firstValueFrom, lastValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, BaseEntityQueryCondition, FilterCondition, OrderBy } from './base-entity-load-response';

vi.mock('@angular/fire/firestore', () => {
  return {
    addDoc: vi.fn(),
    collection: vi.fn(),
    deleteDoc: vi.fn(),
    doc: vi.fn(),
    Firestore: vi.fn(),
    getDoc: vi.fn(),
    getDocs: vi.fn(),
    limit: vi.fn(),
    orderBy: vi.fn(),
    query: vi.fn(),
    setDoc: vi.fn(),
    updateDoc: vi.fn(),
    where: vi.fn(),
  };
});

describe('BaseEntityFirestoreService', () => {
  const collectionName = 'test-entity';
  const fakeCollectionRef = { id: 'collection-ref' } as unknown;
  const fakeDocRef = { id: 'doc-ref' } as unknown as DocumentReference;
  const fakeQuery = { id: 'fake-query' };
  let baseEntityService: TestEntityFirestoreService;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(collection).mockReturnValue(fakeCollectionRef as never);
    vi.mocked(doc).mockReturnValue(fakeDocRef as never);
    vi.mocked(query).mockReturnValue(fakeQuery as never);
    vi.mocked(limit).mockReturnValue('limit-constraint' as never);
    vi.mocked(orderBy).mockReturnValue('order-by-constraint' as never);
    vi.mocked(where).mockReturnValue('where-constraint' as never);
    vi.mocked(setDoc).mockResolvedValue(undefined as never);
    vi.mocked(updateDoc).mockResolvedValue(undefined as never);
    vi.mocked(deleteDoc).mockResolvedValue(undefined as never);

    TestBed.configureTestingModule({
      imports: [],
      providers: [{ provide: Firestore, useValue: {} }, TestEntityMapper, TestEntityFirestoreService],
    });
    baseEntityService = TestBed.inject(TestEntityFirestoreService);
  });

  // region creation
  it('should be created', () => {
    expect(baseEntityService).toBeTruthy();
    expect(collection).toHaveBeenCalledWith(expect.anything(), collectionName);
  });
  // endregion

  // region add()
  describe('add()', () => {
    it('throws when entity is undefined', () => {
      expect(() => baseEntityService.add(undefined as unknown as TestEntity)).toThrow('Entity cant be undefined');
    });

    it('persists an entity that already has an id and returns the mapped result', async () => {
      const entity = new TestEntity('existing-id', 'hello', 'desc', true, 42, new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_ONE);
      const docRefWithId = { id: 'existing-id' } as DocumentReference;
      vi.mocked(doc).mockReturnValueOnce(docRefWithId as never);

      const result = await firstValueFrom(baseEntityService.add(entity));

      expect(doc).toHaveBeenCalledWith(fakeCollectionRef, 'existing-id');
      expect(setDoc).toHaveBeenCalledWith(docRefWithId, expect.objectContaining({ id: 'existing-id', name: 'hello' }));
      expect(result.id).toBe('existing-id');
    });

    it('generates a new doc reference when entity has no id', async () => {
      const entity = new TestEntity(undefined, 'name');
      Reflect.set(entity, 'id', '');
      const generatedDocRef = { id: 'generated-id' } as DocumentReference;
      vi.mocked(doc).mockReturnValueOnce(generatedDocRef as never);

      const result = await firstValueFrom(baseEntityService.add(entity));

      expect(doc).toHaveBeenCalledWith(fakeCollectionRef);
      expect(setDoc).toHaveBeenCalledWith(generatedDocRef, expect.any(Object));
      expect(result.id).toBe('generated-id');
    });
  });
  // endregion

  // region delete()
  describe('delete()', () => {
    it('deletes the document referenced by the supplied id', async () => {
      await firstValueFrom(baseEntityService.delete('some-id'));

      expect(doc).toHaveBeenCalledWith(expect.anything(), collectionName, 'some-id');
      expect(deleteDoc).toHaveBeenCalledWith(fakeDocRef);
    });
  });
  // endregion

  // region deleteAll()
  describe('deleteAll()', () => {
    it('completes without performing any firestore operations', async () => {
      const result = await firstValueFrom(baseEntityService.deleteAll());

      expect(result).toBeUndefined();
      expect(deleteDoc).not.toHaveBeenCalled();
    });
  });
  // endregion

  // region findById()
  describe('findById()', () => {
    it('maps a document that exists', async () => {
      const snapshot = {
        exists: () => true,
        id: 'id-1',
        data: () => ({ name: 'mapped-name', description: 'mapped-desc' }),
      } as unknown as DocumentSnapshot;
      vi.mocked(getDoc).mockResolvedValueOnce(snapshot);

      const result = (await firstValueFrom(baseEntityService.findById('id-1'))) as TestEntity;

      expect(doc).toHaveBeenCalledWith(expect.anything(), collectionName, 'id-1');
      expect(getDoc).toHaveBeenCalledWith(fakeDocRef);
      expect(result?.id).toBe('id-1');
      expect(result?.name).toBe('mapped-name');
    });

    it('returns undefined when the document does not exist', async () => {
      const snapshot = { exists: () => false } as unknown as DocumentSnapshot;
      vi.mocked(getDoc).mockResolvedValueOnce(snapshot);

      const result = await firstValueFrom(baseEntityService.findById('missing'));

      expect(result).toBeUndefined();
    });

    it('throws a contextual error when getDoc rejects', async () => {
      vi.mocked(getDoc).mockRejectedValueOnce(new Error('boom'));

      await expect(firstValueFrom(baseEntityService.findById('id-2'))).rejects.toThrow(/Error: .*boom.* occurred while finding document by id: id-2/);
    });
  });
  // endregion

  // region findAll() / findByQuery()
  describe('findAll() / findByQuery()', () => {
    function snapshotFor(docs: { id: string; data: Record<string, unknown> }[]): QuerySnapshot {
      return {
        docs: docs.map((d) => ({ id: d.id, data: () => d.data })),
      } as unknown as QuerySnapshot;
    }

    it('runs a default query when no conditions are supplied', async () => {
      vi.mocked(getDocs).mockResolvedValueOnce(snapshotFor([]));

      const response = (await firstValueFrom(baseEntityService.findAll())) as BaseEntityLoadResponse<TestEntity>;

      expect(limit).toHaveBeenCalledWith(99);
      expect(query).toHaveBeenCalledWith(fakeCollectionRef, 'limit-constraint');
      expect(response.totalPages).toBe(1);
      expect(response.content).toEqual([]);
    });

    it('passes the page size into the firestore limit constraint', async () => {
      vi.mocked(getDocs).mockResolvedValueOnce(snapshotFor([]));

      await firstValueFrom(baseEntityService.findAll(1, 25));

      expect(limit).toHaveBeenCalledWith(25);
    });

    it('maps each returned doc through the entity mapper', async () => {
      vi.mocked(getDocs).mockResolvedValueOnce(
        snapshotFor([
          { id: 'a', data: { name: 'alpha' } },
          { id: 'b', data: { name: 'beta' } },
        ]),
      );

      const response = (await firstValueFrom(baseEntityService.findAll())) as BaseEntityLoadResponse<TestEntity>;

      expect(response.content).toHaveLength(2);
      expect(response.content[0].id).toBe('a');
      expect(response.content[0].name).toBe('alpha');
      expect(response.content[1].id).toBe('b');
      expect(response.content[1].name).toBe('beta');
    });

    it('builds where constraints from filters', async () => {
      vi.mocked(getDocs).mockResolvedValueOnce(snapshotFor([]));
      const filters: FilterCondition[] = [
        { property: 'name', operator: '==', value: 'alpha' },
        { property: 'description', operator: '!=', value: 'skip' },
      ];

      await firstValueFrom(baseEntityService.findByQuery({ filters } as BaseEntityQueryCondition));

      expect(where).toHaveBeenCalledWith('name', '==', 'alpha');
      expect(where).toHaveBeenCalledWith('description', '!=', 'skip');
      expect(query).toHaveBeenCalledWith(fakeCollectionRef, 'where-constraint', 'where-constraint', 'limit-constraint');
    });

    it('builds orderBy constraints from order conditions', async () => {
      vi.mocked(getDocs).mockResolvedValueOnce(snapshotFor([]));
      const orderBys: OrderBy[] = [{ property: 'name', direction: 'ASC' }];

      await firstValueFrom(baseEntityService.findByQuery({ orderBys } as BaseEntityQueryCondition));

      expect(orderBy).toHaveBeenCalledTimes(1);
      expect(vi.mocked(orderBy).mock.calls[0][0]).toBe('name');
      expect(query).toHaveBeenCalledWith(fakeCollectionRef, 'order-by-constraint', 'limit-constraint');
    });

    it('returns paging metadata that matches the supplied query condition', async () => {
      vi.mocked(getDocs).mockResolvedValueOnce(snapshotFor([{ id: 'x', data: { name: 'x' } }]));

      const response = (await firstValueFrom(baseEntityService.findByQuery({ page: 2, pageSize: 10 }))) as BaseEntityLoadResponse<TestEntity>;

      expect(response.number).toBe(2);
      expect(response.size).toBe(10);
      expect(response.totalPages).toBe(1);
      expect(response.content[0].id).toBe('x');
    });
  });
  // endregion

  // region update()
  describe('update()', () => {
    it('updates the existing document and resolves with the entity', async () => {
      const entity = new TestEntity('persisted-id', 'changed');

      const result = await lastValueFrom(baseEntityService.update(entity));

      expect(doc).toHaveBeenCalledWith(expect.anything(), collectionName, 'persisted-id');
      expect(updateDoc).toHaveBeenCalledWith(fakeDocRef, expect.objectContaining({ id: 'persisted-id', name: 'changed' }));
      expect(result).toBe(entity);
    });

    it('rejects when entity is undefined', async () => {
      await expect(lastValueFrom(baseEntityService.update(undefined as unknown as TestEntity))).rejects.toThrow('Entity cant be undefined');
    });
  });
  // endregion
});
