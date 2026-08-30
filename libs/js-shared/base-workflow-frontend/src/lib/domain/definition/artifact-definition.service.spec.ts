import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ArtifactDefinition } from './artifact-definition';
import { ArtifactDefinitionMapper } from './artifact-definition.mapper';
import { ArtifactDefinitionService } from './artifact-definition.service';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from './test-artifact-definition';

describe('ArtifactDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: ArtifactDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } },
        ArtifactDefinitionMapper,
        ArtifactDefinitionService,
      ],
    });
    service = TestBed.inject(ArtifactDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // A catalog resource of its own: one process produces the invoice, another consumes it, and both
  // name the same record.
  it('reads the artifacts of the organization from their own collection', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/artifacts`).flush([ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO]);

    expect(await pending).toHaveLength(2);
  });

  it('addresses a single artifact by its author-chosen id', () => {
    service.delete('order-entity').subscribe();

    controller.expectOne(`${serviceRoot}/artifacts/order-entity`).flush(null);
  });

  it('sends the bindings into base-entity and base-state on update', () => {
    const entity = new ArtifactDefinitionMapper().fromDto(ARTIFACT_DEFINITION_DTO) as PersistedEntity<ArtifactDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/artifacts/order-entity`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ type: 'ENTITY', entityTypeId: 'order', stateMachineId: 'order' });
    request.flush(ARTIFACT_DEFINITION_DTO);
  });
});
