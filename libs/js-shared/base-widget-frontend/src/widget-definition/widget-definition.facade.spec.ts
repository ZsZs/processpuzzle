import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { InputPort, OutputPort, WidgetDefinition } from './widget-definition';
import { WidgetDefinitionFacade } from './widget-definition.facade';
import { WidgetDefinitionMapper } from './widget-definition.mapper';
import { WidgetDefinitionService } from './widget-definition.service';
import { WidgetDefinitionStore } from './widget-definition.store';
import { WidgetInputPortFacade } from './widget-input-port.facade';
import { WidgetOutputPortFacade } from './widget-output-port.facade';

describe('widget catalogue facades', () => {
  let facade: WidgetDefinitionFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        WidgetDefinitionFacade,
        WidgetInputPortFacade,
        WidgetOutputPortFacade,
      ],
    });
    facade = TestBed.inject(WidgetDefinitionFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(WidgetDefinition);
    expect(facade.entityName).toBe('Widget Definition');
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(WidgetDefinitionMapper));
    expect(facade.service).toBe(TestBed.inject(WidgetDefinitionService));
    expect(facade.storeClass).toBe(WidgetDefinitionStore);
    expect(facade.store).toBe(TestBed.inject(WidgetDefinitionStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
  });

  /** A port's rows live in the definition payload, so its facade brings a descriptor and nothing else. */
  it('describes both port levels as embedded children of the definition', () => {
    const inputPortFacade = TestBed.inject(WidgetInputPortFacade);
    const outputPortFacade = TestBed.inject(WidgetOutputPortFacade);

    expect(inputPortFacade.entityType).toBe(InputPort);
    expect(inputPortFacade.entityName).toBe('Widget Input Port');
    expect(inputPortFacade.descriptor.isEmbedded).toBe(true);
    expect(outputPortFacade.entityType).toBe(OutputPort);
    expect(outputPortFacade.entityName).toBe('Widget Output Port');
    expect(outputPortFacade.descriptor.isEmbedded).toBe(true);
  });
});
