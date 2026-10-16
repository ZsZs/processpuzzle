import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { RoleDefinitionMapper } from './role-definition.mapper';
import { WorkflowRoleDescriptionSource } from './role-description.source';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from './test-role-definition';

describe('WorkflowRoleDescriptionSource', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let source: WorkflowRoleDescriptionSource;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }, RoleDefinitionMapper],
    });
    source = TestBed.inject(WorkflowRoleDescriptionSource);
    controller = TestBed.inject(HttpTestingController);
  });

  // Keyed by the definition's id, because that is the realm role name the user's token carries.
  it('describes each catalogued role under its realm role id', async () => {
    const pending = firstValueFrom(source.describeRoles());

    controller.expectOne(`${serviceRoot}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);

    const descriptions = await pending;
    expect(descriptions.get('clerk')).toEqual({ name: 'Order Clerk', description: ROLE_DEFINITION_DTO.description });
    expect(descriptions.get('manager')).toEqual({ name: 'Order Manager', description: 'Approves orders for shipment.' });
  });
});
