import { TestBed } from '@angular/core/testing';
import { TestEntityMapper } from '../test-entity.mapper';
import { TestEntityFirestoreService } from './test-entity-firestore.service';
import { doc, DocumentReference, DocumentSnapshot, Firestore, getDoc } from '@angular/fire/firestore';
import { TestEntity } from '../test-entity';
import { beforeEach, describe, expect, it, vi } from 'vitest';

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
    updateDoc: vi.fn(),
    where: vi.fn(),
  };
});

describe('BaseEntityFirestoreService', () => {
  let baseEntityService: TestEntityFirestoreService;

  beforeEach(() => {
    const mockDocumentSnapshot: DocumentSnapshot<TestEntity> = {} as DocumentSnapshot<TestEntity>;
    vi.mocked(doc).mockReturnValue({} as DocumentReference);
    vi.mocked(getDoc).mockResolvedValue(new Promise(() => mockDocumentSnapshot) as unknown as DocumentSnapshot);

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
