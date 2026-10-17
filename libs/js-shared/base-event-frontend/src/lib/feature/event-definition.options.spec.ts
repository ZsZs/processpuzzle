import { TestBed } from '@angular/core/testing';
import { EmbeddedAggregateAccessor, EntityDefinition, EntityAttributeDefinition, EntityDefinitionService } from '@processpuzzle/base-entity';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EventDefinition, EventKind } from '../domain/event-definition';
import { EventDefinitionOptions } from './event-definition.options';

describe('EventDefinitionOptions', () => {
  const order = new EntityDefinition({
    code: 'order',
    name: 'Order',
    attributes: [new EntityAttributeDefinition({ code: 'total' }), new EntityAttributeDefinition({ code: 'orderNumber' })],
  });
  const customer = new EntityDefinition({ code: 'customer', name: 'Customer' });
  let owner: EventDefinition | undefined;
  let options: EventDefinitionOptions;

  beforeEach(async () => {
    owner = undefined;
    TestBed.configureTestingModule({
      providers: [
        { provide: EntityDefinitionService, useValue: { findAll: () => of([order, customer]) } },
        { provide: EmbeddedAggregateAccessor, useValue: { resolve: vi.fn(() => (owner ? { rootPayload: owner } : undefined)) } },
      ],
    });
    options = TestBed.inject(EventDefinitionOptions);
    options.entityCodes();
    await Promise.resolve();
  });

  it('offers the entity codes, alphabetically', () => {
    expect(options.entityCodes().map((option) => option.key)).toEqual(['customer', 'order']);
  });

  it('lets an ENTITY_REF reference a document too', () => {
    expect(options.referenceTypes().map((option) => option.key)).toEqual(['customer', 'document', 'order']);
  });

  it("suggests the SYSTEM subject's attributes as parameter names", () => {
    owner = new EventDefinition({ kind: EventKind.SYSTEM, subjectType: 'order' });

    expect(options.subjectAttributes().map((option) => option.key)).toEqual(['orderNumber', 'total']);
  });

  it('suggests nothing for a MESSAGE, whose parameters are named freely', () => {
    owner = new EventDefinition({ kind: EventKind.MESSAGE, subjectType: 'order' });

    expect(options.subjectAttributes()).toEqual([]);
  });
});
