import { TestBed } from '@angular/core/testing';
import { TestEntityMapper } from '../test-entity.mapper';
import { TestEntityFirestoreService } from './test-entity-firestore.service';
import { doc, DocumentReference, DocumentSnapshot, Firestore, getDoc } from '@angular/fire/firestore';
import { TestEntity } from '../test-entity';

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

  beforeEach(() => {
    const mockDocumentSnapshot: DocumentSnapshot<TestEntity> = {} as DocumentSnapshot<TestEntity>;
    (doc as jest.Mock).mockResolvedValue({} as DocumentReference);
    (getDoc as jest.Mock).mockResolvedValue(new Promise(() => mockDocumentSnapshot));

    TestBed.configureTestingModule({
      imports: [],
      providers: [{ provide: Firestore, useValue: {} }, TestEntityMapper, TestEntityFirestoreService],
    });
    baseEntityService = TestBed.inject(TestEntityFirestoreService);
  });

  it('should be created', () => {
    expect(baseEntityService).toBeTruthy();
  });
});
