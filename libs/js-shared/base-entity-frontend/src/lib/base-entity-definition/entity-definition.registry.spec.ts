import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EntityDefinitionRegistry } from './entity-definition.registry';
import { ORDER_DEFINITION, ORDER_LINE_DEFINITION, TEST_ENTITY_DEFINITIONS } from './test-entity-definition';

const ENTITY_DEFINITIONS_URL = 'http://localhost:8080/organizations/acme/entity-definitions?page=0&size=200';

function setup(baseConfiguration: object = { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' }) {
  // The suite-wide beforeEach has already instantiated one, and a spec that wants a different
  // configuration has to start from a fresh module rather than reconfigure that one.
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } }],
  });
  return { registry: TestBed.inject(EntityDefinitionRegistry), controller: TestBed.inject(HttpTestingController) };
}

describe('EntityDefinitionRegistry', () => {
  let registry: EntityDefinitionRegistry;
  let controller: HttpTestingController;

  beforeEach(() => {
    ({ registry, controller } = setup());
  });

  it('indexes the definitions by code', async () => {
    const loaded = registry.load();
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: TEST_ENTITY_DEFINITIONS });

    expect([...(await loaded).keys()]).toEqual(['order-line', 'order']);
  });

  // The translation the whole metadata layer rests on: a route names 'Order', the endpoints take 'order'.
  it('resolves a definition by the entity name a route would give it', async () => {
    const byName = registry.byName('Order');
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: TEST_ENTITY_DEFINITIONS });

    expect(await byName).toBe(ORDER_DEFINITION);
  });

  it('resolves a definition by the code an embedded attribute names it with', async () => {
    const byCode = registry.byCode('order-line');
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: TEST_ENTITY_DEFINITIONS });

    expect(await byCode).toBe(ORDER_LINE_DEFINITION);
  });

  it('answers undefined for an unknown name or code, and for none at all', async () => {
    const lookups = Promise.all([registry.byName('Nowhere'), registry.byCode('nowhere'), registry.byName(undefined), registry.byCode(undefined)]);
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: TEST_ENTITY_DEFINITIONS });

    expect(await lookups).toEqual([undefined, undefined, undefined, undefined]);
  });

  /** Several ENTITY routes of one application resolve while its routes are built; one request has to serve them all. */
  it('fetches once however many lookups are in flight', async () => {
    const lookups = Promise.all([registry.byName('Order'), registry.byName('Order Line'), registry.load()]);
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: TEST_ENTITY_DEFINITIONS });
    await lookups;

    await registry.load();
    controller.verify();
  });

  it('renders only ACTIVE definitions — a draft is being authored and a deprecated one is on its way out', async () => {
    const loaded = registry.load();
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({
      content: [
        { ...ORDER_DEFINITION, code: 'draft', status: 'DRAFT' },
        { ...ORDER_DEFINITION, code: 'gone', status: 'DEPRECATED' },
        { ...ORDER_DEFINITION, code: 'stated', status: 'ACTIVE' },
        { ...ORDER_DEFINITION, code: 'unstated', status: undefined },
      ],
    });

    expect([...(await loaded).keys()]).toEqual(['stated', 'unstated']);
  });

  it('accepts a bare array as well as a page, so a mock backend serves it too', async () => {
    const loaded = registry.load();
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush(TEST_ENTITY_DEFINITIONS);

    expect((await loaded).size).toBe(2);
  });

  /**
   * A deployment may point the root at a host that does not serve this resource — the json-server mock does
   * not — and the shell then renders its "no entity type registered" state. So a failure has to be an
   * ordinary empty answer rather than a rejection that would fail the whole navigation.
   */
  it('treats a failed fetch as no definitions', async () => {
    const loaded = registry.load();
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush('nope', { status: 500, statusText: 'Server Error' });

    expect((await loaded).size).toBe(0);
  });

  it('caches an empty result too, rather than retrying on every route recognition', async () => {
    const loaded = registry.load();
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: [] });
    await loaded;

    await registry.load();
    controller.verify();
  });

  it('re-fetches after a reset, so an edited definition is picked up', async () => {
    const loaded = registry.load();
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: [] });
    await loaded;

    registry.reset();
    const reloaded = registry.load();
    controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: TEST_ENTITY_DEFINITIONS });

    expect((await reloaded).size).toBe(2);
  });

  describe('with no ENTITY_SERVICE_ROOT of its own', () => {
    /** The fallback `BaseConfiguration` documents: the per-feature roots are optional, one host serves all. */
    it('falls back to APP_SERVICE_ROOT', async () => {
      ({ registry, controller } = setup({ APP_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' }));

      const loaded = registry.load();
      controller.expectOne(ENTITY_DEFINITIONS_URL).flush({ content: TEST_ENTITY_DEFINITIONS });

      expect((await loaded).size).toBe(2);
    });

    /**
     * With no root at all the URL would resolve against the document base, and every deployment that
     * rewrites unknown paths to index.html answers 200 with HTML — so no request is issued.
     */
    it('issues no request when the configuration names no root at all', async () => {
      ({ registry, controller } = setup({}));

      expect((await registry.load()).size).toBe(0);
      controller.verify();
    });
  });
});
