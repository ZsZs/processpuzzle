import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GovernedEntityRegistry } from './governed-entity.registry';
import {
  OTHER_STATE_MACHINE_DEFINITION_DTO,
  pageOfStateMachineDefinitions,
  STATE_MACHINE_DEFINITION_DTO,
} from './test-state-machine-definition';

describe('GovernedEntityRegistry', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  const machinesUrl = `${serviceRoot}/state-machines`;

  let registry: GovernedEntityRegistry;
  let controller: HttpTestingController;

  /**
   * The definitions the entity registry would fetch, stubbed rather than flushed: what this class is
   * responsible for is the *translation*, and going through the real registry would make every test here
   * also a test of base-entity's fetch.
   */
  function setup(definitionsByName: Record<string, { code: string; name: string }> = {}) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: serviceRoot } } },
        { provide: EntityDefinitionRegistry, useValue: { byName: vi.fn(async (name: string) => definitionsByName[name]) } },
      ],
    });
    controller = TestBed.inject(HttpTestingController);
    return TestBed.inject(GovernedEntityRegistry);
  }

  /**
   * Answers the one machines request a question triggers, then resolves the question.
   *
   * The request is *awaited* rather than matched straight away: the registry translates the entity name
   * through `EntityDefinitionRegistry` first, so the HTTP call is issued a microtask or two after the
   * question is asked, and matching immediately would find nothing and hang the test.
   */
  async function ask<T>(pending: Promise<T>, ...machines: unknown[]): Promise<T> {
    await flushMachines(...machines);
    return pending;
  }

  /** Waits for the machines request to be issued, then answers it. */
  async function flushMachines(...machines: unknown[]): Promise<void> {
    await respond((request) => request.flush(pageOfStateMachineDefinitions(...machines)));
  }

  async function respond(answer: (request: TestRequest) => void): Promise<void> {
    for (let attempt = 0; attempt < 20; attempt++) {
      const requests = controller.match(machinesUrl);
      if (requests.length > 0) {
        requests.forEach(answer);
        return;
      }
      await Promise.resolve();
    }
    throw new Error(`No request to ${machinesUrl} was issued.`);
  }

  beforeEach(() => {
    registry = setup({ Order: { code: 'order', name: 'Order' } });
  });

  it('finds the machine of an entity through its definition code, not its descriptor name', async () => {
    const machine = await ask(registry.machineFor('Order'), STATE_MACHINE_DEFINITION_DTO, OTHER_STATE_MACHINE_DEFINITION_DTO);

    expect(machine?.entityName).toBe('order');
    expect(machine?.stateAttributeKey).toBe('status');
    // The list response carries the whole machine, which is what lets the tab draw it without a second read.
    expect(machine?.states.map((state) => state.key)).toEqual(['DRAFT', 'DELIVERED']);
  });

  it('reports an entity no machine governs as ungoverned', async () => {
    registry = setup({ 'Order Line': { code: 'order-line', name: 'Order Line' } });

    expect(await ask(registry.governs('Order Line'), STATE_MACHINE_DEFINITION_DTO)).toBe(false);
  });

  /**
   * A compiled-in entity has no definition row to translate through, so the URL rule is the fallback —
   * the same one `BaseFormNavigatorSingletonStore` builds every entity URL with.
   */
  it('falls back to the snake-cased name for an entity with no definition', async () => {
    registry = setup();

    expect(await registry.machineKeyOf('Special Order')).toBe('special-order');
    expect(await registry.machineKeyOf(undefined)).toBeUndefined();
  });

  it('answers a key even for an ungoverned entity, since the caller may still ask the operation layer', async () => {
    expect(await registry.machineKeyOf('Order')).toBe('order');
  });

  it('fetches the machines once and answers later questions from the cache', async () => {
    await ask(registry.machineFor('Order'), STATE_MACHINE_DEFINITION_DTO);
    expect(await registry.governs('Order')).toBe(true);

    controller.verify();
  });

  it('re-fetches after a reset, so an authored machine is picked up', async () => {
    expect(await ask(registry.governs('Order'))).toBe(false);

    registry.reset();

    expect(await ask(registry.governs('Order'), STATE_MACHINE_DEFINITION_DTO)).toBe(true);
  });

  /**
   * base-state need not be deployed at all. An application whose entity screens refused to mount because
   * an optional feature answered 500 would be trading a missing tab for a broken form.
   */
  it('reports nothing governed when the machines cannot be fetched, and retries next time', async () => {
    const failing = registry.governs('Order');
    await respond((request) => request.flush('boom', { status: 500, statusText: 'Server Error' }));
    expect(await failing).toBe(false);

    expect(await ask(registry.governs('Order'), STATE_MACHINE_DEFINITION_DTO)).toBe(true);
  });
});
