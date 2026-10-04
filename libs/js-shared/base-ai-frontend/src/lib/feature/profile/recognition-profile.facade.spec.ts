import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BASE_AI_ENTITY_FACADES, BASE_AI_FACADE_PROVIDERS } from '../../base-ai.providers';
import { RecognitionProfile, RECOGNITION_PROFILE_ENTITY_NAME } from '../../domain/profile/recognition-profile';
import { RecognitionProfileMapper } from '../../domain/profile/recognition-profile.mapper';
import { RecognitionProfileService } from '../../domain/profile/recognition-profile.service';
import { RecognitionProfileStore } from '../../domain/profile/recognition-profile.store';
import { RecognitionProfileFacade } from './recognition-profile.facade';

describe('RecognitionProfileFacade', () => {
  let facade: RecognitionProfileFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { AI_SERVICE_ROOT: 'http://backend/organizations/testbed' } } },
        ...BASE_AI_FACADE_PROVIDERS,
      ],
    });
    facade = TestBed.inject(RecognitionProfileFacade);
  });

  it('registers the profile facade under the generated screens entity name', () => {
    expect(facade.entityType).toBe(RecognitionProfile);
    expect(facade.entityName).toBe(RECOGNITION_PROFILE_ENTITY_NAME);
    expect(BASE_AI_ENTITY_FACADES[RECOGNITION_PROFILE_ENTITY_NAME]).toBe(RecognitionProfileFacade);
  });

  it('reuses the root mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(RecognitionProfileMapper));
    expect(facade.service).toBe(TestBed.inject(RecognitionProfileService));
    expect(facade.storeClass).toBe(RecognitionProfileStore);
    expect(facade.store).toBe(TestBed.inject(RecognitionProfileStore));
  });

  it('binds the generated descriptor to its CRUD store', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });
});
