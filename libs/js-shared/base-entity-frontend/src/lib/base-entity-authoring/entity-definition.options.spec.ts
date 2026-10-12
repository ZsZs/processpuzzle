import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { EntityDefinition } from '../base-entity-definition/entity-definition';
import { EntityDefinitionService } from '../base-entity-definition/entity-definition.service';
import { EntityDefinitionOptions } from './entity-definition.options';
import { EntityDefinitionStore } from './entity-definition.store';

describe('EntityDefinitionOptions', () => {
  function create(findAll: () => unknown, stored: EntityDefinition[] = []): EntityDefinitionOptions {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: EntityDefinitionService, useValue: { findAll } },
        { provide: EntityDefinitionStore, useValue: { entities: signal(stored) } },
      ],
    });
    return TestBed.inject(EntityDefinitionOptions);
  }

  const definition = (code: string) => new EntityDefinition({ code, name: code.toUpperCase() });

  it('offers the fetched and the stored codes once each, alphabetically', async () => {
    const fetched = new Subject<EntityDefinition[]>();
    const options = create(() => fetched, [definition('order'), definition('customer')]);

    expect(options.codes().map((option) => option.key)).toEqual(['customer', 'order']);

    fetched.next([definition('invoice'), definition('order')]);
    fetched.complete();
    await Promise.resolve();

    expect(options.codes().map((option) => option.key)).toEqual(['customer', 'invoice', 'order']);
  });

  it('fetches once, however often the dropdown asks', async () => {
    let calls = 0;
    const options = create(() => {
      calls++;
      return of([definition('order')]);
    });

    options.codes();
    options.codes();
    await Promise.resolve();
    options.codes();

    expect(calls).toBe(1);
  });

  /** An organization with no definitions yet answers the list with an error. */
  it("keeps offering the store's codes when the fetch fails", async () => {
    const options = create(() => throwError(() => new Error('The query returned no content.')), [definition('order')]);

    options.codes();
    await Promise.resolve();

    expect(options.codes().map((option) => option.key)).toEqual(['order']);
  });
});
