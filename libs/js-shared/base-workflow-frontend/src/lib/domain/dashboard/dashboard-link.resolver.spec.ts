import { TestBed } from '@angular/core/testing';
import { EntityRouteRegistry } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it } from 'vitest';
import { ArtifactDefinition, ArtifactType } from '../definition/artifact-definition';
import { ArtifactInstance } from '../execution/workflow-instance';
import { DashboardLinkResolver } from './dashboard-link.resolver';

const BASE_PATHS = new Map<string, string>([
  ['Document', '/design/document'],
  ['Employee', '/employee'],
  ['Artifact Definition', '/design/artifact-definition'],
]);

describe('DashboardLinkResolver', () => {
  let resolver: DashboardLinkResolver;

  beforeEach(() => {
    const registry: Partial<EntityRouteRegistry> = {
      basePath: (entityName: string) => BASE_PATHS.get(entityName),
      detailsPath: (entityName: string, id: string) => (BASE_PATHS.has(entityName) ? `${BASE_PATHS.get(entityName)}/${id}/details` : undefined),
      registeredEntities: () => [...BASE_PATHS.keys()],
    };
    TestBed.configureTestingModule({ providers: [{ provide: EntityRouteRegistry, useValue: registry }] });
    resolver = TestBed.inject(DashboardLinkResolver);
  });

  const contract = { artifactType: ArtifactType.DOCUMENT } as ArtifactDefinition;
  const boundContract = new ArtifactInstance({ id: 'a1', artifactDefinitionId: 'contract', type: ArtifactType.DOCUMENT, entityId: 'c-1' });
  const subject = { entityType: 'employee', entityId: 'e-1' };

  /** One contract document serves every run, so the run's subject is what says whose contract is signed. */
  it('links a bound document of a run with a subject to its Sign tab, carrying the subject', () => {
    expect(resolver.artifactLink('contract', contract, boundContract, subject)).toBe('/design/document/c-1/sign');
    expect(resolver.artifactQueryParams(contract, boundContract, subject)).toEqual({ subjectType: 'employee', subjectId: 'e-1' });
  });

  it('links a document to its details when the run has no subject', () => {
    expect(resolver.artifactLink('contract', contract, boundContract)).toBe('/design/document/c-1/details');
    expect(resolver.artifactLink('contract', contract, boundContract, { entityType: 'employee' })).toBe('/design/document/c-1/details');
    expect(resolver.artifactQueryParams(contract, boundContract)).toBeUndefined();
  });

  it('adds no subject to an entity artifact', () => {
    const employee = { artifactType: ArtifactType.ENTITY, artifactTypeId: 'employee' } as ArtifactDefinition;
    const bound = new ArtifactInstance({ id: 'a2', artifactDefinitionId: 'employee', type: ArtifactType.ENTITY, entityId: 'e-1' });

    expect(resolver.artifactLink('employee', employee, bound, subject)).toBe('/employee/e-1/details');
    expect(resolver.artifactQueryParams(employee, bound, subject)).toBeUndefined();
  });

  it('falls back to the artifact definition for a document nothing has bound yet', () => {
    expect(resolver.artifactLink('contract', contract, undefined, subject)).toBe('/design/artifact-definition/contract/details');
    expect(resolver.artifactQueryParams(contract, undefined, subject)).toBeUndefined();
  });
});
