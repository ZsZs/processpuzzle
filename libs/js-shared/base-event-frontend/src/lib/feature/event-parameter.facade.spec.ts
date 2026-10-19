import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { EventParameter } from '../domain/event-definition';
import { EventParameterFacade } from './event-parameter.facade';

describe('EventParameterFacade', () => {
  let facade: EventParameterFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [EventParameterFacade] });
    facade = TestBed.inject(EventParameterFacade);
  });

  it('registers under the entity name the embedded route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(EventParameter);
    expect(facade.entityName).toBe('Event Parameter');
  });

  it('hands out the embedded descriptor', () => {
    expect(facade.descriptor.isEmbedded).toBe(true);
    expect(facade.descriptor.componentParents).toEqual(['Event Definition']);
  });
});
