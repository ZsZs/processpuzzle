import { TestBed } from '@angular/core/testing';
import { EntityDefinition, EntityDefinitionService } from '@processpuzzle/base-entity';
import { of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { ChatDefinitionOptions } from './chat-definition.options';

describe('ChatDefinitionOptions', () => {
  function optionsOver(findAll: () => unknown): ChatDefinitionOptions {
    TestBed.configureTestingModule({ providers: [{ provide: EntityDefinitionService, useValue: { findAll } }] });
    return TestBed.inject(ChatDefinitionOptions);
  }

  it('offers the entity codes alphabetically once the fetch lands', async () => {
    const options = optionsOver(() => of([new EntityDefinition({ code: 'order', name: 'Order' }), new EntityDefinition({ code: 'customer', name: 'Customer' })]));

    expect(options.entityCodes()).toEqual([]);
    await Promise.resolve();

    expect(options.entityCodes().map((option) => option.key)).toEqual(['customer', 'order']);
  });

  it('offers nothing when the fetch fails, and does not fetch again', async () => {
    let calls = 0;
    const options = optionsOver(() => {
      calls++;
      return throwError(() => new Error('down'));
    });

    options.entityCodes();
    await Promise.resolve();
    options.entityCodes();

    expect(options.entityCodes()).toEqual([]);
    expect(calls).toBe(1);
  });
});
