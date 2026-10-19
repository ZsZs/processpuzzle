import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { CalculationDefinition } from '../domain/calculation-definition';
import { CalculationDefinitionMapper } from '../domain/calculation-definition.mapper';
import { CalculationDefinitionService } from '../domain/calculation-definition.service';
import { CalculationDefinitionStore } from '../domain/calculation-definition.store';
import { SERVICE_ROOT } from '../domain/test-calculation-definition';
import { CalculationDefinitionFacade } from './calculation-definition.facade';
import { CALCULATION_TABS } from './calculation-tabs';

describe('CalculationDefinitionFacade', () => {
  let facade: CalculationDefinitionFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { CALCULATION_SERVICE_ROOT: SERVICE_ROOT } } },
        CalculationDefinitionFacade,
      ],
    });
    facade = TestBed.inject(CalculationDefinitionFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(CalculationDefinition);
    expect(facade.entityName).toBe('Calculation Definition');
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(CalculationDefinitionMapper));
    expect(facade.service).toBe(TestBed.inject(CalculationDefinitionService));
    expect(facade.storeClass).toBe(CalculationDefinitionStore);
  });

  it('puts the Source, Decision tables and Evaluate tabs on the descriptor', () => {
    expect(facade.descriptor.extraTabs).toBe(CALCULATION_TABS);
    expect(CALCULATION_TABS.map((tab) => tab.segment)).toEqual(['source', 'tables', 'evaluate']);
  });
});
