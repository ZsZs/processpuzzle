import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ToolDefinition } from './tool-definition';
import { ToolDefinitionMapper } from './tool-definition.mapper';
import { ToolDefinitionService } from './tool-definition.service';
import { pageOfToolDefinitions, TOOL_DEFINITION_DTO } from './test-tool-definition';

describe('ToolDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: ToolDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } },
        ToolDefinitionMapper,
        ToolDefinitionService,
      ],
    });
    service = TestBed.inject(ToolDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // A resource of its own rather than a sub-resource of a process: a tool is shared across definitions.
  it('reads the tools of the organization from their own collection', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/tools`).flush(pageOfToolDefinitions(TOOL_DEFINITION_DTO));

    const result = await pending;
    expect(result).toMatchObject({ totalElements: 1 });
  });

  it('addresses a single tool by its author-chosen id', () => {
    service.delete('automated-check-tool').subscribe();

    controller.expectOne(`${serviceRoot}/tools/automated-check-tool`).flush(null);
  });

  // The nested `auth` object has to arrive re-nested, and the chips have to arrive as numbers — both are
  // the mapper's work, and this is where it reaches the wire.
  it('sends the re-nested auth block and numeric status codes on update', () => {
    const entity = new ToolDefinitionMapper().fromDto(TOOL_DEFINITION_DTO) as PersistedEntity<ToolDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/tools/automated-check-tool`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.auth).toEqual({ type: 'BEARER_TOKEN', secretRef: 'AUTOMATED_CHECK_TOKEN' });
    expect(request.request.body.type).toBeUndefined();
    expect(request.request.body.operations[1].expectedStatusCodes).toEqual([200, 201]);
    request.flush(TOOL_DEFINITION_DTO);
  });
});
