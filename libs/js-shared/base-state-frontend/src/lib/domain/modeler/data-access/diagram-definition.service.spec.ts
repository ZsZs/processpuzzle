import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramDefinition, NodeLayout, Point } from '../models/diagram-definition';
import { DiagramDefinitionMapper } from './diagram-definition.mapper';
import { DiagramDefinitionService } from './diagram-definition.service';
import { DIAGRAM_DEFINITION_DTO, OTHER_DIAGRAM_DEFINITION_DTO, pageOfDiagramDefinitions } from '../models/test-diagram-definition';

describe('DiagramDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: DiagramDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: serviceRoot } } },
        DiagramDefinitionMapper,
        DiagramDefinitionService,
      ],
    });
    service = TestBed.inject(DiagramDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('lists the layouts of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/diagrams`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfDiagramDefinitions(DIAGRAM_DEFINITION_DTO, OTHER_DIAGRAM_DEFINITION_DTO));

    const result = await pending;
    expect((result as { content: DiagramDefinition[] }).content.map((layout) => layout.id)).toEqual(['order', 'dynamic-entity']);
  });

  it('reads one layout by the entity name its machine governs', async () => {
    const pending = firstValueFrom(service.findByEntityName('order'));

    const request = controller.expectOne(`${serviceRoot}/diagrams/order`);
    expect(request.request.method).toBe('GET');
    request.flush(DIAGRAM_DEFINITION_DTO);

    const layout = await pending;
    expect(layout?.nodes).toHaveLength(2);
    expect(layout?.edges[0].sourcePort).toBe('port-right');
  });

  // 404 is how the modeler learns to fall back to an automatic layout, not a failure to report.
  it('reports a machine that has never been arranged as an absent layout', async () => {
    const pending = firstValueFrom(service.findByEntityName('order'));

    controller.expectOne(`${serviceRoot}/diagrams/order`).flush({ errorId: 'notFound' }, { status: 404, statusText: 'Not Found' });

    expect(await pending).toBeUndefined();
  });

  it('still fails on any other status', async () => {
    const pending = firstValueFrom(service.findByEntityName('order'));

    controller.expectOne(`${serviceRoot}/diagrams/order`).flush({ errorId: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toMatchObject({ status: 403 });
  });

  it('writes a layout with the contract upsert rather than a POST', async () => {
    const layout = new DiagramDefinition({ entityName: 'order', nodes: [new NodeLayout({ stateKey: 'DRAFT', position: new Point({ x: 40, y: 80 }) })] });

    const pending = firstValueFrom(service.save(layout));

    const request = controller.expectOne(`${serviceRoot}/diagrams/order`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.nodes).toEqual([{ stateKey: 'DRAFT', position: { x: 40, y: 80 } }]);
    request.flush({ ...DIAGRAM_DEFINITION_DTO, version: 4 });

    expect((await pending).version).toBe(4);
  });

  // The store's generic paths and the modeler's own gesture have to land on the same request, since
  // the endpoint answers 201 or 200 by itself and no caller gets to choose.
  it('routes a first arrangement through the same upsert as a later one', () => {
    const layout = new DiagramDefinition({ entityName: 'order' });

    service.add(layout).subscribe();
    controller.expectOne((request) => request.url === `${serviceRoot}/diagrams/order` && request.method === 'PUT').flush(DIAGRAM_DEFINITION_DTO);

    service.update(layout as PersistedEntity<DiagramDefinition>).subscribe();
    controller.expectOne((request) => request.url === `${serviceRoot}/diagrams/order` && request.method === 'PUT').flush(DIAGRAM_DEFINITION_DTO);
  });

  it('refuses to write a layout that names no state machine', () => {
    expect(() => service.save(new DiagramDefinition())).toThrow(/entity name/);
  });

  it('discards a layout through the entity name, resetting the machine to an automatic one', () => {
    service.delete('order').subscribe();

    const request = controller.expectOne(`${serviceRoot}/diagrams/order`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
