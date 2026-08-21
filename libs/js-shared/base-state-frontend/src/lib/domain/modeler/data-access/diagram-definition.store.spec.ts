import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramDefinition, NodeLayout, Point } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/models/diagram-definition';
import { DiagramDefinitionStore } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/data-access/diagram-definition.store';
import { DIAGRAM_DEFINITION_DTO, OTHER_DIAGRAM_DEFINITION_DTO, pageOfDiagramDefinitions } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/data-access/test-diagram-definition';

describe('DiagramDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof DiagramDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(DiagramDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/diagrams`).flush(pageOfDiagramDefinitions(DIAGRAM_DEFINITION_DTO, OTHER_DIAGRAM_DEFINITION_DTO));
  });

  it('loads the layouts of the organization on init', () => {
    expect(store.entities().map((layout) => layout.id)).toEqual(['order', 'dynamic-entity']);
  });

  it('makes the opened machine layout current', async () => {
    const pending = store.loadLayout('order');
    controller.expectOne(`${serviceRoot}/diagrams/order`).flush({ ...DIAGRAM_DEFINITION_DTO, version: 4 });

    await pending;
    expect(store.currentId()).toBe('order');
    expect(store.currentEntity()?.version).toBe(4);
    expect(store.error()).toBeUndefined();
    expect(store.isLoading()).toBe(false);
  });

  it('replaces the listed layout with the one just read, rather than appending a second row', async () => {
    const pending = store.loadLayout('order');
    controller.expectOne(`${serviceRoot}/diagrams/order`).flush({ ...DIAGRAM_DEFINITION_DTO, version: 4 });

    await pending;
    expect(store.entities()).toHaveLength(2);
    expect(store.entities()[0].version).toBe(4);
  });

  // A machine with no layout is the normal starting point: the modeler falls back to an automatic
  // layout, so this must not read as a failure.
  it('reports a never-arranged machine as no current layout and no error', async () => {
    const pending = store.loadLayout('order');
    controller.expectOne(`${serviceRoot}/diagrams/order`).flush(null, { status: 404, statusText: 'Not Found' });

    expect(await pending).toBeUndefined();
    expect(store.currentEntity()).toBeUndefined();
    expect(store.currentId()).toBeUndefined();
    expect(store.error()).toBeUndefined();
    expect(store.entities()).toHaveLength(2);
  });

  it('surfaces a genuine read failure through error rather than by throwing', async () => {
    const pending = store.loadLayout('order');
    controller.expectOne(`${serviceRoot}/diagrams/order`).flush({ errorId: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(await pending).toBeUndefined();
    expect(store.error()).toBeDefined();
    expect(store.isLoading()).toBe(false);
  });

  it('saves an arrangement with one upsert and keeps the version the server assigned', async () => {
    const layout = new DiagramDefinition({ entityName: 'order', nodes: [new NodeLayout({ stateKey: 'DRAFT', position: new Point({ x: 40, y: 80 }) })] });

    const pending = store.saveLayout(layout);

    const request = controller.expectOne(`${serviceRoot}/diagrams/order`);
    expect(request.request.method).toBe('PUT');
    request.flush({ ...DIAGRAM_DEFINITION_DTO, version: 4 });

    expect((await pending)?.version).toBe(4);
    expect(store.entities()[0].version).toBe(4);
    expect(store.currentEntity()?.version).toBe(4);
  });

  it('appends the layout of a machine arranged for the first time', async () => {
    const pending = store.saveLayout(new DiagramDefinition({ entityName: 'invoice' }));
    controller.expectOne(`${serviceRoot}/diagrams/invoice`).flush({ entityName: 'invoice', version: 1 });

    await pending;
    expect(store.entities().map((entity) => entity.id)).toEqual(['order', 'dynamic-entity', 'invoice']);
  });

  it('surfaces a rejected save through error and leaves the list alone', async () => {
    const pending = store.saveLayout(new DiagramDefinition({ entityName: 'order' }));
    controller.expectOne(`${serviceRoot}/diagrams/order`).flush({ errorId: 'conflict' }, { status: 409, statusText: 'Conflict' });

    expect(await pending).toBeUndefined();
    expect(store.error()).toBeDefined();
    expect(store.entities()[0].version).toBe(3);
  });

  it('discards a layout through the inherited delete, which is the re-arrange-from-scratch gesture', async () => {
    const pending = store.delete('order');
    controller.expectOne(`${serviceRoot}/diagrams/order`).flush(null);

    await pending;
    expect(store.entities().map((entity) => entity.id)).toEqual(['dynamic-entity']);
  });
});
