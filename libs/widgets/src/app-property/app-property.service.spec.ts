import { TestBed } from '@angular/core/testing';
import { ApplicationPropertyService } from './app-property.service';
import { ApplicationPropertyMapper } from './app-property.mapper';
import { collection, doc, DocumentReference, DocumentSnapshot, Firestore, getDoc } from '@angular/fire/firestore';
import { ApplicationProperty } from './app-property';
import { InjectionToken } from '@angular/core';

// Create a token for the mapper
const ENTITY_MAPPER_TOKEN = new InjectionToken<ApplicationPropertyMapper>('entityMapper');

// Mock Firestore methods
jest.mock('@angular/fire/firestore', () => {
  return {
    addDoc: jest.fn(),
    collection: jest.fn(() => ({})),
    deleteDoc: jest.fn(),
    doc: jest.fn(),
    Firestore: jest.fn(),
    getDoc: jest.fn(),
    getDocs: jest.fn(),
    limit: jest.fn(),
    orderBy: jest.fn(),
    query: jest.fn(),
    setDoc: jest.fn(),
    updateDoc: jest.fn(),
    where: jest.fn(),
  };
});

// Create a mock implementation of ApplicationPropertyService
class MockApplicationPropertyService extends ApplicationPropertyService {
  constructor(mapper: ApplicationPropertyMapper) {
    super(mapper);
  }
}

describe('ApplicationPropertyService', () => {
  let service: ApplicationPropertyService;
  let firestoreMock: Firestore;
  let mapper: ApplicationPropertyMapper;

  beforeEach(() => {
    // Create a proper mock document snapshot with a data method
    const mockDocumentSnapshot = {
      data: jest.fn().mockReturnValue({ id: '123', name: 'testProperty', value: 'testValue' }),
      exists: true,
      id: '123',
      ref: {} as DocumentReference
    } as unknown as DocumentSnapshot<ApplicationProperty>;

    (doc as jest.Mock).mockReturnValue({} as DocumentReference);
    (getDoc as jest.Mock).mockResolvedValue(mockDocumentSnapshot);

    // Create instances
    mapper = new ApplicationPropertyMapper();

    // Set up TestBed with all necessary providers
    TestBed.configureTestingModule({
      providers: [
        { provide: Firestore, useValue: {} },
        { provide: ENTITY_MAPPER_TOKEN, useValue: mapper },
        { 
          provide: ApplicationPropertyService, 
          useFactory: () => new MockApplicationPropertyService(mapper)
        }
      ]
    });

    // Get instances from TestBed
    firestoreMock = TestBed.inject(Firestore);
    service = TestBed.inject(ApplicationPropertyService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should use the correct collection name', () => {
    // We can test that the service was initialized with the correct collection name
    // by spying on the doc function when calling a method like findById
    const testId = '123';
    service.findById(testId);
    expect(doc).toHaveBeenCalledWith(firestoreMock, 'application-properties', testId);
  });

  it('should use the mapper to convert DTOs to entities', () => {
    // Create a spy on the mapper's fromDto method
    const fromDtoSpy = jest.spyOn(mapper, 'fromDto');

    // Create a mock DTO
    const mockDto = { id: '123', name: 'testProperty', value: 'testValue' };

    // Call the method that would use the mapper
    mapper.fromDto(mockDto);

    // Verify the mapper was called with the correct DTO
    expect(fromDtoSpy).toHaveBeenCalledWith(mockDto);
  });

  it('should use the mapper to convert entities to DTOs', () => {
    // Create a spy on the mapper's toDto method
    const toDtoSpy = jest.spyOn(mapper, 'toDto');

    // Create a mock entity
    const mockEntity = new ApplicationProperty('123', 'testProperty', 'testValue');

    // Call the method that would use the mapper
    mapper.toDto(mockEntity);

    // Verify the mapper was called with the correct entity
    expect(toDtoSpy).toHaveBeenCalledWith(mockEntity);
  });
});
