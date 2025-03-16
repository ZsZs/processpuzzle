import { TestBed } from '@angular/core/testing';
import { TestEntityMapper } from '../test-entity.mapper';
import { TestEntityFirestoreService } from './test-entity-firestore.service';
import { doc, DocumentReference, DocumentSnapshot, Firestore, getDoc } from '@angular/fire/firestore';
import { TestEntity } from '../test-entity';
import { testEntity_1 } from '../../test-setup';

jest.mock('@angular/fire/firestore', () => {
  return {
    addDoc: jest.fn(),
    collection: jest.fn(),
    deleteDoc: jest.fn(),
    doc: jest.fn(),
    Firestore: jest.fn(),
    getDoc: jest.fn(),
    getDocs: jest.fn(),
    limit: jest.fn(),
    orderBy: jest.fn(),
    query: jest.fn(),
    updateDoc: jest.fn(),
    where: jest.fn(),
  };
});

describe('BaseEntityFirestoreService', () => {
  let baseEntityService: TestEntityFirestoreService;
  let firestoreMock: Firestore;

  beforeEach(() => {
    //    const mockDocumentRef: DocumentReference<TestEntity> = {} as DocumentReference<TestEntity>;
    const mockDocumentSnapshot: DocumentSnapshot<TestEntity> = {
      // data: jest.fn(() => testEntity_1),
      // exists: jest.fn((): boolean => {
      //   return {} as QueryDocumentSnapshot<TestEntity, DocumentData>;
      // }),
      // get: jest.fn(),
      // id: testEntity_1.id,
      // metadata: {} as SnapshotMetadata,
      // ref: mockDocumentRef,
    } as DocumentSnapshot<TestEntity>;
    (doc as jest.Mock).mockResolvedValue({} as DocumentReference);
    (getDoc as jest.Mock).mockResolvedValue(new Promise(() => mockDocumentSnapshot));

    TestBed.configureTestingModule({
      imports: [],
      providers: [{ provide: Firestore, useValue: {} }, TestEntityMapper, TestEntityFirestoreService],
    });
    baseEntityService = TestBed.inject(TestEntityFirestoreService);
    firestoreMock = TestBed.inject(Firestore) as jest.Mocked<Firestore>;
  });

  it('should be created', () => {
    expect(baseEntityService).toBeTruthy();
  });

  it.skip('findById returns the entity with given Id', (done) => {
    baseEntityService.findById(testEntity_1.id).subscribe((entity: void | TestEntity) => {
      expect(entity?.id).toEqual(testEntity_1.id);
      done();
    });

    expect(doc).toHaveBeenCalledWith(firestoreMock, 'test-entity', testEntity_1.id);
    // const callArg = new Promise(() => {
    //   return { __zone_symbol__state: true, __zone_symbol__value: { id: testEntity_1.id } };
    // });
    expect(getDoc).toHaveBeenCalled();
  });
});
