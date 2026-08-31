import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { RoleDefinitionMapper } from './role-definition.mapper';
import { RoleDefinitionService } from './role-definition.service';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from './test-role-definition';

describe('RoleDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: RoleDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } },
        RoleDefinitionMapper,
        RoleDefinitionService,
      ],
    });
    service = TestBed.inject(RoleDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // A catalog resource of its own, not `/workflows/{id}/roles`: the same role takes part in several
  // workflows, and describing it once is the whole point of the reference model.
  it('reads the roles of the organization from their own collection', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);

    expect(await pending).toHaveLength(2);
  });

  it('addresses a single role by its author-chosen id', () => {
    service.delete('clerk').subscribe();

    controller.expectOne(`${serviceRoot}/roles/clerk`).flush(null);
  });
});
